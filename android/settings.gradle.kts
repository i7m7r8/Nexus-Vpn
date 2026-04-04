pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Guardian Project Maven Repository (official) - for tor-android
        maven { url = uri("https://guardianproject.github.io/gpmaven") }
    }
}

rootProject.name = "NexusVpn"
include(":app")
