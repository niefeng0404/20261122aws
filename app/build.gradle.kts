plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 版本号由 GitHub 自动打包时传入：
// VERSION_CODE 内部编号，每次自动加 1；VERSION_NAME 显示的版本，每位只用 0-9（1.0.9 → 1.1.0）
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()
val buildName = System.getenv("VERSION_NAME") ?: "1.0.$buildNumber"
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.niefeng.caipiao"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.niefeng.caipiao"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = buildName
    }

    // 固定签名：每次更新都用同一把密钥，平板才能直接覆盖安装
    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "caipiao"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
}
