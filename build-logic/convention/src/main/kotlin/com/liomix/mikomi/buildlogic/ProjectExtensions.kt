package com.liomix.mikomi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 24
internal const val TARGET_SDK = 36

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    // 根目录一份 lint.xml，由 AndroidLint.kt 统一接入（规范 §2.5）。
    val lintConfigFile = rootDir.resolve("lint.xml")
    extension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig {
            minSdk = MIN_SDK
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests {
                // 单元测试跑在 JVM 上，android.jar 里的方法默认抛「not mocked」。
                // 本项目最常踩的一处是 `android.util.Log`：ViewModel 在 catch 里记一行日志，
                // 结果那行日志自己把 catch 炸掉 —— 测试报的失败与代码行为无关，只是桩缺失。
                // 让未实现的 android 方法返回默认值，「JVM 测试验行为」这条原则才成立。
                isReturnDefaultValues = true
            }
        }
        lint { configureMikomiLint(lintConfigFile) }
    }
}

internal fun Project.configureAndroidApplication(extension: ApplicationExtension) {
    val lintConfigFile = rootDir.resolve("lint.xml")
    extension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig {
            applicationId = "com.liomix.mikomi"
            minSdk = MIN_SDK
            targetSdk = TARGET_SDK
            versionCode = 1
            versionName = "1.0"
        }
        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        lint { configureMikomiLint(lintConfigFile) }
    }
}
