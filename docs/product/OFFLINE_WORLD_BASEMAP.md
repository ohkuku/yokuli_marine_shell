# 内置全球地图与卫星来源

“地图 / map”是默认来源，由 MapLibre 直接读取 APK 内置的 Natural Earth **1:10m（1:1000 万）**全球数据。陆地、湖泊、河流、边界、主要道路和地名均随应用安装，无需首次下载、地图瓦片服务或 Google Play services。普通地图不再使用在线 Google 普通图或 OpenStreetMap 瓦片。

## 生产资源与显示

以下文件位于 `app-shell/src/main/assets/maps/`，使用 WGS 84 地理坐标：

- `world_land.geojson`：陆地与海岸轮廓。
- `world_lakes.geojson`：湖泊。
- `world_rivers.geojson`：河流。
- `world_boundaries.geojson`：行政边界。
- `world_roads.geojson`：主要道路。
- `world_labels.geojson`：国家、城市及水域等地名，保留中文、英文名称供当前语言选择。

地名使用设备系统字体在本地绘制，不请求远程 glyph 或字体服务。地图背景、图形样式和标签由同一离线地图实现管理，地理图形始终由原生地图引擎按同一相机投影。该实现服务于海图、锚警、AIS 和位置选择等共用地图入口。

Natural Earth 是公共领域数据，可修改和随应用分发。界面保留“Natural Earth”署名；各原始数据集、下载来源、版本及转换说明保存在资产目录的来源清单中。官方[数据目录](https://www.naturalearthdata.com/downloads/10m-physical-vectors/)与[使用条款](https://www.naturalearthdata.com/about/terms-of-use/)说明其范围和许可。

这是概略全球参考地图，不包含可用于航行判断的水深、障碍物或助航设施；局部细节由用户导入的海图提供。

## 自定义海图

自定义图层始终在相同的离线全球地图上渲染。文件夹内海图继续使用既有优先级顺序；缺失、透明或未覆盖的区域显示内置底图。来源选择器直接显示当前选中的来源，空图层可直接切回“浏览内置地图”。删除当前自定义图层时回退到内置地图。

地图来源仍由 `MapSessionStore` 统一管理。持久化类型使用 `offline`；升级前的 `online`、`standard` 自动迁移并保存为 `offline`。自定义图层身份与原有海图文件优先级保持不变。

## 卫星来源边界

“卫星 / satellite”保留 Google Maps **HYBRID**：卫星影像叠加地名、道路等地图信息。它是独立的在线来源，选择项明确注明“高清影像与地名 · 需要网络”；没有配置服务的构建禁用该选项。Google Maps 署名和既有 API key 管理保留，应用不下载、打包或承诺永久离线缓存 Google 卫星瓦片。

纯 AOSP 的 ROM host 默认使用内置离线地图；升级时若保存的是不可用卫星来源，也回退到内置地图。ROM host 不依赖 Google Play services 来显示普通地图和用户海图。
