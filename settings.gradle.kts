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
    ":core:map-domain",
    ":core:marine-data",
    ":core:design",
    ":core:shell-contract",
    ":core:shell-engine",
    ":core:testing",
    ":adapter:chart-google",
    ":adapter:chart-library-android",
    ":adapter:marine-data-android",
    ":adapter:map-storage",
    ":adapter:map-offline",
    ":adapter:shell-android",
    ":adapter:shell-storage",
    ":ui:shell-compose",
    ":feature:desktop",
    ":feature:chart",
    ":feature:chart-library",
    ":feature:nmea-input",
    ":feature:data-sources",
    ":feature:data",
    ":feature:settings",
    ":feature:shell-lab",
    ":benchmark:shell",
    ":baselineprofile:shell",
)
