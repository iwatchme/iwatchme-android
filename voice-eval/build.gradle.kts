plugins {
    id("iwatchme.android.library")
}

android {
    namespace = "com.iwatchme.voiceeval"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// JitPack 上托管了预编译的 TAndroidLame AAR（LAME MP3 编码器的 JNI 包装）。
// 之所以放在 module 级别声明：composite-build 链路里某处覆盖了 settings 级仓库，
// 在本地声明一下能让本模块的依赖解析保持自洽。
//
// JitPack hosts the prebuilt TAndroidLame AAR (LAME MP3 encoder JNI wrapper).
// Declared here at the module level because something in the composite-build
// chain is overriding settings-level repositories — declaring it locally
// keeps resolution self-contained for this module.
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // TAndroidLame —— libmp3lame 的 Android JNI 包装。会带入 4 个 ABI 的 .so
    //（arm64-v8a / armeabi-v7a / armeabi / x86）。它 2017 年的 POM 里声明了
    // 一个传递依赖 `com.android.support:appcompat-v7`，而编码器代码本身从未引用，
    // 这里排掉它以保持纯 AndroidX 环境。
    //
    // TAndroidLame — Android JNI wrapper around libmp3lame. Pulls 4-ABI .so
    // files (arm64-v8a / armeabi-v7a / armeabi / x86). The 2017-vintage POM
    // declares a transitive `com.android.support:appcompat-v7` that the
    // encoder code never actually imports — exclude it to stay AndroidX-clean.
    api("com.github.NorthernCaptain:TAndroidLame:1.1") {
        exclude(group = "com.android.support")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
