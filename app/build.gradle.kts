import java.util.Properties

plugins {
    id("iwatchme.android.application.compose")
    alias(libs.plugins.baselineprofile)
    id("thread-detect")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

// Required because ~/.gradle/init.d/mirror.gradle injects an aliyun-only
// repo set via `allprojects`, which shadows the JitPack entry in
// settings.gradle.kts. Re-declare it here so the transitive TAndroidLame
// pulled in by :voice-eval resolves at app-build time too.
repositories {
    maven(url = "https://jitpack.io")
}

android {
    ndkVersion = "27.2.12479018"
    namespace = "com.iwatchme.android"

    defaultConfig {
        applicationId = "com.iwatchme.android"
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", "\"${localProps.getProperty("CLOUDFLARE_ACCOUNT_ID", "")}\"")
        buildConfigField("String", "CLOUDFLARE_API_TOKEN", "\"${localProps.getProperty("CLOUDFLARE_API_TOKEN", "")}\"")
    }

    buildFeatures {
        buildConfig = true
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

threadDetect {
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
    implementation(project(":player"))
    implementation(project(":voice-eval"))
    implementation(project(":cocos-shell"))
    implementation(libs.permission)
    implementation("io.ai.sdk:ai-tts:1.0.0")
    implementation("io.ai.sdk:ai-translation:1.0.0")
    implementation("io.ai.sdk:ai-asr:1.0.0")
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
