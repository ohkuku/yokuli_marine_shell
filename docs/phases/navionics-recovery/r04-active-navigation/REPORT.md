# NAVREF-R04 — Global Navigation Runtime Integration 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Chart、Navigation 与 Start consumer 继续读取同一个 process-owned `ActiveNavigationSnapshot`，没有创建第二套导航状态或数学实现。
- 保存航线 Start 与 Target/Waypoint Direct-To 在已有 ACTIVE/PAUSED session 时不再静默覆盖；运行时发布一个明确的 replacement proposal。
- Chart 和 Navigation 通过共享 active strip 显示同一个“当前航行 → 请求目标”的确认，并提供确认/取消。
- 确认在 runtime mutex 内串行执行；新 session 持久化成功后才替换内存真相。持久化失败时原 session、route 与 durable store 保持不变。
- 取消只清除 process-local proposal，不暂停、停止或修改当前导航。
- 同 route/revision 的重复 Start 保持幂等；Direct-To 仍是 embedded ephemeral route，不制造 saved `RoutePlan`。
- 如果 active-session 文件损坏、不可读或来自未来 schema，运行时进入可重试的只读阻断：任何会写 session 的命令都被拒绝，原文件不会被空状态或新航行覆盖。
- 在 active-session 真相不可读时，不会用 `activeSessionId = null` 去 reconcile History，避免把仍可能存在的航行错误标为 interrupted。

## Reconciliation with V3

- NAVREF-R01 Target/Quick Mark、R02 A/B Measure、R03 map-direct route edit/Reverse、R04 single runtime 基线和 R08 logical map content 已在 Product Recovery R01–R08 中存在，本批没有重复实现或改写。
- 本批补齐 V3 `OSNAV-05` 的真实缺口：active route replacement confirmation。
- Navigation Camera、Track Recorder、Navigation History 与 Shell ongoing activity 是后续独立代码包，不能计入本批。
- Bundle 中的截图是 conceptual preview，不是 pixel specification；没有复制 Navionics 品牌视觉、图标、订阅或专有制图语义。

## Preserved invariants

- 一次只存在一个 active navigation session。
- route revision 与 restore-safe paused policy 保持。
- Chart transient target/measure/draft guard 先处理未保存草稿，然后才把 start request 交给全局 runtime。
- UI 不持有导航 service，不在 Composable 内复制 navigation math。

## TDD evidence

- Red：`DefaultActiveNavigationRuntimeTest` 因缺少 `ReplacementRequired`、pending proposal 与 Confirm/Cancel command 编译失败。
- Green：`DefaultActiveNavigationRuntimeTest` 14/14 PASS。
- `:feature:navigation:compileDebugKotlin` PASS。
- `:app-shell:compileStandaloneDebugKotlin` PASS。
- 未运行全量质量门禁；按当前工作流交由 push CI 累计验证。

## Human acceptance pending

- Chart 发起 Direct-To 时，已有航行必须保持不变直至明确确认。
- Navigation 发起另一条 Route 时应出现相同确认。
- Cancel 后当前路线、active leg 与导航数值不变。
- 模拟持久化失败时不得出现 UI 已切换、durable session 仍是旧值的双真相。
- 模拟损坏或 future-schema session 时，Start/Direct-To 必须保持只读失败，原始文件和 active History 不得被覆盖或提前终结。

## English summary

NAVREF-R04 makes active-route replacement explicit and atomic across Chart and Navigation. It also protects unreadable or future-schema active-session files with a retryable read-only gate, without overwriting the source or falsely interrupting passage history. CI and real APK acceptance remain pending.
