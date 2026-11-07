plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.profile"
    resourcePrefix = "profile_"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":data:user"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
