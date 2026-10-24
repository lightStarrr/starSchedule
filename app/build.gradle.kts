import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 版本号只在这里写一份。CI 用 -PversionCode / -PversionNameSuffix 追加覆盖，不修改文件，
// 这样快照包的 versionName 能带上提交哈希，而正式版本的版本号依旧由人工推进。
val baseVersionCode = 120
val baseVersionName = "1.2.0"
val versionCodeValue = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: baseVersionCode
val versionNameValue = baseVersionName + providers.gradleProperty("versionNameSuffix").orNull.orEmpty()

// 每次构建生成一个可追溯的标识：git 短哈希 + 构建时间，配合 versionCode 唯一定位一次打包。
val gitShortSha: String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
        .standardOutput.asText.get().trim()
}.getOrDefault("nogit")
val buildTime: String = LocalDateTime.now()
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
val buildId: String = "$gitShortSha-${buildTime.replace("-", "").replace(" ", "").replace(":", "")}"

/**
 * 签名信息按「keystore.properties → 环境变量」的顺序读取。
 * 本地把 storeFile 写进仓库外的 keystore.properties 即可，CI 从 Secrets 注入环境变量，
 * 两边共用同一份构建脚本；两者都没有时 release 仍产出未签名包，保持原来的行为。
 */
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
// 快照流水线在仓库还没配好密钥时要能出包，用调试签名顶上；正式发布不允许走这条路。
val fallbackDebugSigning = providers.gradleProperty("fallbackDebugSigning").orNull.toBoolean()

android {
    namespace = "com.star.schedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.star.schedule"
        minSdk = 33
        targetSdk = 36
        versionCode = versionCodeValue
        versionName = versionNameValue

        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
        buildConfigField(
            "String",
            "BUILD_STAMP",
            "\"$versionName ($versionCode) · $gitShortSha · $buildTime\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

//        ndk {
//            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
//        }
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
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 拿不到正式签名时保持未签名；显式传 fallbackDebugSigning 才用调试签名兜底。
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug").takeIf { fallbackDebugSigning }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // M3E alpha28 brings Compose 1.13 lint, which flags existing context resource reads.
        disable += "LocalContextGetResourceValueCall"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.color.picker)
    implementation(libs.androidx.room.runtime) {
        exclude(group = "com.intellij", module = "annotations")
    }
    implementation(libs.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    kapt(libs.androidx.room.compiler) {
        exclude(group = "com.intellij", module = "annotations")
    }
    implementation(libs.androidx.room.ktx) {
        exclude(group = "com.intellij", module = "annotations")
    }
    implementation(libs.poi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * 每次 release 混淆完成后把 mapping.txt 归档到仓库外的 mappings/ 目录。
 * build/ 会被 clean 掉，历史版本的堆栈就再也还原不了了。
 */
val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
val archiveMapping = tasks.register<Copy>("archiveMapping") {
    from(mappingFile)
    into(rootProject.layout.projectDirectory.dir("mappings"))
    rename { "mapping-$buildId.txt" }
    onlyIf { mappingFile.get().asFile.exists() }
    doFirst { logger.lifecycle("归档 mapping：mappings/mapping-$buildId.txt") }
}

tasks.matching { it.name == "minifyReleaseWithR8" }.configureEach {
    finalizedBy(archiveMapping)
}
