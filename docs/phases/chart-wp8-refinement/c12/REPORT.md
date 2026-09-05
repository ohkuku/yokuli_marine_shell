# C12 报告：同一候选累计验证与 Alpha 交付

状态：`CORE_MACHINE_READY`

基线：`5ac6cf6fb62f3e3d2f46ffc76d8ad6819d7c6110`

累计验证候选：`c98c40d9dadaf8c3f4fd252da47383184411eb65`

同 SHA 托管证据：[GitHub Actions 33994467983](https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33994467983)，结论 `success`

人工状态：`HUMAN_ACCEPTED: PENDING`

三星方屏实体机：`SAMSUNG_SQUARE_DEVICE: PENDING_OWNER`

## 用户流程结果

- W1：Chart 从生产桌面进入并恢复相机/已选本地图包；无位置源时为 Browse/NoSource，不显示假船位，也不把地图 SDK 生命周期状态说成图块完整。
- W2：长按候选可进入地点编辑，名称、分类、备注、稳定 ID/revision 经 Room ack 后可在列表、详情、地图定位、移动、删除与撤销中继续使用。
- W3：两点与多点测量使用 WGS84 距离和起始真方位，支持任意点编辑、undo/redo、清除以及复制为新路线草稿。
- W4：路线草稿、多个草稿切换、正式计划覆盖/另存/复制/反向与 Bridge 恢复均有入口；可空计划船速不会产生默认 5 kn，用词和 reducer 都不启动导航。
- W5：真实本地 MBTiles 的检查、安装、版本切换、回退、沿线 tile-key 检查与缺失定位可离线完成。来源在线获取仍为 `BLOCKED_EXTERNAL`，Release 不显示不存在的下载按钮。
- W6：GPX 预览/取消/确认、分段轨迹和独立 parser 往返已验证；外部驱动的两次 instrumentation 之间执行真实 `am force-stop`，证明已 ack 资料跨进程恢复。force-stop 不冒充系统低内存回收。
- W7：Chart 的 1×1、2×2、4×2 内容由 App 贡献，分别提供入口、真实摘要和纯路线预览；Start 不创建 MapView、不读取定位/网络，三档缩放与重排仍由 Shell 负责。

## C12 新增累计证据

- CI 显式运行 `map-offline`、`map-storage`、`app-shell` 三组 instrumentation，不再把只有 Shell smoke 的结果称为完整设备覆盖；失败注解会扫描三个模块，双进程恢复日志单独上传。
- J01–J06 位于 `ChartC12JourneyTest`，覆盖真实小图包、地点/Bridge/Search、测量转路线、多草稿与恢复、GPX、V1/V2 journal/回退/覆盖失效、三档磁贴和只读数据断流。
- `ChartC12ProcessRestartProbeTest` 被普通单进程 suite 排除，只能由 `run_c12_process_restore.sh` 分两次 runner 执行，中间外部 force-stop。
- C12 在 `c98c40d9dadaf8c3f4fd252da47383184411eb65` 上实跑 API 34：MapLibre/MBTiles 17、Room 7、Shell 普通 suite 59、J01–J06 6、双进程探针 1+1，均为 0 failure/0 skipped。完整本地最终 gate 与该 SHA 自身的托管 CI 均已通过。
- 11 个 release-like macrobenchmark 旅程有结果且无缺失。当前 API 34 模拟器中，有帧指标旅程的 P95 中位数范围为 40–125 ms，P99 为 40–125 ms；这是明确的 `EMULATOR_TREND_ONLY`，不满足也不冒称 60 Hz 真机目标。
- baseline profile 已用当前三档 Shell/Chart 生产路径重新生成：baseline 3520 行、startup 2645 行；不含旧六档、旧导航 intent、旧 Chart target 或 `javax.xml.stream` 规则。
- 本地 Gradle 构建曾输出 D8 partial-compilation 的未匹配 profile 规则警告；最终 `compileStandaloneReleaseArtProfile` 成功，Release APK 同时包含 `assets/dexopt/baseline.prof` 与 `baseline.profm`。这证明 profile 已打包，不冒称三星实体机上的性能收益已经验证。

