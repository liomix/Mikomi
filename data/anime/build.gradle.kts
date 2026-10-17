plugins {
    id("mikomi.android.data")
}

android {
    namespace = "com.liomix.mikomi.data.anime"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(project(":core:model"))
    api(project(":core:database"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
