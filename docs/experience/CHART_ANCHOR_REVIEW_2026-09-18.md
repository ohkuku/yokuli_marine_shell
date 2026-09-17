# 海图与守锚复核 · 2026-09-18

审查输入是 `Yokuli-implementation-review-2026-09-17.md`（固定旧提交的静态审查）和用户本轮 16 项反馈。本文记录实现落点，不把静态检查当作 APK 实测。

## 用户故事与实现

| 项目 | 现在的行为 | 主要实现 |
| --- | --- | --- |
| 船位与船首向 | 船形缩小至 30 dp 位图，只有真实、新鲜的船首向才旋转；没有船首向时保留小型无方向船位标记。COG 独立绘制一分钟航程虚线，低于 0.5 kn 或字段/船位过期不画。 | `chart/MapScene.kt`, `NativeSceneRenderer.kt`, `VesselGeometry.kt`, `ui/ChartMapAdapter.kt` |
| 点击收藏 | 原地高亮并出现坐标和“地点详情”一个入口；不移动镜头，不让准星跳到标点。不再并列“我的航行”。 | `ui/ChartMapAdapter.kt`, `ChartScreen.kt` |
| 点击空地图 | 准星保持屏幕中心；拖动/缩放和明确的定位按钮控制镜头。 | `chart/ChartSurface.kt` |
| 共享地图 UX | 海图和守锚共用标题/图源入口、46 dp 缩放按钮、船位读数与更新时间、准星坐标栏。两应用保留独立视口，同用原生 renderer。 | `ui/MarineMapControls.kt` |
| 下锚 | 下锚 → 在地图确认位置和半径 → 开始值守。当前船位模式明确为“以开始时船位下锚”，坐标及圈随可信船位更新；“锚在准星”切换为固定地图锚点。 | `ui/AnchorExperience.kt`, `AnchorSetupPolicy.kt` |
| 值守与估计 | 主地图保留紧凑状态、边界、渐隐轨迹及累计区域。无轨迹时禁用估计；只有 READY/可观测候选才能画候选点或进入采用确认。失败估计不在地图产生问号。 | `ui/AnchorExperience.kt` |
| 范围的含义 | 边界圈是用户设置的警戒半径；候选圈只用有效估计不确定度，不以预期摆动半径充当。累计色斑从固定 3 m 网格起步，与警戒半径无关，不表示 GPS 精度。 | `chart/AnchorSwingCoverage.kt`, `ui/AnchorExperience.kt` |
| 数据较久未更新 | 船位保留最后读数和更新时间；航速/COG 字段较旧时各自显示时间，不用位置时间给旧字段续期。旧位置可通过定位键查看，但不启动实时跟随。 | `ui/MarineMapControls.kt`, `ChartScreen.kt`, `AnchorExperience.kt` |

## 审查问题对应

| 审查 | 修改 | 回归依据 |
| --- | --- | --- |
| R02 | `effectiveAnchorPoint` 对当前船位模式使用同一可信位置，不再固定旧 picked 点预览。 | `ChartAnchorPolicyTest.currentAnchorPreviewFollowsTheAcceptedPositionUsedAtStart` |
| R03 | 主确认和选项确认调用同一个 `startWatch` 和 `anchorWatchInput`，保留 BACKDOWN、UNKNOWN、锚链、水深及收藏来源。 | `estimateDraftKeepsBackdownGeometryAndUnknownCentre`、无效几何拒绝、手动来源测试 |
| R04 | 导航卡、地图与目标管理读取冻结路线。收藏预览使用独立 `MapViewState.previewRoute` 快照；恢复当前导航显式退出预览。 | `editedSavedRouteCannotChangeActiveGuidanceVersion`、另一条路线预览测试 |
| R05 | 空/非空取消都调用 `cancelRouteDraft` 清除点、ID 和编辑模式；空草稿新建明确清旧 ID。 | 需 APK 操作“撤销至空→取消→新建→保存”并检查旧收藏不变 |
| R12 | `preservedCoordinate` 逐轴比较初始文本；没有编辑的坐标保存原始 Double，原坐标来源和不确定度保留。 | 名称编辑、单轴编辑、非法坐标三项测试 |
| R13 | Chart 内部 Back 顺序处理对话框、地点预览、测距、路线放弃、普通准星；无工具时返回 false，Shell 处理返回开始屏幕。Android/虚拟返回同源。 | 需 APK 虚拟 Back 和 Android Back 手动回归 |
| R17 | Chart 首次 `maps.view` 就传持久化中心和 zoom，不再创建 Auckland 默认视口。 | 需关闭进程后恢复地图镜头实测 |
| R18 | 实时/回顾使用同一个 `anchorSamplesConnected`；来源变化、失位超时、倒序或隔离点不连线。 | 同一 30 秒断流、来源变化、倒序、隔离点测试 |
| SAF 次级 | ChartReader 为版本副本持有引用；元数据成功落盘后回收旧版本，活跃 reader 关闭后再删。取消文件夹关联触发同一回收。原始 SAF 文件不删除。 | 需真实 SAF fallback provider 与多版本大文件实测 |

## 验证边界

- 本子任务新增 `src/rebuildTest/java/.../ui/ChartAnchorPolicyTest.kt` 11 项及 `.../chart/VesselGeometryTest.kt` 3 项，已由根任务执行真实 Gradle 验证，14 项全部通过。
- 未单独启动/修改 emulator；截图验收由根任务统一执行，避免并行 UI 操作污染结果。
- R18 的历史会话目前没有保存当时的 `gpsLossSeconds`。实时与回顾已用一致规则，但修改全局失位超时后，旧历史按新的值展示；不能声称完整复原当时所有断线策略。
- 地图位置仍代表所选定位天线的位置。船形朝向使用真船首向，不自动假设 GPS 天线位于船艏，也不凭空添加天线偏移。
- 船形/标点位图缓存按实际像素内存限制为 4 MiB；native annotation 更新前移除旧对象。Google 显式中心锚点，MapLibre 使用自定义 bitmap 的中心锚点，两者都禁止地图旋转/倾斜后按真北旋转船形。
- 停止、暂停、接受估计和确认警报仍是不同业务命令。隐藏页面或关闭预览不会暂停真实值守。

## 实图后追加：任务卡中的地图黑块

根任务实图发现：活动页面的 MapLibre 地图正常，任务卡窗口 PixelCopy 只包含控件/比例尺，地图本体变黑。`ChartSurface.initLibre` 改用 `MapLibreMapOptions.createFromAttributes(context).textureMode(true)`，使地图参与同一个窗口纹理合成。未用另一次地图截图覆盖 UI，也未改任务截图存储。

本机依赖 13.4.1 的 `classes.jar` 经 `javap` 确认选项和 MapView 构造器存在；[官方 TextureView API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map-options/texture-mode.html) 说明该模式支持视图动画/缩放，代价是额外合成开销。实际任务卡显示和拖图流畅度由根任务重装增量 APK 后复验，不能仅凭 API 可编译认定通过。
