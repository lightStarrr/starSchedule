import org.gradle.api.tasks.wrapper.Wrapper

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.legacy.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.8.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionUrl = "https://services.gradle.org/distributions/gradle-9.8.0-bin.zip"
    distributionSha256Sum = "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"
    networkTimeout = 10_000
    retries = 0
    retryBackOffMs = 500
    validateDistributionUrl = false
}
