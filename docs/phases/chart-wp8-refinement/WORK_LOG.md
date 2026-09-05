# WP8 三档磁贴与海图 V1 工作日志

> English translation: WP8 classic three-size tile and Chart V1 work log. Chinese is the primary project language; English summaries preserve the same scope and truth boundaries.

审核锚点：`9579761a3c7c5cf13735dfecaeb38636b715b695`

分支：`codex/shell-map-contract`

授权：按完成包连续实施 C00–C12；不合并、不发布、不强推。

## 2026-09-06｜输入冻结与真实基线核对

- 归档用户提供的 Completion Pack；源 ZIP SHA-256 为 `151ab808d0d77276b40a343eb8542fbd8922f352b0fbfad2793d1c08df24be49`。
- 将主规格和机器任务清单原样纳入本目录。主规格内部 SHA-256 为 `e346c659b2cb94c003e46e3e67fd71fc7c1a7f0d419660fbbdf05544678d06fc`；任务清单内部 SHA-256 为 `fe6f54d3bc0e03d2935a4403a450ce49d90d24807d73bb39f4ccb889d7029373`。
- 本地最初没有看到远端新增提交；普通 push 的 non-fast-forward 反馈暴露了已审阅基线 `9579761`。随后只执行 fetch/rebase，没有 reset 或 force push。
- 重放本地 C00 时以远端实现为准，保留其连续拖拽、独立编辑命中区域、三档直接缩放、内部路由注册和回归测试。

## 当前范围 / Current scope

- 生产磁贴只允许 1×1、2×2、4×2；Chart 声明三档，Settings 声明小/中两档。
- 保留现有 Shell Engine、App 安装边界、主题、本地化、手势、Bridge/Search/Back 和持久化合同。
- 地图是 Shell 内部安装的独立 App；后续工作只沿一个生产 renderer 和明确端口扩展。
- 生产 NMEA/GNSS 采集、航行指令/输出和默认 Android Launcher 均不在本 Phase 范围。

## C00｜Red：已持久化未知尺寸会扩大成整桌回退

远端审核基线已经完成经典三档 enum、应用声明和大部分触控回归，因此这些不能伪称为本次新发现。增量审查发现真正剩余的持久化缺口：

1. 旧别名 `COMPACT_2X1`、`TALL_2X4`、`LARGE_4X4` 已有明确映射。
2. 但未来或损坏记录中的未知尺寸会让文档 schema 被改为非法值，之后可能触发整个 Start 文档回退。
3. 这违背“未知值受控、未受影响 tile ID、entry ID、rank、group 与 spacer 不应被清空”的 C00 Gate。

新增反向测试使用真实 Proto 同时放入三种旧别名、一个未知尺寸和 spacer，并要求二次编解码幂等。修正策略只在存储边界把未知尺寸降级为 1×1，保留其余身份与排序；生产 enum 仍严格只有三档。

## C00｜Green 候选

- 保留审核基线的 1×1 → 2×2 → 4×2 直接缩放以及 Settings 1×1 ↔ 2×2 行为；没有确认勾中间态。
- 未知持久化尺寸安全降级到 1×1，不再借 schema 值触发整桌重置；旧别名继续映射到最近经典形状。
- Adaptive packer 随机覆盖真实三档集合以及 4/6/8 列，继续验证边界和无碰撞。
- Python 合同按 app-owned renderer 注册能力检查 Chart 三档，不依赖私有 Composable 函数名。

## C00｜Gate 与自查结论

第一次运行聚焦 Gate 暴露两个真实回归，而不是把失败藏在 job 状态后面：

- `TileEditingRegressionTest` 仍用旧六档常数 `nextInt(6)`，三档后越界；已改为从 `MarineTileSize.entries.size` 取值。
- 旧 Stage 6 Python gate 仍绑定“四、六列”测试名；已改为明确要求 4/6/8 列场景。

修正提交 `7971250384e064752ab870b6ea55a805eb736ab8` 上的本地同 SHA 证据：

- 仓库锁定 `jsonschema` 环境中的 Python 合同：149 tests，0 failure。
- 聚焦 Gradle：shell contract、engine、storage、compose、Chart 全部通过。
- 全仓 `test`、`lint`、Standalone Debug/Release：1233 tasks，build successful。
- API 34 模拟器 `:app-shell:connectedStandaloneDebugAndroidTest`：37 tests，0 failure。

