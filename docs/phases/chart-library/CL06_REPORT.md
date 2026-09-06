# CL06 报告 / Process runtime, sessions and external changes

状态：`PASS — API34_RUNTIME_TESTED`

起点：`1826b9dff801a0b5f0b7c0a6a56772e44a8ff4fc`

## 实现

- `ShellApplication` 现在拥有唯一 `ChartLibraryRuntimePort`，进程启动即恢复 catalog
  与中断任务；Feature/ViewModel 关闭不关闭 runtime、grant 或 Chart 的 read lease。
- Runtime 汇合 catalog、来源命令、验证命令和资源读取。所有 renderer/coverage/
  validation 共享 12 个打开会话硬上限；自动 BASIC 队列上限 256、两个 worker。
  超限保持资产为 DISCOVERED 并增加可观察拒绝计数，不虚报完成。
- 每个会话绑定 asset revision 与 source generation。目录事务发布后，runtime 主动
  关闭 revision/generation/授权不再匹配的会话；注册窗口也二次核验，迟到 tile
  不能污染新 revision。
- 引入 `ChartReadPurpose`：CHANGED 原件只允许重新验证，验证通过前 renderer/
  coverage 不能继续使用旧内容。`.part/.tmp/零字节` 为明确 PENDING，不进入验证队列。
- 正在运行的 BASIC/FULL job 以最小持久 marker 记录；进程重启只恢复为
  INTERRUPTED，不恢复 fd、线程或 PASS。普通扫描没有 Service/FGS，且不借用 NMEA。

## TDD 与验证证据

- `:core:map-domain:test`、`:adapter:map-offline:testDebugUnitTest` 与
  `:app-shell:compileStandaloneDebugKotlin` 通过。
- API 34 `ChartLibraryRuntimeAndroidTest` 5/5：UI observer 生命周期隔离、revision
  失效、12-session budget、第 13 个等待、重启中断语义、CHANGED 验证用途隔离。
- CL06 静态合同确认 process owner、资源/队列上限、版本核验和无 FGS/Activity/MapView。

## 真实性边界

- 自动 BASIC 队列不保证一次接纳超过 256 个待检资源；拒绝计数可见，用户可刷新/
  手动重试。CL11 将执行资源压力验证。
- Android 进程重建已用持久 marker 验证；系统强杀、厂商 provider 离线和真实 SD 卡
  热插拔仍为 `NOT_RUN_PHYSICAL_DEVICE`。
