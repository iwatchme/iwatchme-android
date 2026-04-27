plugins {
    id("jetpackstarter.android.application")
}

android {
    ndkVersion = "27.2.12479018"
    namespace = "com.iwatchme.jetpackstarterplayer"

    defaultConfig {
        applicationId = "com.iwatchme.jetpackstarterplayer"
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++11")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
            this.version = "3.22.1"
        }
    }

    sourceSets.getByName("main") {
        jniLibs.srcDir("../ffmpeg_library/android/libs")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.easypermissions)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
