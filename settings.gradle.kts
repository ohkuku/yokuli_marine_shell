pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "YokuliOS"
include(":app-shell")
include(":core:shell-contract", ":core:shell-engine", ":core:design")
include(":ui:shell-compose", ":feature:desktop", ":adapter:shell-android", ":adapter:shell-storage")
include(":legacy-marine")
