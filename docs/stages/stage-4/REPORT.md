# Stage 4 — Engine State, Effects & Persistence Ports

状态：`PENDING_HUMAN_REVIEW`。本报告只覆盖 Stage 4；它与 Stage 3 保持独立 commit，并在 Batch A 末等待一次人工审核。

## 基线

```text
Stage 3 commit / starting SHA: f0315cf1336991ebaaf7ba15f1f81ef9956d3b18
branch: codex/launcher-engine-batch-a
approval: PENDING_HUMAN_REVIEW
```

## 已实现

```text
- pure LauncherReducer with LauncherEngineState, LauncherAction and LauncherEffect
- one serialized Channel action queue in DefaultLauncherEngine
- HostPort catalog StateFlow -> Engine state -> renderer as the runtime truth
- non-crashing unresolved LaunchToken -> keep state + LogIncident
- deterministic LayoutTransaction identity, commit, cancel and undo
- LauncherPersistencePort and in-memory adapter with deterministic repair/fallback
- ShellViewModel owns the Engine across Activity recreation
- ShellActivity renders collected Engine state and no longer owns navigation/document truth
- layout proposals from Compose are committed only through Engine dispatch
```

Stage 2 的四项强制 carryover 已闭合：失效 token 不崩溃、action 串行化、安装 binding 已在基础 commit 统一、HostPort catalog 已成为 UI 的唯一 runtime catalog 来源。

## 边界

本阶段没有实现 pager、gesture arena、空间碰撞器、完整拖拽、Pin 导航反馈、持久化 DataStore、沉浸式虚拟系统键或海事 runtime。内存 persistence 只验证端口和 Activity 配置重建；进程死亡恢复属于 Stage 10。Back／Start／Search 后续输入会复用本阶段唯一 `LauncherEngine.dispatch(action)`，不会从仅为 `VISUAL_ONLY` 的证据编造按压灯效、触觉或时序。

## TDD 与 Gate

Stage 4 合同初始 Red：

```text
Ran 7 tests
FAILED (failures=3, errors=4)
```

Red 精确对应 reducer/controller、非崩溃 unresolved effect、串行 queue、transaction/undo、ViewModel 恢复、baseline/report 和 CI gate 尚不存在。

最终验证：

```text
Stage 0–4 named contracts: PASS
all Python contracts: PASS
shell-engine JVM tests: PASS
Activity recreation story: COMPILED FOR HOSTED DEVICE GATE
full Gradle test/lint/dual Debug+Release/androidTest assembly: PASS
dual Release product-surface audit: PASS
WP8 reference semantic validator: PASS (HUMAN_REVIEWED)
git diff --check: PASS
```

## English translation

Stage 4 introduces a pure reducer and one serialized Engine controller. The HostPort catalog flow is now the runtime source rendered by Compose. Unknown or removed launch tokens keep the current surface and emit a structured incident instead of throwing. Layout changes use deterministic transactions with commit, cancel, undo, persistence effects, and deterministic recovery. A ViewModel owns the Engine across Activity recreation; the Activity no longer owns navigation or Start-document truth. Durable process-death storage, paging, gesture arbitration, spatial collision, immersive virtual system keys, and marine runtime remain outside this stage.
