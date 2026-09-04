plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:shell-contract"))
    testImplementation(libs.junit)
}
