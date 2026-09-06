# W16 Product & Engineering Contract — Final Evidence without False Confidence

版本：1 · 2026-09-07
状态：施工候选

## Intent / 产品愿景

W16 把 Yokuli OS 当前完整产品候选交给同一条可重复、可下载、可追溯的质量门禁。它不是继续堆功能，也不是把“代码已经写完”改写成
“真实航海环境已经验证”。机器可以证明的事实必须自动证明；需要用户、真实船载设备、真实卫星图授权或大型私人海图库的事实必须留在
人工账本中，直到对应证据真正发生。

最终交付的目标是让一次提交拥有一套完整身份：精确 HEAD、四类原始报告、迁移报告、最终验收台账和只有在全部机器门禁通过后才出现的
可安装 alpha APK。Codex 返工只消费同一 HEAD 的统一报告，避免重复跑全量本地任务和跨模块盲查。

## Experience / 用户体验

- 开发者 push 后只需按 SHA 找到一个 `CODEX-CI-REPORT`；成功时它说明无需机器返工，失败时精确指出失败域和额外 raw artifact。
- 统一报告内直接包含迁移报告与最终验收台账，用户不需要从散落日志推断“哪些跑了、哪些没跑”。
- 每项只能是 PASS、FAIL、BLOCKED 或 NOT_RUN；真实三星方屏、GNSS 后台、船载 TCP/UDP、真实 Google 图块与大型 MBTiles 不会被 emulator 冒充。
- `VERIFIED-yokuli-os-alpha-*` 只在合同、单测、lint、Release 表面审计、API 34、API 36 和性能趋势都成功后发布。
- 受信任的 push/手动构建若没有真正注入 Google Maps key，机器门禁直接失败；PR 因 GitHub 不提供 secrets 可以继续验证离线路径。

## Domain / 证据关系

- `CODEX-CI-REPORT` 是提交绑定的聚合证据，不取代原始 JUnit、AndroidTest、lint、Perfetto 或 Gradle 日志。
- build、API 34、API 36、performance 四个 job 各自拥有结果；聚合器只能组合它们，不能把缺失报告改成 PASS。
- migration report 由实际执行的 launcher、DataStore、Room map 和 chart-catalog 迁移 test case 生成，并绑定测试源码 SHA。
- 高频数据 soak 是纯 runtime 的虚拟时钟压力证据：四连接、总计 100 Hz、30 分钟逻辑时间、坏帧和静默窗口；它不等于真实船网 30 分钟。
- Maps build configuration 与 Maps runtime readiness 是两个不同事实。前者能由 APK/manifest 构建证据证明，后者必须在真实网络和授权环境验证。

## Opportunities / 可发展空间

未来可以把人工账本升级成受控的设备实验记录，附上机型、Android build、签名证书、Google Cloud 限制、海图库摘要与脱敏船网拓扑。
也可以让 nightly 承担更长的 wall-clock soak。W16 不预建实验平台，不把用户私人海图、原始 NMEA 或 API key 收集进 CI artifact。

## Non-negotiables / 不可协商边界

- 普通 push、PR 与手动触发仍走同一 `Yokuli OS Android CI`；手动触发事实必须单独记录。
- API 34 必须运行所有活动 Feature 的独立 UI stories、Android adapter tests、Shell stories，以及两个外部 force-stop restore probe。
- API 36 reduced-motion、AndroidX Macrobenchmark、完整 unit、lint、Debug/Release assembly 与 Release 产品表面审计均为最终机器门禁。
- 高频 soak 的集合、raw preview、rate bucket、incident 和 source diagnostics 必须保持已有硬上限。
- migration evidence 必须来自 JUnit/AndroidTest XML 中实际 PASS 的精确 test case；源码存在或测试名字符串不算执行证据。
- 受信任分发构建必须证明 `BuildConfig` 与 merged manifest 都收到非占位 Maps key，但文案只能叫 configuration。
- unified report 缺少 W16 attachment 时只能 BLOCKED，不能根据 job 绿色猜成 PASS。
- 统一 artifact 不读取环境变量、Gradle properties、`local.properties`、签名材料、私人 vault、原始坐标或 NMEA payload。

## Forbidden outcomes / 禁止结果

