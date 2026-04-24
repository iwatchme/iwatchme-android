plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("custom-gradle-plugin")
}

android {
    ndkVersion = "27.2.12479018"
    namespace = "com.iwatchme.jetpackstarter"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.iwatchme.jetpackstarter"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }

    lint {
        baseline = file("lint-baseline.xml")
        disable.add("OldTargetApi")
    }
}

customGradlePlugin {
    enableThreadDetect = true
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)

    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.navigation)
    implementation(libs.activity.ktx)

    implementation(project(":crashLib"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