## 两轮冻结反证审阅

第一轮检查数据、异步和资源边界，发现并修正：Android 运行时不存在 `javax.xml.stream` 导致 GPX 导出崩溃；搜索投影等待与稳定结果 tag；两进程探针被普通 runner 误收导致前置状态依赖；双进程日志未留存；设备失败注解遗漏 adapter。真实 MapView 50 次进出测试要求峰值同时存活为 1、最终为 0，允许 AnimatedContent 的有界重复提交。

第二轮检查旅程、布局和性能，发现并修正：性能旅程仍期待已删除的 4×4 尺寸；英文查询依赖设备 locale；旧 baseline profile 没覆盖当前路径。320×320、360×360、横竖屏、圆角安全带、IME 和 1.0/1.3/1.5 字体证据由累计 geometry/Compose/device tests 提供；模拟器截图/帧数据不替代三星实体方屏触控与刷新率验收。

## Release、安全与边界

- 最终 Release 产品表面保持 Chart + Settings，唯一 Activity 为 `ShellActivity`；Standalone 没有 HOME/DEFAULT 桌面注册，也没有 Lab/Fake/Replay/测试图包入口。
- 合并清单没有粗略/精确/后台位置权限或 Google Maps metadata。MapLibre 依赖带来的网络状态权限存在，但生产 style/source 使用本地 MBTiles，当前地图不需要 Google Maps key。
- NoSource 不启动位置 collector 或 freshness ticker；生产没有 GNSS/NMEA 采集、活动导航、自动舵或船网输出。
- NOAA 小区域仅是可追溯测试 fixture，不进入 Release，也不冒充内置官方海图。在线来源交付保持 `BLOCKED_EXTERNAL`。
- 路线 secondary pin 为 `NOT_APPLICABLE_CURRENT_SHELL`：当前 Shell 无动态 secondary-entry 生命周期，未显示假按钮。
- 临时 refinement workflows 和一次性 apply 工具已在确认无依赖后移除。

## 门禁、制品与未决项

- 本地累计 Gate：219 条 Python 合同、388 个 JVM tests、1207 个 Gradle tasks、双 Release 产品表面审计、API 34 三模块、双进程恢复与 11/11 性能旅程全部通过；最终标记为 `MARINE_SHELL_FINAL_GATE=MACHINE_VERIFIED CHART_C12_GATE=CORE_MACHINE_READY`。
- 同 SHA GitHub Actions run `33994467983` 的 build、API 34 integration、performance、API 36 和 Verified Alpha 五个 job 全部成功。
- 本地 `standaloneDebug` APK SHA-256：`c3f9722f4a4d68bb6ce2ad07f45f0c45648a60b2c4f492125fcf161616414b4f`；unsigned Release APK SHA-256：`b58b22f49bd8b79eda1c52252c493e6ffd2457358fe292618653ae7b34ac8858`。
- 托管 artifact `VERIFIED-yokuli-os-alpha-c98c40d9dadaf8c3f4fd252da47383184411eb65`（ID `9977914046`）归档 SHA-256 为 `a9ee24788e01e0fa1071aa90f507c9ffe1d3e276002dd6d275d5fc1c7e3c6cc3`，保留期截至 `2026-10-05`。
- 未解决 P0/P1：无。
- `CORE_MACHINE_READY` 只表示规格内可自动验证的核心闭环；`HUMAN_ACCEPTED` 和三星方屏实体机仍待用户审核。

English translation: C12 closes the machine-verifiable Chart V1 and three-size WP8 shell workflows with explicit multi-module device tests, real external force-stop restoration, release-like emulator trends, regenerated baseline profiles, and a strict Release surface audit. Online source delivery and Samsung square-device feel remain separate owner/external states. No production GNSS, NMEA, active navigation, vessel-network output, or Google Maps key is claimed.
