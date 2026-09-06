# 实施地图 · 已核实代码到目标职责

基线：Shell `codex/shell-map-contract@4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5`；旧仓 `codex/develop@a845d3d734d3b573a2b53952e66e5f800e944205`。日期 2026-09-06。

这是只读源码核对的地图，不是已修改代码清单。先比较实际HEAD与此基线的相关diff；文件没变不重复读。新符号/路径以“建议新增”标明。旧仓只读，不需要每张工单再搜一遍。

## A. 已读取的主要文件

| 来源 | 已核实路径/用途 | 实施决定 |
|---|---|---|
| R01 | `README.md`<br>阶段与产品面/导航/WP8合同 | 保留现行规则；对海图库新增明确覆盖条款 |
| R02 | `docs/implementation/NMEA_SOURCES_P0_BASELINE.md`<br>当前Shell/旧仓分支、真实接入点、SDK | 只读取相关基线；历史PASS不等于本轮已执行 |
| R03 | `feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt`<br>Chart内资源维护、安装后激活 | 拆维护工作流到Library；显示选择保留Chart |
| R04 | `adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt`<br>app-private复制/验证/版本事务/leases | 提取受管backend；保留恢复，不用于外部零复制扫描 |
| R05 | `adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt`<br>MapLibre生产renderer、mbtiles本地路径 | 注入read/render plan桥；保留MapView生命周期/overlay |
| R06 | `adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidChartCoverageIndex.kt`<br>TMS、本地路径、真实tile key查询 | 改共享reader并支持多资产revision；仍查真实瓦片 |
| R07 | `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt`<br>严格SHA版本、FULL_TILE_DECODED单验证级 | 扩展为外部revision/分层验证；保留legacy映射 |
| R08 | `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartSourceCatalog.kt`<br>发布者/获取渠道静态目录 | 不当文件夹表、不复用枚举、不捏造下载支持 |
| R09 | `app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt`<br>单点InstalledAppBinding与import接线 | 追加Library binding与host；保留其它binding及Start布局 |
| R10 | `app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt`<br>applicationScope、仓储和NMEA所有权 | 注入process Library runtime；不耦合到NMEA FGS |
| R11 | `feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt`<br>typed token、pinnable三尺寸 | 复制贡献模式，不复制Chart领域状态 |
| R14 | `gradle/libs.versions.toml`<br>MapLibre13.4.1等锁定依赖 | 核验实际API；不顺手升级全栈 |
| R12 旧仓 | `app/src/main/java/com/yokuli/anchorwatch/map/OfflineMbTiles.kt`<br>raster识别、table/view检查、TMS/XYZ exact query；旧实现单槽4GB复制 | 只抽取语义；不迁移Google接口、单槽或硬编码256 |
| R13 旧仓 | `app/src/main/java/com/yokuli/anchorwatch/map/nautical/NauticalSourceResolver.kt`<br>底图偏好与叠加职责 | 参考分工，不复活旧在线地图栈 |

## B. 已定位、按任务需要定点展开的文件

以下不代表本轮逐行读完；开始修改时只展开对应符号附近：

| 路径 | 查找目标 |
|---|---|
| `feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt` | import参数/包管理区域/图层入口；文件较大，不默认全读 |
| `feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartImportUiContract.kt` | 原维护UiState/UiAction迁移与兼容 |
| `feature/chart/src/main/java/com/yokuli/marine/feature/chart/OfflineCoverageCoordinator.kt` | requiredKeys、结果revision与取消 |
| `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt` | ChartPackage/activeChartPackageId/显示状态 |
| `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt` | SelectChartPackage/ChartPackagesChanged相关分支；不重写整个reducer |
| `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapStore.kt` | 只读catalog桥/持久显示选择 |
| `core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/OfflineCoverage.kt` | 多资源覆盖结果与真值证据 |
| `adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapLibraryDatabase.kt` | 原Map schema及迁移边界；不要与新资源目录混为一库 |
| `adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/RoomMapPersistence.kt` | 已有图ID/显示选择映射 |
| `adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/MapProtoMapper.kt` | 遗留序列化兼容 |
| `app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt` | picker回调/Back/host/wiring |
| `app-shell/src/main/java/com/yokuli/marine/shell/ShellViewModel.kt` | 原coordinator创建位置、一次性effect分派 |
| `feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartPackageCoordinatorTest.kt` | 维护迁出后的等价行为回归 |
| `feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartLauncherIntegrationContractTest.kt` | 注册与导航不能回归 |
| `feature/chart/src/test/java/com/yokuli/marine/feature/chart/ChartLauncherProjectionTest.kt` | 磁贴状态的域边界 |
| `settings.gradle.kts` / `adapter/map-offline/build.gradle.kts` | 新模块注册/依赖/ABI/构建 |

通过真实`git grep`查找 InstalledAppBinding/registry、WP status contribution、产品surface断言；路径以当前工作树为准。不凭旧文档约定猜出现有没有的类。

## C. 建议新增/抽取，不是已有实现

- `feature/chart-library/`：`ChartLibraryUiState`、`ChartLibraryUiAction`、reducer/projector、workspace、destinations、shell contribution、launcher presentation。
- `adapter/chart-library-android/`：SAF grant/source access、catalog数据库、scanner、验证worker、只读FD SQLite reader、managed backend、read session/lease、runtime。
- `core/map-domain/.../chartlibrary/`：来源/资源/revision/能力/验证模型，read/command/job/access/tile端口，scan reconciliation与display resolver纯逻辑。
- `adapter/map-offline/`：内部loopback TileSet桥、共享reader驱动coverage；保留现有MapLibre host与overlay。
- `app-shell/`：process runtime、picker结果路由、独立InstalledAppBinding、Chart/Settings到Library深链。

现有`AndroidMbTilesRepository`分步抽取受管实现，避免adapter循环依赖。推荐将存储/事务部分移入新Library adapter；渲染adapter只依赖core端口，具体实例由app-shell注入。`ChartPackageRepository`可作为短期legacy facade，但不能长期保留两个权威资产目录。

## D. 必须保留与必须改变

必须保留：用户受管文件/历史、旧ID可解析、map/desktop持久数据、MapLibre单实例生命周期、真实tile覆盖、generation/cancel、NMEA所有权。

必须改变：Chart拥有维护流程；每次扫描完整复制/解码/hash；外部XYZ改写；只允许FULL_TILE_DECODED；文件夹ID和发布者enum混用；每个原件都要求本地path；资源目录只能在Chart页面打开后刷新。

## E. 来源链接

- [R01] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/README.md
- [R02] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/docs/implementation/NMEA_SOURCES_P0_BASELINE.md
- [R03] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt
- [R04] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt
- [R05] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt
- [R06] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidChartCoverageIndex.kt
- [R07] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt
- [R08] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartSourceCatalog.kt
- [R09] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt
- [R10] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt
- [R11] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt
- [R12] https://github.com/ohkuku/yokuli_nmea_anchor_alarm/blob/a845d3d734d3b573a2b53952e66e5f800e944205/app/src/main/java/com/yokuli/anchorwatch/map/OfflineMbTiles.kt
- [R13] https://github.com/ohkuku/yokuli_nmea_anchor_alarm/blob/a845d3d734d3b573a2b53952e66e5f800e944205/app/src/main/java/com/yokuli/anchorwatch/map/nautical/NauticalSourceResolver.kt
- [R14] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/gradle/libs.versions.toml
