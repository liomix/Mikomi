package com.liomix.mikomi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class MikomiHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
        }
    }
}
