package com.liomix.mikomi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class MikomiDataConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("mikomi.android.library")
            pluginManager.apply("mikomi.android.hilt")
            pluginManager.apply("mikomi.android.serialization")
        }
    }
}
