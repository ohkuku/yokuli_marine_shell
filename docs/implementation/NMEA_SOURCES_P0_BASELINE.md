# NMEA_SOURCES P0 实施基线 / P0 Implementation Baseline

状态：`BASELINE_RECORDED`。此文件记录开始施工前的事实与决定；构建证据在 P0 Gate 完成后追加，不预先声称通过。

## 1. 真实工作树与覆盖决定

| 项目 | 已核实事实 |
|---|---|
| Yokuli OS | `/Users/kuku/Documents/ChatGPT/yokuli_marine_shell`，`codex/shell-map-contract`，起始 SHA `69bfd4d0ed29f27450351df530b4a8b1e8e2c6a6`，开始时 clean |
| 旧技术参考 | `/Users/kuku/Documents/ChatGPT/yokuli_nmea_anchor_system`，remote `ohkuku/yokuli_nmea_anchor_alarm`，`codex/develop`，SHA `a845d3d734d3b573a2b53952e66e5f800e944205`，保持只读技术参考（read-only technical reference） |
| 用户规格 | `Yokuli_OS_NMEA_Input_and_Data_Sources_Codex_Implementation_Spec.md`，SHA-256 `a5a38f08f8606d230952dcec8e8f521615efc9ec1a4d30ab4a3821f5943b3348` |
| Android | `compileSdk 36`、`targetSdk 36`、`minSdk 26`、Java/Kotlin JVM 17 |
| Release shape | 只有 `standalone` flavor；默认竖屏；没有 Android HOME／DEFAULT |

附件中的过期事实明确覆盖如下：

- `codex/chart-first-foundation` 不是当前目标分支；不 checkout/reset 到它。
- 旧仓库的 `main` 不用于参考；用户直接指定的 `codex/develop` 优先。
- `assembleHomeDebug` 在当前工程不存在；真实等价基线是 `assembleStandaloneDebug` 和 `assembleStandaloneRelease`。
- 旧文档“只允许 Chart + Settings／禁止生产 NMEA/GNSS”被新的 `NMEA_SOURCES` Phase 覆盖，但旧报告不改写历史。
- 最新直接要求继续有效：不是 Android launcher、默认仅竖屏、Back 最多回到 Shell 桌面且不退出。

## 2. Yokuli OS 真实接入点

| 责任 | 当前真实符号／路径 | 本轮决定 |
|---|---|---|
| 单点安装 App | `ui/shell-compose/.../InstalledAppBinding.kt`、`InstalledAppBindingRegistry` | 两个 Feature 各注册一次，由同一 binding 派生 catalog、launch token、visual 与 internal host |
| 生产组合根 | `app-shell/.../ProductionShellGraph.kt` | 只在 P5 添加两个 binding；默认 Start Document 保持 Chart + Settings，不自动固定新 App |
| 进程所有者 | `app-shell/.../ShellApplication.kt` | 持有共享 marine-data runtime；Feature 页面关闭不停止连接 |
| Shell 状态／导航 | `app-shell/.../ShellViewModel.kt`、`ShellActivity.kt` | 只接类型化状态与 action；保持串行 Engine、竖屏和 bounded Back |
| 顶部状态条 | `feature/desktop/.../WpStatusStrip.kt` | P5 新增中立 status contribution，不让 desktop import NMEA 领域类型；两个入口分别导航 |
| 只读地图消费者 | `core/map-domain/.../PositionObservation.kt` 的 `ReadOnlyPositionPort` | P6 只证明消费统一结果；不扩造假地图能力 |
| Feature 范式 | Chart／Settings 的 `ShellContribution`、`LauncherPresentation`、`UiState/UiAction` | 新 App 自己拥有 UI 和 projector；不得互相依赖 |
| 当前持久化 | Shell DataStore、Map Room adapters | marine-data 新建自己的版本化存储边界，不污染 Shell document 或 Map schema |

当前生产目录只有 Chart + Settings 是历史基线，不是新 Phase 的最终产品面。P5 Gate 将把 All Apps 精确改为 Chart、Settings、NMEA Input、Data Sources 四项；两个新 App 不自动出现在现有或全新 Start Document。

## 3. 旧仓真实符号审计

### 3.1 可抽取语义与测试素材

