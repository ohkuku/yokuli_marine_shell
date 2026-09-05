# C11 报告：三档地图磁贴与 Shell 集成

状态：`VERIFIED_LOCAL`

基线：`08f9dece033ddc76f70d3549a125fb4e0be8784b`

候选：`5ac6cf6fb62f3e3d2f46ffc76d8ad6819d7c6110`

## 实现结果

- Chart 的 1×1、2×2、4×2 磁贴分别承担入口、状态摘要和纯数据路线预览；Start 上不创建 MapView，不访问网络或位置，不伪造地图截图。
- 磁贴状态按写入失败、未保存、编辑草稿、已选计划、上次视图、普通入口的明确优先级投影；Start 编辑期间冻结非关键装饰，关键失败仍可见。
- Browse 恢复最近 Chart 会话，不隐式重置相机、工具或对象；搜索以有界不透明 token 打开地点、路线、图包，缺失或无效目标留在 Chart 并显示真实状态。
- 本地搜索来源由同一 `InstalledAppBinding` 注册，Catalog、Launch token 和视觉贡献没有新增平行真值表。
- 当前 Shell 没有可安全复用的动态 secondary-entry 生命周期，因此路线二级固定标记为 `NOT_APPLICABLE_CURRENT_SHELL`；没有加入假入口或借此重写 Shell。

## TDD 与自查

- 新增 14 个 Chart JVM 测试，覆盖三档内容、状态优先级、缓存失效、会话恢复、搜索 token 和缺失对象。
- 新增 Host Port 动态 token 冲突/异常边界测试，以及 Search → 地点详情的 API 34 真实 Shell 场景。
- 自查发现 Stage 2 的静态合同将 `LaunchTarget` 视为被禁止的应用耦合命名；候选类型收敛为 `ChartDestination`，未修改 Stage 2 合同。
- 预览缓存仅保存纯数据投影并按 entry/generation/key 失效；当前没有位图或 renderer 快照缓存，因此 C12 的 64 MiB 图像缓存预算不适用。

## Gate 证据

- Host Gate：212 个 Python 合同、387 个 JVM XML 测试、1207 个 Gradle tasks，全绿。
- API 34：map-offline 17、map-storage 7、app-shell 53，全部通过，无 skipped。
- Debug APK SHA-256：`ae263807d2f19df1525edfe833c71ae69d3a792efbcb11b34db27f3153073731`。
- unsigned Release APK SHA-256：`181ac2786c648406021b4c74301228c607fb53f64b8d6e534fc16956a7013315`。
- Release 产品面仍只有 Chart + Settings；没有位置权限、Google Maps metadata、Lab/Fake/Replay 入口。

## C12 入口条件

- 把 map-offline、map-storage 和 app-shell 三组设备测试显式纳入托管 CI，而不是只运行 Shell stories。
- 处理或重新生成因 API 演进而产生的 baseline profile stale-rule 警告。
- 执行 J01–J06、性能/内存、50 次切换、最终 Release 清单和同 SHA 托管 CI 审计。
- 三星方屏与实体刷新率仍为所有者实机验收，不得用模拟器结果替代。
