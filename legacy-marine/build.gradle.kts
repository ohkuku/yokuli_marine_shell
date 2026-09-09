plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Consume the same build-time vault/CI variables as the Shell. Never package a
// second credential file or import private local.properties from the old app.
fun configuration(name: String) = providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull?.trim().orEmpty()
fun String.quoted() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val mapsConfigured = configuration("GOOGLE_MAPS_ANDROID_API_KEY").let { it.isNotBlank() && it != "MAPS_API_KEY_NOT_CONFIGURED" }
val linzKey = configuration("LINZ_API_KEY").ifBlank { configuration("YOKULI_LINZ_API_KEY") }
val linzOverride = configuration("LINZ_HYDRO_TILE_TEMPLATE")
val linzTemplates = if (linzOverride.isNotBlank()) listOf(linzOverride) else if (linzKey.isNotBlank()) listOf(4758, 4759, 4767).map { "https://tiles-a.data-cdn.linz.govt.nz/services;key=$linzKey/tiles/v4/set=$it/EPSG:3857/{z}/{x}/{y}.png" } else emptyList()
android {
    namespace = "com.yokuli.anchorwatch"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsConfigured.toString())
        buildConfigField("String", "LINZ_API_KEY", linzKey.quoted())
        buildConfigField("String", "LINZ_HYDRO_TILE_TEMPLATES", linzTemplates.joinToString("|").quoted())
        buildConfigField("boolean", "LINZ_HYDRO_CONFIGURED", linzTemplates.isNotEmpty().toString())
        buildConfigField("String", "LINZ_SOUNDING_LAYER_IDS", "50858|50866|50506|50418|51612".quoted())
        buildConfigField("String", "LINZ_DEPTH_AREA_LAYER_IDS", "50671|50553|50447|50852|51639".quoted())
        buildConfigField("String", "LINZ_DEPTH_CONTOUR_LAYER_IDS", "50672|50554|50448|50849|51638".quoted())
        buildConfigField("String", "YOKULI_YOUTUBE_URL", configuration("YOKULI_YOUTUBE_URL").ifBlank { "https://www.youtube.com/@yokuli_ocean_diary" }.quoted())
        buildConfigField("String", "YOKULI_BUYMEACOFFEE_URL", configuration("YOKULI_BUYMEACOFFEE_URL").ifBlank { "https://buymeacoffee.com/ukus3yya8a" }.quoted())
        buildConfigField("String", "YOKULI_WEBSITE_URL", configuration("YOKULI_WEBSITE_URL").quoted())
        buildConfigField("String", "YOKULI_CONTACT_EMAIL", configuration("YOKULI_CONTACT_EMAIL").ifBlank { "kuku.the.developer@gmail.com" }.quoted())
        buildConfigField("String", "YOKULI_PRIVACY_URL", configuration("YOKULI_PRIVACY_URL").quoted())
        buildConfigField("String", "YOKULI_SOURCE_CODE_URL", "https://github.com/ohkuku/yokuli_marine_shell".quoted())
        buildConfigField("String", "BUILD_GIT_SHA", configuration("GITHUB_SHA").ifBlank { "local" }.quoted())
        buildConfigField("String", "BUILD_GIT_BRANCH", configuration("GITHUB_REF_NAME").ifBlank { "local" }.quoted())
        buildConfigField("boolean", "BUILD_GIT_DIRTY", "false")
        buildConfigField("String", "BUILD_TIMESTAMP_UTC", configuration("BUILD_TIMESTAMP_UTC").quoted())
        buildConfigField("boolean", "BUILD_IN_CI", (configuration("GITHUB_ACTIONS") == "true").toString())
        buildConfigField("int", "DATABASE_SCHEMA_VERSION", "22")
        buildConfigField("String", "VERSION_NAME", configuration("YOKULI_VERSION_NAME").ifBlank { "0.3.0-experience.2" }.quoted())
        buildConfigField("int", "VERSION_CODE", configuration("YOKULI_VERSION_CODE").toIntOrNull()?.toString() ?: "3")
    }
    buildTypes {
        getByName("debug") { buildConfigField("String", "BUILD_CHANNEL", "debug".quoted()) }
        getByName("release") { buildConfigField("String", "BUILD_CHANNEL", "release".quoted()) }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
}
