plugins {
    id("iwatchme.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.iwatchme.player"

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.recyclerview)
    implementation(libs.constraintlayout)
}
