plugins { kotlin("multiplatform") version "2.1.20" }

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
                // SDK CSP excludes eval/inline scripts. Production output is packaged below.
                sourceMaps = false
            }
        }
        binaries.executable()
    }
}

// Production compile/distribution only. Creates the exact installable SDK 1 package format.
tasks.register<Zip>("packageYokuli") {
    dependsOn("jsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/js/productionExecutable"))
    from("manifest.json")
    archiveFileName.set("org.example.kotlinboat.yokuli.zip")
    destinationDirectory.set(layout.buildDirectory.dir("yokuli"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
