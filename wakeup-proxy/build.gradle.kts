import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 与主应用共用同一套签名配置，确保 signature 级 READ_SCHEDULE_EXPORT 权限在 debug/release 下都能校验通过。
val signingProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { stream -> load(stream) } } }

fun signingValue(propertyKey: String, envKey: String) =
    signingProperties?.getProperty(propertyKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(envKey).orNull?.trim()?.takeIf { it.isNotEmpty() }

val storeFilePath = signingValue("storeFile", "STAR_KEYSTORE_FILE")
val storePasswordValue = signingValue("storePassword", "STAR_KEYSTORE_PASSWORD")
val keyAliasValue = signingValue("keyAlias", "STAR_KEY_ALIAS")
val keyPasswordValue = signingValue("keyPassword", "STAR_KEY_PASSWORD")
val releaseSigningReady =
    listOf(storeFilePath, storePasswordValue, keyAliasValue, keyPasswordValue).all { it != null }
val fallbackDebugSigning = providers.gradleProperty("fallbackDebugSigning").orNull.toBoolean()

android {
    namespace = "com.suda.yzune.wakeupschedule"
    compileSdk = 37
    enableKotlin = false

    defaultConfig {
        applicationId = "com.suda.yzune.wakeupschedule"
        minSdk = 33
        targetSdk = 36
        versionCode = 999999
        versionName = "99.99.99"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { fallbackDebugSigning }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
