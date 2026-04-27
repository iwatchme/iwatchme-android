plugins {
    id("jetpackstarter.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.iwatchme.jetpackstarter.baselineprofile"
    targetProjectPath = ":app"

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
}
