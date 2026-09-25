# Yokuli OS 开发入口

- 先核对当前 HEAD、未提交修改、目录级规则与 `app-shell/build.gradle.kts` 的 sourceSets。当前手机与 ROM HOME 共用 `app-shell/src/rebuild/java`、`src/rebuild/res` 和 rebuild Manifest；旧 `src/main/java` 页面不是生产入口。
- 主实施规则：[YOKULI_MASTER_EXECUTION](docs/product/YOKULI_MASTER_EXECUTION.md)。真实边界：[系统接入现状](docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md)；新功能沿 [领域接入指导](docs/os/02-DOMAIN-AND-CONTRACTS.md#新功能接入路径) 实施。
- 当前视觉基线为 [Windows 10 Mobile / MDL2](docs/product/WINDOWS_10_MOBILE_DESIGN.md)，以当期官方资料及共享语义控件实现。WP8 资料保留作历史参考；用户明确保留经典应用开合/Home 翻转与倾斜磁贴，控件仍为 W10M。不要复原超大普通标题、圆圈动作按钮或套用 Win11 Fluent 样式。大字号只保留给实际关键读数。
- UI 只拥有展示与本次访问状态；来源、导航、记录、守锚、AIS、内容、通知各有唯一所有者。禁止页面直连 DAO/控制器、重复采集或新增平行业务状态。现存兼容桥不能成为新功能的捷径。
- 对象动作必须带稳定 ID 与来路；点击捕获时刻后才能编辑描述，成功提示等待真实落盘，同 ID 重试。原生地图/三维场景保持单实例，覆盖面板与退出页不能抢输入。
- 单位统一使用 `MarineUnitPreferences` / `MarineUnitFormats`，禁止地图或页面按数值自行换单位；显示平滑不改传感器时间、质量与业务依据。
- 动画用系统帧时钟，连续位移/旋转在绘制层读状态，不按帧重组整页；历史图不补间原始点，测量时间、来源连续段与固定回看轴遵循 [来源契约](docs/product/DATA_CENTER_CONTRACT.md) 及 [仪表契约](docs/product/INSTRUMENT_TILE_CONTRACT.md)。
- 磁贴按规范内容绑定去重、按 tileId 编辑。工坊管内容，桌面管布局，应用提供真实内容；三入口调用 Shell 临时编辑会话，不另建工坊任务。提交/撤销走原 Proto 原子回执，预览复用实际排布与渲染器；参见 [磁贴契约](docs/product/INSTRUMENT_TILE_CONTRACT.md) 和 [已实施用户故事](docs/phases/tile-workshop/IMPLEMENTATION.md)。
- 保持 [来源契约](docs/product/DATA_CENTER_CONTRACT.md)、[导航返回契约](docs/product/APP_NAVIGATION_CONTRACT.md)、[通知契约](docs/product/NOTIFICATION_CENTER_CONTRACT.md)。Home 不停止任务；通知已读/清除不确认警报；历史/规划不能冒充实时。
- 普通 APK 与 ROM 共用领域实现；如变更进程、权限或 IPC，更新 [生命周期](docs/os/03-LIFECYCLE-AND-RECOVERY.md)、[安全](docs/os/05-SECURITY-AND-UPDATES.md) 与 [ROM 入口](rom/README.md)。同 UID 子进程不是安全沙箱，HOME APK 不是已构建 ROM。
- 本轮代码优先：不新增、修改或运行测试，不改 CI，不制作截图、录屏或验收报告。允许必要编译，保留现有构建保护。异常、持久化、权限、返回关系和资源释放必须完整。未有新授权不 push、发布、部署、刷机或破坏性清理；提交使用命令行。
- 更新受影响权威文档及 [API 索引](docs/product/API_INDEX.md)，不要再造互相竞争的“最终版”。完成状态区分：接入生产、编译状态、IPC、ROM 配置、镜像、设备运行，不能混写。
