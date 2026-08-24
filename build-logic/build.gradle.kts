import de.connect2x.conventions.applyKtfmt
import de.connect2x.conventions.asLibrary

plugins {
    `kotlin-dsl`
    alias(sharedLibs.plugins.c2xConventions)
    alias(sharedLibs.plugins.ktfmt)
}

dependencies {
    compileOnly(sharedLibs.plugins.kotlin.multiplatform.asLibrary())
    compileOnly(sharedLibs.plugins.android.library.asLibrary())
}

applyKtfmt()

gradlePlugin {
    plugins {
        register("conventions") {
            id = "de.connect2x.trixnity.conventions"
            implementationClass = "ConventionsPlugin"
        }
    }
}
