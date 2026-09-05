# C10 NoSource 与只读观测质量

> English translation: C10 keeps production in an explicit NoSource state while defining a read-only future observation boundary. Position, true/magnetic heading, COG/SOG and accuracy age independently on a boot-scoped monotonic clock. The renderer uses separate live, historical, true-heading, COG and accuracy planes; no GNSS collection, NMEA output, autopilot or navigation runtime is added.

## 状态

- package：C10
- baseline：`c3bf7557fb6275ef4f3a4cac52d20be51629817b`
- implementation candidate / cumulative verified SHA：`260a343940c49bfa1564de544dea5881b9d8bf3a`
- status：`VERIFIED_LOCAL`
- production port：`NoSourcePositionPort`
- hosted CI：最终同 SHA 证据留给 C12；本报告不把本地 Gate 写成托管结果

## 交付

1. `ReadOnlyPositionPort` 只暴露观测事件，不能发送 NMEA、航线或船舶命令。生产 composition root 明确使用 `NoSourcePositionPort`；它不启动 collector 或 freshness timer。
2. 每个样本带 source ID/epoch、observation ID、可选 sequence/UTC 与置信度、boot-scoped 单调接收时间、validity 和可选精度。相同坐标的新身份可刷新；同身份缓存、乱序和同 boot 未来时间会产生 incident 而不覆盖最后好样本。
3. Position、Heading、COG/SOG 分别老化。30/10/15 秒窗口是显式策略；不同 boot 永远不判 fresh，UTC 回拨不影响单调年龄，持久快照不保存 live position。
4. 真航向、磁航向和 COG 不混写。无磁差的 magnetic heading 不转换为 true；低速或低质量 COG 不画向量；未知 accuracy 不画精度圈。
5. MapLibre 分开维护 live point、historical point、solid true-heading、dashed course-over-ground 和 dashed accuracy sources/layers。断流立即把最后点变为历史样式并停止实时向量，同时保留“尚未超时”这一独立事实。
6. Browse/Follow 是用户意图。无 provider 时没有 Follow 操作；用户 pan 退出 Follow；Browse 状态收到新数据不会抢相机。此前明确 Follow 且没有浏览动作时，有效新样本才继续跟随。
7. 中英文 UI 明示无源、无有效位置、断流但最后 fix 暂有效、历史年龄、实时中性点、真航向或 COG；没有“位置已启用”或虚构实时值。

## Red、自查与纠错

- Red 先固定 NoSource、身份去重、epoch、单调时间、不同 boot、质量分量、历史渲染和显式 Follow；旧模型无法编译这些测试。
- 第一轮实现后，全仓 Gate 暴露两个历史字面测试仍禁止合法 `courseOverGround` 或只在 `MapModel.kt` 查找已拆分类型；按本阶段合同改为拒绝假值/输出 runtime，并跨领域文件检查真实模型。
- 全仓 Gate 还发现隔离的旧 Google adapter 读取已删除的 `observation.source`。它仍未接回生产，只迁移为新 identity 访问以保证仓库所有模块可编译。
- 一个既有慢写并发用例在首次聚焦运行超时，单独复现和完整重跑均通过；没有删除、放宽或静默忽略该测试。
- 自审补了同坐标新样本、切源不混 heading、UTC 回拨、历史不持久化、实际 adapter feature planes 与生产无源 UI 反证。

## 聚焦与累计证据

- C10 repository contract：6 passed；position domain：11 tests；coordinator：2 tests；renderer quality projection：2 tests，全部通过。
- API 34：真实离线 package/renderer/index 17、Shell 52、Room 7，0 failure/error/skipped。Shell 正常生产入口验证无源提示可见且 Follow 不存在。
- 当前代码候选累计 Host Gate：206 个 Python repository contracts；357 个 JVM XML tests；全仓 test、lint、Standalone Debug/Release 和 test APK 共 1178 Gradle tasks。
- Release 产品表面审计通过：仍只有 Chart + Settings，无 HOME、Shell Lab、GPS/位置权限或 Google Maps key 依赖。
- Debug APK SHA-256：`be64095f91ef312f20ea13612901eb19d598a8e572098e397be7826a762b2124`。
- unsigned Release APK SHA-256：`2c6bf9aba4d8fa7a1806242c2831c8f873c588b993d0c1385a484c3c7f45c2d8`。

## 未宣称完成

- 本轮没有生产 GNSS/NMEA provider、权限流程、Socket、船网输出、自动舵、航位推算或导航任务；只完成未来 provider 可接入的只读消费合同。
- C11 的三档动态地图磁贴与 Shell 会话集成、C12 最终同 SHA 托管验证尚未完成。
- 三星方屏实体机、物理输入延迟和实际海上数据质量仍属于独立人工／硬件审核。
