plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zerogram"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zerogram"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
        }
        create("playstore") {
            initWith(getByName("release"))
            matchingFallbacks.add("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

composeCompiler {
    // enableStrongSkippingMode is deprecated, enabled by default in Compose Compiler 2.0
}

dependencies {
    implementation(project(":core-tdlib"))
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":feature-home"))
    implementation(project(":feature-folder"))
    implementation(project(":feature-category"))
    implementation(project(":feature-search"))
    implementation(project(":feature-transfers"))
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation(libs.tink.android)
    implementation(libs.androidx.security.crypto)

    
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.profileinstaller)
}
