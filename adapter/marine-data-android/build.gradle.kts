plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.yokuli.marine.data.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        // Keep the library instrumentation APK on the same permission semantics as app-shell.
        targetSdk = 36
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
            }
        }
    }
}

dependencies {
    api(project(":core:marine-data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.protobuf.javalite)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
