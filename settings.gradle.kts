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

    /**
     * https://androidx.tech/
     * find details about all of the artifacts and packages that make up the AndroidX family
     */
    versionCatalogs {
        this.create("baselibs") {
            library("appcompat", "androidx.appcompat:appcompat:1.6.1")
            library("core-ktx", "androidx.core:core-ktx:1.9.0")
            library("material", "com.google.android.material:material:1.8.0")
            library("junit","junit:junit:4.13.2")
            library("androidx-test-junit","androidx.test.ext:junit:1.1.5")
            library("androidx-test-espresso", "androidx.test.espresso:espresso-core:3.5.1")
        }

        this.create("jetpack") {
            library("startup", "androidx.startup:startup-runtime:1.1.0")
        }

        this.create("compose") {
            library("compose-activity", "androidx.activity:activity-compose:1.6.0")
            library("compose-compiler", "androidx.compose.compiler:compiler:1.3.1")
            library("compose-foundation", "androidx.compose.foundation:foundation:1.3.1")
            library("compose-foundation-layout", "androidx.compose.foundation:foundation-layout:1.3.1")
            library("compose-material", "androidx.compose.material:material:1.3.1")
            library("compose-material-icons-extended", "androidx.compose.material:material-icons-extended:1.3.1")
            library("compose-runtime", "androidx.compose.runtime:runtime:1.3.3")
            library("compose-runtime-livedata", "androidx.compose.runtime:runtime-livedata:1.3.3")
            library("compose-ui", "androidx.compose.ui:ui:1.3.3")
            library("compose-ui-tooling", "androidx.compose.ui:ui-tooling:1.3.3")
            library("compose-ui-tooling-preview", "androidx.compose.ui:ui-tooling-preview:1.3.3")
            library("compose-ui-util", "androidx.compose.ui:ui-util:1.3.3")
            library("compose-navigation", "androidx.navigation:navigation-compose:2.5.3")
            library("compose-viewmodel", "androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")
            library("compose-animation", "androidx.compose.animation:animation:1.3.3")
            library("compose-constraintlayout", "androidx.constraintlayout:constraintlayout-compose:1.0.1")
        }

        this.create("exoplayer") {
            library("exoplayer-core", "com.google.android.exoplayer:exoplayer-core:2.18.0")
            library("exoplayer-ui", "com.google.android.exoplayer:exoplayer-ui:2.18.0")
        }

        this.create("thirdparty") {
            library("permission", "com.google.accompanist:accompanist-permissions:0.22.0-rc")
            library("image", "io.coil-kt:coil-compose:1.4.0")
        }
    }
}
rootProject.name = "JetpackStarter"
include(":app")
include(":customCoroutine")
include(":startupLauncher")
