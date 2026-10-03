# Actual Auckland route regression, 2026-10-04

## Immutable source and exact user input

Source baseline: `05229686ca880b434157dfb56dd71d4415e561d1`.
Package: `chart-library/packages/nz-linz-native-2026-10-03.yklpkg`, 1,211,654,336 bytes.
Verified SHA-256: `0259dc0b71842e5615456aa35947b6745f40d9170c19fa944b407e8128c8d576`.
Prepared source identity: `6ba67ff543c33e5e33741f2fccfe1cf5e63840d262f1a331c11d89402e3a5e27`.

A = latitude -36.786067, longitude 174.672049.
B = latitude -36.744436, longitude 174.807857.

The actual SQLite index contains a highest-priority depth-area hit at both endpoints:
A row 9227: 5–10 m; B row 9243: 10–20 m. Both are LINZ reference areas without a known vertical datum. Native span containment and an independent GEOS reconstruction agree. The prepared base products contain A in component 0 of region 2837/425 and B in component 0 of region 2838/426.

The original production `inspectPreparedPosition` method and native point-query classes, compiled unchanged with a small JDBC/platform adapter, return the expected hits with `incomplete=false`. The user's phone-specific empty lookup is NOT reproduced by this result, and the phone's installed bytes/selection are not established. A data-file absence must not be inferred from the original error message.

## Reproduced routing failures

Original production router on this actual package: connectivity and 2.1 m threshold, each run twice, all reached the cumulative two-second search timeout with no result. Warm preparation was about 1 ms, so this was not merely cold IO.

Instrumentation identified a real first-corner WGS84 round-trip rejection. A boundary-touching local funnel vertex produced an outside segment of about 2e-8 m after reprojection; repeated candidate corridors failed at the same corner. No positive tolerance or water expansion is used by this repair.

After avoiding boundary vertices, a second failure appeared: with the existing bounded working set, the 2838/425 corridor product and 2838/426 destination product repeatedly evicted each other. Throwing a preparation request restarted the entire search, which demanded the destination again. The query reached 768 reads of only three distinct products.

The filtered product was additionally a polygon-only GeometryCollection. JTS selected BasicPreparedGeometry, causing whole-region topological relation work on repeated line checks. Profiling showed GeometryGraph/Relate/MonotoneChain work dominating.

## Changes

Use interior triangle-portal points, followed by exact-tested bounded straightening, rather than coast-touching funnel vertices. Keep exact water checks locally and after WGS84 conversion. Start/end are never moved. Exponential lookahead plus six tested refinement steps avoids both quadratic scans and hundreds of almost-collinear points caused by a fixed 32-portal horizon.

Suspend the same refinement coroutine for product IO/preparation. Pause a cumulative search clock during producer work and resume it afterward. Do not restart the route on every LRU miss. Keep the 48 MiB working-set policy, 96 MiB individual product limit, source identity, product format and two-second active-search budget. Producer work retains its 120-second timeout. Budget exhaustion is a distinct NAVIGATION_SEARCH_BUDGET failure, not a no-route/no-data answer.

Unwrap polygon-only collection containers before preparing runtime predicates. Preserve all coordinates, polygons and holes, with no overlay or buffer. Collections whose flattened MultiPolygon would be invalid retain their original representation. This changes runtime wrappers, not stored facts or product identity.

## Verification and measured boundary

All figures below are host JVM route-call timings, NOT phone tap-to-render timings. Initial package download/import, Android UI/Binder/SAF, and full navigation analysis are excluded. Tests use 2.1 m as a reference depth threshold with zero extra UKC and no beam/air-draft setting; these are test parameters, not operating recommendations.

A fresh private fixture containing only the original prepared base pieces returned the 2.1 m draft in 2864 ms: 411 ms active search, 2442 ms reading/preparing condition products. A repeat returned in 151 ms: 144 ms search, 7 ms preparation. A separate loaded-data run returned in 179 ms. No claim is made that first-time condition preparation is one second.

The A–B 2.1 m result has 12 points and WGS84 length 28,587.946934 m. An independent GEOS check densified the emitted WGS84 route to 1,149 subsegments of at most 25 m, tested whole subsegments against actual base water and nearby shallow constraints, and found no violations. The lowest numeric depth constraint intersected was 5 m. Missing datum warnings remain; no tide or actual clearance is inferred.

The actual-map matrix also returned B–A and a short A-nearby route while preserving exact endpoints. A land start and a 1000 m threshold returned no route. Deterministic checks covered the cumulative paused clock and bounded, non-monotone shortcut acceptance. Five synthetic mesh cases cover open water, an island, a 0.5 m connecting corridor, disconnected water and an endpoint in a hole, including propagation of a cancelled mesh check.

These tests do not prove phone execution, global optimality, coverage for every route, bridge clearance, or suitability for active navigation. All app candidates remain draft-only and require the independent full-route analysis. 3D, published package contents, existing CI definitions and existing repository tests are unchanged.
