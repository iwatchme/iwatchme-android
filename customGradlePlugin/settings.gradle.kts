pluginManagement {
    plugins {
        `kotlin-dsl`
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        this.create("asm") {
           library("asm-commons", "org.ow2.asm:asm-commons:9.6")
           library("asm", "org.ow2.asm:asm:9.6")
        }

        this.create("std") {
            library("gradle-plugin", "com.android.tools.build:gradle:7.2.2")
            library("kotlin", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21")
        }
    }
}