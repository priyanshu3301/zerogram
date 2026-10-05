plugins {
    id("com.android.library")
}

android {
    namespace = "org.drinkless.tdlib"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    sourceSets {
        getByName("main") {
            java.srcDir("java")
            jniLibs.srcDir("libs")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
