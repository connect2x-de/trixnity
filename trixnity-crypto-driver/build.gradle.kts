plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addNativeTargets()
    applyDefaultHierarchyTemplate()
}
