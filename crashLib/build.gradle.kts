plugins {
    id("iwatchme.android.library")
}

android {
    ndkVersion = "27.2.12479018"
    namespace = "com.iwatchme.crashlib"

    buildFeatures {
        prefab = true
    }

    packagingOptions {
        jniLibs {
            excludes += setOf("**/libxunwind.so")
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.unwind)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
