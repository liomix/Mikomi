plugins {
    id("mikomi.android.application")
}

android {
    namespace = "com.liomix.mikomi"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        /**
         * 性能测量专用变体（规范 §12.1 / §12.5：结论只在 release 上取）。
         *
         * 三个要点，缺一个数字就不可信：
         * - **继承 release**：R8、资源压缩、同一套 proguard 规则。不继承就等于在测另一个 App；
         * - **`isDebuggable = false`**：可调试构建会关掉 ART 的 AOT 优化，并把大量调用留在
         *   解释执行路径上 —— 这正是「debug 的 p95 是 release 的 4 倍」的来源；
         * - **debug 签名**：release 用的是正式签名，本地没有 keystore 就装不上去；
         *   换成 debug 签名不影响运行时行为，只影响"谁能装"。
         *
         * `matchingFallbacks` 让 `:benchmark` 里同名变体在找不到同构依赖时回退到 release 的实现。
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    packaging {
        jniLibs {
            // libzhuque.so 会 dlopen 同目录的 libaddressing_ffi.so；使用传统解压安装
            // 可以保证原生库位于可被动态链接器找到的 lib 目录。
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:home"))
    implementation(project(":feature:animap"))
    implementation(project(":feature:community"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:search"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))
    implementation(project(":data:user"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // per-entry 的 ViewModelStore：`rememberViewModelStoreNavEntryDecorator` 在这里用。
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Baseline Profile 的安装端：API 24~30 上由它在后台把 profile 编译进 ART；
    // 同时提供 macrobenchmark 需要的 DROP_SHADER_CACHE 广播接收（ProfileInstallReceiver）。
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