- 用 emulator 性能声明三星方屏、60/90/120 Hz 真机通过。
- 用 Maps key 非空声明 API 授权、账单、包名/SHA 限制、网络或图块加载通过。
- 用虚拟时钟 soak 声明真实船载 TCP/UDP、锁屏或 OEM 后台通过。
- 在 PR 因 GitHub 正常隐藏 secret 时阻断全部离线验证，或为让 CI 变绿把分发包退回占位 key。
- 生成没有 HEAD、run id、attempt 或 SHA-256 inventory 的“最终报告”。
- 从迁移报告遗漏失败/跳过 case，或用“基本完成”“大致通过”替代离散状态。

## Compatibility / 兼容策略

W16 不改任何用户数据 schema。它执行并汇总已经冻结的 Launcher product migration、DataStore schema migration、地图 Room v1→v4 和
Chart Catalog v1→v3 夹具；W15 已退出活动 Gradle 图的旧 Feature 仍只作为迁移历史存在。失败会阻止 verified alpha，但不会执行清库、重置或
自动重写用户文件。

## Acceptance stories

1. 受信任 push 在 Maps secret 缺失时无法得到 `VERIFIED` alpha；PR 仍能验证离线产品且台账把 Maps 配置记为 NOT_RUN。
2. 四连接 100 Hz 的 30 分钟逻辑输入包含坏 checksum 与静默窗口，全部 catalog/counter/rate window 仍保持硬上限。
3. API 34 执行 Navigation、Preferences、内部 NMEA、Chart Library、所有 Android adapter 与 Shell 的真实 instrumentation suites。
4. Chart 与 NMEA 各自在外部 `am force-stop` 前后运行独立 probe；策略/持久状态恢复，实时值不复活。
5. 迁移报告只有在指定 JVM 与 Android migration cases 实际 PASS 时为 PASS，并记录源码 hash 与原始 XML 路径。
6. Release APK 二进制仍只有五个正式 App surface，保留内部 NMEA runtime，拒绝旧 Settings/Data Sources 产品 class。
7. API 36 smoke 和 Macrobenchmark 任一失败都阻止 verified alpha。
8. 普通 push 台账把 manual full run 记为 NOT_RUN；手动 workflow_dispatch 只有在同一机器矩阵全绿时记为 PASS。
9. 五类人工/物理验证在没有外部证据时全部明确 NOT_RUN。
10. 统一报告内同时存在 manifest、Codex repair report、raw artifact 索引、migration report、final acceptance ledger 和 SHA-256 inventory。

## Evidence required

- build：W00–W16 静态合同、完整 JVM、lint、standalone Debug/Release、release surface、Maps configuration 与虚拟高频 soak。
- API 34：所有活动 UI/adapter stories、Room/Chart Catalog migrations、Chart/NMEA 外部 process restore。
- API 36：reduced-motion compatibility smoke。
- performance：AndroidX Macrobenchmark 原始 measurement 与 trace，仅作 emulator trend。
- artifacts：四类 raw reports、`CODEX-CI-REPORT-*`、`VERIFIED-yokuli-os-alpha-*`、聚合 migration report 和 acceptance ledger。
- human：三星方屏、GNSS、船载 TCP/UDP、Google Satellite runtime、大型真实 MBTiles，全部从 NOT_RUN 开始。

## Existing code landmarks

优先审阅 Android CI DAG、设备测试 wrapper、Codex job/unified report 生成器、Maps configuration evidence、NMEA ingress bounds、现有
Launcher/Room/Chart Catalog migration tests 与产品表面 APK 审计。这些是证据入口，不规定必须新建何种 production 架构。

## Implementation freedom / 实现自由

可以复用现有 JUnit XML、process logs 和 per-job attachments，也可以选择等价的有界证据格式；只要每个结论能回到真实执行且缺失证据不被
升级为 PASS。W16 不要求为了报告再造业务 repository、Feature coordinator 或持久状态。

## English translation

W16 seals the current Yokuli OS candidate with one commit-bound evidence chain: full build and release-surface gates, all active API 34 stories, API 36 compatibility, emulator performance trends, external process restore, exact migration fixtures, and a bounded four-connection 100 Hz virtual-time soak. Trusted distribution builds require an injected non-placeholder Google Maps key, while runtime map readiness remains a separate physical check. The unified report includes an exact migration report and a PASS/FAIL/BLOCKED/NOT_RUN ledger; Samsung hardware, GNSS background behavior, boat TCP/UDP, real Google tiles, and large private MBTiles remain NOT_RUN until real evidence is supplied.
