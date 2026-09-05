# Yokuli OS — Launcher Engine TDD 施工规范

状态：`ACTIVE`。Shell 历史阶段仍以 [`LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md`](requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md) 追溯；当前 Map V1 施工以 [`CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md`](phases/chart-wp8-refinement/CODEX_FINAL_PHASE_WP8_CHART_COMPLETION.md) 的 C00–C12 为产品合同。后者明确覆盖旧文档中的扩展磁贴尺寸、地图禁入和逐 Stage 等待要求。

## 1. 当前 Phase 一次只写一个工作包

每个 Stage 必须独立经历：

```text
Freeze working base
→ Red
→ Green
→ Refactor
→ Package Gate
→ Commit
→ Update execution state
→ Continue when dependencies are green
```

不能把下一个工作包的模型、UI 或“顺手优化”混进当前提交。用户对起始 SHA 或范围的直接修正优先于附件旧仓库快照；不得 reset 到不相干旧 SHA。每包先提交并 push，再跑昂贵累计 Gate；失败立即记为未验证并用独立纠错提交，禁止 squash 或改写历史。

## 2. Red 必须证明合同缺失

先写 Given／When／Then／禁止副作用，再运行最小测试。有效 Red 必须因为当前 Stage 的合同尚未满足而失败，不得来自拼写错误、错误路径、环境缺失或故意破坏既有代码。

Stage 0 使用文档与静态合同；后续 Engine Stage 优先使用纯 JVM reducer、几何、布局、事务与恢复测试。Renderer、Android Adapter、Pager、设备和性能测试只能在 Master 指定的 Stage 进入 Gate。

Stage 2.5 是 Stage 3 的强制前置：先取得合法 capture、完成 scenario-specific geometry/motion measurement sets，并把状态经人工审核提升到 `HUMAN_REVIEWED`。缺少 `APPROVED` review 或 measurement hash 不得进入 geometry 实现。

## 3. Green 只覆盖当前 Stage

- 实现当前失败合同的最小闭环；
- 不弱化断言，不用 retry 掩盖 flaky；
- 不把 fixture、模拟器或视觉印象冒充真实数据或硬件证据；
- 未测量的 Golden、帧耗时、输入延迟和方屏硬件统一写 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`；
- 当前允许实现规范内的 MBTiles、地点、测量、手工路线、GPX、离线覆盖和只读观测端口；生产 GPS/NMEA 采集、活动导航和船网输出继续禁止。

## 4. Refactor 与边界检查

Refactor 只能在当前 Gate 全绿后进行。Engine 施工必须持续验证依赖方向：Core Engine 不引用 Android、Compose、Feature、Google Maps 或 Marine Domain；Renderer 不拥有布局事实或持久化；Adapter 执行平台 effect；`app-shell` 只负责组合。

## 5. Record

每个 Stage 更新：

- `docs/TDD_LOG.md`：starting SHA、Red 原始结果、Green 命令与边界；
- `CHANGELOG.md`：本阶段用户/工程可见变化；
- 需求或 ADR：只在合同确实变化时更新；
- Stage 报告：implemented、explicitly not implemented、测试、硬件和停止语句。

旧的 Slice 1–14 记录只保存在 [`archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md`](archive/pre-launcher-engine/TDD_LOG_PRE_LAUNCHER_ENGINE.md)，不能继续作为当前完成证明。

## 6. 测试层级

1. 静态合同：规范、目录、模块依赖和禁止项。
2. 纯 JVM：Reducer、geometry、二维文档、collision、transaction、repair、Home/Back priority。
3. Renderer/component：render model、semantics、Golden 与可控 motion clock。
4. 真实 Activity：Android 生命周期、手势竞争、恢复、HOME intent、API 兼容。
5. Macrobenchmark/真机：帧预算、输入延迟、刷新率和方屏；模拟器只看趋势。

测试进入顺序由 Master Stage 决定，不提前用空壳测试制造“未来功能已覆盖”的印象。

## 7. Stage Gate 报告

报告必须包含 Master §40 的 Baseline、Scope、Architecture、Interaction、Tests、Hardware 和 Stop。未执行项写 `NOT RUN`，未验证设备写 `UNVERIFIED_HARDWARE`，不得写“应该可以”或“看起来流畅”。

每个 Stage 最后一行固定为：

```text
STOPPED AT STAGE GATE.
AWAITING HUMAN REVIEW BEFORE NEXT STAGE.
```

## English translation

Status is `ACTIVE`. The Launcher Shell Engine Master Spec is the sole stage-order authority; older requirements and Slice 1–14 evidence are archived. Work proceeds one stage at a time through baseline freeze, meaningful Red, minimum Green, refactor, complete gate, commit, report, and mandatory stop for human review. Direct owner corrections are versioned in the Master and baseline lock while retaining the previous specification hash. Stage 2.5 must acquire lawful evidence and reach a hash-bound `HUMAN_REVIEWED` approval before Stage 3 geometry can begin.

Tests enter only in the stage that owns them: static contracts, pure JVM engine behavior, renderer components, real-Activity platform behavior, and finally macrobenchmark/physical hardware. Missing measurements remain `NOT_YET_MEASURED`; emulator evidence never becomes square-device proof. Marine capabilities remain prohibited until the Launcher Engine definition of done is reviewed. Every report ends with `STOPPED AT STAGE GATE.` and `AWAITING HUMAN REVIEW BEFORE NEXT STAGE.`
