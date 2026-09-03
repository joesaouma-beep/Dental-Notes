pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Compose Multiplatform pulls a few androidx artifacts that are only
        // published to Google's repository.
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
}

// The Android app is a separate Gradle build under android/ so that the desktop
// side can be built and tested without the Android SDK. Both consume the same
// shared/ sources.
rootProject.name = "dental-notes"
include(":shared", ":desktop-core", ":desktop")
