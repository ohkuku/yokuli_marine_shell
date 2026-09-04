# WP8 Reference Lab

状态：`NOT_YET_MEASURED`。本目录只建立证据合同与目录边界；Stage 0 没有采集、批准或伪造任何 WP8 测量值，也不声明 pixel-perfect。

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
- [`fixtures/`](fixtures/README.md)：由真实 Draft 2020-12 validator 执行的三份有效与四份无效 schema fixture；fixture 数值不构成 WP8 测量证据。

未来测量输出文件固定命名为 `WP8_REFERENCE_MEASUREMENTS.json`。状态合同为：

- `NOT_YET_MEASURED`：只需 schema version、profile 与 status，不得为了通过校验伪造 provenance 或数值；
- `MEASURED`：必须有至少一个内容寻址的 `captures[]` 与至少一个场景化 `measurementSets[]`；
- `HUMAN_REVIEWED`：除完整测量外，必须有 reviewer、review time、`APPROVED` decision、notes、canonical measurement hash 与 approved profile revision。

每个 capture 记录路径、SHA-256、字节数、MIME、像素尺寸、来源、原图/裁剪状态和权利说明。每个 measurement set 关联 scenario、capture IDs、viewport、测量者/时间/方法，并携带 geometry 和/或 motion evidence。直接操控 motion 必须同时保留 input timeline 与 visual samples。`reviewedMeasurementHash` 的输入是仅对 `measurementSets` 数组执行 UTF-8 JSON canonicalization：key 排序、无多余空格、保留 Unicode，然后计算 SHA-256。

## Stage 0 Gate

Stage 0 只验证目录、schema、来源追踪、正反 fixtures 和 CI 机器合同。截图采集、数值测量、Golden 审批、Macrobenchmark、方屏真机与刷新率数据均为 `NOT_YET_MEASURED`。Stage 2.5 必须使测量文件达到 `HUMAN_REVIEWED` 且哈希一致；否则 Stage 3 不得开始。

## English translation

Status is `NOT_YET_MEASURED`. Stage 0 creates only the evidence contract and directory boundaries; it captures no WP8 measurement and makes no pixel-perfect claim. The owner selected `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7` as the starting implementation. Before Stage 0 approval, Master v1.1 added a mandatory Stage 2.5 acquisition and human-approval gate while retaining the previous Master hash. The state-aware schema allows an honest empty unmeasured record, requires content-addressed captures and scenario measurement sets for `MEASURED`, and additionally requires an approved signed review for `HUMAN_REVIEWED`. Direct-manipulation evidence contains both input timelines and visual samples. Real Draft 2020-12 validation runs against positive and negative fixtures in CI. Stage 3 cannot start until Stage 2.5 reaches the reviewed state.
