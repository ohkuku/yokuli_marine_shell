# Stage 1 产品表面审计

状态：`PENDING_HUMAN_REVIEW`。本审计从 annotated tag `launcher-engine-stage0-approved-v1.1` 指向的 `16b0e5cd1c8fa2e5f4b78aefadf3fa7c012698b2` 开始，只重新验证 Product Surface Reduction。`ca84ef9…` 的候选结果不继承为 Stage 1 批准。

## Gate 结果

| Master Gate | 代码与产物证据 | 判定 |
| --- | --- | --- |
| Release Start 只有 Chart + Settings | `ProductionShellGraph.kt` 的 `defaultDesktopDocument` 恰有两个 placement：Chart Wide 与 Settings Small。 | `PASS_CANDIDATE_REVALIDATED` |
| Release All Apps 只有 Chart + Settings | `productionContributions` 恰为 `ChartShellContribution`、`SettingsShellContribution`；API 34 story 断言 All Apps 节点数恰为 2。 | `PASS_CANDIDATE_REVALIDATED` |
| 旧产品模块移除 | `settings.gradle.kts` 与 app dependency graph 不含 cockpit/library/system，仓库不含这三个模块的 active source 或 build script。本地可能遗留的 ignored `build/` 输出不是仓库或 APK 内容。 | `PASS_CANDIDATE_REVALIDATED` |
| Chart 只开放 Browse | `ChartDestinations` 只有 `chart.browse`；UI contract 只有 map surface 状态和打开地图设置 action，不含 tracking/navigation/anchor/trip/survey mode。 | `PASS_CANDIDATE_REVALIDATED` |
| Settings 只讲真实状态 | 外观、语言、地图配置与 About 使用真实 state/build facts；地图状态只表示提供了非占位密钥，不宣称 API、网络、账单、限制或图块已验证。About 只说明构建、地图配置与桌面文档事实。 | `PASS_CORRECTED` |
| 无 Coming Soon | release source/copy 静态扫描拒绝 `Coming Soon`、`COMING SOON` 与“即将推出”。 | `PASS_CANDIDATE_REVALIDATED` |
| 无假 SAFE/SOG/COG/Trip/NMEA | 静态 Gate 拒绝已知假船名、固定航行数值、trip/survey/anchor 状态与带数字的 SOG/COG；“NMEA 未实现”只作为真实缺失说明，不是模拟结果。 | `PASS_CANDIDATE_REVALIDATED` |
| Shell Lab 不进入 Release | app 只用 `debugImplementation(project(":feature:shell-lab"))`；standalone 与 HOME 两个 release APK Gate 都用 `apkanalyzer` 确认 `ShellLabActivity` 不在 manifest/Dex，且 Chart/Settings classes 存在。HOME 额外具有预期 HOME/DEFAULT 类别。 | `PASS_CORRECTED` |

## Release catalog

```text
installed apps: Chart + Settings
Start placements: Chart + Settings
All Apps entries: Chart + Settings
Chart destination: Chart Browse
Coming Soon: ABSENT
fake SAFE/SOG/COG/Trip/NMEA values: ABSENT
```

`feature:shell-lab` 继续存在于工程源码，供 debug 场景使用；它不是 release catalog contribution，只有 debug dependency，两个 release manifest 也都没有 `ShellLabActivity`。

Correction 后所有 Release 用户可见资源使用“地图已配置 / MAP CONFIGURED”和“仅浏览 / BROWSE ONLY”，不再表达地图 operational readiness 或船位状态；地图 attribution 与 About 只描述当前事实，不包含未来功能或诊断路线图。

## TDD 结论

新增 Stage 1 contract 的第一次运行共 8 项：5 项直接通过，证明候选产品面满足代码层 Gate；3 项因为 Stage 1 baseline/audit/report 和 release APK/CI 独立 Gate 尚不存在而失败。Green 补齐的是可审计证据与 release binary 防回归，不为制造 diff 重写已经合规的生产 UI。

本 Stage 不修改 Shell Engine contract、geometry、reducer、persistence 或 renderer 架构，也不执行 Stage 2。后续如获人工批准，只允许从 Stage 1 ending commit 开始 Stage 2。

## English translation

Status is `PENDING_HUMAN_REVIEW`. Stage 1 starts exactly from the annotated Stage 0 approval tag and revalidates, rather than inherits, the candidate work first present in `ca84ef9`. The correction reports map configuration only, exposes no vessel-position state or future-feature copy, and renames About without implying diagnostics. The production catalog, Start document, and All Apps contain exactly Chart and Settings. Both standalone and HOME release APKs are inspected with `apkanalyzer`; HOME adds the expected HOME/DEFAULT categories. No Stage 2 work is included.
