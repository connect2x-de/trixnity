@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addWebTarget(rootDir, nodeJsEnabled = false)
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            implementation(sharedLibs.kotlin.browser)
        }
    }
}
