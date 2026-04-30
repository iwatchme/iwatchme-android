package com.iwatchme.android

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.test")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<TestExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
                        "EMULATOR,LOW-BATTERY"
                }

                packaging {
                    jniLibs {
                        keepDebugSymbols += setOf("**/*.so")
                    }
                }
            }
        }
    }
}
