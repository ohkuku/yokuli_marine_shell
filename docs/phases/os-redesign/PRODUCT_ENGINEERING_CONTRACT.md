# Yokuli OS Product & Engineering Contract

版本：1 · 2026-09-06  
状态：W01 起生效；后续工作包按版本追加，不静默改写历史报告。

## Intent / 产品愿景

Yokuli OS 不是一组互相跳转的工具页，而是一个以航海任务为中心的应用内操作环境。最终一等 App 只有：

1. **Chart** — 回答“我在哪里、周围是什么、正往哪里走”。
2. **Chart Library / 海图库** — 建立、维护并理解 Yokuli 可使用的地图世界。
3. **Data / 数据** — 看懂数据怎样进入系统、有哪些候选、系统最终相信哪一份以及为什么。
4. **Navigation / 导航** — 规划、保存并执行一次航行。
5. **Preferences / 偏好设置** — 个性化 OS 与各 App 的表现方式。

页面、adapter、runtime 或设置分组不会仅因技术上独立就成为 App。一个 App 必须有清楚目标、自己的领域
对象、可辨认的主视觉，以及完整的“进入 → 操作 → 结果”体验。

## Experience / 用户体验

- 用户从 Start、All Apps 或 Search 进入一等 App；管理页不靠互相跳转掩盖职责混乱。
- Chart 是实时态势面，不承担海图文件夹、NMEA endpoint、source policy 或 route library CRUD。
- Chart Library 让用户管理“地图内容与覆盖”，Sources 只处理真实文件/权限/扫描事实；外部原件默认只读。
- Data 从最终值反向解释 input → sentence → candidate → selection → resolved value，不隐藏失败，不静默切源。
- Navigation 同时容纳路线库与 active navigation，但不输出自动舵或控制船网。
- Preferences 只拥有表现偏好；连接、图层、来源选择和活动路线仍由其业务 App 拥有。
- 所有 App 继续采用 WP8 Classic 的排版、空间、动效与 Back/Start 语义；中文为主，英文完整对等。

## Domain / 领域边界

- `MarineDataRuntime`、Phone demand、Chart Library runtime、Active Navigation runtime 与 Tile snapshot
  runtime 由进程拥有，Feature UI 不以页面订阅数量决定其生命周期。
- 结构状态与高频值分离；结构变化立即发布，高频值 latest-wins 并按消费场景限频。
- App identity、launcher entry、launch token、tile placement 与业务对象 ID 是不同概念，不互相偷用。
- “已登记、可读取、允许显示、正在显示、离线可用、完整验证、适合航海”是不同事实。

## Opportunities / 可发展空间

- Data 可形成稳定拓扑图、可解释选源历史和设备健康时间线；实现可自由选择 Compose layout、Canvas 或
  其它仓库内合适手段，只要高频值不会让整个拓扑重建。
- Chart Library 可逐步形成 Coverage、Stack、Sources 三个工作空间，并容纳更多只读格式；首版能力之外
  的格式必须清楚标成未支持。
- Navigation 可扩展航段策略、ETA 和航线离线检查；不会因此暗中加入自动驾驶输出。
- Live Tile 可由 App 自己声明内容与尺寸，在 Shell 的 cadence/resource budget 内更新。

## Non-negotiables / 不可协商边界

- 默认沉浸式竖屏；方屏适配；不注册 Android HOME/DEFAULT，不打开 Android 桌面设置。
- Back 最远回到 Yokuli Start，不退出应用；虚拟键、系统 Back 和可交付实体键统一进入串行 Shell Engine。
- 用户数据迁移必须版本化、确定性、幂等；失败不能清库或让旧功能在新入口就绪前消失。
- 外部 MBTiles 默认零写入、零隐式整库复制；Google 不得填补已选择本地图的 coverage 缺口。
- NMEA/Phone 选源必须可解释；不能把 COG 当 Heading，不能静默 NMEA→Phone failover。
- UI 不拥有 socket、fd、MapView 或 process runtime 生命周期；Feature 间不建立 peer 依赖。
- 高频输入有背压与有界内存；日志、诊断和 CI artifact 不泄露密钥、完整 URI、精确位置或 provider token。

## Forbidden outcomes / 禁止结果

- 为了最终五 App 目标提前安装空壳、Coming Soon 或无真实 workflow 的入口。
- Settings 与 Preferences、NMEA Input/Data Sources 与 Data 在最终 Release 中长期并存。
- 迁移时把两个旧 Data 磁贴变成两个新 Data 磁贴，或改变幸存磁贴的 rank/size/group。
- 删除旧海图包、places、routes、tracks、连接配置或 source selection 来简化架构。
- 用测试文件名、构建成功、key 存在或 metadata bounds 冒充真实运行能力。

## Compatibility / 兼容策略

- W04 起当前 Release 安装 Chart、Settings、Data、Chart Library；Data 的真实纵切片与 host 已就绪，
  NMEA Input/Data Sources 只保留历史实现或 Data 内部连接子流程，不再形成两个目录入口。Preferences
  仍须等自己的真实业务纵切片就绪后才替换 Settings。
- 迁移分两步：v1 合并 NMEA Input + Data Sources → Data；v2 Settings → Preferences。步骤必须按序，
  目标 entry 未安装时停止并保留旧布局/入口。
- 两个旧数据磁贴或已存在的新 Data 磁贴发生重叠时，最早 rank 的 placement 幸存；保留它的 tileId、
  size、rank、group，其他别名移除。动态 connection token 保留 opaque 后缀并改到 Data 命名空间。
- Chart 的重型地点/路线 UI 只有在 Navigation 已能读取、编辑和恢复同一数据后才能迁出。

## Evidence required / 证据要求

- 每个 W 都有独立 commit、报告、baseline lock 和 named CI step；修复另起 commit，不 squash。
- 行为测试优先验证 migration、selection、math、projector/reducer；instrumentation 只覆盖关键旅程。
- 每次 push 由 GitHub 执行 full unit/lint/build/device/兼容门禁并生成精确 SHA 的
  `CODEX-CI-REPORT`；本地默认只做非测试 sanity。
- 每轮报告含 `DESIGN DECISIONS`，解释复用、取舍、未实现边界与兼容策略。

## Implementation freedom / 实现自由

本合同限制产品结果、数据正确性、危险行为与证据，不指定 class 数量、repository 层数、数据库选型或
Compose 绘制算法。施工者应先复用当前代码的清晰边界；新增抽象必须解决真实所有权、生命周期、并发或
测试问题，而不是为了对应文档名。

## English translation

Yokuli OS is an in-app operating environment organized around five eventual first-class apps: Chart, Chart Library, Data, Navigation, and Preferences. The contract deliberately keeps the solution space broad while making product boundaries, migration safety, data semantics, and evidence strict. Empty future apps must never be installed early. Existing user data and working entry points stay available until their replacement vertical slice is real.

The launcher migration is staged and deterministic: Data consolidation is version 1 and Preferences replacement is version 2. A step runs only when its target entry is installed. Duplicate aliases collapse to the earliest ranked placement while retaining that placement’s identity, size, rank, and group. CI, not repeated local full suites, is authoritative for each exact commit.