自查没有发现生产六档入口、确认勾中间态或未知尺寸导致整桌回退。C00 状态为 `VERIFIED_LOCAL`；它只关闭三档磁贴与当前基线，不代表地图 V1 完成。GitHub CLI 在本机不可用，托管 CI 状态不伪造；C12 仍必须给出最终同 SHA 托管证据。

## C01｜Red：恢复失败会清空语义，慢写会阻塞交互

反向测试先固定四类旧风险：用户库读取失败后任何相机/包/工具动作都不得把默认空库写回；慢资料写入不得阻塞地图动作；旧 revision 的 ack/失败不能覆盖更新编辑；队列满、异常 action、异常 effect observer 和 close 后输入都只能产生 typed 结果，不能杀死消费者或向 UI 抛异常。

真实 Android 仓储测试另外覆盖损坏 session、未来 schema、单条非法 Room 记录、事务资料 round-trip，以及关闭数据库再重开后相同 ID/revision 的恢复。首次设备测试确实暴露了 Kotlin expression-body 测试返回 Boolean 导致 JUnit 拒绝加载，修正后不把初始化失败误记为产品通过。

## C01｜实现候选与产品边界

- 用户地点、路线草稿和正式路线改由 Room 事务仓储持有；DataStore 只保留相机、测量和活动对象引用等 session。生产没有 `fallbackToDestructiveMigration` 或 `ReplaceFileCorruptionHandler`。
- 加载明确区分 NotLoaded、Loading、ReadyEmpty、Ready、ReadFailed、Corrupt。加载失败保持只读，不允许资料动作覆盖未知原库；合法单条坏记录局部隔离并报告，不清空其他记录。
- Store 使用串行动作 actor 与独立持久 writer；相机事件可合并，可靠动作有有界背压和 typed rejection。资料使用注入式稳定 ID 与 revision，迟到回调按 revision 淘汰，undo/redo 上限为 50。
- UI 只有 durable revision ack 后才显示 Saved；失败保留内存编辑并给出重试。用户还可以通过系统 Create Document 显式导出 `yokuli-map-recovery.json`，取消选择不会伪报成功；该 JSON 是 C01 的恢复逃生口，不冒充 C08 的 GPX 交换能力。
- 默认 incident 仅记录分类，不记录精确位置、备注、源 URI、token 或异常 message。恢复文件包含私人资料是用户主动选择的导出结果，不进入日志或自动上传。

当前实现已提交并通过 30 个相关 JVM 测试与 API 34 上 4 个 Room 设备测试；状态仍为 `IMPLEMENTED_UNVERIFIED`，等待累计 C00-C01 Gate 后才能封口。

## C01｜Gate 与自查结论

冻结候选 `5c63d5df2c2fd8a1ac51c0cdba01ff1c583d2ec0` 的累计结果：

- Python 合同 164 项，全部通过。
- 全仓测试、`lintStandaloneDebug`、Standalone Debug/Release 构建通过：1176 tasks；XML 汇总为 226 个 JVM 测试、0 failure/error/skipped。
- API 34：Room 4 项、离线适配器 1 项、Shell Activity/Compose 37 项，全部实际执行并通过。
- Debug APK SHA-256：`cdc3dde9fd9bfbacf784b980a7b2a14323515dfa26ff902097569bc6937b8853`；unsigned Release APK SHA-256：`ce28afa7b2dd6de0351b3799bcbbaa5f2c1bc380981986cd95353e39d442d540`。

独立数据/异步复查确认相机 session 不再整库重写，迟到 revision 不改变新编辑，损坏文件无静默替换。资源/安全复查确认 Release 合并清单无位置权限，代码无 destructive migration/corruption fallback，异常日志不含 URI/message/坐标。C01 标记为 `VERIFIED_LOCAL`；托管同 SHA 证据仍统一留给 C12。

## C02｜Red：renderer 存在不等于图面、配置不等于可用

