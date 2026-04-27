plugins {
    id("jetpackstarter.android.test")
}

android {
    namespace = "com.iwatchme.jetpackstarter.benchmark"
    targetProjectPath = ":app"

    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
}
