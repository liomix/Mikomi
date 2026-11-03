plugins {
    id("mikomi.android.data")
}

android {
    namespace = "com.liomix.mikomi.data.video"

    /**
     * 内置插件（`DM84.json`、`7sefun.json`）放在插件源包根部的 `plugin/` 下，
     * 加插件不用跨模块找文件；这里把这个目录登记成 assets 源集，打包后它们就在 assets 根下，
     * 由 `VdoPlgRepositoryImpl` 首次启动时播种到 `files/vdoplugin/plugin/`。
     *
     * 直接登记 `plugin/`（而不是它的父目录）：父目录里还有 .kt 源文件，登记进去等于把源码也打进了 APK。
     */
    sourceSets {
        getByName("main") {
            assets.directories.add("src/main/kotlin/com/liomix/mikomi/data/video/remote/vdoplg/plugin")
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
}
