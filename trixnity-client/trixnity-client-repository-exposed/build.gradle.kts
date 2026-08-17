plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
}

kotlin {
    addJvmTarget()
    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(sharedLibs.lognity.api)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.trixnityClient.clientRepositoryTest)
            }
        }
        jvmMain {
            dependencies {
                api(libs.exposed.core)

                implementation(libs.exposed.dao)
                api(libs.exposed.r2dbc)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.r2dbc.h2)
            }
        }
    }
}
