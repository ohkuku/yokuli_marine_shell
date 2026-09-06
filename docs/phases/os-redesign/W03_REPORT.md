# W03 报告 / Data Domain Merge

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`038c36b5a870f7fd82465a3ff42ae0e71a5e464b`

## 业务结果

- 新 `feature:data` 定义 Overview、Inputs、Sources、Flow、Diagnostics 的统一领域状态，但尚未注册生产 App。
- SourceGroup 冻结为 Position & Motion、Heading、Depth、Wind；候选保留真实 key/sentence/formatter/provider/freshness 证据。
- 新 group compatibility adapter 把一次产品选择翻译成一条底层 atomic preference command。
- core source reducer/runtime 支持通用全有或全无 transaction；任一 selected candidate 不可用就完全拒绝。
- Phone demand 由真实 selected source 决定；新 Data 模型没有独立 Phone enable/disable action。

## 测试源交付给 Pipeline

- core：失败零部分写、成功只推进一次 revision。
- feature:data：group candidate/evidence、unsupported key 明确 disable、unavailable 不 failover、legacy mixed、Phone demand。
- `osr_w03_contract`：模块依赖、五个 section、production 未安装、文档/report/baseline 与 CI 接线。

## DESIGN DECISIONS

- 没有把 SourceGroup 写进 durable schema；复用当前 per-key store，并在 Feature boundary 做兼容投影。
- 原子能力以通用 preference map transaction 进入 core，避免 marine UI 概念污染底层，也避免 Feature 多命令半成功。
- Flow state 只由当前 candidate evidence 生成，不添加“应该存在”的 sentence 节点。
- W03 不依赖 legacy Feature；W04 可以复用它们成熟的业务逻辑或迁移代码，但 Data domain 已独立。

## 刻意未做

- 未安装 Data、未触发 W01 migration v1、未删除 NMEA Input/Data Sources。
- 未实现 Compose Flow/UI、权限请求或 demand-to-runtime lifecycle；它们属于 W04 完整纵切片。

## 本地与待验

CI-first：本地只跑 W03 静态合同与 JSON/XML/YAML/diff sanity。完整 unit/lint/build/device 由 GitHub 对精确 SHA 执行。

## English translation

W03 adds an independent Data domain over the existing process-owned marine runtime. Semantic source groups retain actual evidence and map to one atomic per-key persistence transaction. The production app surface remains unchanged until W04 delivers the complete Data vertical slice.
