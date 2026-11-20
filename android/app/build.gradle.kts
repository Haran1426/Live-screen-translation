import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Properties
import javax.naming.ldap.LdapName

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val privacyTestBuildType = providers.gradleProperty("screenlingoTestBuildType").getOrElse("debug")
val qaDebugSigning = providers.gradleProperty("screenlingoQaDebugSigning").orNull == "true"
val releaseKeyPropertiesFile = providers.environmentVariable("SCREENLINGO_KEY_PROPERTIES")
    .orNull?.let { rootProject.file(it) } ?: rootProject.file("key.properties")
val releaseKeyProperties = Properties().apply {
    if (releaseKeyPropertiesFile.isFile) releaseKeyPropertiesFile.inputStream().use { load(it) }
}
val releaseSigningFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val releaseSigningAvailable = releaseSigningFields.all {
    !releaseKeyProperties.getProperty(it).isNullOrBlank()
}
val releaseStoreFile = releaseKeyProperties.getProperty("storeFile")?.let {
    releaseKeyPropertiesFile.parentFile.resolve(it)
}

// Optimized QA APKs deliberately share the debug identity to retain fixture models.
// Distribution builds must never silently fall back to that identity.
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Reject missing, invalid, or debug distribution signing credentials."
    doLast {
        if (qaDebugSigning) {
            logger.warn("SCREENLINGO_QA_ONLY: debug-signed optimized APK; not for distribution.")
        } else {
            if (!releaseSigningAvailable || releaseStoreFile?.isFile != true) {
                throw GradleException(
                    "SCREENLINGO_RELEASE_SIGNING_REQUIRED: configure private key.properties " +
                        "or SCREENLINGO_KEY_PROPERTIES. Debug signing is available only for " +
                        "authored QA with -PscreenlingoQaDebugSigning=true."
                )
            }
            try {
                val password = releaseKeyProperties.getProperty("storePassword").toCharArray()
                val keyPassword = releaseKeyProperties.getProperty("keyPassword").toCharArray()
                try {
                    val keyStore = KeyStore.getInstance(releaseStoreFile, password)
                    val alias = releaseKeyProperties.getProperty("keyAlias")
                    check(keyStore.isKeyEntry(alias))
                    check(keyStore.getKey(alias, keyPassword) is java.security.PrivateKey)
                    val certificate = keyStore.getCertificate(alias) as X509Certificate
                    certificate.checkValidity()
                    check(LdapName(certificate.subjectX500Principal.name).rdns.none {
                        it.type.equals("CN", ignoreCase = true) &&
                            it.value.toString().equals("Android Debug", ignoreCase = true)
                    })
                } finally {
                    password.fill('\u0000')
                    keyPassword.fill('\u0000')
                }
            } catch (_: Exception) {
                // Never include exceptions containing private paths or credential values.
                throw GradleException("SCREENLINGO_RELEASE_SIGNING_INVALID: release key validation failed.")
            }
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseSigning)
}

android {
    namespace = "com.example.livescreentranslation"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.livescreentranslation"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // Process-local liblog filtering is a public NDK API starting at Android 11.
        // Do not run the text-processing SDK on older versions without this protection.
        minSdk = 30
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        testInstrumentationRunner = if (privacyTestBuildType == "release") {
            "com.example.livescreentranslation.ReleasePrivacyInstrumentation"
        } else "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testBuildType = privacyTestBuildType

    signingConfigs {
        if (releaseSigningAvailable && !qaDebugSigning) {
            create("distribution") {
                storeFile = releaseStoreFile
                storePassword = releaseKeyProperties.getProperty("storePassword")
                keyAlias = releaseKeyProperties.getProperty("keyAlias")
                keyPassword = releaseKeyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                qaDebugSigning -> signingConfigs.getByName("debug")
                releaseSigningAvailable -> signingConfigs.getByName("distribution")
                else -> null
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    testImplementation("junit:junit:4.13.2")
    if (privacyTestBuildType != "release") {
        androidTestImplementation("androidx.test:runner:1.7.0")
        androidTestImplementation("androidx.test.ext:junit:1.3.0")
    }
}
