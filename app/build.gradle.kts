plugins {
    id("jetpackstarter.android.application.compose")
    alias(libs.plugins.baselineprofile)
    id("custom-gradle-plugin")
}

android {
    ndkVersion = "27.2.12479018"
    namespace = "com.iwatchme.jetpackstarter"

    defaultConfig {
        applicationId = "com.iwatchme.jetpackstarter"
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
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
    implementation(libs.core.splashscreen)
    implementation(libs.material)
    implementation(libs.profileinstaller)

    implementation(libs.compose.activity)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.navigation)
    implementation(libs.activity.ktx)

    implementation(project(":crashLib"))
    implementation(project(":startupLab"))
    implementation(project(":render-engine"))
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