先写失败合同约束生产只能有一条 MapLibre 本地 renderer 路线，并把 HostReady、RendererReady 与 Coverage 分开。真实 Android 反向测试不接受“MapView 存在”作为通过条件：它从小型 MBTiles 读取不对称 PNG/JPEG 图块，截取 MapLibre snapshot 并检查方向色块和稳定 overlay 像素。另一个追溯测试把公开 NOAA Chart Display Service 小区域的来源包、处理步骤、fixture 和逐 tile SHA-256 绑定在一起。

第一次证据 Gate 确实发现来源 JSON 内四个 tile hash 与实际复制字节不一致，以及中英文决策页标题不满足文档合同；两项均已修正后重跑。生命周期测试最初错误地假定 50 次地图访问必须恰好创建 50 个 MapView；实测 Compose `AnimatedContent` 在转场 settle 时会串行提交两次，但同时存活峰值始终为 1，离开地图后为 0。Gate 因此收紧为规格真正要求的“有界且不持续增长”：50 次访问允许 50–100 次完整 create/destroy，仍要求 peak=1、final=0。

## C02｜实现候选与真实边界

- 生产 composition root 不再读取 `GOOGLE_MAPS_ANDROID_API_KEY`，也不再按 key 配置分叉。唯一生产 surface 是 MapLibre Native 13.4.1 + 本地 raster MBTiles；历史 Google adapter 仍隔离存在，但 app-shell 不依赖它。
- SDK-free 协议包含 renderer generation、相机 command ID/ack、exact/bounds target、viewport insets、project/unproject、稳定 overlay ID 和 hit query。旧 generation、dispose 后回调与旧 command echo 被丢弃。
- MBTiles 导入验证 SQLite/schema/metadata/PNG/JPEG/tile size，明确把 XYZ row 归一化为 TMS；安装采用 staging 后原子移动，坏块不产生半安装包。
- 空包、丢失包、检查中、已挂载、退化和错误分别表达；`PACKAGE_ATTACHED` 只表示本地 source 已被 style 接受，不冒充所有瓦片已覆盖或已加载。
- 北向复位、查看地点、路线全貌和包范围都通过 typed camera command 进入同一 renderer；用户 pan/pinch/rotate 启用，tilt 关闭。
- NOAA 子集只用于测试和可追溯性验证，不冒充官方 NOAA chart，也不作为 Release 内置海图。当前生产首屏不需要 Google key；用户仍需导入自己有权使用的本地资料。

实现候选冻结在 `32e13b991ed2274296c1b6a0083a3ee63b055e39`。聚焦 JVM、MBTiles/真实像素 API 34 和 50 次生命周期故事已通过；状态暂为 `IMPLEMENTED_UNVERIFIED`，等待累计 C00–C02 Gate。

## C02｜累计 Gate 与自查结论

候选文档提交后的第一次累计 Gate 只因报告使用 `English summary` 而不是仓库规定的 `English translation` 标记失败；该失败已单独提交修正，随后从头重跑。冻结验证点 `11d10156c4b1ad26a55694219bcc9ce00fd97a80` 的结果：

- Python 合同 165 项和 CI/release shell 合同全部通过。
- 全仓 `test`、`lintStandaloneDebug`、Standalone Debug/Release：1151 tasks，build successful；XML 汇总 231 个 JVM tests。
- API 34：离线 renderer/导入 5 项、app-shell 39 项、Room 4 项，全部通过。
- Debug APK SHA-256：`db20d98875953d52046aa613afa6f0a03d4ed88c47fd51be9aadb4a46f115662`；unsigned Release APK SHA-256：`613337922f1f22ddf23417021d2796cf47a4a0dc8c823605069491efd7a4b655`。
- Release 合并清单没有粗略/精确位置权限或 Google Maps metadata；MapLibre 依赖仍合并 INTERNET、NETWORK_STATE、WIFI_STATE 权限，但当前生产 renderer 的 style/source 没有 HTTP URL，真实离线像素测试无需外网 key。

自查确认没有第二条生产 renderer、没有把 style loaded 写成 tile ready、没有旧 generation 回写、没有把测试 NOAA 子集宣称成内置官方海图。C02 标记为 `VERIFIED_LOCAL`，允许开始 C03；同 SHA 托管证据和所有者方屏实体机审核仍留给 C12。

## C02｜封口前自审补测

