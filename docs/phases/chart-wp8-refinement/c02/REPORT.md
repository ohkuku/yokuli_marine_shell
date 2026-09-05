# C02 单一真实地图 Renderer 报告

> English translation: C02 replaces the production key-dependent renderer fork with one local MapLibre raster path, proves real MBTiles pixels and a traced NOAA subset, and defines an SDK-free camera/query/lifecycle contract. It is not an online-chart or bundled-navigation-data claim.

## 状态

- package：C02
- baseline：`038f1b4e87e1fb7db79bb10178ff10b0c7644c82`
- implementation candidate：`8bb791209ec8753340444f629e8f9f04fc232fef`
- cumulative verified SHA：`8bb791209ec8753340444f629e8f9f04fc232fef`
- status：`VERIFIED_LOCAL`
- hosted CI：留给 C12 的最终同 SHA Gate；本报告不伪造远端结果

## 交付

1. `MapRendererContract` 将 MapLibre/Android 类型隔离在 adapter 外，提供 generation、ready/error、coverage、camera command/ack、viewport、投影、反投影、hit query 和稳定 overlay ID。
2. app-shell 只安装 `OfflineMarineChartSurface`。Google adapter 保留为未接入生产图的历史模块；生产构建、manifest 和 workflows 都不再读取 Google Maps key。
3. Android MBTiles repository 验证真实 SQLite raster package、PNG/JPEG、128/256/512 tile、TMS/XYZ 行号并原子安装；单字节坏块是明确负例。
4. API 34 测试让 MapLibre 从 `mbtiles://` 冷路径渲染不对称 fixture 和 NOAA 小区域，再从 snapshot 采样可区分像素，而非只检查 View 存在。
5. renderer generation 和 camera command ID 防止旧 style/包/相机回调覆盖当前状态；默认 SDK camera 在持久恢复前不会反写。
6. 生产地图进出时昂贵 MapView 被销毁。50 次地图访问的峰值并发为 1、最终存活为 0；AnimatedContent 允许每次访问 1–2 次均完整销毁的提交，不将“创建数必须正好 50”误当规格。

## 来源与许可边界

- 决策页：`docs/phases/chart-wp8-refinement/C02_RENDERER_SOURCE_DECISION.md`
- NOAA 原始来源包 SHA-256：`33ed95c59b85514accbcb1deb8eb23f155d47a4b3c3e9a6ca88e70f9d98e616c`
- 仓库测试子集 SHA-256：`7da5ed14bc0b79585ec39f0afeb4fdde9d449a1f811054ea754929525530064f`
- 像素转换：无；只复制四个原始 tile 和必要 metadata
- 限制：测试子集不是官方 NOAA chart，不得据此宣称 Release 已内置可航行资料

## 已运行的聚焦证据

- repository Python contracts：165 passed
- `.github/scripts/test-ci-contract.sh`：passed
- renderer/domain JVM 与编译：passed
- API 34 `:adapter:map-offline:connectedDebugAndroidTest`：6 passed
- API 34 50 次 map/Search/Bridge 生命周期故事：1 passed，peak live=1，final live=0

## 累计 Gate

- Python contracts：165 passed；CI/release workflow contract passed
- Gradle：1151 tasks；全仓 test、lint、Standalone Debug/Release passed
- JVM XML：235 tests，0 failure/error
- API 34：offline renderer 6、app-shell 39、Room 4，0 failure
- Debug APK SHA-256：`f36d7c8c57458b1f23d8f23b2801e20ee15fe8988e017fbe49e959b0cca0309d`
- unsigned Release APK SHA-256：`210b5da8dc469ff8752a1948ff2c92e77df51e6f6a68d3fa7b78da72ea822615`

Release manifest 无位置权限和 Google Maps metadata。MapLibre 库仍声明网络/网络状态/Wi-Fi 状态权限；本 C02 只证明当前生产 renderer/style/source 无 HTTP URL，并由 API 34 的本地 `mbtiles://` 像素测试证明无需 Google key 的离线浏览路径。

封口自审补入的多 zoom、90 度旋转、透明边、动态比例尺、单调 command ID、viewport inset 和“切包不自动 fit-all”断言已在同一候选上累计重跑。高密度 API 34 视口会裁掉原 fixture 的外圈透明边，因此 fixture 改为视口内透明窗口；旋转则比较整帧采样差异，避免单点恰好同色造成假失败。这两项修正都收紧证据，没有改变生产 renderer。

## 未宣称完成

- 没有在线海图提供方、生产资料下载或 Google Maps 路线。
- 没有把测试 fixture 当成用户可用航海产品。
- 没有完成 C03 的 WP 地图层级、feature-first Back、方屏和 IME 交互。
- 没有三星方屏实体机结论；该项仍由所有者审核。
