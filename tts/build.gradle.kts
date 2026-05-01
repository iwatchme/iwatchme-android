plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.atomicfu) apply false
}

allprojects {
    group = "io.tts.sdk"
    version = System.getenv("VERSION") ?: "1.0.0"

    repositories {
        mavenCentral()
        google()
    }

    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/iwatchme/iwatchme-android")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String? ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.token") as String? ?: ""
                    }
                }
            }
        }
    }
}
