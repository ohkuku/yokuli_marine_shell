# CL09 报告 / Managed-chart migration and explicit copies

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`e6fd995bd754d2ec7c3d8dac3f232293eda784bf`

## 实现

- 继续使用当前 Yokuli OS 私有目录 `filesDir/map_packages` 作为唯一受管包后端。启动与显式刷新会先让
  原有 journal/index 自恢复，再把所有已发布版本、活动版本和 history 幂等登记为海图库中的
  `MANAGED` 来源；登记过程不打开瓦片、不复制 payload，也不读取旧 Anchor Watch 的私有目录。
- 旧 `ChartPackageId`、logical ID、version ID 和活动单图选择都可解析到稳定的 catalog asset。
  Map session schema 从 3 升到 4，显示偏好成为持久事实；首次遇到旧选择时迁移一次。用户随后明确
  选择“不使用本机图”后，不会被旧 active index 或新扫描结果重新抢占。
- 外部资源默认仍只读原件。只有用户进入资源详情并确认“需要复制整个文件”后，才会进入一个有界的
  受管副本队列；复制显示已知大小、可用空间、阶段、字节进度和失败原因，可取消，并复用现有
  staging → 完整瓦片验证 → 原子发布 → journal/index 的事务路径。
- 副本完成只登记外部原件与受管副本的关系，不自动改变 Chart 的显示选择。受管读取使用
  `yokuli-managed://<sha256>` 不透明 locator，严格限制在私有根目录，以只读 SQLite 打开并持有旧
  backend 的 package lease 到 session 关闭。
- 删除只针对明确确认的受管副本；先释放 runtime 自己持有的 reader，仍有其它 lease 时返回“正在
  使用”而不删除。外部原件、其它来源、地点、路线、轨迹、Start Document 和 NMEA 数据不参与操作。

## 对“导入后一直解析”的处理

“添加外部文件/目录”现在只建立 SAF 引用并做 CL05 的分层基础检查，不再沿用旧导入动作里的“复制
整库并逐瓦片解码”语义。完整逐瓦片验证只发生在用户明确选择“完整验证”或确认保存应用内副本时；
两者都有有界进度与取消状态。这样大型 MBTiles 不会因为仅仅加入海图库而无限停留在旧式导入页。

## TDD 与 GitHub 证据

- Android fixtures 覆盖旧受管版本/history/active index 的零复制登记、所有 journal checkpoint 恢复、
  复制失败清 staging、删除 lease 与回退、受管 locator 越界拒绝、只读 session 关闭后释放 lease。
- Room 覆盖 catalog v1→v2→v3 非破坏迁移、原件/副本关系重启保留及级联清理；Map reducer/proto/
  display coordinator 覆盖 schema 4 round-trip 和旧活动选择只迁移一次。
- Compose/coordinator 覆盖副本在确认前不会启动，并展示整文件、空间和原件保留语义。
- 独立 `chart_library_cl09_contract` 已接入 `Yokuli OS Android CI`，并参与候选 APK 发布、失败包和最终
  enforcement。按 CI-first 工作流，本地只运行语法/结构检查以及生成 Room v3 schema 所需的最小
  KSP 任务，不运行本工作包的完整 Gradle 门禁；机器结论等提交后的
  `CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>`。
- 为生成并锁定 Room v3 schema 而运行最小 KSP 编译时，发现 CL08 的 Kotlin `fun interface` 抽象方法
  带默认参数、因而无法编译；本提交显式移除该默认值。所有实际调用点本来就传入 fingerprint，行为
  与 CL08 合同未改变。这是已记录的 CL08 编译纠错，不是静默重写其产品语义。

## DESIGN DECISIONS

- 复用已经具备原子发布、journal 恢复、版本回退和 lease 的 `AndroidMbTilesRepository`，没有建立
  第二个受管 blob store；新 catalog 只承担跨应用可消费的索引和关系事实。
- 旧受管包迁移只读 manifest/index，而不重做逐瓦片校验。那些包已经通过旧提交路径完成完整校验；
  若文件缺失则登记为 `MISSING`，不会伪装可用。
- 显示偏好继续属于现有 MapStore，因为它是 Chart 会话偏好；海图库只拥有来源、资源、验证和副本关系，
  两个应用不会共享 Feature UI 状态。
- 没有把“目录登记”“基础可读”“完整验证”“当前显示”压成一个 ready 布尔值；每层事实继续独立。

## 未验证边界

- GitHub Action 尚未回传 JVM、lint、Room migration、API34 fixture 或 APK 编译证据，因此本报告不写
  `PASS`。A31–A35 当前为 `CI_PENDING`。
- 物理 SD 卡/provider permission loss、真实低磁盘、复制中系统杀进程和外部 renderer lease 的真机
  组合仍需 CL11/CL12 设备证据；当前自动化覆盖的是相同状态机和可控 checkpoint。
