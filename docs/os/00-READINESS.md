# 00 · Yokuli OS 的当前能力与 ROM 起点

日期：2026-09-18。应用基线：`b5fc247`，`codex/yokuli-os-rebuild`，experience.6。本目录服务于独立的 `codex/yokuli-os-rom` 分支；不把 ROM 实验倒灌为普通 APK 的默认行为。

## 结论

**现在已经有航海专用 OS 的产品骨架，足以开始做 Android 发行版原型；还没有独立 OS 的运行保障、应用隔离和设备交付能力。** 继续堆应用页面不会补齐这些缺口。第一步应当是能在虚拟设备启动的 AOSP 产品，把已验证的 WP 交互作为 HOME，再逐项取得后台运行、权限、恢复、升级和硬件支持的证据。

这里的 OS 定位为 **面向个人船主的 Android 航海发行版**：继承 Linux、Android HAL、应用沙箱和系统更新能力，拥有统一的航海数据与会话服务、应用组织和 WP 视觉交互。它不意味着另写内核，不意味着把每个应用拆成守护进程，也不意味着预装一个 APK 就完成 OS。

## 已有能力：以源码为依据

| 层面 | 当前事实 | 可以复用什么 | 仍然缺什么 |
| --- | --- | --- | --- |
| 开始屏幕与应用组织 | `WpShellRuntime`、`LauncherEngine`、任务快照、磁贴、通知中心、按键与跨应用调用链 | WP 交互、内部应用身份、页面实例模型 | 现有“应用”多数是同 APK 内 Compose 页面；不是 Android 包、UID 或系统任务 |
| 海图与图册 | `ChartLibrary`、`MapSessionStore`、原生地图覆盖物、文件夹图层和统一视口协议 | 地图操作、文件权限与渲染顺序、离线底图能力 | 纯 AOSP 的所有地图入口、外置介质挂载/撤销、长期存储压力仍需设备验证 |
| 数据中心 | `VesselSourceRegistry`、`VesselDataHub`、逐字段来源策略、来源/新鲜度/质量 | 一份选源策略与可信读模型；手机和 NMEA 都是候选 | 尚无跨 UID 服务、消费方权限、协议版本和跨进程背压契约 |
| 船联网与数据共享 | 多 TCP/UDP 连接、本机 TCP listener、能力筛选、来源代次、防同 IP 回送 | 输入、选源、输出已经具有不同职责 | 连接发现、安全配置、外部应用授权、设备级网络恢复仍需整合 |
| 航行和守锚 | `TripRuntime`、`AnchorWatchRuntime`、Room 会话、资源持有者、前台服务 | 明确暂停/继续/结束、下锚/暂停/起锚、真实事件与轨迹 | 进程仍与 UI 同属一个故障域；恢复策略存在下述缺口 |
| 用户偏好 | DataStore、Shell preference、船舶偏好、手机安装校准 | 中文/英文、单位、坐标、显示偏好 | 还不是 Android 全局设置；亮度、网络、设备管理仍由 Android 提供 |
| 通知 | `SystemNotificationStore` 与 `MarineNoticeBridge`；Android 前台/警报通知另有协调器 | 消费通知不等于确认警报，后台事件桥接 | 当前中心只管理 Yokuli 内部消息；没有系统通知代理/Android 快捷设置集成 |
| 发布与设备 | APK 构建、普通 Android 安装 | 构建版本、包名、已有密钥注入约定 | 无已验证 AOSP 镜像、板级支持包、刷写回滚、OTA 通道、发布签名体系 |

对应源码入口：

- [当前应用组合与状态](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt)、[Shell 运行时](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt)、[业务适配](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarineRuntime.kt)。
- [数据模型](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt)、[数据中心现有契约](../product/DATA_CENTER_CONTRACT.md)。
- [后台协调器](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt)、[前台服务](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt)、[资源持有者](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt)。
- [当前应用与全部源码接口索引](../product/OS_INTERFACE_TOPOLOGY.md)、[API_INDEX](../product/API_INDEX.md)。接口索引是现状，后续章节的 IPC 是设计。

## 不能被“已预装”掩盖的缺口

