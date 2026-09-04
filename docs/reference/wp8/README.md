# WP8 Reference Lab

状态：`NOT_YET_MEASURED`。本目录只建立证据合同与目录边界；Stage 0 没有采集、批准或伪造任何 WP8 测量值，也不声明 pixel-perfect。

## 冻结基线

```text
stage: 0
branch: codex/launcher-engine
starting SHA: ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7
profile: WP8_CLASSIC_PHONE_4COL
master spec SHA-256: f2a13be01ea836652d82f64a3f6b492df87dea4ad0f3d1c36c9faf84f869a4f0
```

Master 文档中的旧 reviewed SHA 被仓库所有者明确覆盖；Stage 0 从上面的最新提交开始。Master 正文保持逐字不变，覆盖记录只放在这里与 TDD 日志中。

## 目录合同

- [`screenshots/`](screenshots/README.md)：原始参考截图及 provenance；不得放实现截图冒充 WP8 来源。
- [`golden/`](golden/README.md)：经人工 Reference Review 批准后才可写入的渲染基线。
- [`artifacts/`](artifacts/README.md)：CI 或本地比较输出；是可再生证据，不是规范输入。
- [`WP8_REFERENCE_MEASUREMENTS.schema.json`](WP8_REFERENCE_MEASUREMENTS.schema.json)：测量文件的机器可读合同。

未来测量输出文件固定命名为 `WP8_REFERENCE_MEASUREMENTS.json`，必须通过 schema、列出原始 capture、设备/模拟器、系统版本、测量者、时间和方法。任何未知值保持缺失或 `NOT_YET_MEASURED`，不能用估计值填充。

## Stage 0 Gate

Stage 0 只验证目录、schema、来源追踪和 CI 静态合同。截图采集、数值测量、Golden 审批、Macrobenchmark、方屏真机与刷新率数据均为 `NOT_YET_MEASURED`。

## English translation

Status is `NOT_YET_MEASURED`. Stage 0 creates only the evidence contract and directory boundaries; it captures no WP8 measurement and makes no pixel-perfect claim. The owner explicitly changed the starting SHA to `ca84ef9c155f1a479ecdeee4da250cc8d9dd85a7`, while the imported Master Spec remains byte-for-byte unchanged. Future measurements must validate against the JSON schema and identify every source capture, environment, operator, timestamp, and method. Screenshots, approved goldens, and generated comparison artifacts have separate ownership.
