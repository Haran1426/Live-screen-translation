param(
    [ValidateSet('debug', 'release', 'both')][string]$Variant = 'both',
    [string]$Serial = 'emulator-5554',
    [switch]$SkipBuild,
    [ValidateSet('privacy', 'revoke-overlay', 'screen-lock', 'remove-task', 'reading-limits', 'reading-content', 'reading-rotation', 'reading-settings', 'network-guidance')][string]$ReleaseScenario = 'privacy',
    [ValidateSet('privacy', 'multilingual', 'mixed-reading')][string]$DebugSuite = 'privacy',
    [ValidateSet('en', 'ko', 'ja', 'zh')][string]$ReleaseSource = 'en',
    [ValidateSet('auto', 'en', 'ko', 'ja', 'zh')][string]$ReleaseTarget = 'auto',
    [switch]$ReuseInstalled
)

$ErrorActionPreference = 'Stop'
if ($ReleaseScenario -ne 'privacy' -and $Variant -ne 'release') {
    throw 'Release scenarios require -Variant release.'
}
if ($DebugSuite -ne 'privacy' -and $Variant -ne 'debug') {
    throw 'Multilingual fixtures require -Variant debug.'
}
if ($ReleaseSource -ne 'en' -and ($Variant -ne 'release' -or $ReleaseScenario -notin @('privacy', 'reading-limits', 'reading-content', 'reading-rotation'))) {
    throw 'A non-English source requires -Variant release with a reading scenario.'
}
if ($ReleaseTarget -ne 'auto' -and ($Variant -ne 'release' -or $ReleaseScenario -notin @('privacy', 'reading-limits', 'reading-content', 'reading-rotation'))) {
    throw 'An explicit target requires -Variant release with a reading scenario.'
}
if ($ReleaseTarget -eq 'auto') { $ReleaseTarget = if ($ReleaseSource -eq 'ko') { 'en' } else { 'ko' } }
if ($ReleaseScenario -eq 'reading-limits' -and ($ReleaseSource -ne 'en' -or $ReleaseTarget -ne 'ko')) {
    throw 'The quota fixture requires en-to-ko. Use reading-content for other long reading cases.'
}
if ($ReleaseSource -eq $ReleaseTarget) { throw 'Source and target must be different.' }
if ($ReuseInstalled -and (-not $SkipBuild -or $Variant -ne 'release')) {
    throw 'Reusing installed fixtures requires -Variant release -SkipBuild.'
}
if ($Serial -notmatch '^emulator-\d+$') {
    throw 'Privacy regression runs only on an emulator with authored fixture text.'
}
if ($ReleaseScenario -eq 'network-guidance' -and $Serial -ne 'emulator-5556') {
    throw 'Network switching is allowed only on the owned API30 QA emulator.'
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$flutter = 'C:\Develop\flutter\bin\flutter.bat'
$adb = 'C:\Users\hanin\AppData\Local\Android\sdk\platform-tools\adb.exe'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $Executable" }
}

function Get-FixtureApk([string]$BuildType) {
    if ($BuildType -eq 'debug') {
        $candidates = @('build/app/outputs/flutter-apk/app-debug.apk', 'build/app/outputs/apk/debug/app-debug.apk')
    } else {
        $abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
        if ($abi -notin @('x86_64', 'arm64-v8a', 'armeabi-v7a')) { throw 'Unsupported fixture ABI.' }
        $candidates = @("build/app/outputs/flutter-apk/app-$abi-release.apk", "build/app/outputs/apk/release/app-$abi-release.apk", 'build/app/outputs/apk/release/app-release.apk', 'build/app/outputs/flutter-apk/app-release.apk')
    }
    foreach ($candidate in $candidates) { if (Test-Path -LiteralPath $candidate) { return $candidate } }
    throw "No $BuildType fixture APK found. Build the application first."
}

