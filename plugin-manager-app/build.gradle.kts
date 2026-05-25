plugins {
    id("iwatchme.android.application")
}

android {
    namespace = "com.iwatchme.plugin.manager"

    defaultConfig {
        // 重要：Shadow plugin APK 必须与 host 共用 applicationId，否则 ContentProvider authority 拼接不上
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
    implementation(libs.tencent.shadow.core.manager)
    implementation(libs.tencent.shadow.dynamic.manager)
    implementation(libs.tencent.shadow.dynamic.loader)
    implementation(libs.coroutines.core)

    // host 已经带的 SDK：plugin manager apk 不重复打包
    compileOnly(libs.tencent.shadow.core.common)
    compileOnly(libs.tencent.shadow.dynamic.host)
}
