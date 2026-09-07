# DATA-R01 — Consumer Data Model

状态：`IMPLEMENTED — TARGETED GATE PASS`

## Product outcome

- Data 现在有且只有 `Boat / Flow / Connections` 三个一级产品区域；Sensor、Trust、Connection Detail 与 Diagnostics 是 drill-down surface。
- Boat 投影稳定产生 Position、Heading、Depth、Wind 四类人类可理解感官，Wind 在 UI 层聚合但 Apparent/True 的 source policy 仍保持分离。
- Boat overview 直接持有 `MarineSourceSnapshot.resolvedData.items` 的同一 immutable map，没有第二份 UI live truth。
- 所选来源失效时 sensor 显示 attention，不会借用 live backup 冒充已选来源。
- Consumer registry 明确登记 Chart、Navigation、Start Tile 的 required/optional 感官依赖；注册与“当前 active”是两个事实。

## Design decisions

- 保留已验证的 per-`DataKey` 原子 source-selection adapter，新模型只是产品语义投影。
- Chart 的 Position 是 optional，因为 Chart 在 Browse Only 时仍可使用；Navigation 的 Position 是 required，失效时为 blocked impact。
- Diagnostics 不在 primary-area enum 内，从 domain 层防止它重新变成首页 tab。

## Tests

- Red：新 projection test 因 Boat、Surface、Consumer contracts 不存在而编译失败。
- Green：`MarineNervousSystemProjectionTest` 与 `DataLiveTileTest` 最终定向执行 `5/5 PASS`。
- Consumer/connection journey 的 coordinator + projection 定向执行 `7/7 PASS`。
- `:app-shell:compileStandaloneDebugKotlin`：`PASS`，证明同一投影可由 production composition root 消费。
- 完整 core/adapter/lint/assemble/device 没有在每个小提交后重复执行，交给 hosted CI。

## English summary

DATA-R01 establishes a single resolved-data projection for four human sensor groups and a stable, activity-aware consumer registry without weakening per-key source-selection safety. Its focused projection, tile, coordinator and composition gates pass.
