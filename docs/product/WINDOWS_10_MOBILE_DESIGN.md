# Windows 10 Mobile · 当前视觉与交互基线

2026-09-25：用户明确将当前生产 UI 从 WP8 升级到 Windows 10 Mobile。以 2015–2016 年 UWP / Microsoft Design Language 2 为来源，保留 Yokuli 的海事任务与系统边界。本文只维护设计依据、适配决策和共享代码入口；通知、导航、数据所有权仍由各自权威契约维护。

## 一手参考及时代边界

| 官方来源 | 采用的事实 | 避免混淆 |
| --- | --- | --- |
| [UWP app design guidelines v1509，Microsoft，2015](https://download.microsoft.com/download/2/4/A/24A81A29-77CF-4AA5-967E-64E42554F21B/UWP%20app%20design%20guidelines%20v1509.pdf) | 印刷页 303 的文字层级；87–89 的 CommandBar 与溢出；97–99 的任务对话框；138–140 的单选；154–159 的 Pivot、即时开关与提交式复选 | 这里是当期 UWP，不是今天 WinUI 3 的默认样式 |
| [Microsoft Design Language，2016](https://download.microsoft.com/download/F/2/C/F2C19EC6-03E2-4D8C-B417-0265B808CD06/Microsoft-Design-Language-1603.pdf) | 第 12 页的 4 epx 栅格与 44 epx 触摸目标；20/30/32 页的文字与层级；41–44 页的系统图标比例 | 像素标注是 Windows effective pixel，不能无条件当作 Android 物理像素 |
| [Windows 10 Design: Getting the balance right，Windows 设计团队，2015](https://blogs.windows.com/windowsexperience/2015/04/29/windows-10-design-getting-the-balance-right/) | 手机设计兼顾熟悉感、内容与操作，并因应用任务选择导航形式 | 不是给每个应用强塞汉堡菜单，也不是照搬桌面导航 |
| [Scaling your phone app design to all UWP device families，Windows Developer Blog，2016](https://blogs.windows.com/windowsdeveloper/2016/06/27/scaling-your-phone-app-design-to-all-uwp-device-families/) | Mobile 保留合适的 Pivot、列表/详情和 CommandBar；空间改变时重排内容与操作 | 更大屏幕需要布局适配，不是所有字等比例放大 |
| [Windows 10 Mobile Build 14322，2016-04-14](https://blogs.windows.com/windows-insider/2016/04/14/announcing-windows-10-mobile-insider-preview-build-14322/) | 通知按应用分组，图标放在组头以让正文获得更多空间 | 分组是视觉归属，不改变单条消息的已读、目标或清除语义 |
| [Windows 10 Mobile Build 14356，2016-06-01](https://blogs.windows.com/windows-insider/2016/06/01/announcing-windows-10-mobile-insider-preview-build-14356/) | 动态磁贴的应用名服从系统文字缩放；快捷操作重排表达准备与拖动状态 | 保留文字可读性，不把修复公告当作逐像素或动效时长规范 |

2015 字阶为 Header 46/Light、Subheader 34/Light、Title 24/Semilight、Subtitle 20/Regular、Base 15/Semibold、Body 15/Regular、Caption 12/Regular。字号建立信息关系；不是每个页面都需要 Header。官方 Pivot 在标题全部放得下时静止；空间不足才进入 carousel 模式。开关用于即时生效，多选草稿使用复选框。以上为历史参考，Yokuli 的具体空间取舍在下一节明确列出。

现有 WP8 资料与历史文档保留作溯源；与本轮冲突时以当前 W10M 契约为准。当前 Learn 网页的 Win11 NavigationView、Mica/Acrylic、圆角和字体更新不能倒灌成本项目的当期参考。

## Yokuli 实际适配决策

- 普通页使用 24sp 标题，一行内表达当前页和真实子页返回；不叠放应用名、品牌、面包屑与第二个大标题。地图内容仍由地图任务决定空间。
- 分组使用 `AppSection`（20sp），对话框使用 `AppDialogTitle`（20sp/Semibold），正文 15sp，辅助信息 12sp。仪表主读数、导航距离、航程总距离保留任务所需的大字号，不能机械缩成正文。
- 行距、控件状态、轻按反馈、强调文字、浅/深主题统一来自 `core:design`。正文不全部细体；可读前景由主题推导，不改变用户保存的强调色。保留 Selawik 与平台 CJK 回退，未获授权或依赖下载的字体不打包，不宣称完整等同 Segoe UI。
- 共同控件使用 4dp 空间节奏；操作命中区保留至少 48dp 以适配 Android 触摸。40×20 开关、20dp 选择指示器等是本实现的适配尺寸，不伪称微软 PDF 给出的逐像素红线。
- 单选使用 `ChoiceRow`；多选使用 `AppCheckRow` 与 `W10CheckIndicator`，整行只暴露一份选择语义。执行按钮只表示下一步动作；不能以染色按钮假扮当前选择。开关写请求及真实结果，禁止另建本地假状态。
- 对话框使用 `AppDialogSurface` 的同一背景、细边界和 24dp 内距。长内容与键盘压缩时在受约束区域内滚动，不再各页自造 2–3dp 边框；嵌套表单不能重复套无界滚动。
- `AppCommandBar` 采用固定 48dp 底栏、按可用空间显示最多四个主命令，最右侧展开实际 Popup 溢出菜单；展开不会重排地图。菜单展示全部命令及完整标签是 Yokuli 对窄屏和中英文的适配，不冒充原生 CommandBar 展开模式。普通图标按钮采用无圆圈的 24dp 图形与 12sp 标签，危险操作保留原确认。
- Pivot 是同层内容切换；选中可见、可访问所有标题，不把每一帧内容位移叠加到整排标题。地图平移、AIS 雷达和仪表模型已有直接操作时，页签手势不能夺取这些触摸。
- 开场、页面转场、最近任务、通知面板分别表达启动、前进/返回、恢复和覆盖层。默认按压是平面反馈与轻微缩放，只有显式请求的磁贴保留弱倾斜；取消 WP8 风格过强的整页透视。只有当前可见内容驱动连续动画。80/120/160/220/240/320ms 等实际动效 token 是 Yokuli 对交互的适配，不宣称从微软真机逐帧测得。
- `AppPageTransition` 已接入数据中心、设置与磁贴库的真实子页：复用原路径和页面键，按深度决定前进/返回方向，以 32dp 短位移和 240/220ms 淡入淡出过渡。固定容器不补间内容尺寸；退出画面不接收 Back、触摸或无障碍操作。页面草稿与滚动由原 `SaveableStateHolder` 保留，不再建立业务模型；原生地图不套这层双画面容器。
- 最近任务标题位于真实快照上方，关闭按钮独立保留 48dp 命中区；卡片向上关闭直接跟手，松手携带速度弹簧收敛，重新抓取可中断回弹或关闭，宿主离开取消待执行关闭。此处只结束内部 UI 会话，业务任务继续由原服务拥有。
- 开始屏幕保留 Live Tile 真实订阅与图快照，应用名和辅助信息服从共享缩放；应用列表采用紧凑图标、名称与实际搜索入口。搜索留在应用列表，系统右键始终为通知。
- 圆角/挖孔通过现有横向安全区避让；自定义列表与页头共享安全边界，不靠把整个应用向下移动解决。

## AIS 观察工作区的空间与直接操作

AIS 首页采用 48dp 紧凑应用栏与 44dp 页签栏，标准合计 92dp，不包含 Shell 安全区域。这是 Yokuli 的持续观察场景预算，并非微软原生控件的逐像素规格。应用栏只保留身份、接收入口、海图入口与更多；输入计数、协议和完整来源说明进入对应详情。雷达、三维、船舶三个页面使用同一个 `HorizontalPager`，横滑与点按同步选中状态，不再只换标题或用目标列表挤掉半幅画布。

雷达和三维共用 `AisRangeZoom`：点按量程档位或拖动刻度改变观察范围，显示采用全局距离单位。当前相机范围即时联动，手势结束才保存偏好；这不是修改 CPA、近距或守锚警戒半径的入口。朝向和视角各由场景内的一个菜单承载，不排成多行常驻选择器。

选中目标由 `AisVesselSheet` 就地呈现摘要，约 108dp 并适应文字缩放；拖开到场景高度的约 90% 后浏览同一份 `AisTargetDetail`。拖动只更改覆盖面板偏移，画布不重测为半屏，不因详情展开重建三维宿主。摘要的拖动跟手，松手由位移、速度与弹簧共同归位；详情内容区独立滚动。普通资料与协议字段折叠，活动风险、报告年龄和数据冲突仍直接可见。关闭取消选中，Back 先收起再取消选中。

三维概览对远处小目标提供最小可辨显示尺寸；真实报告点不动，显示放大不参与距离、会遇或风险计算。可靠广播尺寸在拉近后呈现实尺，外形和高度仍是示意。船艏前视采用透视相机，要求有效本船位置和 Heading；相机、标签、点击拾取与视野判断消费同一投影。位置或 Heading 暂缺时暂用北向，保留原偏好，数据恢复后由用户明确恢复前视，不能用 COG 偷换船首向。桥高按可用船长示意为 4–18m，未知时 6m，属于视觉参数而非实测传感器数据。目标模型总预算为 64 个，另加本船；模型未出帧或超预算时仍显示真实位置符号，其点击范围与实际绘制一致。

三维加载按阶段表达无地理参考、窗口/布局、资产、首帧和恢复错误，不能把超时统一解释为设备不支持。两类实例各从 4 个开始，并按实际需要每帧各补至多 4 个；初始化不再预建满额池。标签按实际空间避让并优先保留选中、风险和关注身份，不能靠堆叠文本占满画布。

## 实际生产入口

| 层级 | 代码入口 | 约束 |
| --- | --- | --- |
| 字阶、调色板、指示器、动效 token | `core/design` | 保留 Wp 名称作为源码兼容，不表示仍采用 WP8 数值 |
| 页面、单选、按钮、文本框、加载、Pivot | `app-shell/src/rebuild/.../ui/Metro.kt` | 页面只组合公共控件，不复制第二套视觉状态 |
| 对话框、动作条、复选行、子页转场 | `ui/AppDialogSurface.kt`、`ui/AppCommandBar.kt`、`ui/AppSelectionRows.kt`、`ui/AppPageTransition.kt` | 必须真实接入业务回调、异常状态和原页面访问键 |
| 开始屏幕与最近任务 | `feature/desktop`、`WpShellExperience.kt`、`ui/TaskSwitching.kt` | 保留同一 app 身份及访问实例；样式不重启业务 |
| 应用内容 | 设置、数据中心、船联网、数据共享、图册、我的航行、日志、守锚、驾驶台、AIS 页面 | 常规标题/说明归一；关键海事读数维持可辨识性 |
| AIS 持续观察 | `ui/AisScreen.kt`、`ui/AisRangeZoom.kt`、`ui/AisVesselSheet.kt`、`ui/AisTargetDetail.kt`、`scene/ais` | 紧凑头部、横滑、共用量程与覆盖详情；三维视图不拥有另一份 AIS 真值 |
| 系统通知 | `NotificationCenter`、`NotificationQuickActions`、`NotificationTasks` | 历史按发布应用分组；保留消息/任务/警报的既有分层与持久命令链 |

应用层已把普通分组/说明和对话框接入共同组件；图册的连接文件夹与导入文件真实接入底部动作条。NMEA 配置、记录前姿态、磁贴轮换以及守锚条件都是提交式草稿，采用复选行；手机定位与屏幕常亮等即时设置仍采用开关。主题色具有真实单选与颜色名称，文件夹、航行、仪表和磁贴列表复用横向安全边界。这些调整继续调用原来的写入者，不新增 UI 私有业务真值。

具体导航规则见 [APP_NAVIGATION_CONTRACT](APP_NAVIGATION_CONTRACT.md)，消息/跟手规则见 [NOTIFICATION_CENTER_CONTRACT](NOTIFICATION_CENTER_CONTRACT.md)，Android / ROM 接管边界见 [06 · 系统体验](../os/06-SYSTEM-UX.md)。本轮设计迁移不能宣称已替换 Android SystemUI、获得 Windows 控件二进制，或已经证明全部设备上的像素与运动一致性。
