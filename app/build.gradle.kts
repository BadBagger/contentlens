import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.smithware.contentlens"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smithware.contentlens"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2-search-control-fix"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("localRelease") {
            if (!keystorePropertiesFile.exists()) {
                throw GradleException("Release signing requires local keystore.properties. Copy keystore.properties.example and fill it with local-only values.")
            }
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: throw GradleException("Missing storeFile in keystore.properties"))
            storePassword = keystoreProperties.getProperty("storePassword") ?: throw GradleException("Missing storePassword in keystore.properties")
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: throw GradleException("Missing keyAlias in keystore.properties")
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: throw GradleException("Missing keyPassword in keystore.properties")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("localRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
