# WP8 三档磁贴与海图 V1 工作日志

> English translation: WP8 classic three-size tile and Chart V1 work log. Chinese is the primary project language; English summaries preserve the same scope and truth boundaries.

审核锚点：`9579761a3c7c5cf13735dfecaeb38636b715b695`

分支：`codex/shell-map-contract`

授权：按完成包连续实施 C00–C12；不合并、不发布、不强推。

## 2026-09-06｜输入冻结与真实基线核对

- 归档用户提供的 Completion Pack；源 ZIP SHA-256 为 `151ab808d0d77276b40a343eb8542fbd8922f352b0fbfad2793d1c08df24be49`。
- 将主规格和机器任务清单原样纳入本目录。主规格内部 SHA-256 为 `e346c659b2cb94c003e46e3e67fd71fc7c1a7f0d419660fbbdf05544678d06fc`；任务清单内部 SHA-256 为 `fe6f54d3bc0e03d2935a4403a450ce49d90d24807d73bb39f4ccb889d7029373`。
- 本地最初没有看到远端新增提交；普通 push 的 non-fast-forward 反馈暴露了已审阅基线 `9579761`。随后只执行 fetch/rebase，没有 reset 或 force push。
- 重放本地 C00 时以远端实现为准，保留其连续拖拽、独立编辑命中区域、三档直接缩放、内部路由注册和回归测试。

## 当前范围 / Current scope

- 生产磁贴只允许 1×1、2×2、4×2；Chart 声明三档，Settings 声明小/中两档。
- 保留现有 Shell Engine、App 安装边界、主题、本地化、手势、Bridge/Search/Back 和持久化合同。
- 地图是 Shell 内部安装的独立 App；后续工作只沿一个生产 renderer 和明确端口扩展。
- 生产 NMEA/GNSS 采集、航行指令/输出和默认 Android Launcher 均不在本 Phase 范围。

## C00｜Red：已持久化未知尺寸会扩大成整桌回退

远端审核基线已经完成经典三档 enum、应用声明和大部分触控回归，因此这些不能伪称为本次新发现。增量审查发现真正剩余的持久化缺口：

1. 旧别名 `COMPACT_2X1`、`TALL_2X4`、`LARGE_4X4` 已有明确映射。
2. 但未来或损坏记录中的未知尺寸会让文档 schema 被改为非法值，之后可能触发整个 Start 文档回退。
3. 这违背“未知值受控、未受影响 tile ID、entry ID、rank、group 与 spacer 不应被清空”的 C00 Gate。

新增反向测试使用真实 Proto 同时放入三种旧别名、一个未知尺寸和 spacer，并要求二次编解码幂等。修正策略只在存储边界把未知尺寸降级为 1×1，保留其余身份与排序；生产 enum 仍严格只有三档。

## C00｜Green 候选

- 保留审核基线的 1×1 → 2×2 → 4×2 直接缩放以及 Settings 1×1 ↔ 2×2 行为；没有确认勾中间态。
- 未知持久化尺寸安全降级到 1×1，不再借 schema 值触发整桌重置；旧别名继续映射到最近经典形状。
- Adaptive packer 随机覆盖真实三档集合以及 4/6/8 列，继续验证边界和无碰撞。
- Python 合同按 app-owned renderer 注册能力检查 Chart 三档，不依赖私有 Composable 函数名。

状态：`IN_PROGRESS`。以上是已提交前候选，不宣称 Gate、APK、设备或最终地图能力已经通过；重放完成后必须在同一提交上执行 C00 累积 Gate。
