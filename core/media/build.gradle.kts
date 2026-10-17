plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.media"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
}
