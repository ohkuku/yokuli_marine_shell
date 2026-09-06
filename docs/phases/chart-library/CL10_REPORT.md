# CL10 报告 / Shell 安装、磁贴与跨应用导航

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`e26b1375ba3d0d05e4b02040cb16c91582fa2a93`

## 产品结果

- 海图库现在是 Yokuli OS 内一个真正独立但不另起 Android Activity 的应用：它只有一个
  `InstalledAppBinding`，由同一注册点派生 All Apps、搜索、三尺寸磁贴、动态深链与内部宿主。升级后
  可发现但不会自动固定，也不会重新排列用户已有桌面。
- 小、中、宽三种磁贴由海图库自身声明并投影真实 catalog 状态。小磁贴使用足够醒目的应用符号和问题
  数量；中、宽磁贴展示可用资源、需处理项目和扫描进度。进入桌面编辑态时装饰内容冻结，新的严重问题
  仍可出现，退出编辑态后才恢复普通实时更新。
- Chart 只保留“选择显示什么”和地图使用；Settings 与 Chart 的管理入口都深链到海图库，避免第二套
  资源编辑器。海图库资源可以用 opaque token 打开详情并“在海图中查看”，token 不含 URI 或路径。
- Shell Engine 增加通用 linked task return：从调用应用进入另一个应用后，Back 先退掉目标应用内新开的
  页面，再恢复调用者原上下文；Start 仍直接回 Yokuli 桌面并放弃 caller 链，但保留各应用可复用任务。
  Android Back 不会因此退出 Yokuli，也没有新增 HOME/DEFAULT intent。
- 海图库 neutral 状态只在扫描进行中或当前显示资源确实有问题时出现；一张未使用坏图不会把整个系统
  伪装成故障。当前状态条容量允许 NMEA、Data Sources 与有条件的 Library 摘要并存。

## 同轮产品纠错

- 圆角屏不再把顶部/底部圆角半径当成整条垂直安全区，从而牺牲一大块应用内容。状态区只服从真实
  top inset；圆角只把边缘内容横向往内收。底部同理，只保留真实导航/手势 inset。
- Google 地图显示只服从新的显式海图选择：选择“无本地海图”且构建确实配置非占位 key 时可以显示
  Google，不再被旧迁移的 `activeChartPackageId` 错误拦截。磁贴状态和本地署名也不再把该旧字段冒充
  当前选择。Google 仍不会填补已选择本地资源的覆盖空洞。

## TDD 与证据

- Engine 单元故事覆盖跨应用进入、目标内部 Back、caller 恢复以及 Start 放弃链接链。
- Window metrics 单元故事覆盖顶部不损失整条内容、左右避开圆角、底部只服从系统导航/手势 inset。
- 海图库单元故事覆盖唯一贡献、三尺寸、opaque token、旧/失效对象恢复、问题筛选与搜索。
- Compose 故事覆盖三尺寸磁贴在深浅主题和大字体下仍显示应用自有内容；Shell 故事覆盖可发现、不自动
  固定、根 Back 停在 Yokuli Start。
- 独立 `chart_library_cl10_contract` 及当前有效 Stage/NMEA/InstalledApp 产品合同已更新为五个正式应用，
  同时继续拒绝 HOME、横屏和调试入口。CL10 gate 已接入 `Yokuli OS Android CI`。
- 本地仅运行了必要的 Kotlin 最小编译（app-shell、对应 unit test compile、Library androidTest compile）和
  静态合同；未重复执行全仓 Gradle 门禁。机器结论等待
  `CODEX-CI-REPORT-<CL10_SHA12>-<run_id>-<attempt>`。
- 最小编译还暴露了 CL07 测试夹具把 `ChartSourceId` 再传给只接受原始字符串的 helper；改为使用同一
  稳定 ID 字符串，不改变任何产品合同。

## DESIGN DECISIONS

- 复用 `InstalledAppBinding` 组合根，把应用如何启动、如何显示和如何被搜索聚合到一次安装声明；没有让
  Shell 或 Desktop 读取海图库数据库类型。
- linked return 是 Launcher Engine 的通用任务语义，不是 Chart/Library 特判。边界记录目标任务接管时的
  back-stack depth，因此目标内部新增页面仍能先正常返回，再跨应用恢复 caller。
- 海图库磁贴只消费有界 UI projection；不会为了动态磁贴打开文件 reader、MapView 或启动扫描。
- Google 与本地显示以显式 `ChartDisplaySelection` 为唯一当前真相。旧 active package 仅保留迁移兼容，
  不再主导已初始化后的界面。
- 没有把海图库加入默认 Start，因为“已安装/可发现”和“替用户固定”是两件不同的产品行为。

## 未验证边界

- GitHub Action 尚未回传 JVM、lint、API34 Compose/Activity、APK 或 Release 二进制证据，因此 A01、A22、
  A38–A40 当前均为 `CI_PENDING`，本报告不写 `PASS`。
- 真机圆角/方屏观感、系统文件选择器权限恢复及真实 Google key 的 package/SHA/API/billing 组合仍需要
  后续 artifact/设备证据；代码只会真实报告“已配置”，不会把这些条件推断成“地图可用”。
