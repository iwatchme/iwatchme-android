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
    implementation(std.kotlin)
    implementation(std.gradle.plugin)
    implementation(asm.asm)
    implementation(asm.asm.commons)
}

gradlePlugin {
    plugins.register("customGradlePlugin") {
        id = "custom-gradle-plugin"
        implementationClass = "CustomGradlePlugin"
    }
}