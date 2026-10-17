plugins {
    id("mikomi.android.core")
    id("mikomi.android.room")
}

android {
    namespace = "com.liomix.mikomi.core.database"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
}
