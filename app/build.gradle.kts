plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull

val debugKeystorePath = signingProperty("HERMES_DEBUG_KEYSTORE_PATH")
val debugKeystorePassword = signingProperty("HERMES_DEBUG_KEYSTORE_PASSWORD")
val debugKeyAlias = signingProperty("HERMES_DEBUG_KEY_ALIAS")
val debugKeyPassword = signingProperty("HERMES_DEBUG_KEY_PASSWORD")
val releaseKeystorePath = signingProperty("HERMES_RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword = signingProperty("HERMES_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProperty("HERMES_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProperty("HERMES_RELEASE_KEY_PASSWORD")

val hasDebugSigning = debugKeystorePath != null &&
    debugKeystorePassword != null &&
    debugKeyAlias != null &&
    debugKeyPassword != null &&
    file(debugKeystorePath).isFile
val hasReleaseSigning = releaseKeystorePath != null &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    file(releaseKeystorePath).isFile

check(signingProperty("HERMES_REQUIRE_DEBUG_SIGNING") != "true" || hasDebugSigning) {
    "Stable debug signing was required, but the Hermes debug keystore configuration is incomplete."
}
check(signingProperty("HERMES_REQUIRE_RELEASE_SIGNING") != "true" || hasReleaseSigning) {
    "Release signing was required, but the Hermes release keystore configuration is incomplete."
}

android {
    namespace = "com.nousresearch.hermes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nousresearch.hermes"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "AUDITED_HERMES_COMMIT", "\"5122ddd478143a6901bb752cf8ebcd1c5154b6da\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasDebugSigning) {
            getByName("debug") {
                storeFile = file(debugKeystorePath!!)
                storePassword = debugKeystorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "META-INF/DEPENDENCIES",
    )

    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markdown.code)
    implementation(libs.markdown.m3)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
