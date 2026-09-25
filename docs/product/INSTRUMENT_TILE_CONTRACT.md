# 仪表与应用磁贴：数据与交互契约

本文件对应 `InstrumentsExperience.kt`、`MarineInstrumentGraphics.kt`、`ReadingTrace.kt`、`InstrumentHistoryModel.kt`、`InstrumentHistoryDrawing.kt`、`InstrumentWeatherPanel.kt`、`TileLibraryExperience.kt`、`TilePresentations.kt` 和 Shell 的布局编辑器。内容描述实际实现，不表示已经完成真机验收。

2026-09-25：磁贴工坊的内容/实例重构已完成用户故事和源码评审，见 [待确认施工文档](../phases/tile-workshop/IMPLEMENTATION.md)。当前下文“每应用一块”和 App 级样式仍是 `da81de3` 的实现事实；重构尚未落地，不能将拟议多内容能力当作已有功能。用户确认后按施工文档替换这些旧约束，再回写本契约。

## 领域边界

- 驾驶台消费系统选好的观测，不能因为打开页面就切换船位来源或创建航行。
- 我的仪表只拥有显示顺序、增删与查看读数的交互。来源、吃水、船舶资料和航行仍由各自的共享服务拥有。
- 磁贴库以应用为入口。一个应用拥有一块主磁贴，样式与尺寸修改这块现有磁贴；样式不是额外的应用或额外的主屏入口。
- 磁贴和仪表读取同一个 `MainViewModel.ui.vesselData` / `DataHub`，没有独立模拟读数。演示模式必须显示演示标记。

## 数据结构

| 数据类型 / 字段 | 中文含义 | 单位与约束 | 持久化 |
| --- | --- | --- | --- |
| `VesselObservation<T>.value` | 真实观测值 | 缺测为 `null`，不能补成零 | 仪表读取，不拥有 |
| `VesselObservation<T>.freshness` | `FRESH / HELD / STALE / UNAVAILABLE` 新鲜度 | 航向和姿态数字/指针只使用 `FRESH` 且质量已知；其他读数保留最后观测，灰色并标时间 | 来源服务拥有 |
| `VesselObservation<T>.receivedElapsedRealtime` | 接收的单调时钟时刻 | 毫秒；仅比较时效，不当 UTC | 来源服务拥有 |
| `VesselObservation<T>.sourceIdentity` | 具体设备或连接的身份 | 详情原样显示来源名称 | 来源服务拥有 |
| `VesselObservation<T>.conflict` | 来源之间的不一致 | 详情解释，不擅自切换来源 | 来源服务拥有 |
| `VesselDataSnapshot` | 同一次融合后的船舶数据快照 | 航速用节，方位和姿态用度，深度用米，温度用摄氏度 | 来源服务拥有 |
| `VesselDataSettings.customLayout` | 我的仪表的有序 ID 列表 | `List<InstrumentTileId>`，去重，顺序即显示顺序 | `vessel_data_settings` DataStore |
| `InstrumentTileId` | 仪表稳定身份 | 不用数组索引绑定读数；拖动后 ID 不变 | 枚举名称 |
| `Reading` | 时间轴中的一份观测 | `value / unit / source / elapsed / observedUtcMillis / freshness / quality / sourceKey / continuityKey / validForMillis`；物理身份与显示名分开，UTC 捕获后不随刷新改写 | DataHub 最近 15 分钟；回看可保留本次固定快照 |
| `VesselDataSnapshot.heelDegrees / pitchDegrees` | 独立选源的横倾/纵倾 | 两项各自保留时间、来源与质量；同时有效才画地平线 | 来源服务拥有 |
| `InstrumentReadingPolicy` | 数字与量表共用的展示策略 | 保留读数不等于允许导航/报警继续使用；风角为 -180°～180° | 无持久化 |
| `ShellApp.id` | 应用身份 | 磁贴去重按照 `LauncherAppId` | 应用目录 |
| `ShellApp.entry` | 应用唯一主入口 | 旧 `tile.*` 样式入口迁回这里 | 应用目录 |
| `TilePlacement.tileId` | 桌面位置对象的稳定身份 | 换尺寸或样式时保留 | Shell 文档 |
| `TilePlacement.entryId` | 当前磁贴所属入口 | 每个应用最多一块 | Shell 文档 |
| `TilePlacement.size` | 小 / 中 / 宽 | `ICON_1X1 / STANDARD_2X2 / WIDE_4X2`，必须被该入口支持 | Shell 文档 |
| `TilePlacement.rank` / `preferredCell` | 排序 / 用户放置的网格位置 | 配置样式时保留，宽度改变时限制横向越界 | Shell 文档 |
| `<app>.tile.mode` | 动态内容样式 | `AUTO`、应用专用样式或 `STATIC`，保存为编码的 `Choice` | Shell 应用偏好 |
| `<app>.tile.animate` | 是否轮换多页信息 | `Toggle`，独立于读数更新；关闭轮换不冻结读数 | Shell 应用偏好 |
| `<app>.tile.interval` | 轮换间隔 | `6 / 10 / 15` 秒 | Shell 应用偏好 |
| `TileFrame` | 一帧磁贴展示内容 | 标题、说明、实际趋势样本、实际方位、实际距离占警戒半径比例、快照来源 | 只在内存派生 |

