plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val mapsKey = providers.environmentVariable("GOOGLE_MAPS_ANDROID_API_KEY")
    .map { it.trim().ifEmpty { "MAPS_API_KEY_NOT_CONFIGURED" } }
    .orElse("MAPS_API_KEY_NOT_CONFIGURED")

android {
    namespace = "com.yokuli.marine.shell"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yokuli.marine"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.environmentVariable("YOKULI_VERSION_CODE").orNull?.toIntOrNull() ?: 4
        versionName = providers.environmentVariable("YOKULI_VERSION_NAME").orNull ?: "0.4.0-domains.1"
        manifestPlaceholders["GOOGLE_MAPS_ANDROID_API_KEY"] = mapsKey.get()
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", (mapsKey.get() != "MAPS_API_KEY_NOT_CONFIGURED").toString())
    }
    flavorDimensions += "shellMode"
    productFlavors { create("standalone") { dimension = "shellMode" } }
    signingConfigs {
        if (releaseKeystorePath != null) create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/rebuild/java"))
            res.setSrcDirs(listOf("src/rebuild/res"))
            manifest.srcFile("src/rebuild/AndroidManifest.xml")
        }
        getByName("test").java.setSrcDirs(emptyList<String>())
        getByName("androidTest").java.setSrcDirs(emptyList<String>())
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:shell-contract"))
    implementation(project(":core:shell-engine"))
    implementation(project(":core:design"))
    implementation(project(":ui:shell-compose"))
    implementation(project(":feature:desktop"))
    implementation(project(":adapter:shell-android"))
    implementation(project(":adapter:shell-storage"))
    implementation(project(":legacy-marine"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.maplibre.android.opengl)
    implementation(libs.play.services.maps)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
