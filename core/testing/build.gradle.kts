plugins {
    id("mikomi.android.library")
}

android {
    namespace = "com.liomix.mikomi.core.testing"
}

dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
