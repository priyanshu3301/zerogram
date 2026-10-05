pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Zerogram 3.0"
include(":app")
include(":tdlib")
include(":core-tdlib")
include(":core-data")
include(":core-ui")
include(":feature-home")
include(":feature-folder")
include(":feature-category")
include(":feature-search")
include(":feature-transfers")
include(":baselineprofile")
