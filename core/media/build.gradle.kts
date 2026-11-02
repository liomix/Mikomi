plugins {
    id("mikomi.android.core")
}

android {
    namespace = "com.liomix.mikomi.core.media"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.media3.exoplayer)
    // 插件源给出的地址绝大多数是 m3u8：没有 HLS 模块，DefaultMediaSourceFactory 认不出，
    // 表现为「有声音没画面」或直接抛解析异常。必须显式带上。
    api(libs.androidx.media3.exoplayer.hls)
    // 画面用 PlayerView：它自带 resizeMode（填充方式）与 shutters，自撸 SurfaceView 还得重写一遍。
    api(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
}
