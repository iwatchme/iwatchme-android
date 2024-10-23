includeBuild("customGradlePlugin")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}
rootProject.name = "JetpackStarter"
include(":app")
include(":customCoroutine")
include(":startupLauncher")
include(":jetpackstarterplayer")
include(":crashLib")
include(":cryptotrack")