在写入机器场景证据时发现，原真实 renderer 测试虽然验证了色板存在，却没有直接断言高 zoom 取代 overview、90 度旋转改变已知图面方位、透明边露出本地空 style；相机范围场景也只有实现调用，没有独立断言 command ID、viewport inset 和切包不自动 fit-all。为避免把“代码看起来存在”写成已验证，C02 暂时退回 `IMPLEMENTED_UNVERIFIED`，新增真实 MapLibre 多 zoom/旋转/透明边测试、纯 reducer 相机命令测试和纬度/zoom 相关比例尺测试。新增 Gate 未通过前不开始 C03。

## C02｜自审纠错与最终封口

新增断言第一次运行没有被“为了过测试”弱化。透明边断言在 API 34 高密度视口上发现 zoom 0 会裁掉原 fixture 外圈；失败快照的采样色板包含图面颜色但没有测试背景色，证明问题是证据区域不可见，而不是 alpha 合成成功。fixture 因此增加视口内透明窗口。旋转断言随后暴露单点采样可能在旋转前后恰好同色，改为比较整帧规则采样，要求显著差异。生产 renderer 代码未因这两个测试失败改变。

冻结验证点 `8bb791209ec8753340444f629e8f9f04fc232fef` 的最终累计证据：

- Python 合同 165 项与 CI/release workflow contract 全部通过。
- 全仓 `test`、`lintStandaloneDebug`、Standalone Debug/Release：1151 tasks，build successful；XML 汇总 235 个 JVM tests。
- API 34：离线 renderer/导入 6 项、app-shell 39 项、Room 4 项，全部通过。
- Debug APK SHA-256：`f36d7c8c57458b1f23d8f23b2801e20ee15fe8988e017fbe49e959b0cca0309d`；unsigned Release APK SHA-256：`210b5da8dc469ff8752a1948ff2c92e77df51e6f6a68d3fa7b78da72ea822615`。
- Release 合并清单仍只有 MapLibre 依赖带来的网络、网络状态和 Wi-Fi 状态权限，没有位置权限或 Google Maps metadata。

C02 恢复为 `VERIFIED_LOCAL`。它证明单一离线 renderer、相机协议和相关资源生命周期；不提前宣称 C03 的地图层级、方屏交互或 feature-first Back。

## C03｜地图层级、输入优先级与方屏候选

先以 Red 合同冻结 Surface/Tool/Transient 分离、feature-first 输入路由、真实 renderer 准星与 gesture ID。实现把 Chart 根页收敛为地图主体，地点/路线/图包改为内部页面；地图点按和长按只产生临时候选或稳定对象候选，显式确认才写一个点。Shell 的 Back、Bridge、Search 和虚拟实体键继续走同一输入边界。

第一次完整 API 34 Shell 场景有 42/43 通过。唯一失败不是测试波动：快速选中测量工具后立即 Back，MapStore 已是 `MEASURE`，但 Compose handler 仍看到上一帧 `BROWSE`，结果 Shell 先退到 Desktop。测试增加状态诊断后稳定复现，生产路由改为读取 MapStore 实时状态，定向测试与完整 43 条场景随后全部通过。

自查又补上 Shell 圆角安全带向 Chart viewport 的映射、四边参与 revision、旋转/IME 重排及 MapLibre 多指开始取消未确认 preview。当前 C03 是 `IMPLEMENTED_UNVERIFIED`；C04 才完成测量数学和真正的任意点编辑，C03 不提前宣称这些能力。

## C03｜累计 Gate、历史合同纠错与封口

第一次累计 Python Gate 有 5 条旧合同失败：两条读取已删除的 `ChartUiContract.kt`，一条仍要求旧 Activity story 名，两条把旧 UI 形态或内部路线草稿字段当成 Stage 1 发布表面。修正只迁移测试真值来源和 device runner 名称，没有恢复废弃文件，也没有删除 Map App 内部规划模型。171 条 Python 合同与 CI 拓扑随后全绿。

冻结点 `56f2abb049a82b71f222548760f4b5511a793928` 的累计结果：全仓 test/lint/Standalone Debug+Release 共 1151 tasks；XML 汇总 248 JVM tests；API 34 的 Shell 43、真实 renderer 6、Room 4 全部通过。Debug APK hash 为 `a222156a38e3bf7fbbd80d4dd386c5507fb1eb14a8bc4e96a971c241115e53c3`，unsigned Release hash 为 `5223becdb806b7986cbef92b0b2d03e9adb380cf1f69743b40de84c3edfb68a7`。C03 标记 `VERIFIED_LOCAL`，只允许进入 C04。

