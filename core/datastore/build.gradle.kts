plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.datastore"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
