# W03 · Data Domain Merge — Product & Engineering Contract

## Intent / 产品愿景

Data 是 Yokuli 的神经系统模型：它把“怎样接入数据”和“系统最终相信哪一份数据”组织为同一个领域，而不是
两个互相跳转的产品 App。W03 建立这个统一模型，保留成熟的 NMEA/runtime 分层；完整 UI 和正式安装留给 W04。

## Experience / 用户体验

- 用户按 Position & Motion、Heading、Depth、Wind 理解来源，而不是被迫逐个配置协议字段。
- 每个组能解释一个候选实际提供哪些值、来自哪些 sentence family、当前 freshness 如何。
- 如果历史配置让同组不同字段选了不同来源，Data 明确显示 mixed legacy selection，不替用户猜一个答案。
- 已选来源失效时仍显示该来源与原因；另一个 live 候选不会静默取代它。

## Domain / 领域边界

- Data 的页面语义为 Overview、Inputs、Sources、Flow、Diagnostics；它们共享一个领域模型，不共享临时 UI page state。
- SourceGroup 是产品语义；底层 durable store 仍保持 per-DataKey，以兼容已有数据和现有消费者。
- group selection 是一次原子 preference transaction：同一 source 支持的 key 被选择，不支持的 key 被明确 disable，
  防止同组悄悄混用别的来源。
- Phone location 是由 OS data selection 产生的 demand，不是新 Data path 中独立的 enable/disable 偏好。

## Opportunities / 可发展空间

W04 可以把真实 input → sentence → group → resolved output 画成稳定 flow；未来可加入选择历史和健康时间线。
内部绘制、投影缓存和状态组织可自由选择，只要不创造不存在的数据关系。

## Non-negotiables / 不可协商边界

- 复用 `core:marine-data` 与 process-owned Android runtime；不得复制 socket/parser/catalog/Phone runtime。
- group transaction 全成功或全失败，只推进一次 selection revision，只做一次持久化提交。
- group evidence 只聚合实际 candidate 的 DataKey、sentence、formatter、provider 和 availability。
- no silent failover；mixed legacy state 不自动归一。
- W03 不安装 Data，也不移除当前 NMEA Input/Data Sources；真实纵切片到 W04 后才执行 W01 migration v1。

## Forbidden outcomes / 禁止结果

- Feature 循环发多条 Select/Disable 命令伪装原子性。
- UI/Feature 持有 socket，或 `feature:data` 依赖两个旧 Feature 来拼页面。
- 为 Flow 视图画假 input/sentence/edge，或把 COG 并入 Heading。
- 因创建 Data module 就提前在 All Apps 显示空入口。

## Compatibility / 兼容策略

现有 per-key preferences、selection repository schema 与 resolved data API 保持可读。新增通用 atomic command 仍落到
同一 repository；SourceGroup adapter 是兼容边界。旧两个 Feature 暂时保留且不再扩展，生产 registry 在 W03 不变。

## Acceptance stories

1. 同一 NMEA source 的 RMC/GGA Position 与 SOG 聚合为 Position & Motion candidate，并保留真实证据集合。
2. 选择 group 生成一条 atomic command；source 不支持的同组 key 被明确 disable，不允许暗中沿用另一 source。
3. atomic command 任一 selected member 不可用时，整个 state/revision/preferences 不变。
4. 已选 A 失效且 B live 时，group 仍为 selected A unavailable，不自动改成 B。
5. 历史 per-key A/B 混选投影为 mixed，selected group source 为空。
6. 只有 Phone 实际被 OS data 选择时产生 demand；没有选择就没有 demand。

## Evidence required

core atomic reducer/runtime tests、Data group/projector/phone-demand tests、依赖方向检查、当前 production registry 不变检查，
以及精确 SHA 的统一 CI report。W03 不需要新的 Activity journey。

## Existing code landmarks

现有 source models/reducer/repository、Phone contract、NMEA snapshot 与两个 legacy Feature 是理解入口；不强制 W03
复制它们的类结构或文件布局。

## Implementation freedom / 实现自由

允许根据当前 repo 选择投影器、compatibility adapter 或 reducer 组合方式。唯一被锁定的是产品语义、原子持久化、
不静默切源和依赖方向，不规定内部 class 数量。

## English translation

W03 unifies NMEA inputs and source resolution under one Data domain without installing the Data app yet. Product-facing source groups project the existing per-key model. A group choice becomes one durable atomic transaction: supported keys select the chosen source and unsupported keys are explicitly disabled. Unavailable selections and mixed legacy preferences remain visible; neither is silently normalized or failed over. Phone location becomes demand derived from selected OS data.
