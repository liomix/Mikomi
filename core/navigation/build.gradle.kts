plugins {
    id("mikomi.android.core")
    // 导航基础设施里有 @Composable（rememberNavigator / LocalNavigator），
    // 需要 Compose 编译器插件；缺了它 `remember` 会被当成普通内联函数，编译期直接崩。
    id("mikomi.android.compose")
}

android {
    namespace = "com.liomix.mikomi.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
}
