plugins {
    id("iwatchme.android.application")
}

android {
    namespace = "com.iwatchme.plugin.loader"

    defaultConfig {
        applicationId = "com.iwatchme.android"  // 与 host 一致，见 plugin-manager-app/build.gradle.kts 注释
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
    implementation(libs.tencent.shadow.core.loader)
    implementation(libs.tencent.shadow.dynamic.loader)
    implementation(libs.tencent.shadow.dynamic.loader.impl)

    compileOnly(libs.tencent.shadow.core.runtime)
    compileOnly(libs.tencent.shadow.core.activity.container)
    compileOnly(libs.tencent.shadow.core.common)
    compileOnly(libs.tencent.shadow.dynamic.host)
}
