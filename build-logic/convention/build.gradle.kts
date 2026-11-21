plugins {
    `kotlin-dsl`
}

group = "com.liomix.mikomi.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)

    // 审计的数值/正则逻辑要能单测：正则写错一个字符，结果是「审计永远通过」。
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("mikomiDesignAudit") {
            id = "mikomi.design-audit"
            implementationClass = "com.liomix.mikomi.buildlogic.DesignAuditPlugin"
        }
        register("mikomiAndroidApplication") {
            id = "mikomi.android.application"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiApplicationConventionPlugin"
        }
        register("mikomiAndroidLibrary") {
            id = "mikomi.android.library"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiLibraryConventionPlugin"
        }
        register("mikomiAndroidCompose") {
            id = "mikomi.android.compose"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiComposeConventionPlugin"
        }
        register("mikomiAndroidHilt") {
            id = "mikomi.android.hilt"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiHiltConventionPlugin"
        }
        register("mikomiAndroidSerialization") {
            id = "mikomi.android.serialization"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiSerializationConventionPlugin"
        }
        register("mikomiAndroidRoom") {
            id = "mikomi.android.room"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiRoomConventionPlugin"
        }
        register("mikomiAndroidFeature") {
            id = "mikomi.android.feature"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiFeatureConventionPlugin"
        }
        register("mikomiAndroidCore") {
            id = "mikomi.android.core"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiCoreConventionPlugin"
        }
        register("mikomiAndroidData") {
            id = "mikomi.android.data"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiDataConventionPlugin"
        }
        // 根项目专用：模块边界校验（规范 §3.7）。它不是模块约定插件，只注册一个校验任务。
        register("mikomiModuleGraph") {
            id = "mikomi.module-graph"
            implementationClass = "com.liomix.mikomi.buildlogic.MikomiModuleGraphPlugin"
        }
    }
}
