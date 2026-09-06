# Chart Library 当前支持矩阵

版本：CL12 候选；机器结论等待 GitHub Action。

## 已实现范围

| 维度 | 当前合同 | 尚未宣称 |
|---|---|---|
| Shell 产品 | 独立「海图库」App；All Apps/搜索/三尺寸磁贴；不自动固定 | Android HOME、第二个 APK |
| 外部来源 | SAF 文件夹树、SAF 单文件；只读、原地引用、持久授权 | 修改/移动/删除外部原件；任意文件系统路径 |
| 可直读形态 | provider 返回可 seek 的完整文件 descriptor | pipe/虚拟文档、依赖未提供 WAL 的活动数据库 |
| 海图格式 | raster MBTiles；PNG/JPEG/WebP；256/512；TMS/XYZ；tiles table/view | S57/S63、GeoTIFF、PMTiles、PBF、混合 tile size |
| 目录 | Room 元数据索引；来源 membership；稳定 asset ID；分层验证 | 把 tile blob 复制进目录 DB；扫描即航海认证 |
| 显示 | MapLibre 多资源计划，最多 8 个 raster；本地真实 tile-key coverage | 用 metadata bounds 冒充真实覆盖；Google 填本地缺口 |
| 在线地图 | 未选择本地图且 CI/APK 注入非占位 key 时，可选择 Google 在线底图 | key 已授权、账单有效、网络可用或图块已加载 |
| 旧数据 | 旧 `map_packages`、活动版本和 history 建立兼容 catalog 映射 | 删除旧包、自动复制全部外部资源 |
| 平台 | minSdk 26、target/compile 36、竖屏、方屏适配合同 | 横屏、物理 16 KiB 设备已验证 |

## 失败语义

- 授权丢失、来源离线、文件缺失、内容变化、不能随机读取、格式不支持、基础可读、完整验证分别表达。
- 扫描部分失败保留上次目录事实；未完整枚举的区域不会批量标 missing。
- 过载返回有类型的资源上限错误，不无限排队；失败不会放宽 loopback 或外部文件写权限。
- 原地引用和“保存应用内副本”是两个明确动作；只有后者会产生完整副本。

## 证据边界

- GitHub `build` job 生成 `google-maps-configuration.json`，仅包含配置布尔值、包名和一致性，不包含 key。
- API34 job 生成 `chart-library-cl11-evidence.json`，包含零复制、读 session/队列和 1,000 项目录的有界测量。
- emulator/host fixture 不能替代真实 USB/SD/provider、OEM、低内存、16 KiB page-size 或 30 分钟物理 soak。

## English translation

Chart Library is an independent in-shell app backed by read-only SAF folder or single-document references. The implemented direct-read path supports seekable, whole-file raster MBTiles with PNG/JPEG/WebP tiles, 256/512 tile sizes, TMS or XYZ row schemes, and table- or view-backed tile queries. Stream-only providers, live databases that depend on unavailable journals, vector chart formats, and mixed tile sizes are not claimed as supported.

MapLibre consumes at most eight explicitly selected local raster resources and uses real tile keys for coverage. Google is an optional connected base map only when no local chart is selected. A configured key does not prove authorization, billing, connectivity, restrictions, or successful tile delivery. Physical provider, 16 KiB device, low-memory, and 30-minute soak evidence remains explicitly unverified until supplied.
