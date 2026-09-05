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
