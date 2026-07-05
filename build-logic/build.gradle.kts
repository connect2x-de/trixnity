import de.connect2x.conventions.asLibrary

plugins {
    `kotlin-dsl`
    alias(sharedLibs.plugins.c2xConventions)
}

dependencies {
    compileOnly(sharedLibs.plugins.kotlin.multiplatform.asLibrary())
    compileOnly(sharedLibs.plugins.android.library.asLibrary())
}

gradlePlugin {
    plugins {
        register("conventions") {
            id = "de.connect2x.trixnity.conventions"
            implementationClass = "ConventionsPlugin"
        }
    }
}
