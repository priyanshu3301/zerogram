plugins {
    id("com.android.library")
}

android {
    namespace = "org.drinkless.tdlib"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    sourceSets {
        getByName("main") {
            java.srcDir("java")
            jniLibs.srcDir("libs")
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