| 语义 | 旧路径／符号 | 证据／处理 |
|---|---|---|
| checksum 与 TCP incremental framing | `data/nmea/NmeaCore.kt`：`NmeaChecksum`、`NmeaStreamSplitter` | 保留算法思想；在纯模块重写 typed result、上限与错误计数；参考 `NmeaParserTest` 的 checksum、半包、粘包和随机 fragmentation |
| 句型字段 | `NmeaCore.kt`：`Nmea0183Parser` | 抽取 RMC/GGA/GLL/VTG/ZDA/HDG/HDM/HDT/DPT/DBT/MWD/MWV 字段索引和单位换算，逐项补 domain validation；不得整文件复制 |
| 空字段与显式 invalid | `NmeaFieldRepository.kt`、`NmeaSourceInvalidation.kt` | 保留“blank 不更新、invalid 只影响对应数据”的语义；以注入的单调 `MapClock` 重写 |
| 不可变当前事实 | `data/vessel/VesselDataHub.kt` 的 `StateFlow` snapshot | 保留迟到订阅者获得完整快照的模式；去除旧 Anchor/Trip 所有权 |
| 来源候选有界 | `data/vessel/VesselSourceRegistry.kt` | 保留有界 catalog 思想；重写 identity、容量 256、选择保护和确定性句型规则 |
| 测试 sender | `testsupport/FakeNmeaEndpoints.kt`／`FakeNmeaInputServer` | 扩成 TCP/UDP、多 sender、慢流、半包、坏帧、静默、恢复与负载工具，只存在 test/debug |
| 诊断隐私 | `data/diagnostics/IncidentDiagnostics.kt` | 保留不记录 raw NMEA、精确位置和 credential 的原则；不迁移 Boat Watch Room schema |

### 3.2 必须重写

- `NmeaConnectionManager` 是单连接模型；P2 重写为每个 `ConnectionId + SessionGeneration` 独立 actor 和 socket。
- 旧 `NmeaStreamSplitter` 被 UDP 共用，会跨 UDP sender 拼接残片且丢失 `DatagramPacket.address/port`；UDP 必须按单个 datagram 和 sender 独立解析，绝不跨包续接。
- 旧 `_lines: Flow<String>` 不携带 generation，可能错误归因迟到 RX；新 transport event 自生成时就携带连接、代际、origin 和 monotonic receive time。
- 旧 parser 用 nullable `NmeaUpdate` 混合未知、畸形和 invalid；新合同分为合法已知、合法未支持、checksum failure、malformed 与 explicit invalid。
- 旧 `NmeaCandidateMapper` 把同连接 RMC/GGA/GLL 做成多来源；新 catalog 按 origin 聚合互补句型，并以稳定、可解释的句型规则解决重叠字段。
- 旧 MWV 以 `IIMWV` retention key 合并 R/T，DPT 又可能把未应用 offset 的值标成 surface/keel reference；新 `DataKey` 必须区分 apparent/true wind、true/magnetic heading 和各 depth reference。
- 旧 freshness 可被 blank heartbeat 无限续命；新规则固定为 `<3s LIVE`、`>=3s HELD`、`>=10s STALE`，没有新包也由 timer 老化。
- 新目录上限 256 keys；frame 最大 1024 bytes；raw preview 同时受 200 条和 256 KiB 限制，overflow 可观察。

### 3.3 明确不迁移

- 不迁移 `NavigationRepository`、`runtime/nmea/NmeaRuntime` 的巨型聚合与 Anchor/Sonar/Trip owner 逻辑。
- 不迁移 Anchor 的 `GpsDataSource`／`NewAnchorPositionSourcePolicy`，也不迁移 Trip/Vessel 的 AUTO selector；不恢复两套 GPS/仪表选源。
- 不迁移 `MainViewModel`、`ui/data/DataScreen.kt`、单 `ConnectionProfile` 设置和旧业务文案。
- 不迁移 Anchor 业务与运行时。
- 不迁移 Trip 业务与运行时。
- 不迁移 Sonar、LiveDepth 或 LiveWind。
- 不迁移 NMEA 输出、GPS proxy 或 sharing server。
- 不迁移 `NmeaEndpointPreflight` 的一次性 disposable TCP 探测；真实运行状态只能来自正式 runtime。

## 4. 实际合同与最小模块变化

