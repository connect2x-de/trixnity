import com.android.build.gradle.tasks.factory.AndroidUnitTest
import de.connect2x.conventions.withAndroidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(sharedLibs.plugins.android.library)
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        group("common") {
            group("web") {
                withJs()
                withWasmJs()
            }
            group("jni") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    addJvmTarget()
    addAndroidTarget()
    addWebTarget(rootDir)
    addNativeTargets()
    withAndroidLibrary("$group.vodozemac")

    compilerOptions {
        freeCompilerArgs.add(
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        configureEach {
            if (isNativeOnly) languageSettings.optIn("kotlin.native.SymbolNameIsInternal")
            if (name == "wasmJsMain") languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }

        commonMain.dependencies {
            implementation(projects.trixnityVodozemac.trixnityVodozemacBinaries)
        }
        webMain.dependencies {
            implementation(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            implementation(sharedLibs.kotlin.browser)
        }

        commonTest.dependencies {
            implementation(sharedLibs.kotlin.test)
            implementation(sharedLibs.kotlinx.coroutines.test)
        }
    }
}

kotlin.targets.withType<KotlinNativeTarget> {
    compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.native.SymbolNameIsInternal") }
    val main by
        compilations.getting {
            defaultSourceSet.languageSettings.optIn("kotlin.native.SymbolNameIsInternal")
        }
}

tasks.withType<Test> { outputs.cacheIf { false } }

tasks.withType<AndroidUnitTest> { enabled = false }
