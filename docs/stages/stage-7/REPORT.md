# Stage 7 — Complete Edit / Drag / Resize

状态：`PENDING_HUMAN_REVIEW`。本提交只覆盖 Stage 7；Pin/Unpin 的定位反馈属于 Stage 8。

## 基线与行为

```text
Stage 6 commit / starting SHA: f1652ee6f10b833cec9220ade5ef98ab86739749
branch: codex/launcher-engine-batch-b
```

编辑、选择、Begin/Update/Auto-scroll/Drop/Cancel、Resize 和无障碍方向移动全部进入 Stage 4 建立的单一串行 Engine queue。`StartInteractionState.Dragging` 保存真实 pointer id、grab offset、visual offset、target cell、proposed document 和边缘滚动速度；committed document 在 Drop 前保持不变。原位置显示 placeholder，邻居使用 Stage 6 proposed layer 在放下前让位。

Back 在 Start 编辑态先取消 provisional 操作，再退出编辑；Activity pause、pointer cancel、viewport 变化和 catalog revision 变化均不会留下脏文档。Resize 先建立 provisional transaction 并至少交给一帧 renderer，再 commit/persist；Chart 的 Small → Medium → Wide → Small 循环和 Entry 支持尺寸一致。

Stage 2.5 没有观察编辑手势，因此 cell hysteresis、48dp（既有最小触控尺寸）边缘区、按 cell pitch 派生的最大滚动速度和 Android 标准 haptic 都标为 `DERIVED_UNVERIFIED` 运行策略，不写成 WP8 测量值。精确手感仍需后续真机调校。

## TDD 与 Gate

初始 7 项合同结果：`FAILED (failures=4, errors=3)`。JVM stories 覆盖 grab offset、放下前邻居让位、滞回、防越界、滚动补偿、cancel、catalog 变化和三尺寸循环；API 34 增加 Back 优先级与真实 Resize 循环 story。

最终累计 Gate：

```text
Stage 0–7 Python contracts: 100 PASS
CI / release-metadata / secrets contracts: PASS
Stage 2.5 evidence validator: PASS (HUMAN_REVIEWED, canonical hash unchanged)
JVM / lint / Standalone+Home Debug / Standalone+Home Release: PASS (954 tasks)
core:shell-engine JVM stories: 32 PASS
API 34 Activity stories: 15 PASS
dual Release product-surface audit: PASS
git diff --check: PASS
```

自审期间纠正了三项问题：Activity pause 曾把普通 Idle 错变成 EditIdle、磁贴右侧编辑控件曾被拖拽 pointer listener 抢占、Resize story 曾把选中态 2.5% 放大误当尺寸错误。另恢复 Stage 5 的 `onEditModeChanged` 只读兼容通知，运行时编辑真相仍只来自 Engine state；未改写旧 Stage 合同。

## English translation

Stage 7 moves the full edit, drag, auto-scroll, drop/cancel, resize, and accessibility movement flow into the serialized Engine. The committed document remains unchanged until drop, while the renderer shows an origin placeholder and the Stage 6 proposed layout before commit. Back, pause, pointer cancellation, viewport change, and catalog change leave no provisional document behind. Edit timing was not observed in Stage 2.5, so hysteresis, edge scrolling, and platform haptics are explicitly derived operational behavior rather than claimed WP8 measurements.