## 公开调用与状态流

| 调用 / 流 | 输入 | 结果与业务约束 |
| --- | --- | --- |
| `InstrumentsScreen(os)` | 系统上下文 | 航行 / 帆航 / 姿态 / 天气 / 回看 / 我的六个透视页 |
| `MainViewModel.updateVesselDataSettings(settings)` | 更新后的布局偏好 | 通过仓库保存；界面订阅结果，不覆盖数据观测 |
| `MarineCompass(os, snapshot)` | 真船首向、对地航向 | 真北罗盘；船首向和航向分别显示，跨 359° → 1° 走最短动画 |
| `WindRose(os, snapshot)` | 真风和视风角度、速度 | 船艏朝上，两组箭头表示来风方向；缺哪组就不画哪组 |
| `AttitudeHorizon(os, snapshot)` | 已校准的横倾与纵倾 | 2D 地平线；失去观测隐藏动态地平线，明确等待校准观测 |
| `InstrumentGauge(os, snapshot, tile)` | 某个稳定仪表 ID | 方位罗盘、带中线的有符号偏差或标注范围的刻度；读数单位调用全局格式器 |
| `ReadingTrace(os, values, metric, now, current?)` | 最近观测样本及当前权威观测 | 固定本次窗口、样本及坐标；显示截止时刻和新观测数；明确更新才替换窗口。按稳定来源身份/字段有效期断线；历史端点不自行证明实时有效 |
| `InstrumentPressureHistory(os, live, active)` | 内容服务持久气压与当前来源 | 固定查询截止 UTC；从真实存储读取样本，另订阅截止后的新增数量；存储错误直接呈现，不把未落盘观测说成已保存 |
| `TileLibraryScreen(os, initialApp?)` | 可选的稳定应用 ID | 应用清单 → 此应用样式预览；硬件和虚拟返回都先回应用清单 |
| `tileModes(app)` | 应用身份 | 此应用支持的样式清单；应用间不混排 |
| `tilePresentation(os, app, animate, modeOverride?, rotateOverride?, intervalOverride?)` | 真实应用和完整预览草稿 | 样式、轮换开关和间隔立即参与同一个开始屏幕渲染器；保存前不改系统偏好 |
| `LauncherAction.PinEntry(entry, size)` | 应用主入口及选定尺寸 | 未固定则创建，已固定则更新同一位置；不会创建同应用副本 |
| `LauncherAction.UnpinTile(tileId)` | 已固定磁贴身份 | 移除主屏入口，保留应用数据和偏好 |
| `StartLayoutEditor.pin(document, entry, catalog, size)` | 当前布局与应用目录 | 保留同应用最早位置、稳定 tileId，替换入口与尺寸；去掉旧重复项 |
| `StartDocumentRepair.repair(...)` | 已保存布局 | 按 rank、tileId 确定性修复，去重依据应用身份 |
| `StartDocumentValidator.isValid(...)` | 布局文档 | 拒绝同一应用多块主磁贴、非法尺寸和身份重复 |

## 用户故事与保存时机

