plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

// To make it available as direct dependency

repositories {
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/public")
    google()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(std.kotlin)
    implementation(std.gradle.plugin)
    implementation(asm.asm)
    implementation(asm.asm.commons)
    implementation(asm.asm.util)
}

gradlePlugin {
    plugins.register("threadDetect") {
        id = "thread-detect"
        implementationClass = "ThreadDetectPlugin"
    }
}
