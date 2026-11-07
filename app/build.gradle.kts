plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ng.pixelslimited.logoxels"
    compileSdk = 35

    defaultConfig {
        applicationId = "ng.pixelslimited.logoxels"
        minSdk = 26
        targetSdk = 35
        // CI passes -PbuildNumber so every release has a higher versionCode than the last.
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // Release key comes from the environment (GitHub secrets in CI); never committed.
    val keyFile = System.getenv("LOGOXELS_KEYSTORE")
    val keyPass = System.getenv("LOGOXELS_STORE_PASS")
    if (keyFile != null && keyPass != null) {
        signingConfigs {
            create("shared") {
                storeFile = file(keyFile)
                storePassword = keyPass
                keyAlias = "logoxels"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug { signingConfigs.findByName("shared")?.let { signingConfig = it } }
        release {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.graphics:graphics-path:1.0.1")
    testImplementation("junit:junit:4.13.2")
}
