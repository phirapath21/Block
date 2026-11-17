pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.mlkit.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("com\\.google\\.play.*")
                includeGroupByRegex("com\\.google\\.ai.*")
                includeGroupByRegex("com\\.google\\.ar.*")
                // The emulator test runner (UTP) pulls its device provider from here.
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.mlkit.*")
                includeGroupByRegex("com\\.google\\.gms.*")
                includeGroupByRegex("com\\.google\\.play.*")
                includeGroupByRegex("com\\.google\\.ai.*")
                includeGroupByRegex("com\\.google\\.ar.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BTCWidget"
include(":app")
