import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "com.iwatchme.jetpackstarter.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation.set(true)
        failOnWarning.set(true)
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "jetpackstarter.android.application"
            implementationClass = "com.iwatchme.jetpackstarter.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "jetpackstarter.android.application.compose"
            implementationClass = "com.iwatchme.jetpackstarter.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "jetpackstarter.android.library"
            implementationClass = "com.iwatchme.jetpackstarter.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "jetpackstarter.android.library.compose"
            implementationClass = "com.iwatchme.jetpackstarter.AndroidLibraryComposeConventionPlugin"
        }
        register("androidTest") {
            id = "jetpackstarter.android.test"
            implementationClass = "com.iwatchme.jetpackstarter.AndroidTestConventionPlugin"
        }
    }
}
