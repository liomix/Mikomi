plugins {
    id("mikomi.android.library")
}

android {
    namespace = "com.liomix.mikomi.benchmark"

    // TODO: 配置为 Macrobenchmark 测试模块：
    //   targetProjectPath = ":app"
    //   experimentalProperties["android.experimental.self-instrumenting"] = true
    //   androidTestImplementation(libs.androidx.benchmark.macro)
}
