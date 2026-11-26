plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.cache"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
