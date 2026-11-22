plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 版本号由 GitHub 自动打包时传入（每次打包自动加 1）
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.niefeng.caipiao"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.niefeng.caipiao"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
}
