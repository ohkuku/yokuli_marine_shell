# NAVREF-R07 — Navigation History 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- 每次 active navigation 使用唯一 session ID；同一路线在同一毫秒内快速重启也不会覆盖上一段航行历史。
- 历史保存路线名称与 revision、规划航点快照、开始/结束时间、结束结果和最远推进 checkpoint，不依赖后来可能被编辑或删除的 Route library 条目。
- 到达半径自动推进和手动 Next 会记录不同的 typed reason。只有推进当下确有可用船位时才记录 actual position 与 source；恢复 checkpoint 不伪造时间或位置证据。
- Stop、Complete、Replace 和异常恢复分别保存为 `STOPPED`、`COMPLETED`、`REPLACED`、`INTERRUPTED`，不会把中断冒充完成。
- Navigation 页面显示最近航行摘要；实际保存的 Track 仅通过相同 navigation session ID 关联，规划路线和实际航迹仍是两个对象。
- 历史由 `ShellApplication` 进程级 runtime 持有并持久化；上限 1000 段，只淘汰最早的已结束记录，绝不为腾容量删除仍 active 的 passage。
- History 文件损坏、不可读或来自未来 schema 时进入只读故障态；在成功重新读取以前，runtime 不会用空历史覆盖原文件。单段 2000 航点、总计 20 万航点的上限同时由 runtime 与 wire encoder 强制。

## Design decisions

- Active Navigation 仍是安全关键运行真相；History 是独立的 durable evidence sink。历史写入失败会通过 `historyIssue` 单独暴露，不会伪装成导航输入故障，也不会阻断 Stop 或安全推进。
- 两个 DataStore 不能提供跨文件原子事务，因此恢复按保守语义收敛：推进 checkpoint 可以从 active session 恢复，但没有 durable passage event 就明确保持 `NO_POSITION_EVIDENCE`；孤立 active history 会转为 `INTERRUPTED`。
- passage 保存 route snapshot，而不是持有可漂移的 library 引用；actual track 通过 immutable session ID 关联，不根据名称或时间猜测。
- wall clock 回拨时 runtime 不崩溃：新状态时间不会早于 session start；外部提交给 History 的矛盾时间被原子拒绝。

## Preserved invariants

- Planned Route、Active Navigation、Waypoint Passage Event、Actual Track 四层语义分离。
- 不因 route library 后续修改而改写既有 passage。
- 不因 process restore 而编造 waypoint 到达时刻、船位或 source。
- 不将手动 Previous 记录成一次新的 passage。
- UI 不拥有 history runtime，也不执行 navigation math。

## TDD evidence

- Red：Navigation History 合同、runtime、proto/store 与 UI projection 缺失，首轮测试在 compile 阶段失败。
- Correction Red：同一路线快速重启 session ID 冲突、完成时刻不精确、恢复时伪造事件、wall clock 回拨构造异常均由行为测试先锁定。
- Persistence Correction Red：future-schema load 后首次 begin 可能覆盖原文件，以及 total-waypoint 容量只在 decoder 限制，新增安全测试先覆盖这两个反例。
- Green：`DefaultNavigationHistoryRuntimeTest` 8/8 PASS。
- Green：定向 `DefaultActiveNavigationRuntimeTest` 13/13、`MarineOngoingActivityPortTest` 1/1 PASS。
- Green：`NavigationHistoryProtoMapperTest` 2/2 与 `ActiveNavigationSessionProtoMapperTest` 3/3 PASS。
- `:feature:navigation:compileDebugKotlin` PASS。
- `:app-shell:compileStandaloneDebugKotlin` PASS。
- 全量 machine gate 留给 push CI。

## Human acceptance pending

- 完成、停止和替换导航后，Navigation 最近航行摘要与真实操作一致。
- 自动到达与手动 Next 的历史原因可区分；无船位证据时不得显示虚构的到达坐标。
- 同一路线快速 Stop → Go 两次产生两条不同历史。
- 保存对应 Track 后，历史只关联这一次 session 的实际航迹。
- 强制结束进程并恢复后，未闭合的旧历史显示为 Interrupted，不显示 Completed。

## English summary

NAVREF-R07 adds durable, bounded and truthful navigation passage history. It snapshots the planned route, records typed waypoint-advance evidence only when evidence exists, distinguishes completion, stop, replacement and interruption, and links an actual track only by the same immutable navigation session ID. CI and physical acceptance remain pending.
