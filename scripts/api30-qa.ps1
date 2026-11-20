param([switch]$Stop)

$ErrorActionPreference = 'Stop'
$qaRoot = 'D:\CodexWork\ScreenLingo-QA-01a1efbc'
$adb = 'C:\Users\hanin\AppData\Local\Android\sdk\platform-tools\adb.exe'
$emulator = 'C:\Users\hanin\AppData\Local\Android\sdk\emulator\emulator.exe'
$serial = 'emulator-5556'
$avdName = 'ScreenLingo_API30'
$state = & $adb -s $serial get-state 2>&1
if ($LASTEXITCODE -eq 0 -and $state -contains 'device') {
    $identity = & $adb -s $serial emu avd name 2>&1
    if (-not ($identity -contains $avdName)) { throw 'Port 5556 belongs to another AVD; leaving it untouched.' }
    if ($Stop) {
        & $adb -s $serial emu kill
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the isolated QA emulator.' }
    } else { 'API30 QA emulator is already available on ' + $serial }
    return
}
$slotOccupied = (& $adb devices) -match '^emulator-5556\s'
if ($Stop) {
    if ($slotOccupied) { throw 'The emulator is offline; its identity cannot be verified for shutdown.' }
    'API30 QA emulator is not running.'
    return
}
if ($slotOccupied) { throw 'Port 5556 is busy or offline; wait rather than starting another process.' }

$image = Join-Path $qaRoot 'sdk\system-images\android-30\google_apis\x86_64'
$ini = 'C:\Users\hanin\.android\avd\ScreenLingo_API30.ini'
$avdPath = Join-Path $qaRoot 'avd-api30'
if (-not (Test-Path -LiteralPath (Join-Path $image 'system.img'))) { throw 'Install the isolated API30 image described in docs/QA_ENVIRONMENT.md first.' }
if (-not (Test-Path -LiteralPath $ini)) { throw 'Create the isolated AVD described in docs/QA_ENVIRONMENT.md first.' }
if (-not ((Get-Content -LiteralPath $ini) -contains ('path=' + $avdPath))) { throw 'The AVD path does not match this project QA environment.' }
$availableMb = (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024
if ($availableMb -lt 3000) { throw 'At least 3 GB of free RAM is required before starting another emulator.' }

$arguments = @('-avd', $avdName, '-sysdir', $image, '-port', '5556', '-no-window', '-no-snapshot', '-noaudio', '-no-boot-anim', '-no-metrics', '-memory', '1536', '-cores', '2', '-gpu', 'swiftshader')
$process = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $qaRoot 'emulator-api30.stdout.log') `
    -RedirectStandardError (Join-Path $qaRoot 'emulator-api30.stderr.log')
$deadline = [DateTime]::UtcNow.AddMinutes(3)
while ([DateTime]::UtcNow -lt $deadline) {
    $boot = & $adb -s $serial shell getprop sys.boot_completed 2>&1
    if ($LASTEXITCODE -eq 0 -and $boot -contains '1') {
        $identity = & $adb -s $serial emu avd name 2>&1
        if (-not ($identity -contains $avdName)) { throw 'Unexpected emulator identity after boot.' }
        # Keep geometry equal to the existing API36 comparison device.
        & $adb -s $serial shell wm size 1080x2400
        if ($LASTEXITCODE -ne 0) { throw 'Could not configure QA display size.' }
        & $adb -s $serial shell wm density 420
        if ($LASTEXITCODE -ne 0) { throw 'Could not configure QA display density.' }
        & $adb -s $serial shell input keyevent KEYCODE_WAKEUP
        & $adb -s $serial shell wm dismiss-keyguard
        'API30 QA emulator is ready on ' + $serial
        return
    }
    Start-Sleep -Milliseconds 500
}
throw "API30 emulator did not complete boot in 3 minutes (launcher PID $($process.Id)). Inspect the isolated emulator logs."
