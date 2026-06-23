plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addNativeTargets()
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.trixnityCryptoDriver)
            implementation(projects.trixnityVodozemac)
        }

        commonTest.dependencies {
            implementation(projects.trixnityCryptoDriver.driverTest)
        }
    }
}