## C04｜WGS84 选点、测量与编辑

Red 先冻结独立 WGS84 参考值、同点/多解对跖语义、日期线/高纬边界、DD/DMM 输入、三点任意编辑、最终 drop、转换隔离与 GeoJSON 轴序。实现使用 GeographicLib Java 2.1 统一距离、起始真方位和 renderer 大地线；合法 `±90°` 模型坐标不被 Web Mercator 相机裁剪写回。测量提供 0/1/2+ 真实反馈、任意点移动/插删、精确准星、坐标输入、undo/redo、clear、fit 和复制式转路线。

自查逐项修复了测量模式保存候选污染测量、最后 `UP` 坐标漏提交、含冒号 route ID、方屏英文按钮溢出、恢复回调迁移以及固定 25km 加密缺少显式屏幕误差预算。最终绘制密度按 zoom/纬度计算约 0.75px 弓高目标并限制 500m–25km，只影响显示采样，不降低 WGS84 数学精度。

冻结点 `c15d3d17f832608b328d8722f475ff5fed694e59` 的完整 Host Gate 通过 175 条 Python 合同、271 个 JVM XML 测试与 1207 个 Gradle tasks；API 34 上 MapLibre 6、Room 4、Shell 45 全部通过。Debug/unsigned Release hash 分别为 `32e091c2a27810cfd053305ada2e3bf3edb0c7973e3e1cf3a9ec2e2c40ea4850` 与 `01aaa18ad1adb890171ebfdecea0a8f4e6d0ed08cb4637f73e725d399935fb49`。C04 标记 `VERIFIED_LOCAL`，只允许进入 C05；地点库、正式路线及后续能力没有提前宣称。

## C05｜地点库、身份语义与本地搜索

Red 先锁定完整地点字段、稳定 ID/revision、显式移动、被路线引用时的删除语义、可恢复撤销、中文/英文/坐标本地搜索、单地点完整导出和 Room v1→v2 迁移。实现将候选保存改为完整编辑器，把地点列表、详情、搜索、排序、移动预览/确认/取消、引用计数删除与撤销连成真实 UI；所有保存状态仍以持久层 ack 为准。

自查发现初版中文分类词不完整，以及删除后随机 ID 生成器可能重用刚删除或仍存在路线来源中的 ID；两项先加失败测试，再修正实现。设备测试准备阶段还发现 schema 文件虽已导出却没有打包进 androidTest assets、Compose harness 会记住上一轮输入；分别补上显式 schema source set 和每场景唯一 store key。单地点导出静态合同的字符串转义写错、历史 Stage 1 合同把合法内部 `ANCHORAGE` 分类误判成生产第三入口，也分别收窄到正确的产品边界，没有放宽 Chart + Settings 的 Launcher 发布面。

冻结点 `76341a6a5d7fdc5c10160769cb093e6ab646d1f1` 的累计 Host Gate 通过 180 条 Python 合同、280 个 JVM XML 测试和 1207 个 Gradle tasks；API 34 上离线 renderer 6、Room 5、Shell 48 全部通过。Debug/unsigned Release hash 分别为 `456a51a14c87c8875134b17162ebdc805805983ace7d5215800d5837ea02dc9e` 与 `44e11239c0c5eef993c80b1db401570a2956d8ff17d8ccf7a202eed983311b2c`。C05 标记 `VERIFIED_LOCAL`，只允许进入 C06；正式路线编辑、海图包作业、GPX、覆盖和位置观测没有提前宣称。

## C06｜路线草稿、正式计划与异步提交边界

Red 先固定 RouteDraft 与 RoutePlan 的独立身份、可空计划船速、多个草稿切换、稳定航点 ID、插入/移动/删除/重排/反向/undo/redo、预览不产生 dirty draft、同 ID revision 更新、复制/另存、删除撤销和全流程不启动导航。Room schema v3 同时要求备注、base plan revision、航点 ID、地点来源 revision 以及活动草稿/计划会话引用在关闭重开后不丢。