function Assert-PrebuiltFixtures([string]$AppPath, [string]$TestPath, [string]$BuildType) {
    if (-not (Test-Path -LiteralPath $TestPath)) { throw 'The instrumentation APK must be built first.' }
    $sources = @(& rg --files lib android/app/src/main assets -g '!*.md') + @(
        'pubspec.yaml', 'pubspec.lock', 'android/app/build.gradle.kts', 'android/app/proguard-rules.pro',
        'android/build.gradle.kts', 'android/settings.gradle.kts', 'android/gradle.properties'
    )
    $appTime = (Get-Item -LiteralPath $AppPath).LastWriteTimeUtc
    if ($sources | Where-Object { (Get-Item -LiteralPath $_).LastWriteTimeUtc -gt $appTime }) {
        throw 'Application sources are newer than the prebuilt APK. Rebuild before checking privacy.'
    }
    $testSources = @(& rg --files "android/app/src/androidTest$((Get-Culture).TextInfo.ToTitleCase($BuildType))")
    $testTime = (Get-Item -LiteralPath $TestPath).LastWriteTimeUtc
    if ($testSources | Where-Object { (Get-Item -LiteralPath $_).LastWriteTimeUtc -gt $testTime }) {
        throw 'Instrumentation sources are newer than the prebuilt test APK.'
    }
    $abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $AppPath))
    try {
        if (-not $zip.GetEntry("lib/$abi/libscreenlingo_privacy.so")) {
            throw 'The selected application APK does not include the required native log protection.'
        }
    } finally { $zip.Dispose() }
    'Verified prebuilt fixture SHA256: ' + (Get-FileHash -LiteralPath $AppPath -Algorithm SHA256).Hash
}

function Wait-AndroidDevice {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = & $adb -s $Serial get-state 2>&1
        if ($LASTEXITCODE -eq 0 -and $state -contains 'device') { return }
        Start-Sleep -Milliseconds 250
    }
    throw 'Emulator did not reconnect within 15 seconds.'
}

function Assert-InstalledFixture([string]$PackageName, [string]$LocalPath) {
    $paths = @(& $adb -s $Serial shell pm path $PackageName)
    if ($LASTEXITCODE -ne 0 -or $paths.Count -ne 1 -or
        $paths[0] -notmatch '^package:(/data/app/[A-Za-z0-9_~+=./-]+\.apk)$') {
        throw 'Cannot verify the installed fixture APK path.'
    }
    $remotePath = $Matches[1]
    $checksum = (& $adb -s $Serial shell sha256sum $remotePath) -join ''
    if ($LASTEXITCODE -ne 0 -or $checksum -notmatch '^([A-Fa-f0-9]{64})\s') {
        throw 'Cannot verify the installed fixture APK checksum.'
    }
    if ($Matches[1] -ne (Get-FileHash -LiteralPath $LocalPath -Algorithm SHA256).Hash) {
        throw 'The installed fixture differs from the selected local APK. Install it before reusing.'
    }
    "Verified installed fixture: $PackageName"
}

function Install-FixtureApk([string]$Path, [switch]$TestOnly, [switch]$Debuggable) {
    $arguments = @('-s', $Serial, 'install', '-r')
    # Flutter split releases use ABI-prefixed version codes (e.g. 4002 vs debug 2).
    # Public ADB support for debuggable packages preserves downloaded fixture models.
    if ($Debuggable) { $arguments += '-d' }
    if ($TestOnly) { $arguments += '-t' }
    $arguments += $Path
    $result = & $adb @arguments 2>&1
    if ($LASTEXITCODE -ne 0 -and -not ($result -match 'INSTALL_FAILED_')) {
        Wait-AndroidDevice
        $result = & $adb @arguments 2>&1
    }
    $result | Write-Output
    if ($LASTEXITCODE -ne 0) { throw 'Fixture APK installation failed.' }
}

function Invoke-PrivacyTest([string]$Runner, [string]$Scenario = '', [string]$TestClass = '', [int]$ExpectedTests = 1) {
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Scenario) { $arguments += @('-e', 'scenario', $Scenario) }
    if ($Scenario) { $arguments += @('-e', 'source', $ReleaseSource) }
    if ($Scenario) { $arguments += @('-e', 'target', $ReleaseTarget) }
    if ($TestClass) { $arguments += @('-e', 'class', $TestClass) }
    $arguments += $Runner
    $result = & $adb @arguments 2>&1
    # This emulator occasionally reconnects ADB immediately after package install.
    # Retry transport interruption once, never retry a reported assertion failure.
    if ($LASTEXITCODE -ne 0 -and -not ($result -match 'INSTRUMENTATION_(RESULT|CODE):')) {
        Wait-AndroidDevice
        $result = & $adb @arguments 2>&1
    }
    $result | Write-Output
    if (-not ($result -match "OK \($ExpectedTests tests?\)")) { throw 'Privacy regression did not pass.' }
}

