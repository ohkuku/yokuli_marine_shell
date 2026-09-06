# W04 · Data Complete Vertical Slice — Product & Engineering Contract

## Intent / 产品愿景

Data 是 Yokuli OS 的“神经系统观察窗”。用户在一个 App 内既能管理数据怎样进入系统，也能理解系统最终
采用什么、来自哪里、为何采用，并沿真实证据向下追溯。W04 完成后，协议配置和选源不再被包装成两个
彼此跳转的产品 App。

## Experience / 用户体验

- Overview 以稳定 LiveField 显示 Position、SOG/COG、Heading、Depth、Wind；没有真实 resolved value 就显示空值。
- Inputs 保留已验证的 TCP/UDP 新建、编辑、保存、启动、停止、重试、删除与原始输入详情流程。
- Sources 按 Position & Motion、Heading、Depth、Wind 选择真实候选，明确显示失效、混合旧配置和停用状态。
- Flow 从真实 input/source 出发，经实际观察到的 sentence family 到 source group 和 resolved output，不画理想化拓扑。
- Diagnostics 显示有界 sentence inventory、raw preview 和运行计数；高频输入不会让页面做入口动画或无限增长。
- 用户选择“手机位置”是在选择 Position & Motion 的来源，不是在另一个页面打开独立 Phone 开关。

## Domain / 领域边界

- Data 统一五个 section 的产品语义；连接 runtime、source runtime 和 Phone runtime 仍由进程拥有。
- Inputs 的表单/确认是局部 UI 状态，socket 与接收事实只来自 `NmeaInputRuntimePort`。
- SourceGroup choice 仍通过 W03 的单条 atomic transaction 落入兼容的 per-key store。
- Phone demand 由 persisted selection 或正在完成的 Phone selection 请求派生；页面进入、退出和重建不参与 lifecycle。
- 旧 `nmea.*` / `sources.*` token 只能重定向到同一个 Data task，不创建可见旧 App。

## Opportunities / 可发展空间

Flow 将来可以发展为稳定的可视拓扑和选择历史；Diagnostics 可以加入健康时间线、受控导出和隐私擦除。
实现可以使用 Compose layout、Canvas 或缓存投影，但只能表达已观察事实，高频值不得触发整个拓扑重建。

## Non-negotiables / 不可协商边界

- Production All Apps 精确为 Chart、Settings、Data、Chart Library；默认 Start 仍只有 Chart + Settings。
- 旧 NMEA Input/Data Sources 磁贴按 W01 v1 幂等合并，保留最早 placement 的 tileId/size/rank/group。
- no silent failover；COG 不得冒充 Heading；Phone 不得独立 enable/disable。
- Data UI 不依赖 Android adapter，也不拥有 socket、位置 listener 或 runtime 生命周期。
- 中文为主、英文完整；Back 在 Data root 返回 Yokuli Start，不退出应用。
- 运行诊断有界，UI 使用 W02 cadence；不展示假连接、假 sentence、假数值或假“已连接”。

## Forbidden outcomes / 禁止结果

- 在 All Apps 同时显示 Data、NMEA Input、Data Sources。
- 为了合并入口复制 parser/socket/source repository，或让 Feature 互相依赖形成环。
- 页面关闭时停止仍有选择需求的 Phone/NMEA runtime。
- 权限未给、系统位置关闭或平台限制时宣称手机位置正在接收。
- 把历史 token 静默丢弃，或把同一个用户磁贴迁成多个 Data 磁贴。

## Compatibility / 兼容策略

成熟的 NMEA 连接编辑器被复用为 Data/Inputs 的内部子流程；其历史模块仍可独立测试，但没有 catalog binding。
旧 Data Sources 代码保留为历史合同证据，不进入当前 app-shell production dependency。W01 migration v1 在 Data host
真实安装后生效；旧 root、attention 和 connection token 继续由 Data 动态 matcher 解析。

## Acceptance stories

1. 用户从唯一 Data 入口依次进入 Inputs、Sources、Overview；所有页面属于同一 Shell task。
2. 旧 `nmea.connection.gateway` 和 `sources.attention` 能打开 Data 的对应内部语义，不恢复旧入口。
3. Position & Motion 选择 Phone 时先处理权限/系统状态；候选出现后只用一条 atomic group transaction 选择。
4. persisted Phone selection 在 UI 未打开时仍产生 demand；没有 demand 时在 source 初始化后停止 Phone runtime。
5. Overview、Flow、Diagnostics 都只显示 runtime 当前快照中的值、关系、sentence 与 raw evidence。
6. Data root Back 返回 Yokuli Start；Inputs 内部编辑 Back 先返回连接列表，再返回 Data Overview。
7. 旧两个磁贴与新 Data alias 并存时只保留确定性的一个 Data placement。

## Evidence required

Feature domain/destination/demand/coordinator tests、launcher migration/registry tests、API 34 Data journey、三尺寸双主题
renderer story、Release APK 产品面审计和精确 SHA 的统一 CI report。完整 Gradle/lint/device 只在 GitHub 执行。

## Existing code landmarks

理解入口包括 `core:marine-data` ports、W03 Data domain、现有 NMEA connection workflow、W01 migration plan、
ProductionShellGraph 和 W02 live presentation primitives；它们是线索，不是强制文件清单。

## Implementation freedom / 实现自由

允许复用成熟子流程、提取适配边界或重组 projection；不规定 coordinator 数量、Compose 绘制方式或文件名。
新增抽象必须解决真实产品所有权、进程生命周期、原子性、背压或可测试性问题。

## English translation

Data is Yokuli OS's nervous-system observatory: one app for managing inputs, selecting explainable sources, viewing resolved values, following actual runtime relationships, and inspecting bounded diagnostics. W04 replaces the two visible NMEA Input and Data Sources entries only after a real Data host exists. Existing connection CRUD is reused as an internal Inputs workflow, while source selection and Phone demand obey the unified Data contract. Phone location runs because selected OS data requires it, never because a page happens to be open or an independent toggle was set.
