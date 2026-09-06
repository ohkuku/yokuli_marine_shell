# W16 报告 / Final Gate and Evidence

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`f61b86e5eb09a088303f9635ec3abf4bd1a22bb7`（W15 后的累计报告修正点）

## 产品结果

- 最终 Android CI 汇总 W00–W16 合同、JVM、lint、Debug/Release、Release surface、API 34、API 36 与 Macrobenchmark。
- API 34 设备矩阵补齐 Navigation、Preferences 和内部 NMEA 的独立 instrumentation stories，并保留全部 adapter/Shell stories。
- 新增四连接、总计 100 Hz、30 分钟虚拟时间的 NMEA bounded soak，注入坏 checksum 和 12 秒静默窗口。
- 迁移证据收集器从实际 JUnit/AndroidTest XML 验证 Launcher、DataStore、地图 Room 和 Chart Catalog case，并绑定测试源码 SHA。
- 每个 per-job Codex report 携带自己的 W16 evidence；统一报告生成 `MIGRATION_REPORT` 与 `FINAL_ACCEPTANCE_LEDGER` 的 JSON/Markdown。
- 受信任的 push/workflow_dispatch 必须注入非占位 Maps key 才能发布 verified alpha；PR 保持 secret-free 离线验证。
- 台账严格分离 Maps build configuration 与真实授权/网络/图块加载，后者与其它物理项目均从 NOT_RUN 开始。

## DESIGN DECISIONS

- 复用现有四 job DAG 和统一报告协议，没有新造第二条“final workflow”；同一 workflow 的手动触发就是 manual full run。
- 用执行后的 XML 与精确 test case 生成迁移报告，而不是扫描源码猜测测试存在；缺 attachment 时聚合器给 BLOCKED。
- 高频压力使用真实 parser/catalog pipeline 和虚拟单调时间，既能执行 180,000 个 frame，又不把 CI 墙钟伪装成 30 分钟船网。
- active Feature 的独立 UI stories 直接加入现有 device wrapper；历史 Data Sources 不重新进入活动测试或产品图。
- Maps secret 的分发 gate 只证明 BuildConfig/manifest 注入。没有加入错误的“MAP READY”语义，也没有把真实 Google 加载设成 emulator 假证据。
- 最终账本禁止模糊的总结语；机器、手动触发和物理证据分别持有清楚状态。

## 兼容与未验证项

- 本工作包不修改用户数据 schema，不清库，不删除迁移 alias，也不改变五 App 产品表面。
- 当前提交只运行 CI helper 的窄本地验证；完整 Gradle/Android/性能执行由本提交触发的 GitHub Actions 提供权威结论。
- 三星方屏真机、真实 GNSS 权限/后台、船载 TCP/UDP、真实 Google Satellite 图块以及大型真实 MBTiles 均为 `NOT_RUN`。
- 普通 push 不是人工触发，因此 ledger 中 `manual_full_actions_run` 为 `NOT_RUN`；之后可对同一 HEAD 手动 Run workflow。

## English translation

W16 completes the evidence harness, not a fictional physical-device approval. It expands API 34 to every active app and adapter, adds a bounded 180,000-frame NMEA virtual-time soak, derives migration proof from exact executed test cases, requires Maps-key injection for trusted alpha builds, and places migration plus final acceptance ledgers inside the unified report. Authoritative GitHub Actions evidence remains pending for this implementation commit.
