plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.animap"
    resourcePrefix = "animap_"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.android)
    // 搜索建议是页面回传的 JSON 数组（见 AniMapHit），直接在 UI 层解码。
    implementation(libs.kotlinx.serialization.json)
    // 地图加载动画用 Lottie；它只被本域消费，所以依赖留在这里，不下放到 core:ui。
    implementation(libs.lottie.compose)
    ksp(libs.hilt.compiler)
}
