# NAVREF-R06 — Track Recorder 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- Track Recorder 由 `ShellApplication` 的进程级 runtime 持有，不属于 Chart 或 Navigation 页面生命周期；切换 App、返回桌面或 Close Chart session 都不会隐式 Stop。
- Chart 与 Navigation 使用同一个 snapshot 和同一组 Start / Pause / Resume / Stop / Save / Discard 命令。
- Start 会立即接纳当下已经可用的选中船位；之后只记录带新 revision 的可用位置。
- Pause 不收点；Resume 明确开始新 segment。进程恢复时原 `RECORDING` 安全恢复为 `PAUSED`，不会把停机时间或陈旧位置伪装为连续航迹。
- Stop 只结束记录并进入待保存状态；Save 才事务性写入 durable Navigation track。写库失败时停止的全部轨迹仍被保留，可重试或丢弃。
- 达到 20 万点上限或写入失败会安全暂停，不继续显示为正在记录。
- Saved Track 保存分段、点时间、位置来源、可选 SOG/COG、距离、时长及开始时 active navigation/route 关联。
- 实际航迹使用独立 `ACTIVE_TRACK` overlay；它与 planned route / active navigation leg 不是同一个对象，切换 map content 不清除轨迹。
- map library schema 从 4 迁移到 5；旧 GPX track 保持 `IMPORTED`，新增 recorded metadata 默认为空。
- recording 文件损坏、不可读或来自未来 schema 时进入只读故障态；成功重新读取以前，Start 不会用新空会话覆盖原文件。wall clock 回拨时点、Stop 与 archive 时间保持单调，20 万点上限由 runtime 与 encoder 双重强制。

## Design decisions

- 复用现有 Navigation Library 事务与 Room 数据库，没有另造第二份 Track archive truth；兼容字段 `importedTracks` 保留为 wire/storage 名，新的 domain 读取入口为 `tracks`。
- 进行中的录制使用独立、受限的 Proto DataStore；library commit 成功而 session 清理失败时，基于相同 ID + digest 的重试只完成清理，不重复存档。
- 每个有效 fix 先持久化再发布新 snapshot；异常时宁可暂停，也不向用户报告未落盘的录制进度。
- Compose 只投影 runtime；实时 duration 的 1 秒显示 ticker 不写回 domain，也不拥有录制状态。

## Preserved invariants

- 一个 process recorder truth；Chart、Navigation 和 Shell 不各自持有 recorder。
- Planned Route、Active Navigation、Actual Track 三者模型与 renderer namespace 分离。
- 录制点仅来自已选择且可用的 position source；不记录或输出原始 NMEA。
- Close UI session 不删除 durable tracks，也不停止 process recording。
- 旧数据库通过显式 Room migration 升级，不 destructive reset。

## TDD evidence

- Red：核心 Track 模型/runtime 缺失，首轮 `DefaultTrackRecorderRuntimeTest` 在 compileTest 失败。
- Correction Red：首次命令错误复活 stopped session、容量达到后仍 recording、Start 不采集现有 fix，三项行为测试精确失败。
- Persistence Correction Red：future-schema load 后 Start 覆盖原文件、回拨 wall clock 产生早于 session 的点/archive 时间、encoder 缺少独立容量拒绝，均先由安全测试覆盖。
- Green：`DefaultTrackRecorderRuntimeTest` 11/11 PASS。
- Renderer Red：`ACTIVE_TRACK` action/state/overlay 缺失，`TrackOverlayContractTest` 在 compileTest 失败；Green PASS。
- `TrackRecordingProtoMapperTest` 2/2 PASS。
- `:feature:navigation:compileDebugKotlin` PASS。
- `:feature:chart:compileDebugKotlin` PASS。
- `:app-shell:compileStandaloneDebugKotlin` PASS。
- Room 4→5 instrumentation migration story 已加入，留给 push CI 的 Android gate。
- 全量 machine gate 留给 push CI。

## Human acceptance pending

- 在 Chart 点一次开始航迹，确认 map 上出现 console 与 live trail；切回 Yokuli Desktop、进入 Navigation 后仍在记录。
- Pause 后移动不新增点；Resume 后新增 segment；Stop 后明确 Save/Discard。
- Close Chart session 不停止 Track，重新打开 Chart 后 console/trail 同步。
- 强制结束进程后，未结束的 Recording 必须恢复为 Paused，不自动继续。
- 旧安装升级后，原 GPX track 仍可见且不被误标为 recorded passage。

## English summary

NAVREF-R06 adds one process-owned Track Recorder shared by Chart and Navigation. It records only usable selected-position fixes, preserves pause/resume segments, renders actual passage independently from planned navigation, safely pauses after process restore or persistence/capacity failure, and archives a durable recorded track only after explicit Stop and Save. CI and physical acceptance remain pending.
