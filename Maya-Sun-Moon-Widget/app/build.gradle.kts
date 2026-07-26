plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ivanwitt.mayasunmoon"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ivanwitt.mayasunmoon"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.2.8"
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
    // Kept as an offline fallback if the USNO cache has not yet been downloaded.
    implementation("io.github.cosinekitty:astronomy:2.1.19")
}
