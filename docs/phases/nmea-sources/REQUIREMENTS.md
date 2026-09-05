# NMEA_SOURCES 产品合同 / NMEA_SOURCES Product Contract

## 中文（主文）

本 Phase 在现有 Yokuli OS 中交付两个独立的 Shell 内部应用，不是给旧 Anchor Alarm 增加两个页面，也不是两个 APK：

1. **NMEA 输入**只负责配置和运行 NMEA 0183 TCP client／UDP listener，并诚实区分已启用、传输、字节、合法语句、有效数据和错误状态。
2. **数据来源**展示 OS 实际收到的语句、数据和全部候选，并按数据语义保存“Yokuli OS 采用谁”的决定；它不编辑连接。

两者共享由进程拥有的 marine-data runtime，但只能通过类型化端口、不可变快照、`StateFlow` 和明确 action 协作。Feature 不读取另一个 Feature 的 ViewModel／Composable／可变状态，不建立全局 EventBus 或 service locator。`app-shell` 仍是唯一 composition root。

本合同由用户提供的 `Yokuli_OS_NMEA_Input_and_Data_Sources_Codex_Implementation_Spec.md`（SHA-256 `a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348`）和用户直接指令共同产生。它覆盖 README、旧 Stage 报告与 Map C00–C12 中“生产只允许 Chart + Settings”及“禁止生产 NMEA/GNSS”的冲突条款，但不改写历史证据。旧仓库 `codex/develop` 只作为只读技术参考；旧 Anchor／Trip／Sonar 业务、UI 和单连接组织不进入本 Phase。

以下最新 Shell 产品边界继续生效：

- Yokuli OS 是普通沉浸式全屏 Android 应用，不注册 Android HOME／DEFAULT，也不打开 Android 桌面设置。
- `ShellActivity` 默认仅竖屏；方屏仍适配，横屏不属于产品能力。
- Back 的终点是应用内 Shell 桌面；已经在桌面时无副作用，不退出应用。
- 两个新应用各通过一个 `InstalledAppBinding` 单点安装到 All Apps，可固定、移动、切换 1×1／2×2／4×2、取消固定；升级不自动改变现有 Start Document。

本轮只实现 NMEA 0183 TCP 输入、UDP 监听、真实手机系统定位、统一来源目录和选源。不实现 NMEA 输出／转发、NMEA 2000 原生总线、USB、Bluetooth、Signal K、mock location、AIS、Anchor、Trip、Survey、活动导航、自动舵或船网控制。

## English translation

This phase delivers two independent in-Shell apps inside the existing Yokuli OS, not two APKs and not two pages added to the old Anchor Alarm. NMEA Input owns real NMEA 0183 TCP-client and UDP-listener configuration and runtime truth. Data Sources owns discovery and per-data-semantic source decisions for the whole Yokuli OS. They share a process-owned marine-data runtime only through typed ports, immutable snapshots, `StateFlow`, and explicit actions; features never share mutable UI state or use a global event bus.

The hash-bound owner specification and direct owner instructions supersede conflicting old “Chart + Settings only” and “no production NMEA/GNSS” clauses while preserving historical reports unchanged. The latest Shell boundary remains active: this is a portrait-only immersive regular Android app, not an Android Home replacement; Back stops at the in-app Shell Desktop; both new apps install once through `InstalledAppBinding`, remain absent from the default Start document, and use the existing three tile sizes.
