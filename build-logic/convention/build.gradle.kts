plugins {
    `kotlin-dsl`
}

group = "com.liomix.mikomi.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
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
    }
}
