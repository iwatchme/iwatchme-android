plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("custom-gradle-plugin")
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace="com.iwatchme.jetpackstarter"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.iwatchme.jetpackstarter"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName="1.0"

        testInstrumentationRunner ="androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures.apply {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled =false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    /**
     * https://developer.android.com/jetpack/androidx/releases/compose-kotlin
     * compose和kotlin版本的对应关系
     * https://www.jetpackcomposeversion.com/
     * compose的最新版本号
     * */
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0-alpha02"
    }

    lint {
        baseline = file("lint-baseline.xml")
        disable.apply {
            add("OldTargetApi")
        }
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
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.navigation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.constraintlayout)
    implementation(libs.permission)
    implementation(libs.image)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.core)
    implementation(libs.startup)
    implementation(project(":crashLib"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}