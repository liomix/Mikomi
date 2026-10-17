plugins {
    id("mikomi.android.data")
}

android {
    namespace = "com.liomix.mikomi.data.user"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(project(":core:model"))
    api(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.android)
}
