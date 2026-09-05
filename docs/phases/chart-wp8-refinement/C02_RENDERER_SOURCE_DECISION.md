# C02 Renderer 与真实资料决定 / Renderer and real-source decision

## 中文

- 生产交互地图只有 MapLibre Native Android `13.4.1` 一条路径。Google adapter 保留为隔离的历史模块，但 `app-shell` 不再依赖或选择它。
- 本地 raster 包使用 MapLibre 已实现的 `mbtiles://` 协议；本仓库用设备级 snapshot 验证真实像素，不以 View 存在或 style callback 代替渲染证据。
- Host ready、renderer ready 与 package attached/degraded/missing 是三个独立事实。`PACKAGE_ATTACHED` 只表示 style 接受本地 source，不宣称所有瓦片齐全。
- 恢复、北向复位与查看对象都通过带单调 ID 的相机命令；SDK 默认相机不会在首次恢复前写回，程序化 echo 不重复持久化。
- renderer、style 与 package 均用 generation 隔离迟到回调；释放后的 query port 和回调无副作用。

真实资料 fixture 来自 NOAA Chart Display Service 的官方 `ncds_21.mbtiles`。NOAA 明确提供该格式给离线应用；仓库只保留四个未重新编码的 PNG tile 和最小 MBTiles 元数据。完整来源 URL、原包 SHA-256、tile 坐标及每块 SHA-256 见同目录 `NOAA_NCDS_21_SOURCE.json`。该派生样本仅用于渲染验证，不是 NOAA 官方产品，也不得用于满足法规或实际航行需求。

## English

- Production has one interactive map path: MapLibre Native Android `13.4.1`. The Google adapter remains isolated history; `app-shell` neither depends on nor selects it.
- Local raster packages use MapLibre's implemented `mbtiles://` protocol. A device snapshot proves actual pixels; View existence and style callbacks are not accepted as rendering evidence.
- Host ready, renderer ready, and package attached/degraded/missing are separate facts. `PACKAGE_ATTACHED` never claims complete tile coverage.
- Restore and view commands carry monotonic IDs. The SDK default camera cannot overwrite the session before restore, and a programmatic echo is not persisted again.
- Renderer/style/package generations discard late callbacks; disposed query ports and callbacks have no effect.

The real-data fixture is a four-tile, non-reencoded subset of NOAA Chart Display Service `ncds_21.mbtiles`. It is verification material, not an official NOAA chart or a navigation product. Exact provenance and hashes are stored beside the fixture.

## Primary evidence

- NOAA GIS Data & Services: https://nauticalcharts.noaa.gov/data/gis-data-and-services.html
- NOAA NCDS MBTiles download: https://distribution.charts.noaa.gov/ncds/index.html
- NOAA public-domain/CC0 dataset record: https://www.fisheries.noaa.gov/inport/item/39977
- MapLibre local MBTiles implementation discussion: https://github.com/maplibre/maplibre-native/discussions/3650
- MapLibre Android `13.4.1` release: https://github.com/maplibre/maplibre-native/releases/tag/android-v13.4.1
