plugins {
    id("mikomi.android.data")
}

android {
    namespace = "com.liomix.mikomi.data.video"

    /**
     * 内置规则（`DM84.json`、`7sefun.json`）与解析代码放在同一个包里的 `rules/` 下，
     * 改规则不用跨模块找文件；这里把这个目录登记成 assets 源集，打包后它们就在 assets 根下，
     * 由 `VdoPluginRepositoryImpl` 首次启动时播种到 `files/vdoplugin/rules/`。
     *
     * 直接登记 `rules/`（而不是它的父目录）：父目录里还有 .kt 源文件，登记进去等于把源码也打进了 APK。
     */
    sourceSets {
        getByName("main") {
            assets.directories.add("src/main/kotlin/com/liomix/mikomi/data/video/remote/vdoplugin/rules")
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
