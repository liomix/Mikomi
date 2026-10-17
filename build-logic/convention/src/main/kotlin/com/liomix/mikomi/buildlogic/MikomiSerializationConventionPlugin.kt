package com.liomix.mikomi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class MikomiSerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        }
    }
}