1. `AnchorForegroundService` 没有单独 `android:process`，`onBind()` 返回 null；并非已经存在的 Binder 服务。`MainViewModel → MarineRuntime → OsStore` 仍在同一进程，UI 崩溃会同时影响后台。
2. `BootRestoreReceiver` 会暂停活动守锚并记录重启中断；它没有同样暂停活动航行。`TripRuntime.restore()` 对未暂停会话会重新申请资源并启动记录。ROM 不能因此宣称“全部业务冷启动都等待用户确认”。
3. `OsStore` 会从 JSON 恢复 `activeRouteId / navigationSnapshot / routeLeg`。这是现有行为；拟议的“重启后导航恢复为待确认”尚未落地。
4. NMEA 连接与本机服务已有本次开机租约，但锚警恢复仍有独立的安全连接恢复路径。需要统一恢复判断入口，不能只检查连接配置文件。
5. 历史代码仍含声纳类型、数据库与恢复入口。当前产品没有声纳入口，不代表底层所有兼容代码已删除；R0 不启用或扩充该产品，清理不能破坏旧数据迁移。
6. 当前手机 GNSS 使用 `LocationManager.GPS_PROVIDER`，有纯 Android 路径；Google Maps 与旧依赖里的 Play Services 不能被认为是纯 AOSP 的组成部分。无 GMS 时须验证原生离线/MapLibre 路径、GPS 与全部启动流程，不允许为消除错误而捆绑未知授权的 GMS 包。

## 分阶段交付定义

阶段编号与完成状态以 [08 · 交付顺序与验收门槛](08-ROADMAP-AND-ACCEPTANCE.md) 为唯一总表；下表补充各阶段的能力边界。

| 阶段 | 交付内容 | 进入下一阶段的条件 |
| --- | --- | --- |
| R0 · ROM 开发原型 | 固定 AOSP 16 基线、Cuttlefish 产品配置、`rom` HOME APK 预置为普通 system app；替换 Launcher3，保留 SystemUI、Android Settings、权限弹窗与恢复入口 | Linux/KVM 构建成功；冷启动/解锁进入 Yokuli HOME；无 GMS 可启动、离线地图可用；权限拒绝不崩溃；有镜像与版本清单 |
| R1 · 独立航海运行时 | 从 Activity/ViewModel 分离运行时；受权的 Binder 客户端；统一租约/恢复决策；幂等命令与可追溯快照 | 杀 UI 不停止已授权守锚；强停/重启/权限撤销都有明确状态；没有新授权的自动选源或输出；故障注入证据可复现 |
| R2 · 系统交互 | Android 通知、应用任务、安装权限和偏好的真实系统集成 | Android Task 与内部故事栈分开；通知授权和清除一致；WP 交互、设置与救援入口可达 |
| R3 · 第一台真机 | 确定设备及内核/vendor 支持；GNSS、传感器、音频、供电、屏幕与网络专项适配 | 可恢复刷写；稳定 GNSS 与音频告警；长时间屏灭运行、温升/耗电/断电恢复通过；硬件能力矩阵没有伪支持 |
| R4 · 可更新设备 | 正式签名、AVB、OTA、升级/回滚/备份、许可清单与设备支持期限 | 升级前后数据完整；能阻止活动守锚时计划重启；补丁/撤回演练完成；用户可恢复设备 |
| R5 · 外部 SDK | 对第三方提供版本化、受权、可撤销的航海数据能力 | SDK 兼容、权限撤销、限流及慢客户端隔离经过验证；没有公开全部内部运行时 |

R0 不是“可刷到任意手机”的通用 ROM。Cuttlefish 是开发用虚拟 Android 设备，能验证系统组合与框架行为；它不能证明海上 GNSS、磁罗经、断电告警或具体设备的驱动可用。[AOSP Cuttlefish](https://source.android.com/docs/setup/create/cuttlefish)

R0 固定 `android-16.0.0_r4`（`BP4A.251205.006`，2025-12-05 补丁）作为与 compileSdk 36 对齐的可复现验证基线；它不是“当前最新”或可直接出货的安全版本。投产前必须重新评估补丁基线。产品目标 `yokuli_cf_x86_64_phone-trunk_staging-userdebug` 继承 Cuttlefish x86_64 phone；`userdebug` 只供开发验证。

## 本次交付的证据边界

本机为 macOS，当前没有可用的 AOSP Linux/KVM 构建环境，磁盘余量也不足以同步并构建完整系统。本分支可交付架构、协议、产品配置、构建入口以及 HOME APK 的本地验证；**未完成 AOSP 系统编译和 Cuttlefish 启动，就不得把它称为已交付的可运行 ROM 镜像**。实际执行状态以本目录构建记录为准，不用 APK 的成功替代系统镜像成功。

Android 的内核、HAL、系统服务、API 与应用属于不同层；预置 app 不会自动变成系统服务，系统 API/特权也需要相应集成。[AOSP 架构](https://source.android.com/docs/core/architecture)

下一步读 [01 架构](01-ARCHITECTURE.md)、[02 领域与协议](02-DOMAIN-AND-CONTRACTS.md)、[03 生命周期与恢复](03-LIFECYCLE-AND-RECOVERY.md)。
