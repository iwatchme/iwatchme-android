plugins {
    id ("com.android.application")
    id ("org.jetbrains.kotlin.android")
}

android {
    namespace="com.iwatchme.jetpackstarter"
    compileSdkVersion(33)

    defaultConfig {
        applicationId = "com.iwatchme.jetpackstarter"
        minSdkVersion(24)
        targetSdkVersion(33)
        versionCode = 1
        versionName="1.0"

        testInstrumentationRunner ="androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(baselibs.appcompat)
    implementation(baselibs.core.ktx)
    implementation(baselibs.material)
    testImplementation(baselibs.junit)
    androidTestImplementation(baselibs.androidx.test.junit)
    androidTestImplementation(baselibs.androidx.test.espresso)
}