plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.terrace"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = "android123"
            keyAlias = "androidagent"
            keyPassword = "android123"
        }
    }

    defaultConfig {
        applicationId = "com.terrace"
        minSdk = 26
        targetSdk = 35
        // 每次 CI 构建用递增的 run number 作为 versionCode，保证可覆盖安装/升级
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM (Compose 1.8.3 / Material3 1.3.2)
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Room (SQLite)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Markdown (原生 Compose 渲染：GFM 表格 / 代码高亮 / 链接 / 图片)
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.38.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.38.1")
}
