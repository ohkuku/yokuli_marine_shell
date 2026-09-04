# 参与 Yokuli OS Launcher Engine 开发

## 中文（主文）

所有施工以 [Launcher Shell Engine Master Spec](docs/requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md) 为唯一阶段合同，并遵守 [TDD 规范](docs/TDD_PLAYBOOK.md)。旧需求和 Slice 记录位于归档目录，只能作为历史证据，不能越过当前 Stage。

固定流程：

1. 记录当前 Stage、starting SHA 和明确不做的范围。
2. 用 Given／When／Then 和禁止副作用定义合同。
3. 先运行因合同缺失而失败的最小 Red。
4. 实现当前 Stage 的最小 Green，不夹带下一 Stage。
5. 运行 Master 指定的完整 Gate，记录所有 `NOT RUN`／`NOT_YET_MEASURED`。
6. 更新 `docs/TDD_LOG.md` 与 `CHANGELOG.md`，提交并输出 §40 格式报告。
7. 写出停止语句，等待人工审核后才能进入下一 Stage。

Stage 0 不允许修改 UI、Registry、Google Map 或 Feature 行为。后续 Engine 代码必须维持 Core Engine → Contract 的单向依赖，禁止 Android、Compose、Feature、Google Maps 和 Marine Domain import。任何例外都需要当前 Stage 明确授权与 ADR。

中文仍是默认资源，英文翻译必须 key 完整；生产不得加入未标注 fixture。模拟器结果不能写成 Samsung 方屏、刷新率或实船结论。

```text
STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
```

## English translation

All work follows the Launcher Shell Engine Master Spec and the TDD playbook. Record the stage and starting SHA, create a meaningful Red, implement only the minimum current-stage Green, run the entire named gate, record every unrun or unmeasured item, commit, report in the Master §40 format, and stop for human review. Archived requirements and Slice logs are evidence only. Stage 0 must not alter UI, Registry, Google Maps, or feature behavior. Emulator results never count as square-device, refresh-rate, or vessel proof.
