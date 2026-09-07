# R08 — Shell / Preferences Completion 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Start 使用持久的二维 `preferredCell`：每个 1×1 tile 都能独立放在真实 grid cell，两个小磁贴可以横排也可以竖排；重新启动后恢复该 cell，不因移动一个 tile 把所有 tile 做 row-major repack。
- 长按 Back 打开 Recents；每张 task card 有明确 `×`。Close 关闭 UI session，保留 waypoint、route、chart library、preferences 和 process-owned marine runtime。
- Status strip 的时间、电池与 App 状态均为 display-only semantics，没有隐藏的 Preferences 点击区。
- 中文 All Apps 继续使用 A–Z Latin jump；海图和海图库属于 H，数据属于 S，导航属于 D，偏好设置属于 P。
- Language、Units、Motion、Start、App tile preference 和 About 都由唯一 Preferences App 管理。
- Unit preference 现在贯穿 Chart、Data、Navigation：canonical meters/knots/NM 不被改写，只在渲染边界显示 NM/kn 或 km/km/h。
- Reduced Motion 现在不仅关闭 Shell surface transition，也关闭 Feature 内 stagger entrance 和 pointer tilt；Follow System 仍保留 WP motion。

## Replaced presentation

- 不再存在 status/battery 隐形导航入口。
- 不再把 tile 位置压回一维 rank 作为唯一真相；rank 只保留为冲突 tie-breaker。
- 修复“Preferences 显示 Metric/Reduced，但 Chart/Data 或 Feature motion 仍按旧值工作”的双真相。

## Preserved runtime/domain

- Stage 3–11 Shell Engine、serialized action queue、Start transaction/undo、task/session model、ProtoDataStore migration 与 crash recovery 保持不变。
- map/navigation/marine-data domain 继续保存 canonical 单位；偏好变化不迁移或重写历史数据。
- active navigation 不因 Close Chart session 停止；Chart Close 只清 target/measure/selection/draft 等 app-owned transient state。
- production Activity 保持 immersive、portrait-only、MAIN+LAUNCHER，并继续由内部 gesture 与虚拟 WP hardware keys 驱动；没有重新加入 Android HOME launcher intent。

## Design decisions

- 复用已存在的 `LocalMeasurementUnitSystem`，补齐 Chart/Data presentation boundary；避免在各 domain 各存一份单位偏好。
- 新增一个 composition-local reduced-motion truth，由 Shell 与所有 design primitives 共同读取；不要求每个 Feature 重复传 Boolean。
- Recents Close 保持 per-task 显式动作；`Close All` 仍是可选增强，不用一个非必要批量按钮延迟本轮 P0。

## Known gaps

- 真机必须验证 1×1 垂直拖放、重启恢复、长按 Back、Close Chart/Navigation、冷启动 locale 第一帧和系统“移除动画”组合。
- Chart 中仍有少量诊断/旧 secondary surface 使用 legacy presentation；它们不再是主地图旅程，若人工验收仍能触达并造成混乱，将在对应 App correction 中移除，而不是用旧 presentation tests 保护。
- `Close All` 属 optional，不在本轮 P0 声明完成。

## Human stories

待验收：`SHELL-01`–`SHELL-06`、`PREF-01`–`PREF-05`。重点：两个 1×1 竖排 → 重启；Chart/Navigation → 长按 Back → switch/close；点击 status/battery 无跳转；中文冷启动与 H 索引；单位切换后 Chart/Data/Nav 同步；Reduced Motion 后应用内进场与 tilt 同步消失。

## Tests

- retained shell-engine focused tests：19/19 PASS（二维 pack/place、task/Recents/Back/session）。
- retained shell-storage focused tests：6/6 PASS（含 `preferredCell`/locale/units/motion durable round trip）。
- retained locale tests：3/3 PASS。
- new display conversion Red→Green tests：3/3 PASS。
- `:feature:data:compileDebugKotlin`、`:feature:chart:compileDebugKotlin`、`:app-shell:compileStandaloneDebugKotlin`：PASS。
- 不新增按钮字符串/具体坐标/presentation class 存在性 gate；手势和冷启动在 R09 人工接受后才进入 R10。

## Git

- R08 implementation：`028e75216a5addce2d1e393de17d1cbac652d5b4`
- workflow / `CODEX-CI-REPORT-*` / installable APK：本批 push 后登记。

## English summary

R08 closes the shared-preference truth gap while preserving the existing two-dimensional Start, closable Recents, display-only status strip, Latin Chinese index, portrait immersive shell, and durable session model. Units and reduced motion now apply across production surfaces. CI and real-device acceptance remain pending.
