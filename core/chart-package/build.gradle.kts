plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
}
