# 参与 Yokuli OS Launcher Engine 开发

## 中文（主文）

当前地图施工以 [WP8 三档磁贴与地图 V1 收尾规范](docs/phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md) 为产品合同，并遵守 [TDD 规范](docs/TDD_PLAYBOOK.md)。Launcher Master 和旧 Stage 记录继续约束已批准的 Shell 边界，但与本 Phase 冲突时，以当前规范的三档磁贴、地图内页和连续 C00–C12 流程为准。

固定流程：

1. 记录当前工作包、working base SHA 和明确不做的范围。
2. 用 Given／When／Then 和禁止副作用定义合同。
3. 先运行因合同缺失而失败的最小 Red。
4. 实现当前工作包的最小 Green，不跨越未满足的依赖。
5. 先跑窄 Gate，提交并推送，再跑累计 Gate；失败用独立纠错提交，不改写历史。
6. 更新唯一 `EXECUTION_STATE.json`、工作日志与 Changelog；未运行项目必须保持 `NOT_RUN`。
7. 工作包之间不等待人工逐项批准；只在规范规定的真实外部阻塞或最终人工/真机 Gate 停止。

Stage 0 不允许修改 UI、Registry、Google Map 或 Feature 行为。后续 Engine 代码必须维持 Core Engine → Contract 的单向依赖，禁止 Android、Compose、Feature、Google Maps 和 Marine Domain import。任何例外都需要当前 Stage 明确授权与 ADR。

中文仍是默认资源，英文翻译必须 key 完整；生产不得加入未标注 fixture。模拟器结果不能写成 Samsung 方屏、刷新率或实船结论。

```text
STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
```

## English translation

Current Map work follows the WP8 Chart Completion contract and the TDD playbook. Each C00–C12 package records a meaningful Red, a bounded Green, an atomic commit, and actual narrow plus cumulative evidence. Packages continue without per-package owner approval; failures remain visible and receive follow-up commits. Historical stages still protect the Shell boundary, while physical-square, refresh-rate, and vessel claims remain human or real-device gates.
