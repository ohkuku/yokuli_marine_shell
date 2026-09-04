# WP8 Golden 基线合同

状态：`NOT_YET_MEASURED`。

Golden 是 Yokuli Renderer 的批准输出，不是 WP8 原始资料。只有 `WP8_REFERENCE_MEASUREMENTS.json` 为 `HUMAN_REVIEWED`、review decision 为 `APPROVED`，且 reviewer、批准时间、notes、reviewed measurement hash 与 approved profile revision 全部通过机器合同后，才允许加入像素基线。初始场景名由 Master Spec 固定：

```text
wp8_start_360_dark
wp8_start_360_light
wp8_start_320_square
wp8_all_apps_360
wp8_edit_medium
wp8_context_menu
wp8_alphabet_jump
wp8_tile_launch_plane
```

每个 Golden 必须有同名元数据，包含 renderer commit、viewport、density、font scale、theme、locale、reviewer 和批准时间。更新 Golden 不能用来掩盖失败；必须说明规范或 Renderer 为何改变。

## English translation

Status is `NOT_YET_MEASURED`. Goldens are approved Yokuli Renderer outputs, not original WP8 evidence. A golden may be added only after the measurement document reaches `HUMAN_REVIEWED` with an `APPROVED` decision, reviewer, time, notes, reviewed-measurement hash, and approved profile revision. Golden metadata also records renderer commit, viewport, density, font scale, theme, and locale. Updating a golden never substitutes for explaining a contract or renderer change.
