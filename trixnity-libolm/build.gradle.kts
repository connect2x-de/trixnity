@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import de.connect2x.conventions.asAAR
import de.connect2x.conventions.withAndroidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    alias(sharedLibs.plugins.android.library)
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
    alias(libs.plugins.download)
}

val olmBinariesDirs = TrixnityOlmBinariesDirs(project, libs.versions.trixnityOlmBinaries.get())

class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinMultiplatformExtension.() -> KotlinNativeTarget,
) {
    val libPath: File = olmBinariesDirs.binStatic.resolve(target.name).resolve("libolm.a")
}

val olmNativeTargetList = listOf(
    OlmNativeTarget(
        target = KonanTarget.LINUX_X64,
        createTarget = { linuxX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_ARM64,
        createTarget = { macosArm64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MINGW_X64,
        createTarget = { mingwX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_SIMULATOR_ARM64,
        createTarget = { iosSimulatorArm64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_ARM64,
        createTarget = { iosArm64() },
    ),
)

val installOlmToJvmResources by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBinariesDirs.binShared)
    include("*/libolm.so", "*/olm.dll", "*/libolm.dylib")
    into(layout.buildDirectory.dir("processedResources/jvm/main"))
    dependsOn(trixnityBinariesTask)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToJvmResources)
}

tasks.withType<ExternalNativeCleanTask> {
    enabled = false
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(trixnityBinariesTask)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        jniLibs.srcDirs(olmBinariesDirs.binSharedAndroid)
    }
}
tasks.withType(com.android.build.gradle.tasks.MergeSourceSetFolders::class).configureEach {
    if (name.contains("jni", true)) {
        dependsOn(trixnityBinariesTask)
    }
}

val desktopOlmLibs by tasks.registering(Jar::class) {
    dependsOn(trixnityBinariesTask)

    from(olmBinariesDirs.binShared)
    archiveBaseName = "trixnity-olm-desktop-libs"
    destinationDirectory = layout.buildDirectory.dir("tmp")
}

kotlin {
    addJvmTarget()
    addAndroidTarget()
    addWebTarget(rootDir)
    withAndroidLibrary("$group.libolm") {
        instrumentedTestVariant.sourceSetTree = KotlinSourceSetTree.test
    }

    applyDefaultHierarchyTemplate {
        common {
            group("olmLibrary") {
                group("jna") {
                    withJvm()
                    withAndroidTarget()
                }
                group("native") {
                    group("linux")
                    group("mingw")
                    group("apple")
                }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    olmNativeTargetList.forEach { target ->
        target.createTarget(this).apply {
            compilations {
                "main" {
                    cinterops {
                        val libolm by creating {
                            packageName("org.matrix.olm")
                            includeDirs(olmBinariesDirs.headers)
                            tasks.named(interopProcessingTaskName) {
                                dependsOn(trixnityBinariesTask)
                            }
                        }
                    }
                }
            }
            compilerOptions {
                freeCompilerArgs.addAll(listOf("-include-binary", target.libPath.absolutePath))
            }
        }
    }

    sourceSets {
        configureEach {
            languageSettings.optIn("kotlin.RequiresOptIn")
            if (isNativeOnly) languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            if (isWebOnly) languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }

        commonMain {
            dependencies {
                implementation(projects.trixnityCryptoCore)
                implementation(sharedLibs.kotlinx.serialization.json)
                implementation(sharedLibs.ktor.utils)
                implementation(sharedLibs.lognity.api)
            }
        }
        // TODO: proper kotlin multiplatform variant of using jna
        val jnaMain by getting {
            dependencies {
                compileOnly(sharedLibs.jna)
            }
        }
        androidMain {
            dependencies {
                implementation(sharedLibs.jna.asProvider().asAAR())
            }
        }
        jvmMain {
            dependencies {
                implementation(sharedLibs.jna.asProvider().asJar())
            }
        }
        webMain {
            dependencies {
                implementation(
                    npm(
                        "trixnity-olm-wrapper",
                        "https://gitlab.com/api/v4/projects/46553592/packages/generic/build/v${libs.versions.trixnityOlmBinaries.get()}/trixnity-olm-wrapper.tgz"
                    )
                )
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
        androidUnitTest {
            dependencies {
                implementation(files(desktopOlmLibs))
                implementation(sharedLibs.jna.asProvider().asJar())
            }
        }
        androidInstrumentedTest {
            dependencies {
                implementation(sharedLibs.androidx.testRunner)
            }
        }
    }
}

private fun Provider<MinimalExternalModuleDependency>.asJar(): Provider<String> = map { "$it@jar" }

private val KotlinSourceSet.isWebOnly: Boolean
    get() = name == "jsMain" || name == "wasmJsMain" || name == "webMain"
