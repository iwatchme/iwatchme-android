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
    }

    versionCatalogs {
        this.create("baselibs") {
            library("appcompat", "androidx.appcompat:appcompat:1.6.1")
            library("core-ktx", "androidx.core:core-ktx:1.9.0")
            library("material", "com.google.android.material:material:1.8.0")
            library("junit","junit:junit:4.13.2")
            library("androidx-test-junit","androidx.test.ext:junit:1.1.5")
            library("androidx-test-espresso", "androidx.test.espresso:espresso-core:3.5.1")
        }
    }
}
rootProject.name = "JetpackStarter"
include(":app")