Push-Location $projectRoot
try {
    Invoke-Checked $adb @('-s', $Serial, 'get-state')
    if ($ReleaseScenario -eq 'network-guidance') {
        $identity = & $adb -s $Serial emu avd name
        if ($LASTEXITCODE -ne 0 -or -not ($identity -contains 'ScreenLingo_API30')) {
            throw 'Network guidance QA requires the owned ScreenLingo_API30 AVD.'
        }
    }
    $api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    "Privacy fixture device: $Serial, API $api"
    if ($Variant -in @('debug', 'both')) {
        if (-not $SkipBuild) {
            # Some OS images refuse release → debug downgrades even with -d.
            # Reuse the installed fixture's code for this debug build, preserving models.
            # The product's release build still uses the version from pubspec.yaml.
            $debugBuildArguments = @('build', 'apk', '--debug')
            $installedPackage = (& $adb -s $Serial shell dumpsys package com.example.livescreentranslation) -join "`n"
            if ($installedPackage -match 'versionCode=(\d+)') {
                $debugBuildArguments += "--build-number=$($Matches[1])"
            }
            Invoke-Checked $flutter $debugBuildArguments
            Push-Location android
            try { Invoke-Checked '.\gradlew.bat' @(':app:assembleDebugAndroidTest', '--console=plain') }
            finally { Pop-Location }
        }
        $appApk = Get-FixtureApk 'debug'
        $testApk = 'build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        if ($SkipBuild) { Assert-PrebuiltFixtures $appApk $testApk 'debug' }
        Install-FixtureApk $appApk -Debuggable
        Install-FixtureApk $testApk -TestOnly
        # Keep the default privacy run bounded; new suites are explicitly selected.
        $testClass = switch ($DebugSuite) { 'multilingual' { 'MultilingualPipelineTest' }; 'mixed-reading' { 'MixedReadingTest' }; default { 'PrivacyPipelineTest' } }
        $testCount = switch ($DebugSuite) { 'multilingual' { 12 }; 'mixed-reading' { 4 }; default { 1 } }
        Invoke-PrivacyTest 'com.example.livescreentranslation.test/androidx.test.runner.AndroidJUnitRunner' -TestClass "com.example.livescreentranslation.$testClass" -ExpectedTests $testCount
    }
    if ($Variant -in @('release', 'both')) {
        # A release-only run requires English/Korean models prepared by the debug fixture.
        if (-not $SkipBuild) {
            Invoke-Checked $flutter @('build', 'apk', '--release', '--split-per-abi')
            Push-Location android
            try { Invoke-Checked '.\gradlew.bat' @(':app:assembleReleaseAndroidTest', '-PscreenlingoTestBuildType=release', '--console=plain') }
            finally { Pop-Location }
        }
        $appApk = Get-FixtureApk 'release'
        $testApk = 'build/app/outputs/apk/androidTest/release/app-release-androidTest.apk'
        if ($SkipBuild) { Assert-PrebuiltFixtures $appApk $testApk 'release' }
        if ($ReuseInstalled) {
            Assert-InstalledFixture 'com.example.livescreentranslation' $appApk
            Assert-InstalledFixture 'com.example.livescreentranslation.test' $testApk
        } else {
            Install-FixtureApk $appApk
            Install-FixtureApk $testApk -TestOnly
        }
        Invoke-Checked $adb @('-s', $Serial, 'shell', 'appops', 'set', 'com.example.livescreentranslation', 'SYSTEM_ALERT_WINDOW', 'allow')
        Invoke-PrivacyTest 'com.example.livescreentranslation.test/com.example.livescreentranslation.ReleasePrivacyInstrumentation' $ReleaseScenario
    }
} finally { Pop-Location }
