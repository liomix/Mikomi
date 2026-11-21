plugins {
    id("mikomi.android.core")
    id("mikomi.android.room")
}

android {
    namespace = "com.liomix.mikomi.core.database"

    testOptions {
        unitTests {
            // Robolectric 需要拿到合并后的 Manifest 与资源；DAO 测试本身不用资源，
            // 但不开这一项时 Robolectric 会抱怨找不到 AndroidManifest。
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)

    // DAO 的语义住在 SQL 里，只有真的 SQLite 能验（见 FavoriteDaoTest 的注释）。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
