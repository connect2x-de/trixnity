plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addNativeTargets()
    addWebTarget(rootDir)
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            implementation(projects.trixnityClient)
            implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)
        }
    }
}
