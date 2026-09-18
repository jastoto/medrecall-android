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
        // Required by MSAL (OneDrive sign-in): its "common" internal
        // module depends on com.microsoft.device.display:display-mask,
        // which Microsoft only publishes here, not on Maven Central --
        // see https://learn.microsoft.com/entra/identity-platform/tutorial-mobile-app-android-prepare-app
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
            name = "Duo-SDK-Feed"
        }
    }
}

rootProject.name = "MedRecall+"
include(":app")
