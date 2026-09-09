# Packaged world reference background

The custom-chart renderer always starts with a local ocean background and the packaged Natural Earth land geometry. User raster charts are drawn above it in their existing file priority order; missing or transparent pixels reveal the reference background. The asset is part of the APK and never requires a first-run download. This background provides general land and coastlines only: it has no soundings, hazards, aids to navigation, or navigational accuracy guarantee.

- Dataset: Natural Earth **1:50m land, version 4.1.0**, WGS 84. The version comes from the downloaded `ne_50m_land.VERSION.txt`; the download webpage still labels its link 4.0.0.
- Official dataset page: https://www.naturalearthdata.com/downloads/50m-physical-vectors/50m-land/
- Downloaded archive: https://naturalearth.s3.amazonaws.com/50m_physical/ne_50m_land.zip
- Retrieved: 2026-09-09.
- License: **public domain**. Natural Earth permits modification and redistribution, including commercial use, without requiring attribution. The application nevertheless displays “Natural Earth”. Official terms: https://www.naturalearthdata.com/about/terms-of-use/
- Archive SHA-256: `0b8e670cf80dce9cbebe2a193bc44ba5602758c22e1fa603980553646d7ff162`.
- Packaged file: `app-shell/src/main/assets/maps/ne_50m_land.geojson`.
- Asset SHA-256: `8140a89705e005fb43a1548f2830fc5fa4559706408ffb1686b53a46e43d0ce3`.

Conversion reads the archive's Polygon shapefile records, splits exterior rings into individual GeoJSON Polygon features, assigns holes to the smallest containing exterior, reverses the shapefile ring winding to GeoJSON winding, rounds coordinates to five decimal places, and removes unused attributes. No shoreline simplification is added. The resulting asset contains 1,421 land polygons, 60,669 source vertices and one retained interior ring; it occupies 1,379,459 bytes (approximately 477 KB compressed in the APK).

MapLibre loads the geometry directly with `asset://maps/ne_50m_land.geojson`, a supported packaged-asset URI: https://maplibre.org/maplibre-native/android/examples/geojson-guide/. Raster priority and reader code do not use or modify the base geometry. The source picker explains the background's limits; the map uses a compact attribution instead of a warning panel.

The map-source single-choice row follows the original `feature/settings/SettingsWorkspace.kt` `SelectionRow`: a 22 dp square, 2 dp border and 10 dp accent insert. The whole row is selectable with a minimum 56 dp touch height, a radio-button role, a selected state and a single-choice group; unavailable options are actually disabled.
