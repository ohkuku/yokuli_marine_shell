# Stage 6 — Custom Spatial Grid

状态：`PENDING_HUMAN_REVIEW`。本提交只覆盖 Stage 6，不包含 Stage 7 的完整编辑手势。

## 范围

```text
Stage 5 commit / starting SHA: 0b39797a1cffb387e78f1ee4fbf0b5607d90af2a
branch: codex/launcher-engine-batch-b
```

新增显式 `StartOccupancyIndex`、`TileCollisionSolver` 和确定性的 `LocalTileCollisionSolver`。移动或缩放只重新安置与目标区域直接碰撞的磁贴，未受影响坐标、磁贴顺序和用户留出的空白保持不变。越界与不支持的策略会返回 `Rejected`，不会隐式 clamp 或全局重排。

Start renderer 改为像素对齐的自定义 Compose `Layout`。提交前的 `proposedDocument` 是独立视觉层：浮动磁贴跟随手指，碰撞邻居通过 `graphicsLayer` 移向建议位置；只有 Engine transaction 才能提交文档。动画中的浮点平移不是新的文档坐标。

## TDD 与 Gate

初始合同在实现文件、测试、报告和 CI Gate 均不存在时进入 Red。实现后，JVM 测试锁定：显式占用查询、局部性、空白保持、重复求解一致，以及 60 个合成磁贴的合法稳定结果。

最终累计 Gate：

```text
Stage 0–6 / all Python contracts: PASS (93 tests)
full Gradle test + lint + dual Debug/Release + androidTest assembly: PASS (954 tasks)
dual Release product-surface audit: PASS
WP8 reference semantic validation: PASS (HUMAN_REVIEWED revision 1)
API 34 real-Activity stories: PASS (13/13)
git diff --check: PASS
```

三星方屏继续标为 `UNVERIFIED_HARDWARE`，本阶段没有把模拟器结果冒充真机结论。

## English translation

Stage 6 introduces an explicit occupancy index and a deterministic local collision solver. Only placements directly intersecting the requested tile are relocated; unrelated coordinates, ordering, and intentional whitespace remain stable. The Start renderer is now a pixel-snapped custom Compose Layout with a separate proposed-document visual layer. A floating tile follows the pointer while affected neighbors use graphics-layer translation; only an Engine transaction can commit document state. Tests include a deterministic, valid 60-tile synthetic layout. Hardware fidelity remains unverified.