实现把 Routes 页面连成可操作的草稿组与正式计划组：可从地图或地点创建、继续在地图放点、精确坐标移动、查看 WGS84 每段距离与起始真方位、输入可留空的计划船速、保存后预览、再编辑并覆盖同一计划，或显式另存/复制/反向复制。正式计划预览只显示保存点线和计划估算，不出现“开始导航”、实时 ETA、NMEA 或自动舵语义。

第一次实现后进行了两轮反证。第一轮发现相邻重复点只有 incident 没有用户反馈、路线腿事实没有直接暴露、旧 ack 在较新资料写入 pending 时不能结束路线 transaction；补了可见提示、WGS84 route legs 和 revision-aware ack。第二轮发现持久 mailbox 会合并连续资料写：若新写失败，未确认路线必须回滚并恢复原草稿；若旧路线已确认而更新的地点失败，路线不能被误标失败；并且第二条路线保存不能覆盖第一条 pending transaction。修正后正式路线提交被显式串行化，pending 计划的编辑/复制/删除入口禁用，其他资料仍可进入同一后续快照。

累计 Gate 首轮准确暴露三项测试基础设施漂移：系统 Python 缺 pinned jsonschema、C05 静态断言把迁移调用锁死为单一 v1→v2，以及旧 UI 扫描禁止合法的 `activeRoute` 字样。使用临时 pinned Python 环境运行；迁移断言改为要求链中保留 MIGRATION_1_2；字面扫描按主规格改成“生产文案无开始导航 + reducer 行为保持 navigation false”。同时补齐显式 zh-CN 的 C06 资源键，没有把 Gate 设为 optional。

冻结点 `15d0f1a91623cb4121e92cf82bf9dc01952eb982` 的累计 Host Gate 通过 185 条 Python 合同、294 个 JVM XML 测试和 1207 个 Gradle tasks；API 34 上离线 renderer 6、Room 6、Shell 49 全部通过。Debug/unsigned Release hash 分别为 `1c5df1c621993695a502e6a6108e4a05f0381f5d663a3eb846414ff3c205c71f` 与 `8ca772e2d3c8052647d514fcc4e98dc5fd134b1ff34c2fc9a86191d9209f8a78`。C06 标记为 `VERIFIED_LOCAL`，进入 C07；GPX、覆盖检查、实时观测与 Shell 地图磁贴摘要仍未提前宣称。

## C07｜海图包验证、可取消任务与版本恢复

Red 首先固定 raster PNG/JPEG 的 tables/views、缺推荐 metadata 推导、逐图块完整解码、索引/资源边界、逻辑包/不可变版本/SHA 身份、安装 journal、版本 lease，以及新选择/取消淘汰迟到结果。实现将 UI 拆成 Copying、Inspecting、ReadyToInstall、Installing、Cancelled、Failed，并由 operation ID、generation 和单一 Job owner 串行化当前选择。

第一轮自查修正 Kotlin 编译问题、旧合同错误强制 WebP，以及 XYZ 归一化后仍沿用导入前摘要的身份错误。第二轮反证覆盖三个 journal checkpoint、复制写满、同字节去重、同名隔离、空包/重复 metadata/超限文本/编码矛盾、renderer lease、版本回退与损坏 manifest 可见性。Unknown 只表示事实未知或用户声明，不被写成官方核验。

冻结点 `cc1b598343b3dd9cd4ed43c43d43ae76b276c90b` 的累计 Host Gate 通过 190 条 Python 合同、300 个 JVM XML 测试和 1151 个 Gradle tasks；API 34 上离线包/renderer 15、Room 6、Shell 49 全部通过。Debug/unsigned Release hash 分别为 `9a52af6c7c88627c600b3d5260466ea95095f69e93e36525c223bc0587f04238` 与 `63cd91ffd9c70c518061fe1598b0732ce84c1a572247f5768313acece833a24b`。C07 标记 `VERIFIED_LOCAL`，只允许进入 C08；GPX、覆盖/来源、实时观测和动态 Shell 摘要没有提前宣称。

## C08｜GPX 1.1 互操作与只读分段轨迹

