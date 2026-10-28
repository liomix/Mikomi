plugins {
    id("mikomi.android.core")
    id("mikomi.android.compose")
}

android {
    namespace = "com.liomix.mikomi.core.ui"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(project(":core:designsystem"))
    api(project(":core:model"))
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
