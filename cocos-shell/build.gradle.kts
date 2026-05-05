plugins {
    id("iwatchme.android.library")
}

android {
    namespace = "com.iwatchme.cocosshell"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// Same JitPack escape-hatch as voice-eval — the global init.d aliyun mirror
// shadows settings-level repositories, so we re-declare locally for any
// transitive that lives on JitPack. Harmless if every dep resolves elsewhere.
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)

    api(project(":voice-eval"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
