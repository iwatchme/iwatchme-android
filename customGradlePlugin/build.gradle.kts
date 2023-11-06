plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

// To make it available as direct dependency

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib-jdk8"))
}

gradlePlugin {
    plugins.register("customGradlePlugin") {
        id = "custom-gradle-plugin"
        implementationClass = "CustomGradlePlugin"
    }
}