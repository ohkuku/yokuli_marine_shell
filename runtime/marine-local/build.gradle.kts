plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.yokuli.runtime.marine"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":core:runtime-contract"))
    // Transitional domain DTOs live here. App consumers use the api package; never the controller/DAO.
    api(project(":legacy-marine"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(libs.androidx.core.ktx)
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
