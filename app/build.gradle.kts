import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from a keystore.properties file that lives outside
// this repository. local.properties (ignored by Git) points to it with
// recipebox.signing=PATH. Without it, release builds are left unsigned.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signingProperties = localProperties.getProperty("recipebox.signing")
    ?.let { file(it) }
    ?.takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "io.github.isaiahyoder.recipebox"
    compileSdk = 37

    defaultConfig {
        // Never change applicationId: Android treats a new ID as a new app,
        // and the existing app's data can't carry over.
        applicationId = "io.github.isaiahyoder.recipebox"
        minSdk = 26
        targetSdk = 37
        // Increase versionCode for every release, or the update won't install.
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signingProperties != null) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds are signed with this computer's debug key, so they
            // install beside the real app instead of trying to replace it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
