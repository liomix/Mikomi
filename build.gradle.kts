// Top-level build file. Plugin versions are declared here (apply false) so that
// convention plugins in build-logic can apply them by id with a resolved version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // 模块边界校验任务（规范 §3.7）。只装在根项目：边界是全局性质，单模块看不到别人的边。
    id("mikomi.module-graph")
    // 设计系统的六条自检 + 主题契约（规则本体在 build-logic 的 DesignAuditPlugin）。
    id("mikomi.design-audit")
}

