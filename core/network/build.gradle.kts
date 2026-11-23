plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.network"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    // 图片下载走自己的 OkHttp 客户端（独立连接池 / 超时 / UA），配置归网络层，
    // 组件层（core:ui）只留 Compose 的 AsyncImage。
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
