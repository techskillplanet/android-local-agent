plugins {
    id("com.android.application")
}

android {
    namespace = "com.skillplanet.localagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skillplanet.localagent"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 统一 UI：planet-components Android View（本仓 library）
    implementation(project(":planet-components-android"))

    // 本地 GGUF 推理：llama.cpp JNI
    implementation("net.ladenthin:llama-android:5.1.0")
}
