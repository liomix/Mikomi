plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.player"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:media"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
