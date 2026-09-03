pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "YokuliOS"

include(
    ":app-shell",
    ":core:model",
    ":core:design",
    ":core:shell",
    ":core:testing",
    ":feature:desktop",
    ":feature:chart",
    ":feature:cockpit",
    ":feature:library",
    ":feature:system",
)
