import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "com.iwatchme.android.buildlogic"

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
            id = "iwatchme.android.application"
            implementationClass = "com.iwatchme.android.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "iwatchme.android.application.compose"
            implementationClass = "com.iwatchme.android.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "iwatchme.android.library"
            implementationClass = "com.iwatchme.android.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "iwatchme.android.library.compose"
            implementationClass = "com.iwatchme.android.AndroidLibraryComposeConventionPlugin"
        }
        register("androidTest") {
            id = "iwatchme.android.test"
            implementationClass = "com.iwatchme.android.AndroidTestConventionPlugin"
        }
    }
}