| 规格责任 | 实际合同／模块 |
|---|---|
| 纯 NMEA、catalog、freshness、selection、resolved snapshot | 新建纯 Kotlin `core:marine-data`；不得依赖 Android、Compose、Feature、socket 或 Map UI |
| socket、手机系统定位、DataStore、FGS／通知 | 新建 Android library `adapter:marine-data-android`，只实现 core ports |
| NMEA 配置和状态 UI | 新建 `feature:nmea-input`，只依赖 core contracts、design、shell contract／compose；通过 action port 写 |
| 目录和选源 UI | 新建 `feature:data-sources`，依赖同一 core contracts；不依赖 `feature:nmea-input`，受控深链使用 opaque Shell token |
| 安装、生命周期、状态条 wiring | `app-shell` composition root + `InstalledAppBinding`; desktop 仅看中立 status presentation |
| Chart 只读位置桥 | adapter 将统一 `ResolvedData` 映射到现有 `ReadOnlyPositionPort`；Chart 不拥有 provider 或 source policy |

依赖方向固定为：

```text
core:marine-data
  ↑ ports implemented by adapter:marine-data-android
  ↑ read/actions consumed by feature:nmea-input and feature:data-sources
  ↑ composed only by app-shell

feature:nmea-input  ─X─> feature:data-sources
feature:data-sources ─X─> feature:nmea-input
feature:desktop      ─X─> NMEA domain types
```

没有全局 EventBus、没有任意模块 service locator、没有 Feature 间可变状态共享。`StateFlow` 只承载不可变当前事实；一次性失败／导航 effect 由拥有它的 Feature 明确消费。

## 5. Android 36 后台与权限决定

当前 app 是 targetSdk 36 的普通 Android 应用，没有系统特权。因此：

1. TCP／UDP 活跃输入使用 foreground service 的 `connectedDevice` 类型，manifest 申明 `FOREGROUND_SERVICE` 与 `FOREGROUND_SERVICE_CONNECTED_DEVICE`，并满足该类型的网络运行时前提；通知提供真实停止路径。
2. 手机系统定位只有在用户从前台可见状态明确启用后才请求 `ACCESS_COARSE_LOCATION`／`ACCESS_FINE_LOCATION`。定位采集使用 `location` FGS 类型及 `FOREGROUND_SERVICE_LOCATION`；粗略授权、精确授权、拒绝、永久拒绝、系统定位关闭和运行中撤权分别建模。
3. 首版不预先索取 `ACCESS_BACKGROUND_LOCATION`，也不声称能从任意后台状态启动 FGS。平台拒绝恢复时发布“待用户恢复”，不显示正在接收。
4. Activity／Feature 关闭不等于用户停止；运行意图由 process runtime 持久拥有。Android 强制停止后的自动恢复不能保证，重新由用户启动后只恢复配置／选择，不复活实时值。
5. emulator 可验证权限流、socket、重建与进程恢复语义；锁屏／熄屏、OEM 限制、真实手机 GNSS/融合定位和实机网络切换保持 `UNVERIFIED_PHYSICAL_DEVICE`，不得冒充通过。

平台依据：Android 官方 foreground service 启动限制、服务类型声明、服务类型和位置权限文档。P2/P3 实现后再以 merged manifest、API 34/36 emulator 和物理设备日志验证实际行为。

## 6. P0 Gate

已完成：

- 找到两个仓库的真实 branch、commit、dirty state 和目标 SDK。
- 找到当前 `InstalledAppBinding`、production composition root、status strip、进程 owner、只读 Chart port 和测试门禁。
- 定点审计旧 parser、TCP/UDP、freshness、source、diagnostics 与测试，列出可抽取、必须重写和不迁移项。
- 明确旧仓保持只读，没有 checkout/reset/clean，也没有碰用户未提交内容。
- 锁定单一 OS source selection；不恢复 Anchor GPS manager 加 Vessel/Instrument source manager。

待 P0 封口命令：

```text
python3 .github/scripts/test_nmea_sources_p0_contract.py
python3 -m unittest discover .github/scripts 'test_*.py'
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleStandaloneRelease
git diff --check
```

## English translation

P0 binds the implementation to the real clean Shell baseline `codex/shell-map-contract@69bfd4d…`, the owner-selected read-only reference `codex/develop@a845d3d…`, and the supplied specification hash. Stale branch, main-branch, Home-flavor, landscape, Android-Home and exiting-Back assumptions are explicitly rejected.

The old repository supplies protocol semantics and test ideas, not the new architecture. Its single-connection runtime, cross-sender UDP framing, untyped generation-free line flow, parallel source policies, stale heartbeat retention and Anchor/Trip ownership must not migrate. A pure `core:marine-data`, Android `adapter:marine-data-android`, two independent feature modules and the existing `InstalledAppBinding` composition root form the minimum implementation boundary. Android 36 background behavior remains constrained by foreground-service and permission rules, with physical-device claims kept unverified until real evidence exists.
