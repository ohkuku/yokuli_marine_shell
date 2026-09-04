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
    ":core:shell-engine",
    ":core:testing",
    ":adapter:chart-google",
    ":feature:desktop",
    ":feature:chart",
    ":feature:settings",
    ":feature:shell-lab",
)
