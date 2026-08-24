plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
    alias(sharedLibs.plugins.android.library)
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir, browserEnabled = false)
    addAndroidTarget()
    addNativeTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                api(libs.okio)
                implementation(sharedLibs.lognity.api)
            }
        }
        jsMain { dependencies { implementation(libs.okio.nodefilesystem) } }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(libs.okio.fakefilesystem)
            }
        }
    }
}
