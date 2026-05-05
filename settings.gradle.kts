pluginManagement {
    includeBuild("build-logic")
    repositories {
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
