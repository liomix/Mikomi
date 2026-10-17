plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.common"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
