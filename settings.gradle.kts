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
    ":core:shell-contract",
    ":core:shell-engine",
    ":core:testing",
    ":adapter:chart-google",
    ":adapter:shell-android",
    ":adapter:shell-storage",
    ":ui:shell-compose",
    ":feature:desktop",
    ":feature:chart",
    ":feature:settings",
    ":feature:shell-lab",
)
