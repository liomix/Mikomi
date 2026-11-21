plugins {
    id("com.android.test")
}

/*
 * macrobenchmark 与 Baseline Profile 的生产者（规范 §12.2 / §15）。
 *
 * ## 为什么这个模块不是 mikomi.android.library
 *
 * 它测的是**已经装好的 App**，不是自己构建出来的库。`com.android.test` 会按
 * `targetProjectPath` 把 `:app` 当成被测对象构建，并向它下发指令（启动、滚动、抓帧），
 * 这是 application 与 library 插件都做不到的事。
 *
 * ## 为什么只保留一个性能模块
 *
 * 规范 §12.2：`:benchmark` 是唯一的性能测量入口。Baseline Profile 的生成器与 macrobenchmark
 * 的用例放在同一个模块里，就不存在「两份基准、两个结论」。
 *
 * ## 断言的单位是「帧」和「毫秒」，不是「通过 / 失败」
 *
 * 这里的测试类不写 assert：产出是指标（frameDurationCpuMs 的 P50 / P90 / P95）。
 * 结论只在 release 上取 —— debug 的 p95 是 release 的 4 倍（见 2026-11-07 日志）。
 */

android {
    namespace = "com.liomix.mikomi.benchmark"
    compileSdk = 37

    defaultConfig {
        // 与被测 App 的 minSdk 对齐：更低不会跑到，更高会漏掉 minSdk 设备的真实表现。
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    /**
     * 与 `:app` 的 `benchmark` 变体对齐：测试模块的变体名必须和被测应用一致，
     * 否则 `connectedBenchmarkAndroidTest` 不会出现，只能测 debug（数字没有参考价值）。
     */
    buildTypes {
        create("benchmark") {
            // 测试 APK 自己不做混淆，但必须非 debuggable 才能装到非调试的目标应用旁边。
            isDebuggable = false
            // 自定义 build type 不会自动签名；不写这行，安装阶段会报
            // INSTALL_PARSE_FAILED_NO_CERTIFICATES（vivo 上表现为"User rejected permissions"的下一层）。
            signingConfig = signingConfigs.getByName("debug")
            // `benchmarkTestedApks` 会把 BuildTypeAttr=benchmark 透传给被测应用的依赖图；
            // 库模块只有 debug / release 两个变体，这里声明回退，避免 26 个模块各加一个变体。
            matchingFallbacks += listOf("release")
        }
    }

    /**
     * 被测模块。
     *
     * 测试模块的 variant 名与 App 的 build type 一一对应：这里只有 `debug`，所以就测 debug。
     * 要出 release 数字，先在 `:app` 加一个「继承 release 且用 debug 签名」的 build type，
     * 再来这里加同名 build type —— 两步都做完才能装到设备上（规范 §12.5：先测量，别先改实现）。
     */
    targetProjectPath = ":app"

    /**
     * 让自己成为「被测应用」：Baseline Profile 的生成器必须能给自己下发 profile 采集请求，
     * 否则只能测到测试 APK 自己。这一行是 macrobenchmark 官方的硬性要求。
     */
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
