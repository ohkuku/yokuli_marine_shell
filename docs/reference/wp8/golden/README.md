# WP8 Golden 基线合同

状态：`CANDIDATE_PENDING_HUMAN_REVIEW`。

Stage 0 冻结时的历史状态为 `NOT_YET_MEASURED`；这里的候选状态不会回写或改写该历史证据。

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

Stage 11 已加入 Master 规定的八个场景和一个额外 360×360 方屏场景，共九张 API 34 模拟器候选图及 `GOLDEN_CANDIDATES.json`。稳定场景直接使用 `adb screencap`；启动平面场景从 Android `screenrecord` 的真实透视转场窗口提取，再无裁切缩放至 360×640。它们绑定到人工批准的 Stage 2.5 measurement hash，并由语义 validator 校验 PNG signature、路径、hash、字节数、尺寸和精确场景集合。它们还不是 Golden；manifest 刻意不含 reviewer 或批准时间。320×320／360×360 只证明模拟 viewport，不证明三星方屏真机。

## English translation

Status is `CANDIDATE_PENDING_HUMAN_REVIEW`; the frozen Stage 0 historical state remains `NOT_YET_MEASURED`. Goldens are approved Yokuli Renderer outputs, not original WP8 evidence. Stage 11 adds all eight Master scenes plus one extra 360-square scene: nine content-addressed API 34 emulator candidates tied to the owner-approved Stage 2.5 measurement hash. Settled scenes use direct screenshots; the launch-plane candidate is an extracted real transition frame. A validator checks PNG signatures, paths, hashes, byte sizes, dimensions, and the exact scene set. They carry no reviewer or approval time and are not approved Goldens. Square captures verify simulated viewports only, not Samsung hardware.
