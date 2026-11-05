plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.community"
    resourcePrefix = "community_"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
