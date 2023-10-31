plugins {
    id ("com.android.application")
    id ("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
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

    buildFeatures {
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

}

dependencies {
    implementation(baselibs.appcompat)
    implementation(baselibs.core.ktx)
    implementation(baselibs.material)
    testImplementation(baselibs.junit)
    androidTestImplementation(baselibs.androidx.test.junit)
    androidTestImplementation(baselibs.androidx.test.espresso)


    implementation(compose.compose.activity)
    implementation(compose.compose.material)
    implementation(compose.compose.material.icons.extended)
    implementation(compose.compose.animation)
    implementation(compose.compose.ui.tooling)
    implementation(compose.compose.viewmodel)
    implementation(compose.compose.navigation)
    implementation(compose.compose.foundation)
    implementation(compose.compose.constraintlayout)

    implementation(thirdparty.permission)
    implementation(thirdparty.image)

    implementation(exoplayer.exoplayer.ui)
    implementation(exoplayer.exoplayer.core)

    implementation(jetpack.startup)

    implementation(project(":crashLib"))
}