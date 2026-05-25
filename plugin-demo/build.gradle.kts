// Shadow Transform 要求 com.tencent.shadow.plugin 在 kotlin-android 之前 apply。
// 因此本模块不走 iwatchme.android.application convention plugin（那会隐式带入 kotlin-android），
// 而是手动 apply 三个 plugin，并显式重复 convention plugin 里的 compileSdk / minSdk / JDK 配置。
import com.tencent.shadow.core.gradle.extensions.PackagePluginExtension
import groovy.lang.Tuple2
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// 顺序敏感：android-application 先（Shadow 需要 android extension）→ shadow（必须在 kotlin-android 前）→ kotlin-android → kotlin-compose
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.tencent.shadow.plugin)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Kotlin 2.0+ Compose 编译器
}

android {
    namespace = "com.iwatchme.plugin.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.iwatchme.android"  // 与 host 一致
        minSdk = 26
        targetSdk = 33
        versionCode = 5
        versionName = "5.0.0"
    }

    buildFeatures {
        compose = true
    }
    // Kotlin 2.0+ 不再需要 kotlinCompilerExtensionVersion —— 由 kotlin-compose plugin 自动管理

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    aaptOptions {
        additionalParameters("--package-id", "0x7e", "--allow-reserved-package-id")
    }

    lint {
        abortOnError = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

repositories {
    mavenLocal()
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

    // 协程 —— 插件 Service 用它做周期任务，证明 kotlinx-coroutines 在插件 ClassLoader 下正常工作
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Compose UI 探针：用 ComposeView 兜底（Shadow PluginActivity 继承 Activity 而非 ComponentActivity）
    // 这些 dep 真打进 plugin apk —— host 也有但 ClassLoader 不共享，自包含更稳
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.ui)
    // Compose 在非 ComponentActivity 宿主里需要手动 set ViewTreeLifecycleOwner —— 引入 lifecycle/savedstate API
    implementation(libs.lifecycle.runtime.ktx)
    implementation("androidx.savedstate:savedstate:1.2.1")

    compileOnly(libs.tencent.shadow.core.runtime)
}

// 把 plugin-loader-app + plugin-runtime-app + plugin-demo (pluginDebug variant) 打成 Shadow zip + config.json
// :app 走 PluginUpdateService 拉到 zip 后，直接喂给 Manager.installPluginFromZip
configure<PackagePluginExtension> {
    archivePrefix = "iwatchme-plugin"
    destinationDir = "${rootProject.buildDir}/iwatchme-plugin"
    // Shadow 用 ${rootDir}/${loaderApkProjectPath}/build/outputs/apk/<buildType>/<apkName> 定位
    loaderApkProjectPath = "plugin-loader-app"
    runtimeApkProjectPath = "plugin-runtime-app"
    buildTypes.create("debug").apply {
        // apkName 必须与实际产物文件名一致；AGP 默认输出 <moduleName>-<buildType>.apk
        loaderApkConfig = Tuple2("plugin-loader-app-debug.apk", ":plugin-loader-app:assembleDebug")
        runtimeApkConfig = Tuple2("plugin-runtime-app-debug.apk", ":plugin-runtime-app:assembleDebug")
        pluginApks.create("memberCenter").apply {
            businessName = "iwatchme-plugin-demo"
            partKey = "iwatchme-plugin-main"
            buildTask = ":plugin-demo:assemblePluginDebug"
            apkPath = "plugin-demo/build/outputs/apk/plugin/debug/plugin-demo-plugin-debug.apk"
        }
    }
}
