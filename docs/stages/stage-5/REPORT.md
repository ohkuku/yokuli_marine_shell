# Stage 5 — Interactive Start / All Apps Pager

状态：`PENDING_HUMAN_REVIEW`。本报告仅覆盖 Stage 5；Batch B 的 Stage 6 尚未进入本提交。

## 基线与范围

```text
Stage 4 commit / starting SHA: 4b7522dce88c633a9c49025b14b75861e8ee9f46
branch: codex/launcher-engine-batch-b
```

旧 `SwipeSurface` 的“内容静止、松手后固定 px 判定”已删除。`InteractiveLauncherPager` 使用 Compose Foundation `HorizontalPager` 提供 direct manipulation：两页同时存在、页面逐帧随手指、纵横轴仲裁、位置/速度 settle、边界约束与 settle 中重新触摸接管。settled page 与 Stage 4 的串行 Engine surface 双向同步；Start edit mode 会关闭 pager 的 user scroll。

没有添加未被 Stage 2.5 观察到的自定义 fling 阈值、按压时序或弹性常数。Foundation 的行为是本阶段可测试的运行基础，不冒充 WP8 真机测量结论；320/360 仍是模拟 viewport，Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## TDD 与 Gate

初始合同 Red：

```text
Ran 6 tests
FAILED (failures=2, errors=4)
```

编译期自审先发现错误的 `snapshotFlow` import，再发现 AndroidTest touch scope 不暴露 `size`；两项均修根因后重跑窄编译通过。

最终验证：

```text
Stage 0–5 cumulative contracts: PASS
all Python contracts: PASS
API 34 emulator Activity stories: PASS (13/13, including five pager cases)
full Gradle test/lint/dual Debug+Release/androidTest assembly: PASS
dual Release product-surface audit: PASS
WP8 reference validator: PASS (HUMAN_REVIEWED)
git diff --check: PASS
```

## English translation

Stage 5 removes the release-only fixed-distance swipe detector and installs a Foundation-backed direct-manipulation pager. Start and All Apps track the pointer, settle by Foundation position/velocity behavior, arbitrate vertical intent, remain bounded, and can be interrupted. Settled pages synchronize with the serialized Engine in both directions. Editing disables page swipes. No unobserved WP8 thresholds or feedback values are invented. Spatial collision, full edit/drag, and pin navigation feedback remain for Stages 6–8.
