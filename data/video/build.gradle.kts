plugins {
    id("mikomi.android.data")
}

android {
    namespace = "com.liomix.mikomi.data.video"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
}
