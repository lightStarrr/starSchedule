plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.suda.yzune.wakeupschedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suda.yzune.wakeupschedule"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
