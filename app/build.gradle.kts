plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gongpx.aistockanalyst"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gongpx.aistockanalyst"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
}
