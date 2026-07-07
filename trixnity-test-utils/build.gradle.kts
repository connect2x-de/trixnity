@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import de.connect2x.conventions.withAndroidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(sharedLibs.plugins.android.library)
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addAndroidTarget()
    addNativeDesktopTargets()
    addNativeAppleTargets()
    applyDefaultHierarchyTemplate()
    withAndroidLibrary("$group.test.utils")
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain.dependencies {
            api(sharedLibs.kotlin.test)
            api(sharedLibs.kotlinx.coroutines.test)
            api(sharedLibs.kotest.assertions.core)
            api(sharedLibs.lognity.api)
            implementation(sharedLibs.lognity.core)
            implementation(sharedLibs.lognity.test)
        }

        jvmMain.dependencies {
            api(kotlin("test-junit5"))
            implementation(sharedLibs.lognity.slf4j)
        }

        androidMain.dependencies {
            api(kotlin("test-junit"))
        }
    }
}
