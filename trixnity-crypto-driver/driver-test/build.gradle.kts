plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addNativeTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            api(projects.trixnityCryptoDriver)
            api(projects.trixnityTestUtils)
        }
    }
}