1. 看仪表：打开即可看到船首向 / 航向图形；切到帆航看真风、视风相对船艏方向。缺测是缺测，不显示默认航向或虚构海况。
2. 定制驾驶台：进入“我的” → 添加常用仪表 → 排列 → 长按拖动或点上移 / 下移 → 完成。添加弹窗使用独立草稿，快速连续选中或取消选中只修改草稿，“完成”一次保存，“取消”、返回或点遮罩丢弃草稿；不从异步 DataStore 结果反复读取并覆盖。长按手势结束才提交重排；显式上移、下移和移除立即保存。详情也能添加到我的仪表。
3. 看来源：点读数打开观测详情，看来源、时效与冲突说明；无需被带到系统设置。
4. 选磁贴：磁贴库选择应用 → 左右滑看真实预览 → 选尺寸和样式 → 应用到现有磁贴。静态图标也是样式；轮换开关只控制信息页动画，真实数据仍然刷新。
5. 旧版本升级：Shell 产品迁移把每个应用旧 `tile.*` 入口合并到主入口。保留最早的 rank / tileId / 可支持尺寸；默认样式为 `AUTO`。旧版若在应用偏好中已明确保存模式，仍按该模式展示。
6. 看趋势：选择具体仪表 → 只看该仪表的当前/最后读数、历史和来源。水深不会附带无关的气压面板；只有选择气压时才出现 1/3/6 小时气压变化。读数详情同样打开自己的曲线。
7. 手机作船舶传感器：航行页“固定手机”或姿态页“安装与校准” → 确认手机顶部朝向船艏，或匹配同时收到的船网艏向 → 直接预览手机方向及对齐后的船首向。姿态安装另选船艏边缘并确认，不会把当前横倾归零；没有实时读数时禁止确认艏向。

## 历史可视化与固定回看

历史图是回看，不伪装成不断被重算的实时仪表。`ReadingTrace` 在打开、选择 1/5/15 分钟或明确点“更新至现在”时，捕获本次不可变样本列表、截止时刻和坐标范围。后台继续采集；新观测只更新数量提示，不移动旧点、不重新分桶、不让 DataHub 的短期淘汰抹去正在查看的样本。时间轴使用实际时刻，卡片明确区分“本段末次”与选中样本；切单位是用户明确改变呈现，原始值不变。初次为空也不会补零，收到样本后提示更新。

气压长期回看通过已有内容服务查询 1/6/24 小时的真实存储快照，同样固定截止 UTC；另订阅截止后的新记录数量，明确更新才重新查询。旧版本没有连续段信息的记录保留为独立点；只有相同非空 `continuityKey`、正向时间且间隔不超过 180 秒的新记录可以连接。保存或读取恢复中的错误从 `pressureStorageIssue` 呈现，不能把未保存的实时读数混入“已记录”图。存储/来源规则由数据领域拥有。

