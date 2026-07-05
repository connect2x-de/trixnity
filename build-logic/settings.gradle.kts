rootProject.name = "build-logic"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
    }
}

plugins {
    id("de.connect2x.conventions.c2x-settings-plugin") version "20260618.161104"
}
