plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.search"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":data:anime"))
    implementation(project(":data:user"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)
}
