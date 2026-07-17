import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyReleaseConfigurationTask : DefaultTask() {
    @get:Input
    abstract val versionNameInput: Property<String>

    @get:Input
    abstract val versionCodeInput: Property<String>

    @get:Input
    abstract val signingConfiguredInput: Property<Boolean>

    @get:Input
    abstract val storeFilePathInput: Property<String>

    @TaskAction
    fun verify() {
        val versionName = versionNameInput.get()
        val match = Regex("""1\.0\.(\d+)""").matchEntire(versionName)
            ?: throw GradleException("Release versionName must match 1.0.x")
        val patch = match.groupValues[1].toInt()
        if (patch !in 0..999) {
            throw GradleException("Release patch version must be between 0 and 999")
        }
        val versionCode = versionCodeInput.get().toIntOrNull()
            ?: throw GradleException("Release builds require a numeric -PreleaseVersionCode")
        val expectedVersionCode = 1_000_000 + patch
        if (versionCode != expectedVersionCode) {
            throw GradleException(
                "Release versionCode must be $expectedVersionCode for version $versionName",
            )
        }
        if (!signingConfiguredInput.get()) {
            throw GradleException(
                "Release signing is not configured. Run scripts/setup-release-signing.ps1 " +
                    "or provide the ANDROID_RELEASE_* environment variables.",
            )
        }
        val storeFilePath = storeFilePathInput.get()
        if (!File(storeFilePath).isFile) {
            throw GradleException("Release keystore does not exist: $storeFilePath")
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseVersionNameProperty = providers.gradleProperty("releaseVersionName")
val releaseVersionCodeProperty = providers.gradleProperty("releaseVersionCode")
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseStoreFilePath = providers.environmentVariable("ANDROID_RELEASE_STORE_FILE").orNull
    ?: keystoreProperties.getProperty("storeFile")
val releaseStorePassword =
    providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull
        ?: keystoreProperties.getProperty("storePassword")
val releaseKeyAlias = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
    ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword = providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull
    ?: keystoreProperties.getProperty("keyPassword")
val releaseStoreFile = releaseStoreFilePath?.let(rootProject::file)
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).none { it.isNullOrBlank() }

android {
    namespace = "com.gongpx.aistockanalyst"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gongpx.aistockanalyst"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCodeProperty.orNull?.toIntOrNull() ?: 1
        versionName = releaseVersionNameProperty.orNull ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = requireNotNull(releaseStoreFile)
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }

        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                signingConfig = signingConfigs.getByName("release")
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val verifyReleaseConfiguration = tasks.register<VerifyReleaseConfigurationTask>(
    "verifyReleaseConfiguration",
) {
    group = "verification"
    description = "Verifies release versioning and signing inputs."
    versionNameInput.set(releaseVersionNameProperty.orElse(""))
    versionCodeInput.set(releaseVersionCodeProperty.orElse(""))
    signingConfiguredInput.set(releaseSigningConfigured)
    storeFilePathInput.set(releaseStoreFile?.absolutePath.orEmpty())
}

tasks.matching {
    it.name == "packageRelease" || it.name == "packageReleaseBundle"
}.configureEach {
    dependsOn(verifyReleaseConfiguration)
}
