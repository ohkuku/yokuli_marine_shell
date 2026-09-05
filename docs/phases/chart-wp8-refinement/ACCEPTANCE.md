# Yokuli OS WP8 Chart Completion 验收单

核心状态：`CORE_MACHINE_READY`

人工批准：`PENDING`

## 候选

- Branch：`codex/shell-map-contract`
- 审阅基线：`9579761a3c7c5cf13735dfecaeb38636b715b695`
- 累计验证候选：`c98c40d9dadaf8c3f4fd252da47383184411eb65`
- Alpha：`standaloneDebug`，artifact 为 `VERIFIED-yokuli-os-alpha-c98c40d9dadaf8c3f4fd252da47383184411eb65`（ID `9977914046`）。
- Hosted CI：[run 33994467983](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33994467983)，与候选同 SHA，所有必要 job 为 `success`。
- 本地 Debug APK SHA-256：`c3f9722f4a4d68bb6ce2ad07f45f0c45648a60b2c4f492125fcf161616414b4f`；unsigned Release APK SHA-256：`b58b22f49bd8b79eda1c52252c493e6ffd2457358fe292618653ae7b34ac8858`。
- 托管 artifact 归档 SHA-256：`a9ee24788e01e0fa1071aa90f507c9ffe1d3e276002dd6d275d5fc1c7e3c6cc3`；GitHub 当前保留期截至 `2026-10-05`。

## 请人工验收

1. 安装 Alpha，确认默认全屏、内部 Back/Bridge/Search 虚拟实体键与系统 Back 的优先级符合预期。
2. 在三星方屏实机检查 320/360 方屏、圆角、触控命中、磁贴长按拖放、三档缩放、Start/All Apps 跟手和转场中间帧。
3. 导入你有权使用的真实 MBTiles，完成地点保存、测量转路线、正式计划、沿线离线资料检查、Bridge/Search 返回和重启恢复。
4. 导入后取消一次 GPX，再确认导入并导出；确认分段轨迹没有跨缺口连线。
5. 接入测试只读数据源时观察 fresh/stale/disconnected/recovered；确认无源时没有假船，恢复时不抢用户相机。

## 明确限制

- 三星方屏实体机和真实刷新率：`PENDING_OWNER`。模拟器性能只用于回归趋势，不能证明真机 P95 或手感达标。
- 在线合法资料获取：`BLOCKED_EXTERNAL`。当前提供用户导入、安装、版本、回退与离线检查，不显示假下载能力。
- 路线 secondary pin：`NOT_APPLICABLE_CURRENT_SHELL`，没有动态 secondary-entry 合同，因此未提供假按钮。
- 当前生产地图为 MapLibre + 本地 MBTiles，不需要 Google Maps key。
- 本轮没有生产 GNSS/GPS 权限采集、NMEA、活动导航、自动舵或船网输出；规划结果不是适航性结论。
- Release 不内置测试 NOAA 图包；地图内容、许可、更新与适用性由资料提供方和用户核对。

## 自动证据

- `TASK_PLAN.json` 将 C00–C12 的 104 个逻辑验收场景逐项绑定到实现测试、命令、结果和验证 SHA；条件性交付保持 `BLOCKED_EXTERNAL` 或 `NOT_APPLICABLE_CURRENT_SHELL`，不伪装成 PASS。各包报告/锁及早期统一工作日志位于本目录。
- C12 报告逐项记录 W1–W7、J01–J06、双进程恢复、性能、制品摘要和两轮冻结审阅。
- Release 清单 gate 验证仅实际产品入口、无 HOME 默认桌面注册、无 Lab/Fake/Replay 和无位置权限。
- 未解决 P0/P1：无。

English translation: This is the owner acceptance sheet for the machine-ready WP8 Chart candidate. The downloadable artifact is gated by all required jobs on the ending SHA. Samsung square-device feel, lawful online source delivery, and all production GNSS/NMEA/navigation/output capabilities remain explicitly outside the machine-approved claim.
