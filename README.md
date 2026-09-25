# Yokuli OS

**ROM 开发分支：`codex/yokuli-os-rom`。** 系统分层、设备/安全/升级设计与 AOSP 产品配置见 [ROM 总入口](rom/README.md)。本分支从 experience.6 继续开发系统集成；当前可构建 HOME APK，完整 ROM 镜像尚未在 Linux 构建机编译或启动。原手机应用体验分支保留为 `codex/yokuli-os-rebuild`。

以 [Windows 10 Mobile / MDL2](docs/product/WINDOWS_10_MOBILE_DESIGN.md) 的排版、横滑、磁贴和虚拟键组织的 Android 航海应用平台；旧 WP8 资料保留作历史参考。当前版本与构建身份统一取自 [根 Gradle 配置](build.gradle.kts)。当前生产实现包含分类历史/三维驾驶台、独立 AIS 交通能力，以及统一消息服务和跟手通知中心；必要编译与实际设备运行是不同完成状态。

完整结构见 [26 项反馈、全部应用接口与数据拓扑](docs/product/OS_INTERFACE_TOPOLOGY.md)，声明位置见 [API 索引](docs/product/API_INDEX.md)。数据所有权见 [数据中心契约](docs/product/DATA_CENTER_CONTRACT.md)，页面访问与返回见 [应用导航契约](docs/product/APP_NAVIGATION_CONTRACT.md)。继续实施见 [主规则](docs/product/YOKULI_MASTER_EXECUTION.md)，通知具体规则见 [通知契约](docs/product/NOTIFICATION_CENTER_CONTRACT.md)。旧 experience 交付文件仅记录对应历史版本，不能代表当前 HEAD。

## 系统代码的层级

`app-shell` 页面与 Shell → `runtime:marine-local` 组合宿主 / `MarineServices` 领域端口 → `LegacyMarineController` 与内容适配 → 现有领域运行时、仓储和 Android 服务。`core:runtime-contract` 只放纯 Kotlin 的连接、航行状态与命令回执。海图和日志共同调用运行时航行协调器；偏好只提交本次修改的字段，内容读取不把 DAO 交给页面。

海事业务目前仍在默认进程，兼容读投影含 `MainUiState` 和部分 legacy DTO。通知已通过 `NotificationClient` 接到同包、同 UID 的 `:notifications` Binder 服务，消息历史单写并处理持久化/重连；完整海事 IPC、独立 UID 和 Android 系统通知/Recents 接管尚未实现。源码图、命令方向和当前限制见 [系统接入边界](docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md)，整体路线见 [OS 文档导航](docs/os/README.md)。

## 应用

| 应用 | 主要操作 |
| --- | --- |
| 海图 | 选点、对象查询、测距、手工与自动规划、沿程资料检查、候选比较、统一导航会话及立体方向视图。 |
| 图册 | 栅格文件夹与优先级；结构化 S-57 数据集导入、连续更新、用途、图幅版本与选用。 |
| 航海日志 | 当前航程、时刻与笔记、暂停/继续/保存、历史回放/报告/导出、在海图预览。 |
| 守锚 | 下锚、选点和半径、值守、近期渐隐轨迹、累计停留区域、起锚与收藏。 |
| 我的航行 | 坐标、航线、收藏锚地、集合、GPX、预览与导航。 |
| 驾驶台 | 航行/帆航空间图、真实读数驱动的 3D 姿态与水平参考、方向/速度/深度/摆幅/气象/累计量分类历史、持久气压回看、自选仪表与排序。 |
| AIS | 横滑雷达、三维与船舶列表；相机式量程、就地拖开目标详情、关注与交通警戒；位置和航迹明确进入海图查看。 |
| 数据中心 | “我的船”呈现真实船体/设备安装与可选热点；逐项比较手机与 NMEA 候选，统一选源、手机定位和校准。 |
| 船联网 | 多连接、报文与流量、发送目的地、按能力发送系统读数或转发指定输入。 |
| 数据共享 | 本机监听服务、实际连接客户端、选择发布内容与转发输入；采用数据中心选定的读数。 |
| 设置 | 船名与船舶资料、语言、单位、坐标格式、主题色、文字大小、常亮、声音、权限、资料备份。 |

磁贴工坊是 Shell 工具：按应用提供可固定内容，共用临时编辑器；同一内容绑定去重，同一应用可以提供不同的内容磁贴。切换样式保留位置，实时读数与应用使用同一来源。海图图像快照显示捕获时间，船位和速度独立实时更新。

图册数据与离线自动规划的实际链路、支持格式和限制见 [海图契约](docs/product/CHART_INTERACTION_CONTRACT.md)。当前没有潮汐/天气情景；S-63/NZ 加密 ENC 尚缺少获许可客户端和设备许可条件。

## 一套系统

