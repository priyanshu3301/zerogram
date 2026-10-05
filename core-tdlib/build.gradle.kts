plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zerogram.core.tdlib"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "SECURE_LOG_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "SECURE_LOG_ENABLED", "false")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":tdlib"))
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
