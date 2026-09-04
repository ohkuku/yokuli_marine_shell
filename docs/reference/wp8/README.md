# WP8 Reference Lab

状态：`HUMAN_REVIEWED`／`APPROVED`。Stage 2.5 已从仓库所有者提供的 WP8.1 模拟器录屏建立内容寻址的真实测量包，并由仓库所有者 kuku 批准；Stage 3 尚未开始。Stage 0 的历史状态是 `NOT_YET_MEASURED`。

## 冻结基线

```text
stage: 0
branch: codex/launcher-engine
starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
profile: WP8_CLASSIC_PHONE_4COL
master spec version: 1.1
master spec SHA-256: b0aeb000012283d56cf7e8eb343e2a026366e150b2f9e3d9969f45ed12f4bb40
previous master SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
```

Master 文档中的旧 reviewed SHA 被仓库所有者明确覆盖；Stage 0 从上面的最新提交开始。Stage 0 人工审核前，Master 升级为 v1.1 并加入强制 Stage 2.5 Reference Gate。新旧哈希、覆盖原因和实现基线由 [`BASELINE_LOCK.json`](../../stages/stage-0/BASELINE_LOCK.json) 锁定；提前存在的候选实现由 [`BASELINE_RECONCILIATION.md`](../../stages/stage-0/BASELINE_RECONCILIATION.md) 对账。

## 目录合同

- [`screenshots/`](screenshots/README.md)：原始参考截图及 provenance；不得放实现截图冒充 WP8 来源。
- [`golden/`](golden/README.md)：经人工 Reference Review 批准后才可写入的渲染基线。
- [`artifacts/`](artifacts/README.md)：CI 或本地比较输出；是可再生证据，不是规范输入。
- [`WP8_REFERENCE_MEASUREMENTS.schema.json`](WP8_REFERENCE_MEASUREMENTS.schema.json)：测量文件的机器可读合同。
- [`WP8_REFERENCE_MEASUREMENTS.json`](WP8_REFERENCE_MEASUREMENTS.json)：16 个 capture 与 5 个场景 measurement set；当前为 `MEASURED`。
- [`SOURCE_MANIFEST.json`](SOURCE_MANIFEST.json)：唯一视觉录屏、逐帧 extraction、覆盖缺口和 Microsoft 文档来源链。
- [`MEASUREMENT_METHOD.md`](MEASUREMENT_METHOD.md)：取帧、坐标归一化、误差、时序与未观察项。
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)：仓库所有者授权边界与第三方权利说明。
- [`fixtures/`](fixtures/README.md)：由真实 Draft 2020-12 validator 执行的三份有效与四份无效 schema fixture；fixture 数值不构成 WP8 测量证据。

未来测量输出文件固定命名为 `WP8_REFERENCE_MEASUREMENTS.json`。状态合同为：

- `NOT_YET_MEASURED`：只需 schema version、profile 与 status，不得为了通过校验伪造 provenance 或数值；
- `MEASURED`：必须有至少一个内容寻址的 `captures[]` 与至少一个场景化 `measurementSets[]`；
- `HUMAN_REVIEWED`：除完整测量外，必须有 reviewer、review time、`APPROVED` decision、notes、canonical measurement hash 与 approved profile revision。

每个 capture 记录路径、SHA-256、字节数、MIME、像素尺寸、来源、原图/裁剪状态和权利说明。每个 measurement set 关联 scenario、capture IDs、viewport、测量者/时间/方法，并携带 geometry 和/或 motion evidence。直接操控 motion 必须同时保留 input timeline 与 visual samples。`reviewedMeasurementHash` 的输入是仅对 `measurementSets` 数组执行 UTF-8 JSON canonicalization：key 排序、无多余空格、保留 Unicode，然后计算 SHA-256。

## Stage 2.5 Gate

Stage 0 只建立了 `NOT_YET_MEASURED` 合同。Stage 2.5 现已验证 MP4/PNG signature、路径边界、字节数、尺寸、SHA-256、capture 引用、timeline delta、核心 geometry/motion coverage 与 canonical measurement hash。当前 hash 是：

```text
af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
```

仓库所有者 kuku 已在 `2026-09-04T13:41:36Z` 批准 profile revision 1 与上方 hash；reviewer、UTC review time、`APPROVED` decision、notes 和 reviewed hash 均已写入。Golden、Macrobenchmark、方屏真机、物理 WP8 设备、输入 latency 和刷新率仍为 `NOT_YET_MEASURED` 或 `UNVERIFIED_HARDWARE`。

## English translation

Status is `HUMAN_REVIEWED` / `APPROVED`; the Stage 0 historical state was `NOT_YET_MEASURED`. Stage 2.5 uses only the repository-owner-supplied WP8.1 emulator recording as visual evidence. Sixteen uncropped exact frames and five scenario measurement sets are content-addressed and checked by a semantic validator for signatures, hashes, byte sizes, dimensions, references, viewport, timeline deltas, geometry, coverage, and hash-bound review. Repository owner kuku approved profile revision 1 and canonical hash `af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5` at `2026-09-04T13:41:36Z`. Stage 3 has not started. Golden output, benchmarks, physical WP8 and Samsung-square hardware, refresh rate, and input latency remain unmeasured or unverified.