- 普通应用入口打开新的首页访问；最近任务恢复原页面实例。明确的跨应用对象操作使用调用关系，Back 恢复调用者的原页、选择和草稿；工具和内部子页优先返回。任务卡使用真实窗口截图，冷启动才显示应用启动画面。中间 Home 回桌面并结束当前跨应用调用链。
- 坐标、测距、航线、锚圈在地图引擎内原生绘制，与海图同帧缩放。准星坐标紧挨底部命令，比例尺使用整数刻度并避开操作区。
- 海图、锚警、坐标预览和日志回放共享图源与地图组件，各自保留视野。选择内置离线、卫星或任一自定义文件夹图层，另选结构化数据集用于对象查询和规划。
- 自定义海图内置离线全球陆地/海岸参考底图，不依赖首次下载。它不包含水深或助航资料，不能替代用户海图。[数据说明](docs/product/OFFLINE_WORLD_BASEMAP.md)。
- 海图、日志、系统栏和磁贴读取同一个航行会话。记录、导航、锚警彼此独立，关闭页面不停止后台工作。
- 数据中心统一船位与逐项读数来源，展示手机、NMEA 和派生候选及采纳结果；手机定位启停与安装校准也在这里。继续使用原 `VesselSettingsRepository.metricSourcePins` 与船位 `GpsDataSource / POSITION_CONNECTION`，不创建另一套来源配置。选中来源失效不会偷偷切换；GPS 短时未更新只显示时效，不反复遮挡操作。
- 本机分享与主动发送共用发布规则；按能力筛选实际报文，保留来源与连接代次，禁止给相同 IP 回送其输入数据。
- 轻提示先出现在系统栏，底部右键（原搜索键）打开通知中心，中间 Home 始终返回开始屏幕。通知可点按查看或横向滑动清除；已读、移除与业务警报确认分开。系统通知目的地已是当前页时只收起中心；已有目标页和返回关系优先复用。顶部下滑完全交还 Android 系统。
- 通知中心统一四个入口：夜间显示、屏幕常亮、船位来源、船舶连接；更多另列，守锚/记录/AIS 单独显示真实任务。面板上滑直接跟手，历史可见后才已读，清除不改变活动警报；通知交接返回中心原位置，再回原应用。显示偏好与任务结果均读原所有者，不创建第二份开关状态。
- 页面标题、分组、正文与辅助文字保持层级，正文和选择行更紧凑；圆形单选、矩形输入框与横滑仍统一。全局单位、坐标格式、文字大小在应用和磁贴同步。顶部按圆角在当前高度的截面横向避让，并处理挖孔；背景保持全屏。
- 声纳测深调查的界面、采样与自动恢复已移除；既有数据库保留，真实 NMEA 水深仍可作为仪表和条件警戒输入。

## 构建和密钥

```bash
./gradlew :app-shell:assembleStandaloneDebug
# app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
```

Java 17、Android SDK 36、Python 3；Android 9 及以上；包名 `com.yokuli.marine`。原 Gradle wrapper、签名参数和 CI 下载制品继续使用。所有 APK flavor 的 `preBuild` 会执行源码边界检查；也可单独运行 `python3 scripts/check_runtime_boundaries.py` 或 `./gradlew :app-shell:checkRuntimeBoundaries`。

Google 高清卫星混合地图（含地名与道路）的 `GOOGLE_MAPS_ANDROID_API_KEY` 仍由原仓库的密钥管理注入。普通地图始终读取 APK 内置离线数据，不需要 Key。GitHub 使用同名 Repository Secret；本地使用：

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew :app-shell:assembleStandaloneDebug
```

密钥不写入源码，见 [密钥管理](docs/SECRETS_MANAGEMENT.md)。无 Google Key 时可使用用户本地海图与内置全球地图，包括海岸、湖泊、主要河流、道路及中英文地名；无首次下载或在线字体依赖。高清卫星仍需要网络与有效 Key，不能将内置概略地图当作港湾级航海图。

## 体验重点与边界

按海图选点→标记预览→详情→我的航行首页、下锚→值守→起锚、记录→时刻→暂停→保存→地图回放走完整故事；再检查多连接停止隔离、输出能力选择和全局偏好。

当前支持栅格 PNG/JPEG/WebP MBTiles；S57/S63、PBF、GeoTIFF、PMTiles 未接入。导航为用户手动规划与几何引导。锚泊色块表示实际记录到的停留范围，不是声纳或安全水域推断。

任务截图与趋势有各自的进程内保留周期。资料备份覆盖原航行、锚地、照片和船舶资料；不宣称是包含所有 Shell/连接/文件夹授权的一键整机备份。新坐标和路线可 GPX 导出；GPX 导入支持坐标和计划航线，含历史轨迹的文件整份拒绝，不隐式转成航线。收藏保存等待磁盘回执，失败时在通知中心重试；重试不会重复创建对象。原资料无法读取时保护原文件并阻止覆写，真正重读成功后保留完整恢复副本、合并本次改动，副本可导出。

真实船网、GNSS、报警声、厂商后台和曲面屏仍需真机体验；构建和模拟器检查不替代这些条件。字体使用 Microsoft 开源 Selawik（许可证随包附带），中文采用平台 CJK 回退，不宣称完全等同于 Segoe WP。

## English

The current UI baseline is Windows 10 Mobile / MDL2, using contemporary Microsoft guidance and shared production controls. WP8 references remain available as historical context; they no longer override the current design.

The build version comes from the root Gradle configuration. Current production code includes durable content save receipts, correlated anchor and voyage requests, visit-aware Shell navigation, a shared AIS service and a notification centre with direct manipulation. A real versioned Binder client now connects to the same APK's `:notifications` process, which exclusively owns internal message history. Marine operations remain in the default process with legacy DTOs and the MainUiState compatibility projection. Same-UID message separation is not a security sandbox; full Marine Core IPC and Android notification/Recents replacement remain unimplemented. Necessary compilation does not establish device or offshore operation.

The centre key remains Home and the right key opens internal notifications. Data Center owns source selection and phone calibration; Boat Network owns connections and outgoing traffic; Data Sharing owns local publishing. Linked app operations return to their caller, ordinary launches open the app home, and internal Recents resumes an existing page. See the OS documentation index for the implemented boundaries and later system integration stages.

See the linked interface topology, contracts and delivery record for implementation and validation boundaries. The original build, Android identity and API-key management remain in use.
