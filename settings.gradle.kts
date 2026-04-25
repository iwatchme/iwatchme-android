includeBuild("customGradlePlugin")

pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "JetpackStarter"
include(":app")
include(":baselineprofile")
include(":benchmark")
include(":customCoroutine")
include(":startupRuntime")
include(":startupLab")
include(":jetpackstarterplayer")
include(":crashLib")
