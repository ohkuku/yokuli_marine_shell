plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("../../runtime/marine-local/src/main/java")
        kotlin.include("com/yokuli/compiler/**")
        // These are the Android production implementations, compiled unchanged against a JDBC
        // storage adapter. Never introduce a second LINZ/S-57 interpretation or binary schema.
        listOf("ChartSql", "ChartFeatureEncoder", "ChartGeometryWriter", "ChartNativeIndexWriter",
            "ChartGeometryBinary", "ChartPreparedIdentity", "ChartFactReader", "ChartGeometryBounds", "GeoPackageChartImporter", "GeoPackageGeometryReader",
            "LinzLdsAdapter", "S57Reader", "ChartDrawingClipper", "ChartDisplayWindow", "ChartGeometryQueryIndex").forEach {
            kotlin.include("com/yokuli/runtime/marine/chart/$it.kt")
        }
        listOf("ChartTerrainCompiler", "ChartTerrainTile", "ChartTerrainGeometry", "ChartTerrainGlb", "ChartTerrainBlockCodec").forEach {
            kotlin.include("com/yokuli/runtime/marine/chart/terrain/$it.kt")
        }
        listOf("PassageGeometry", "PassageGeometryOperations", "PassageGeometryWindow", "PassageWorkSession",
            "PassageRasterGeometry", "RasterSupercover", "PassageSemanticRegion", "PassageNavigationMesh",
            "PassageRegionCompiler", "PassageRegionProducts", "PassagePreparedArchive").forEach {
            kotlin.include("com/yokuli/runtime/marine/planning/$it.kt")
        }
    }
}
sourceSets.main { resources.srcDir("../../runtime/marine-local/src/main/assets") }
dependencies {
    implementation(project(":core:runtime-contract"))
    implementation(project(":core:chart-package"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.locationtech.jts:jts-core:1.20.0")
    implementation(libs.geographiclib)
    implementation("org.json:json:20240303")
}
application {
    mainClass.set("com.yokuli.compiler.MaritimeCompilerKt")
    applicationDefaultJvmArgs=listOf("-Xmx4g")
}
