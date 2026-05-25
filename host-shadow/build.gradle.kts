plugins {
    id("iwatchme.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.iwatchme.host.shadow"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// ~/.gradle/init.d/mirror.gradle 用 `allprojects { repositories { ... } }` 注入了 aliyun-only 镜像，
// 把 settings.gradle.kts 的 mavenLocal() 屏蔽掉了。每个消费 Shadow SDK 的 module 都要在这里手动加回 mavenLocal。
repositories {
    mavenLocal()
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    api(libs.okhttp)  // 暴露给消费者：PluginUpdateClient.create 的可选 OkHttpClient 参数会被 Kotlin 编译期解析
    implementation(libs.kotlin.serialization)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)

    // Shadow 接入层 —— 由 vendor/Shadow 发布到 mavenLocal
    implementation(libs.tencent.shadow.core.common)
    implementation(libs.tencent.shadow.core.runtime)
    implementation(libs.tencent.shadow.core.activity.container)
    // dynamic-host 通过 api() 暴露给 :app（demo 屏要用 PluginManager / PluginManagerUpdater 类型）
    api(libs.tencent.shadow.dynamic.host)

    testImplementation(libs.junit)
}
