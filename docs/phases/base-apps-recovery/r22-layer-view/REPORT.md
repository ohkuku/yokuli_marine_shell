# R22 — Logical Layers and User Views

状态：`IMPLEMENTATION + FOCUSED MACHINE GATE COMPLETE — HOSTED CI PENDING`

## Baseline

- 基线 HEAD：`359f4b71e107ba9bafd5fd4b3621e140dfb63976`
- 实现代码 HEAD：`77ed58002d4b5f320ada22dc718b1b7ace26999a`
- Execution directive SHA-256：`36f520ea6232b152554dd1d23d224e18b08009af89157ca046b1381a9dd68ac5`
- 分支：`codex/shell-map-contract`

## 产品行为变化

- V1 中一个已授权 folder 对应一个稳定的 logical Layer；folder 内多个 MBTiles 仅作为该 Layer 的 coverage fragments，不再成为多个日常 Layer。
- Layer rename 保留在 Layer 自己的 durable record 中，rescan、restart、permission repair 和新增文件不会把名称改回 folder/file 名。
- 删除自动生成的产品语义 `Sailing` View。零用户 View 是有效状态，Chart 使用 Satellite fallback，但不伪造一个持久 View。
- 启动时只清除精确匹配旧生成形状的 `default-view-v1 / Sailing`；用户改名、改底图或改 composition 的记录保留。
- View 现在拥有自己的 basemap、Layer membership、顺序、visibility 和 opacity；修改 global Layer 顺序不会静默改写用户 View。
- Chart Library 的 View 列表可选择要编辑/预览的 View，并直接编辑该 View 的 composition。

## 复用与边界

复用现有 `ChartLayer`、`ChartMapView`、Room catalog、`ChartViewDisplayPlanner` 和 feature coordinator。没有增加第二套 catalog、Layer/View model 或 renderer。旧 asset/source display planner 暂时保留作兼容入口，但不再生成系统 `Sailing` 真相；R23 会把 production preview/coverage 调到 logical View/Layer plan。

## TDD 证据

- Red：零 View 仍被报告 `ACTIVE_VIEW_MISSING`；repository 自动造 `Sailing`；新 asset 被自动写入已有 View；UI 没有 View-local visibility/opacity/order action。
- Green：二个 folder、五个初始 MBTiles 精确投影为二个 logical Layers；rename 经 repair/add/restart 保留；`Test A` 的顺序和 opacity 保留；零 View 使用 Satellite transient fallback。

| 定向 Gate | 结果 |
| --- | --- |
| `ChartMapContentContractsTest` | PASS |
| `ChartLibraryCoordinatorTest` | PASS |
| chart-library Android test compilation | PASS |
| `git diff --check` | PASS |

完整 test/lint/assemble/device gate 继续交给 hosted CI，不在本地重复跑。

## 仍待完成

- R23：Coverage 和 selected/inactive View 必须由同一真实 map surface 预览，且不激活 durable View。
- R24：Navigation 成为 Waypoint/Route/GPX 的唯一 durable truth。
- 人工可用性和物理真机视觉/触控证据仍属于最终人工验收，自动化绿色不会被写成产品批准。

## CI

- 报告前缀：`CODEX-CI-REPORT-`
- Hosted run：`PENDING_PUSH`

## English summary

R22 completes the existing logical Layer/View model without adding a parallel catalog. One folder is one logical raster Layer regardless of MBTiles fragment count; Layer names survive source lifecycle changes; user Views own their own basemap and composition; and a zero-View library remains usable through a transient Satellite fallback. The generated `Sailing` product fiction is removed with a conservative normalization that preserves edited user content. Focused machine gates pass; hosted CI and human product acceptance remain pending.
