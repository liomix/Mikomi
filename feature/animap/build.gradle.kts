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
    ksp(libs.hilt.compiler)
}
