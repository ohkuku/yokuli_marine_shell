# CL12 报告 / 最终回归与产品验收候选

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`271f606b219211d34c9756e6c0a3f1cd9b611fa4`

## 交付结论

- CL00–CL11 的文件夹授权、零复制读取、catalog、扫描、验证、独立海图库 UI、Chart 多图消费、旧包迁移、
  Shell 安装和资源边界已经汇合成一个 CL12 候选。生产 All Apps 当前精确为 Chart、Settings、NMEA
  Input、Data Sources、Chart Library；默认 Start 仍只有 Chart 与 Settings。
- Chart Library 是唯一资源维护入口；Chart 只选择和使用海图。受支持外部原件只读、零写入、默认零复制；
  不支持直接读取时必须明确失败或由用户确认保存单张应用内副本。
- README 与支持矩阵已更新到五 App 真相，不再保留“精确四项”的过期声明。历史 Stage/Phase 报告保留为
  历史证据，没有被改写成当前合同。
- A01–A42 已逐项写入验收表。托管 CI 未回传前只允许 `CI_PENDING`、`PARTIAL_CI_PENDING` 或明确
  `NOT_RUN` 边界；本提交不预先填写 PASS。

## 最终机器 Gate

- 新的 `chart_library_cl12_contract` 校验当前五 App 产品面、默认 Start、竖屏/非 HOME、完整 A01–A42
  条目、支持矩阵、全仓 test/lint/debug+release assemble、API34/API36、performance、产品二进制审计和
  两份有界证据附件。
- Build job 在实际 assemble 后生成 `google-maps-configuration.json`：只报告 standalone release 的
  `BuildConfig` 与 merged manifest 是否都收到非占位配置，以及两者是否一致；绝不输出 key。这样可以先
  判断“不出 Google 图”到底是没有注入，还是进入 API 授权/账单/package/SHA/network/runtime 范围。
- API34 job 的 `chart-library-cl11-evidence.json` 继续验证 `zero-copy-read`、`runtime-bounds` 和
  `catalog-1000`；两份 JSON 都复制进相应 `CODEX-JOB`，最终统一报告可直接交回 Codex。
- 最终 Gate 审计发现 API34 `all` 列表此前漏掉 `adapter:chart-library-android`，导致 reader/runtime/catalog
  instrumentation 和 CL11 JSON 在 CI 中不可能完整执行；现已加入。旧的四 App Activity story 也按已
  supersede 的当前五 App 产品面更新并继续断言默认 Start 不变，而不是删除该故事。

## 本地执行

- 按用户的 CI-first 约定，本轮不在本地执行 `./gradlew test`、lint、assemble、emulator、完整 Python
  discovery 或旧全量合同。
- 只执行本范围 Python 单元/静态合同、JSON 解析、shell 语法和 `git diff --check`；最终裁决等待精确
  HEAD 的 `CODEX-CI-REPORT`。

## DESIGN DECISIONS

- 没有为 CL12 再建 runtime 或 repository；最终阶段只收敛当前产品真相、证据协议与可下载诊断。
- Google 证据只回答“构建是否注入”，不把配置存在提升为地图 ready。运行时图块失败仍属于 Google
  控制台限制、账单、网络、SDK 初始化或设备日志的后续诊断。
- 保留当前五 App 命名直到 W01 的新产品 migration scaffold 以显式版本迁移它们；CL12 不偷跑
  Settings→Preferences 或 NMEA/Data Sources→Data。
- 支持矩阵按能力描述 provider，而不是按品牌白名单；seek/静态完整 DB 是可直读条件，pipe 仍可被发现
  但不能伪装成零复制可用。

## 未验证与下一步

- 当前仍是 `GITHUB_ACTION_PENDING`，所以没有 hosted CI PASS 声明；若 job 失败，以统一报告定点修复。
- 真实物理 provider、30 分钟操作/故障负载、物理 16 KiB page-size、三星方屏和 Google 真图加载仍需
  设备证据；这些项目不会因 emulator 绿色自动变成 PASS。
- CL12 提交后进入 W01。W01 负责新五 App 产品模型和兼容迁移 scaffold，但不会把未实现的空 App 提前
  放进 All Apps。

## English translation

CL12 closes the implementation candidate and its evidence protocol; it does not pre-approve unrun tests. The production surface remains the current five apps, with Chart and Settings on the default Start document. GitHub Actions must decide the full unit, lint, build, API 34, API 36, performance, binary-surface, zero-copy, and bounded-resource gates for the exact commit.

The build report now includes a secret-free Google Maps configuration fact. It distinguishes missing injection from later runtime authorization, billing, package/SHA restriction, connectivity, SDK, or tile-delivery failures. Physical providers, long-duration soak, 16 KiB devices, square-screen review, and real Google tile loading remain explicitly unverified.
