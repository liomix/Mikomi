plugins {
    id("mikomi.android.core")
    id("mikomi.android.compose")
}

android {
    namespace = "com.liomix.mikomi.core.designsystem"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // 揭示动画用 suspendCancellableCoroutine 把系统 Animator 桥接成挂起调用。
    implementation(libs.kotlinx.coroutines.android)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    debugApi(libs.androidx.compose.ui.tooling)
}
