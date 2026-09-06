# CL07 报告 / Independent Chart Library application

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`225ab0c0f60aef6b64c05da27d64ac6c29df4a3c`

## 实现

- 新增独立 `feature:chart-library`。页面状态、动作、纯 projector 和串行 coordinator
  只依赖 `core:map-domain` 的 `ChartLibraryRuntimePort`；没有 Activity、MapView、
  Chart feature 或 Android adapter 反向依赖。
- 首页以来源为主，支持文件夹/单文件 picker、搜索、待处理/启用筛选、资源长按批量
  启停；来源详情支持刷新、取消、权限修复、启停和非破坏性移除。
- 资源详情显示访问、格式、编码、瓦片尺寸、行号方案、zoom、范围、revision、署名
  provenance、角色和优先级；Unknown 不伪造。基础检查、完整验证及取消都经 runtime
  端口。外部 opaque URI 不进入 UI state，只显示 provider authority。
- Storage 区分外部原件引用、Yokuli 受管副本、catalog 与 cache。相同物理资源的多个
  membership 不重复累计。复制 capability 为 runtime 真值；CL09 后端未实现时不显示
  一个能点击却不能工作的假入口。
- Back 在 feature 根页返回 `false` 交给 Shell 回 Start；内部详情页先返回海图库首页，
  不退出 Android。UI 使用既有 WP8 header/application bar/entrance token 和中英资源。

## TDD 与 GitHub 证据

- 新增 projector 与 coordinator JVM 场景，覆盖 locator 隐私、重复引用计量、未知状态、
  runtime capability、串行 catalog 事务、opaque picker token 和 reauthorization。
- 新增独立 Compose host API34 stories，覆盖无 Chart 空态、权限修复、角色/优先级以及
  Shell-owned root Back；`run_device_tests.sh all` 已纳入该模块。
- 新增 `chart_library_cl07_contract` 独立 Action step，并纳入 build 最终 enforcement、
  failure bundle、API34 原始报告与统一 Codex 报告。
- 本地未运行 Gradle（用户指定 CI-first 工作流）。需要从本提交对应的
  `CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>` 读取机器结论；失败时再批量纠错。

## 真实性边界

- CL07 是可独立 host 的应用页面，不在本工单安装到生产 Shell；正式 App ID、Tile、
  picker host 与跨应用导航属于 CL10。
- “在 Chart 查看”的 typed action/effect 合同保留给 CL08/CL10，但独立 CL07 页面不假设
  Chart host 一定存在。
- 受管复制状态机由 core 明确表达，Android runtime 当前只返回 `NOT_AVAILABLE`；真正
  可恢复、原子发布的 copy/migration 属于 CL09。