| 历史内容与真实入口 | 呈现类型 | 选择原因与限制 |
| --- | --- | --- |
| 对地/对水航速、真/视风速、流速；仪表回看及详情 | 36 个固定时间桶的低–高范围柱与均值横划 | 看波动范围和极值，不用密集心电图；缺口不合并来源 |
| COG、船首向、目标方位；回看/详情 | 按实际采样时刻的 0–360° 方位时间图 | 保留先后顺序；跨北断开，避免把 359°→1°画成反向大转弯 |
| 真风向、流向；回看/详情 | 方位玫瑰图 | 表示方向分布；扇区长度是样本数，不是假装停留时长；均值按向量计算 |
| 横倾/纵倾、舵角、风角、横向偏差、角速度、VMG/VMC、1/3/6 小时气压变化 | 零中线双向极值柱 | 正负物理意义清楚；气压变化不能当绝对气压；风角归一到 ±180° |
| 水深、龙骨下余量；详情 | 向下增加的实测深度柱/范围 | 负余量可以显露；不补海床，不冒充声纳 |
| 绝对气压、气温、水温；短期回看/详情；天气页长期气压 | 真实点和同源连续直线 | 不平滑，不拿可变桶末值替换历史点；断源、时间缺口不连线 |
| 总航程、本次航程；仪表详情 | 同源连续差值的分段增量柱 | 累计量转成可比较的行进增量；重置和缺口不算入增量 |
| 冲击候选；仪表详情 | 整数阶梯图 | 原值是每次观测时前 5 分钟的滚动次数；下降是移出窗口，禁止求差当新增事件、禁止各窗求和 |
| 运动强度、横摇周期、目标距离；仪表详情 | 范围柱与均值；运动强度轴固定 0–100 | 强度是算法指数，不因微小变化放大到满屏；周期是秒、目标距离是剩余距离，均不冒充累计量 |
| 航行日志航迹与回放 | 地理分段轨迹、真实样本船标、时间拖动条 | 时间游标选择已有样本；船位缺口隐藏船标，不插值制造航行；报告仍用统计读数和覆盖率 |
| 守锚近期轨迹和全部占用区域 | 按年龄渐隐轨迹 + 观测占用网格色斑 | 近期方向与总体覆盖分别表达；色斑浓度是样本量，不是水深或 GPS 精度圈 |
| AIS 观测历史 | 按目标/连续段绘制的地图或三维尾迹 | 表示收到过的位置；与虚线 COG/CPA 预测分开，不能把预测写回历史 |
| 沿途随记、记录事件、通知记录 | 带时刻、来源/应用的离散记录列表 | 事件没有连续数值，不画曲线；通知按应用分组，清除记录不等于确认业务告警 |

最后四类由日志、守锚、AIS、通知各自持有。本表仅统一选择图型的依据，不把这些历史迁入仪表，也不创建另一份采集服务。相关边界见 [航行与 NMEA](VOYAGE_AND_NMEA_CONTRACTS.md)、[通知中心](NOTIFICATION_CENTER_CONTRACT.md)。回看选定指标后，来源离线或实时缓存到期不能自动跳到另一个指标。

## 2026-09-18 修订的回归入口

`app-shell/src/rebuildTest/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicyTest.kt` 覆盖左右舷风角、30 秒气压连续性、15 秒船首向有效期、HELD 状态、同名设备与重连代次断线、跨北趋势断线、各仪表独立趋势键。这 7 例已在 2026-09-18 本轮统一 Gradle 构建中通过；属于 rebuild 单测的 25 例之一。页面实图和真实传感器验收另行记录，不由 JVM 通过推断。

## 拓扑

```mermaid
flowchart LR
    Sources[手机传感器 / 多连接 NMEA] --> Fusion[共享 VesselDataSnapshot]
    Sources --> Hub[DataHub / Reading 历史]
    Fusion --> Instruments[仪表应用]
    Fusion --> TileFrames[派生 TileFrame]
    Hub --> Trends[真实趋势与拖动选点]
    Trends --> Instruments
    Hub --> TileFrames
    Shared[全局航行 / 锚警 / 导航 / 海图库] --> TileFrames
    Units[全局单位与坐标格式] --> Instruments
    Units --> TileFrames
    Instruments --> Custom[customLayout 稳定 ID 有序列表]
    Custom --> VesselStore[vessel_data_settings]
    Gallery[磁贴库：应用 → 样式 → 尺寸] --> Preferences[应用 tile.mode / animate / interval]
    Preferences --> TileFrames
    TileFrames --> Renderer[同一个磁贴渲染器]
    Renderer --> Gallery
    Renderer --> Start[开始屏幕]
    Gallery --> Pin[PinEntry / UnpinTile]
    Pin --> Layout[唯一应用磁贴布局]
    Legacy[旧 tile.* 重复入口] --> Migration[合并并保留最早位置]
    Migration --> Layout
    Layout --> Start
```

## 已知边界

- 海图封面仍明确标注“海图快照”和拍摄时间，不冒充后台持续渲染的地图；航行、船位、风、读数和连接计数直接订阅实时状态。
- 趋势属于当前进程的短期观测；长期航行回放属于航行日志，不由仪表伪造或另存一份。
- 自定义仪表目前支持增删和顺序；不把缺少来源的数据类型自动隐藏，用户可以保留待接入的仪表并看到缺测状态。
- 拖动排序支持当前可见列表区域；长列表也提供上移 / 下移的明确操作。
