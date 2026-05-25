pluginManagement {
    includeBuild("build-logic")
    repositories {
        // mavenLocal 排前面：解析 Shadow gradle-plugin 的 com.tencent.shadow.plugin
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("threadDetectPlugin")
includeBuild("ai-sdk")

dependencyResolutionManagement {
    repositories {
        // mavenLocal 必须排前面，让 Shadow SDK 的本地发布优先命中
        mavenLocal()
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "iwatchme-android"
include(":app")
include(":baselineprofile")
include(":benchmark")
include(":startupRuntime")
include(":startupLab")
include(":crashLib")
include(":render-engine")
include(":player")
include(":voice-eval")
include(":cocos-shell")
include(":lock-simulator")
include(":netopt-demo")

// Shadow 插件化相关模块（依赖 vendor/Shadow 发到 ~/.m2 的 com.tencent.shadow.* artifact）
include(":host-shadow")            // 宿主接入层，:app 依赖它
include(":plugin-manager-app")     // 产出 plugin-manager.apk，作为独立 partKey 下发
include(":plugin-loader-app")      // 产出 plugin-loader.apk
include(":plugin-runtime-app")     // 产出 plugin-runtime.apk
include(":plugin-demo")            // 业务插件示例（应用 Shadow Transform）
