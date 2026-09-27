plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.zerogram.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        targetSdk = 32
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    targetProjectPath = ":app"
}



dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
    implementation(libs.androidx.junit)
}
