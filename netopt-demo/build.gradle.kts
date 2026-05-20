plugins {
    id("iwatchme.android.application.compose")
}
// NOTE: Wire's Gradle plugin (both 4.9.9 and 5.0.0) still references the
// internal class FileOrUriNotationConverter, removed in Gradle 8.13. Until
// upstream catches up we do a hand-written protobuf reader for feed.proto.

android {
    namespace = "com.iwatchme.netopt"

    defaultConfig {
        applicationId = "com.iwatchme.netopt"
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)

    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.navigation)
    implementation(libs.compose.viewmodel)

    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // E5 encoding experiment
    implementation(libs.brotli.dec)
    // E8 image experiment — Coil for async load + decode
    implementation(libs.image)

    testImplementation(libs.junit)
}
