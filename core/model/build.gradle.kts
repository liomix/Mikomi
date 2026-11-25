plugins {
    alias(libs.plugins.kotlin.serialization)
    id("mikomi.android.library")
}

android {
    namespace = "com.liomix.mikomi.core.model"
}

dependencies {
    // `@Serializable` 落在模型上：缓存直接存领域对象，不为它再写一套 DTO。
    api(libs.kotlinx.serialization.json)
}
