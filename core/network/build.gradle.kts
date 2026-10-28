plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.network"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
}
