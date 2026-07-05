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
            api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            api(sharedLibs.kotlin.browser)
            api(sharedLibs.kotlinx.coroutines.core)
        }
    }
}
