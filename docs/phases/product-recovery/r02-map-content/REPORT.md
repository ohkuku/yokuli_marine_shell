# R02 — Map Content Domain 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI/HUMAN PRODUCT ACCEPTANCE PENDING`

## Product outcome

- 用户授权的外部 Source 首次进入 catalog 时，会在同一事务中生成一个同名 logical Layer；一个 folder 内的多个 MBTiles 仍是该 Layer 的文件资源，不再天然成为多个用户图层。
- catalog 现在持久化 Layer、View、View 内的显隐/opacity/stack order，以及唯一 Active View。
- 第一个外部 Layer 会建立默认 `Sailing` View；以后加入的新 Layer 会进入当前 Active View，不修改用户已有 Layer 名。
- 新 View resolver 输出用户 Layer 名和确定性的底层文件顺序。裸文件名只保留在 File/Source 诊断层。
- 旧 catalog v4 非破坏迁移到 v5：既有外部 Sources 生成默认 Layers 与 Active View；assets、grants、validation、warnings 和 managed-copy relations 原样保留。

这些能力现在是共享 domain/runtime truth；R02 本身没有宣称新的 Chart Library/Chart presentation 已经完成人工验收。

## Replaced presentation

本包没有保留或固化旧 `SourceSet` / Asset-first UI。旧 Chart Library 首页、Quick Layer 文件名和 `priority=N` presentation 仍处于 superseded 状态，等待 R03/R04 替换。

## Preserved runtime/domain

- 复用现有 Room catalog 事务、SAF grant、scan generation、validation、managed copy 和 read-session budget；没有新建平行数据库。
- 保留 R01 的 direct/provider 与 local fallback、metadata tolerance、TMS/XYZ、Basic renderability 和 Full diagnostics。
- renderer 最终仍接收不可变的 per-file read requests；Layer/View 只负责用户组合与确定性展开。

## Design decisions

- 选择扩展现有 catalog v5，而不是创建 `LayerRepository`/`ViewRepository` 平行真相。
- 外部 Source 在 V1 只属于一个 logical Layer，防止同一 Asset 被重复渲染；未来要合并多个 Source 时，一个 Layer 可以明确拥有多个 Source。
- managed private store 是访问实现，不伪装成用户 Layer。
- View 内的顺序和 opacity 是显示真相；Asset 的 locator、revision、format 和 validation 仍是内部文件真相。
- Layer coverage/health 由其全部可渲染 Assets 聚合；缺 metadata 的 coverage 使用 R01 tile extent 派生结果。

## Known gaps

- R03 尚未把 Coverage / Layers / Views / Sources / composite preview 做成最终 Chart Library 产品表面。
- R04 尚未让生产 Chart coordinator 只消费 Active View；当前旧 Asset/SourceSet presentation 仍存在但没有产品兼容权。
- v4→v5 迁移和 Room round-trip 已编译，真实 API 34 执行等待 GitHub Actions artifact。
- 用户实际 known-good MBTiles 仍需要人工 fixture 验收。

## Human stories

当前实现为 `CL-10`、`CL-11`、`CL-13`、`CL-14`、`CL-20`–`CL-23`、`CL-25` 提供 domain/persistence 基础。它们只有在 R03/R04 APK 可见旅程通过后才可标记为人工作品通过。

## Tests

- retained correctness：map-domain、MBTiles、Room migration、catalog atomicity、resource bounds；
- superseded presentation：见 `docs/SUPERSEDED_PRODUCT_TESTS.md`，未恢复任何旧 UI blocker；
- 新增 domain behavior：多文件 Layer 聚合、health/coverage、View 展开、logical name、确定性文件顺序、不可用 Layer 真实性；
- 新增 Android behavior：自动 Layer/View、rename/compose/activate restart、单 Source 单 Layer、v4→v5 migration；
- 本地：`:core:map-domain:test` 157/157 PASS；adapter、feature test source 与 Chart 均编译通过；Product Recovery 6/6、Legacy MBTiles 4/4 PASS。

## Git

- R00 registry：`6cc61ffa2bef694ea4b9024e37c879fcf96e0e9a`
- R01 coverage/runtime correction：`080a973e698c43d6fef02975dfc2328ff2a2c00d` + `2ec567989261df5899c35ac522c75619f975e9c3`
- R02 implementation：`fbe0206236e32121f2a17355619b8e025d523273` through `e1a554d63e1044187a1502a66afeba95de8d15d6`
- GitHub workflow/artifact：等待本批推送后填写 `CODEX-CI-REPORT-*` 与 `yokuli-os-api34-reports-*`。

## English summary

R02 adds one durable map-content truth to the existing catalog: external Sources become logical Layers, Views compose ordered Layer settings and a durable Active View resolves to deterministic immutable file requests. Existing resources migrate non-destructively. The new product surfaces remain pending R03/R04 and human review.
