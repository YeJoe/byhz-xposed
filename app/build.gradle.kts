plugins {
    id("com.android.application")
}

android {
    namespace = "com.byhz.xposed"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byhz.xposed"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Xposed API — 编译时需要，运行时由 LSPosed 提供
    compileOnly("de.robv.android.xposed:api:82")
}