Red 先固定受限流式解析、预览后确认、重复摘要显式副本、wpt/rte/trk 类型映射、分段身份、Room v4 和 SAF/FileProvider 真实状态。实现保留地点、正式路线和只读轨迹三种语义；单点路线、空批次、未知扩展和超过资源预算都不会被包装成成功。只有一个 durable batch ack 后才报告导入完成。

两轮反证补齐 DTD/外部实体防护、独立 XML parser 往返、200k 点预算、日期线 bounds、中立 fallback 名称、分段 GeoJSON 和原始点不被 LOD 修改。累计设备 Gate 还真实暴露后台轨迹抽稀后 MapLibre mutation 可能留在测试工作线程；先加失败合同，再把所有 source mutation 显式切回 Main，并在 style 失效时丢弃旧结果。

冻结点 `d711ac843391622e26a3b2077e124f206c9f90dd` 的累计 Host Gate 通过 196 条 Python 合同、323 个 JVM XML tests 和 1207 个 Gradle tasks；API 34 上离线 renderer/包 15、Room 7、Shell 51 全部通过。Debug/unsigned Release hash 分别为 `396ac0dc8299974828be79e90401da36a1ca06fab688c5da746777fe94e55886` 与 `668d56587bf95a0a46b2ce3995e73c38e57cbac26fe2c06431a902379fea0b3f`。C08 标记 `VERIFIED_LOCAL`，只允许进入 C09；来源下载、沿线覆盖、只读位置观测和动态 Shell 磁贴尚未提前宣称。

## C09｜沿线离线资料检查、精确索引与来源边界

Red 先冻结 route/package/zoom/宽度/备用区域 fingerprint、保守走廊、日期线、200k 预算、精确 target-zoom key、事实维度拆分、唯一可取消 job 和 capability-gated 来源。实现从正式路线进入检查页，扫描真实 MBTiles row 并列出缺失 key；结果永远把 tile availability、有效图面和适航性分开。

自查将规划中状态改为可见且可取消，把所有结果绑定 route ID，避免跨路线串状态；SQLite 从整级扫描改为 400-key 参数化批次；最后复用来源哈希锁定的 NOAA NCDS fixture 验证真实 TMS→XYZ 命中及 bounds 内洞。NOAA 只登记 `IMPORT_ONLY/BLOCKED_EXTERNAL`，UI 不显示不存在的下载能力。

冻结点 `968e1b8e2cf958a9361258378529134b29f1bfc1` 的累计 Host Gate 通过 201 条 Python 合同、338 个 JVM XML tests 和 1207 个 Gradle tasks；API 34 上离线 package/renderer/index 17、Shell 52、Room 7 全部通过。Debug/unsigned Release hash 分别为 `6e3f2fd052693d27958769a0328c4983fde1dda679a45a10e6f42a802d1053ca` 与 `52f7f83997ffb6ba4afafdf83082b37c3b71e23068e22f6f29a961cb17b72326`。C09 标记 `VERIFIED_LOCAL`，只允许进入 C10；来源交付仍单列 `BLOCKED_EXTERNAL`，位置观测和动态 Shell 磁贴未提前宣称。

## C10｜NoSource、单调质量与只读观测边界

Red 固定 source/epoch/identity、boot-scoped monotonic age、独立 Position/Heading/COG-SOG/accuracy 质量、断流历史样式以及显式 Browse/Follow。实现用 `NoSourcePositionPort` 作为生产默认，不启动空闲 collector/timer；未来 provider 只能通过只读事件进入现有串行 MapStore。

MapLibre 使用实时点、历史点、真航向、虚线 COG 和精度圈五个独立 plane。同 ID 缓存、乱序和未来样本不覆盖好 fix；同坐标新身份和新 epoch 可用；不同 boot 与持久历史不恢复为 fresh。无磁差的 magnetic heading、低速 COG 和未知精度不会被画成确定事实。

累计 Gate 先准确发现历史字面测试与新合法观测模型冲突，以及隔离 Google adapter 的旧字段访问；均以窄纠错提交修正，没有把 adapter 接回 Release。冻结点 `260a343940c49bfa1564de544dea5881b9d8bf3a` 通过 206 条 Python 合同、357 个 JVM XML tests、1178 个 Gradle tasks；API 34 的 MapLibre 17、Shell 52、Room 7 全绿。C10 标记 `VERIFIED_LOCAL`，只允许进入 C11。
