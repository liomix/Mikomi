plugins {
    id("mikomi.android.feature")
}

android {
    namespace = "com.liomix.mikomi.feature.player"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:media"))
    // 番名要拿来搜插件源；出流与规则解释只有 data:video 的 vdoplg 一处实现。
    implementation(project(":data:anime"))
    // 用 api 不用 implementation：`PlayerUiState.sources` 是公开状态，里面的 `VdoSourceFamily`
    // （大源）是这个模块的类型。用 implementation 的话，下游读 `sources` 会看不见那个类型。
    api(project(":data:video"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
