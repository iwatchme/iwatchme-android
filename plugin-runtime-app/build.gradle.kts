plugins {
    id("iwatchme.android.application")
}

android {
    namespace = "com.iwatchme.plugin.runtime"

    defaultConfig {
        applicationId = "com.iwatchme.android"
        versionCode = 1
        versionName = "1.0"
    }

    lint {
        abortOnError = false
    }
}

repositories {
    mavenLocal()
}

dependencies {
    implementation(libs.tencent.shadow.core.runtime)
    implementation(libs.tencent.shadow.core.activity.container)
}
