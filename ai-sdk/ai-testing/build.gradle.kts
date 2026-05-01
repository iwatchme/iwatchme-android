plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.atomicfu)
    `maven-publish`
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":ai-tts"))
            api(project(":ai-translation"))
            api(project(":ai-asr"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
