plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val releaseVersionCode = providers.environmentVariable("YOKULI_VERSION_CODE").orNull?.toIntOrNull() ?: 1
val releaseVersionName = providers.environmentVariable("YOKULI_VERSION_NAME").orNull ?: "0.1.0-dev"
val unavailableMapsApiKey = "MAPS_API_KEY_NOT_CONFIGURED"
val googleMapsApiKey = providers.environmentVariable("GOOGLE_MAPS_ANDROID_API_KEY")
    .map { value -> value.trim().ifEmpty { unavailableMapsApiKey } }
    .orElse(unavailableMapsApiKey)
val googleMapsConfigured = googleMapsApiKey.map { value -> value != unavailableMapsApiKey }
val gitSha = providers.environmentVariable("GITHUB_SHA").orElse("local")

android {
    namespace = "com.yokuli.marine.shell"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yokuli.marine"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["GOOGLE_MAPS_ANDROID_API_KEY"] = googleMapsApiKey.get()
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", googleMapsConfigured.get().toString())
        buildConfigField("String", "GIT_SHA", "\"${gitSha.get().replace("\"", "")}\"")
    }
    flavorDimensions += "shellMode"
    productFlavors {
        create("standalone") {
            dimension = "shellMode"
            buildConfigField("boolean", "SHELL_HOME_MODE", "false")
        }
        create("home") {
            dimension = "shellMode"
            applicationIdSuffix = ".home"
            versionNameSuffix = "-home"
            buildConfigField("boolean", "SHELL_HOME_MODE", "true")
        }
    }
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("zh-rCN", "en")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:shell-contract"))
    implementation(project(":core:shell-engine"))
    implementation(project(":ui:shell-compose"))
    implementation(project(":adapter:shell-android"))
    implementation(project(":adapter:chart-google"))
    implementation(project(":feature:desktop"))
    implementation(project(":feature:chart"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(project(":feature:shell-lab"))
}
