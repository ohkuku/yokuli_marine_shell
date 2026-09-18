# 生产接口与结构声明索引

共 3240 项类型与方法声明；按源文件排序。

由 `python3 scripts/export_api_index.py` 从当前源码生成。包含活动重制应用、Shell 合同和所复用的业务领域/存储/运行时。遗留类中的保留 API 不代表其 UI 或功能仍启用；例如声纳历史类型仅为读取已有数据库而保留。

本索引是源码定位工具，包含类型与方法声明，并非所有声明都是跨应用公共 API。完整参数、中文业务语义、公开边界和生命周期见 [总拓扑](OS_INTERFACE_TOPOLOGY.md) 及链接源码。显式 private 声明、旧 UI 和 Gradle 依赖 API 不在本索引范围。

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LauncherProtoMapper` | [LauncherProtoMapper.kt:21](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L21) |
| `fun encode` | [LauncherProtoMapper.kt:22](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L22) |
| `fun decode` | [LauncherProtoMapper.kt:44](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L44) |
| `fun emptyDefaults` | [LauncherProtoMapper.kt:135](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L135) |

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherStateSerializer` | [LauncherStateSerializer.kt:10](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L10) |
| `fun readFrom` | [LauncherStateSerializer.kt:13](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L13) |
| `fun writeTo` | [LauncherStateSerializer.kt:19](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L19) |

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun load` | [ProtoDataStoreLauncherPersistence.kt:60](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L60) |
| `fun save` | [ProtoDataStoreLauncherPersistence.kt:65](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L65) |
| `fun saveDocument` | [ProtoDataStoreLauncherPersistence.kt:73](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L73) |
| `fun savePreferences` | [ProtoDataStoreLauncherPersistence.kt:77](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L77) |
| `fun updatePreferences` | [ProtoDataStoreLauncherPersistence.kt:88](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L88) |
| `fun beginLaunch` | [ProtoDataStoreLauncherPersistence.kt:102](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L102) |
| `fun markLaunchHealthy` | [ProtoDataStoreLauncherPersistence.kt:113](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L113) |
| `fun reset` | [ProtoDataStoreLauncherPersistence.kt:117](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L117) |
| `fun create` | [ProtoDataStoreLauncherPersistence.kt:148](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L148) |
| `fun create` | [ProtoDataStoreLauncherPersistence.kt:158](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L158) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt

| 声明 | 实现位置 |
| --- | --- |
| `object Gpx` | [Gpx.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L9) |
| `class Contents` | [Gpx.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L11) |
| `fun read` | [Gpx.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L12) |
| `fun write` | [Gpx.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L49) |
| `fun element` | [Gpx.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L52) |
| `fun point` | [Gpx.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L53) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MainActivity : ComponentActivity` | [MainActivity.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L30) |
| `fun onCreate` | [MainActivity.kt:65](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L65) |
| `fun onNewIntent` | [MainActivity.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L80) |
| `fun onWindowFocusChanged` | [MainActivity.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L92) |
| `fun onResume` | [MainActivity.kt:93](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L93) |
| `fun dispatchKeyEvent` | [MainActivity.kt:95](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L95) |
| `fun onPause` | [MainActivity.kt:109](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L109) |
| `fun onDestroy` | [MainActivity.kt:110](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L110) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarineNoticeBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun OsStore.observeMarineNotices` | [MarineNoticeBridge.kt:7](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarineNoticeBridge.kt#L7) |
| `fun AlarmEventEntity.asNotice` | [MarineNoticeBridge.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarineNoticeBridge.kt#L14) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt

| 声明 | 实现位置 |
| --- | --- |
| `class GeoPoint` | [Model.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L23) |
| `fun json` | [Model.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L24) |
| `fun valid` | [Model.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L25) |
| `fun distance` | [Model.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L28) |
| `fun bearing` | [Model.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L33) |
| `fun coordinates` | [Model.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L37) |
| `fun value` | [Model.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L38) |
| `fun nm` | [Model.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L41) |
| `fun decimal` | [Model.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L42) |
| `fun uid` | [Model.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L43) |
| `class PlaceKind` | [Model.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L45) |
| `class Place` | [Model.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L47) |
| `fun json` | [Model.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L49) |
| `class Route` | [Model.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L55) |
| `fun json` | [Model.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L57) |
| `fun JSONArray.objects` | [Model.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L60) |
| `class TileSpec` | [Model.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L62) |
| `class AnchorDraft` | [Model.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L64) |
| `class ChartInteractionSnapshot` | [Model.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L66) |
| `class AppId` | [Model.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L72) |
| `class YokuliApplication : Application` | [Model.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L87) |
| `fun onCreate` | [Model.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L94) |
| `class OsStore` | [Model.kt:102](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L102) |
| `fun requestPosition` | [Model.kt:156](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L156) |
| `fun connectSystem` | [Model.kt:157](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L157) |
| `fun t` | [Model.kt:182](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L182) |
| `fun title` | [Model.kt:183](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L183) |
| `fun notify` | [Model.kt:184](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L184) |
| `fun open` | [Model.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L189) |
| `fun openLinked` | [Model.kt:191](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L191) |
| `fun openNotification` | [Model.kt:193](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L193) |
| `fun openSystemDestination` | [Model.kt:199](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L199) |
| `fun home` | [Model.kt:203](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L203) |
| `fun back` | [Model.kt:204](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L204) |
| `fun fly` | [Model.kt:205](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L205) |
| `fun captureChartInteraction` | [Model.kt:206](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L206) |
| `fun restoreChartInteraction` | [Model.kt:212](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L212) |
| `fun mark` | [Model.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L224) |
| `fun startRoute` | [Model.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L228) |
| `fun advanceRoute` | [Model.kt:229](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L229) |
| `fun save` | [Model.kt:234](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L234) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun bundle` | [MySailingRepository.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L45) |
| `fun updatePlace` | [MySailingRepository.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L46) |
| `fun updateSpot` | [MySailingRepository.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L47) |
| `fun createSpot` | [MySailingRepository.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L48) |
| `fun archivePlace` | [MySailingRepository.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L49) |
| `fun restorePlace` | [MySailingRepository.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L50) |
| `fun createCollection` | [MySailingRepository.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L51) |
| `fun toggleCollection` | [MySailingRepository.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L52) |
| `fun observeCollectionMembers` | [MySailingRepository.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L53) |
| `fun anchorTrackPage` | [MySailingRepository.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L54) |
| `fun saveAnchorage` | [MySailingRepository.kt:56](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L56) |
| `fun put` | [MySailingRepository.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L58) |
| `fun remove` | [MySailingRepository.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L59) |
| `fun import` | [MySailingRepository.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L60) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NoticeSeverity` | [SystemNotifications.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L13) |
| `class SystemNotice` | [SystemNotifications.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L16) |
| `fun text` | [SystemNotifications.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L21) |
| `fun json` | [SystemNotifications.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L22) |
| `fun from` | [SystemNotifications.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L26) |
| `fun post` | [SystemNotifications.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L92) |
| `fun awaitLoaded` | [SystemNotifications.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L101) |
| `fun acceptAnchorEvent` | [SystemNotifications.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L103) |
| `fun open` | [SystemNotifications.kt:109](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L109) |
| `fun close` | [SystemNotifications.kt:110](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L110) |
| `fun toggle` | [SystemNotifications.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L111) |
| `fun dismissBanner` | [SystemNotifications.kt:112](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L112) |
| `fun remove` | [SystemNotifications.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L113) |
| `fun clearAll` | [SystemNotifications.kt:120](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L120) |
| `fun clearRead` | [SystemNotifications.kt:121](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L121) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskDismissal.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun shouldDismissTask` | [TaskDismissal.kt:4](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskDismissal.kt#L4) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TaskSnapshot` | [TaskSnapshots.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L21) |
| `class TaskSnapshotStore` | [TaskSnapshots.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L24) |
| `fun bind` | [TaskSnapshots.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L30) |
| `fun unbind` | [TaskSnapshots.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L35) |
| `fun retain` | [TaskSnapshots.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L36) |
| `fun captureCurrent` | [TaskSnapshots.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L38) |
| `fun Context.activity` | [TaskSnapshots.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L64) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun OsExperience` | [WpShellExperience.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L55) |
| `fun ShellAppIcon` | [WpShellExperience.kt:295](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L295) |
| `fun getResources` | [WpShellExperience.kt:355](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L355) |
| `fun onChange` | [WpShellExperience.kt:386](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L386) |
| `object Launcher : ShellMotionTarget` | [WpShellExperience.kt:397](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L397) |
| `object Search : ShellMotionTarget` | [WpShellExperience.kt:398](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L398) |
| `object Recents : ShellMotionTarget` | [WpShellExperience.kt:399](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L399) |
| `class App` | [WpShellExperience.kt:400](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L400) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun pageForToken` | [WpShellRuntime.kt:149](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L149) |
| `fun updateSystemPreferences` | [WpShellRuntime.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L163) |
| `fun canonicalPage` | [WpShellRuntime.kt:168](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L168) |
| `fun appForPage` | [WpShellRuntime.kt:180](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L180) |
| `fun openLinked` | [WpShellRuntime.kt:191](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L191) |
| `fun reportVisibleRoute` | [WpShellRuntime.kt:194](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L194) |
| `fun openSystemDestination` | [WpShellRuntime.kt:198](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L198) |
| `fun open` | [WpShellRuntime.kt:223](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L223) |
| `fun dispatch` | [WpShellRuntime.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L238) |
| `fun back` | [WpShellRuntime.kt:277](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L277) |
| `fun popRoute` | [WpShellRuntime.kt:285](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L285) |
| `fun home` | [WpShellRuntime.kt:286](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L286) |
| `fun input` | [WpShellRuntime.kt:287](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L287) |
| `fun resetStart` | [WpShellRuntime.kt:303](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L303) |
| `class ShellApp` | [WpShellRuntime.kt:328](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L328) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun add` | [AnchorSwingCoverage.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt#L25) |
| `fun areas` | [AnchorSwingCoverage.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt#L50) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartFile` | [ChartLibrary.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L34) |
| `fun json` | [ChartLibrary.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L42) |
| `fun from` | [ChartLibrary.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L48) |
| `class ChartReader` | [ChartLibrary.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L62) |
| `fun inspect` | [ChartLibrary.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L81) |
| `fun tile` | [ChartLibrary.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L113) |
| `fun coverageZoom` | [ChartLibrary.kt:120](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L120) |
| `fun raster` | [ChartLibrary.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L130) |
| `fun close` | [ChartLibrary.kt:148](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L148) |
| `fun target` | [ChartLibrary.kt:160](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L160) |
| `fun release` | [ChartLibrary.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L167) |
| `fun prune` | [ChartLibrary.kt:172](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L172) |
| `fun copy` | [ChartLibrary.kt:181](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L181) |
| `class ChartFolder` | [ChartLibrary.kt:203](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L203) |
| `fun json` | [ChartLibrary.kt:207](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L207) |
| `fun from` | [ChartLibrary.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L210) |
| `fun linked` | [ChartLibrary.kt:212](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L212) |
| `class ChartLayer` | [ChartLibrary.kt:218](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L218) |
| `fun folderFiles` | [ChartLibrary.kt:246](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L246) |
| `fun errorText` | [ChartLibrary.kt:257](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L257) |
| `fun toggle` | [ChartLibrary.kt:281](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L281) |
| `fun setLayer` | [ChartLibrary.kt:282](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L282) |
| `fun removeLayer` | [ChartLibrary.kt:286](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L286) |
| `fun moveFile` | [ChartLibrary.kt:287](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L287) |
| `fun showOnly` | [ChartLibrary.kt:295](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L295) |
| `fun renameFile` | [ChartLibrary.kt:299](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L299) |
| `fun renameFolder` | [ChartLibrary.kt:303](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L303) |
| `fun includeAll` | [ChartLibrary.kt:307](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L307) |
| `fun forget` | [ChartLibrary.kt:310](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L310) |
| `fun restore` | [ChartLibrary.kt:314](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L314) |
| `fun forgetFolder` | [ChartLibrary.kt:319](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L319) |
| `fun rescan` | [ChartLibrary.kt:320](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L320) |
| `fun linkFolder` | [ChartLibrary.kt:321](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L321) |
| `fun importCopy` | [ChartLibrary.kt:382](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L382) |
| `fun register` | [ChartLibrary.kt:427](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L427) |
| `fun raster` | [ChartLibrary.kt:439](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L439) |
| `fun close` | [ChartLibrary.kt:490](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L490) |
| `class FolderTileProvider` | [ChartLibrary.kt:498](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L498) |
| `fun getTile` | [ChartLibrary.kt:501](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L501) |
| `fun close` | [ChartLibrary.kt:505](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L505) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface ChartCamera` | [ChartSurface.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L50) |
| `fun project` | [ChartSurface.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L51) |
| `fun unproject` | [ChartSurface.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L52) |
| `fun move` | [ChartSurface.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L53) |
| `fun zoom` | [ChartSurface.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L54) |
| `fun fit` | [ChartSurface.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L55) |
| `fun onDraw` | [ChartSurface.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L88) |
| `fun onTouchEvent` | [ChartSurface.kt:115](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L115) |
| `fun destination` | [ChartSurface.kt:137](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L137) |
| `fun dispatchTouchEvent` | [ChartSurface.kt:171](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L171) |
| `fun project` | [ChartSurface.kt:196](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L196) |
| `fun unproject` | [ChartSurface.kt:197](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L197) |
| `fun move` | [ChartSurface.kt:198](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L198) |
| `fun zoom` | [ChartSurface.kt:199](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L199) |
| `fun fit` | [ChartSurface.kt:200](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L200) |
| `fun project` | [ChartSurface.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L228) |
| `fun unproject` | [ChartSurface.kt:229](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L229) |
| `fun move` | [ChartSurface.kt:230](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L230) |
| `fun zoom` | [ChartSurface.kt:231](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L231) |
| `fun fit` | [ChartSurface.kt:232](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L232) |
| `fun updateStyle` | [ChartSurface.kt:248](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L248) |
| `fun captureSnapshot` | [ChartSurface.kt:305](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L305) |
| `fun save` | [ChartSurface.kt:313](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L313) |
| `fun update` | [ChartSurface.kt:327](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L327) |
| `fun lifecycle` | [ChartSurface.kt:336](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L336) |
| `fun destroy` | [ChartSurface.kt:343](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L343) |
| `fun MarineMap` | [ChartSurface.kt:347](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L347) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MapSource` | [MapScene.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L16) |
| `object Online : MapSource` | [MapScene.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L17) |
| `object Satellite : MapSource` | [MapScene.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L18) |
| `class CustomLayer` | [MapScene.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L19) |
| `class MapVessel` | [MapScene.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L23) |
| `class MapPoint` | [MapScene.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L33) |
| `class MapLine` | [MapScene.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L34) |
| `class MapCircle` | [MapScene.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L35) |
| `class MapArea` | [MapScene.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L37) |
| `class MapScene` | [MapScene.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L38) |
| `interface MapEvent` | [MapScene.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L47) |
| `class CameraChanged` | [MapScene.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L48) |
| `class ItemSelected` | [MapScene.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L49) |
| `class PointMoved` | [MapScene.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L50) |
| `class CoordinateSelected` | [MapScene.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L51) |
| `object GestureStarted : MapEvent` | [MapScene.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L52) |
| `class MapCameraRequest` | [MapScene.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L55) |
| `class MapViewState` | [MapScene.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L58) |
| `fun fly` | [MapScene.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L77) |
| `fun fit` | [MapScene.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L83) |
| `fun view` | [MapScene.kt:117](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L117) |
| `fun selectedLayer` | [MapScene.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L118) |
| `fun sourceName` | [MapScene.kt:119](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L119) |
| `fun select` | [MapScene.kt:124](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L124) |
| `fun removingLayer` | [MapScene.kt:141](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L141) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun sizeOf` | [NativeSceneRenderer.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L38) |
| `fun invalidate` | [NativeSceneRenderer.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L41) |
| `fun render` | [NativeSceneRenderer.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L43) |
| `fun item` | [NativeSceneRenderer.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L50) |
| `fun polygon` | [NativeSceneRenderer.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L59) |
| `fun line` | [NativeSceneRenderer.kt:65](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L65) |
| `fun marker` | [NativeSceneRenderer.kt:93](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L93) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/VesselGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun vesselCourseVector` | [VesselGeometry.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/VesselGeometry.kt#L6) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun close` | [MarinePresentationBridge.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L66) |
| `fun add` | [MarinePresentationBridge.kt:95](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L95) |
| `fun motionReading` | [MarinePresentationBridge.kt:132](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L132) |
| `fun syncLanguage` | [MarinePresentationBridge.kt:160](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L160) |
| `fun startRecording` | [MarinePresentationBridge.kt:168](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L168) |
| `fun pauseRecording` | [MarinePresentationBridge.kt:169](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L169) |
| `fun resumeRecording` | [MarinePresentationBridge.kt:170](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L170) |
| `fun finishRecording` | [MarinePresentationBridge.kt:171](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L171) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Fix` | [Nmea.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L16) |
| `fun fresh` | [Nmea.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L23) |
| `fun freshSpeed` | [Nmea.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L24) |
| `fun freshCourse` | [Nmea.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L25) |
| `fun freshHeading` | [Nmea.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L27) |
| `class Reading` | [Nmea.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L30) |
| `fun fresh` | [Nmea.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L36) |
| `class VesselData` | [Nmea.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L39) |
| `fun fix` | [Nmea.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L49) |
| `class DataHub` | [Nmea.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L52) |
| `fun update` | [Nmea.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L58) |
| `fun resetNmea` | [Nmea.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L75) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AnchorExperience` | [AnchorExperience.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L60) |
| `fun backInside` | [AnchorExperience.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L105) |
| `fun command` | [AnchorExperience.kt:131](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L131) |
| `fun startWatch` | [AnchorExperience.kt:169](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L169) |
| `fun displayedWind` | [AnchorExperience.kt:484](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L484) |
| `fun storedWind` | [AnchorExperience.kt:490](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L490) |
| `fun flush` | [AnchorExperience.kt:548](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L548) |
| `fun anchorSamplesConnected` | [AnchorExperience.kt:562](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L562) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun effectiveAnchorPoint` | [AnchorSetupPolicy.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt#L9) |
| `fun anchorWatchInput` | [AnchorSetupPolicy.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NativeChart` | [ChartMapAdapter.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt#L15) |
| `fun isCurrent` | [ChartMapAdapter.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt#L19) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartScreen` | [ChartScreen.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L26) |
| `fun closeTool` | [ChartScreen.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L41) |
| `fun ConfirmDialog` | [ChartScreen.kt:149](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L149) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CoordinateEditPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun preservedCoordinate` | [CoordinateEditPolicy.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CoordinateEditPolicy.kt#L6) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun DataCenterScreen` | [DataCenterExperience.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L29) |
| `fun ColumnScope.PhoneSourceSettings` | [DataCenterExperience.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L81) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rememberMarineClock` | [DataScreen.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt#L16) |
| `fun readingAge` | [DataScreen.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt#L23) |
| `fun connectionLabel` | [DataScreen.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt#L30) |
| `fun metricName` | [DataScreen.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt#L38) |
| `fun DataScreen` | [DataScreen.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataScreen.kt#L77) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun OsStore.formatDistance` | [DisplayFormats.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L12) |
| `fun OsStore.formatSpeed` | [DisplayFormats.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L18) |
| `fun OsStore.formatDepth` | [DisplayFormats.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L22) |
| `fun OsStore.formatBearing` | [DisplayFormats.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L24) |
| `fun OsStore.formatAngle` | [DisplayFormats.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L27) |
| `fun OsStore.formatTemperature` | [DisplayFormats.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L28) |
| `fun OsStore.formatMetric` | [DisplayFormats.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L30) |
| `fun OsStore.formatCoordinates` | [DisplayFormats.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L46) |
| `fun coordinate` | [DisplayFormats.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L49) |
| `fun OsStore.formatLatitude` | [DisplayFormats.kt:61](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L61) |
| `fun OsStore.formatLongitude` | [DisplayFormats.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L64) |
| `fun parseCoordinate` | [DisplayFormats.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L69) |
| `fun durationLabel` | [DisplayFormats.kt:89](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L89) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object InstrumentReadingPolicy` | [InstrumentReadingPolicy.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L6) |
| `fun requiresFresh` | [InstrumentReadingPolicy.kt:7](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L7) |
| `fun isLive` | [InstrumentReadingPolicy.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L13) |
| `fun displayable` | [InstrumentReadingPolicy.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L16) |
| `fun signedWindFraction` | [InstrumentReadingPolicy.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L20) |
| `fun VesselObservation<*>.displayIsLive` | [InstrumentReadingPolicy.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L24) |
| `fun instrumentObservation` | [InstrumentReadingPolicy.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L26) |
| `fun instrumentTrendKey` | [InstrumentReadingPolicy.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentReadingPolicy.kt#L64) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InstrumentTrendGroup` | [InstrumentTrendCatalog.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L6) |
| `class InstrumentTrendMetric` | [InstrumentTrendCatalog.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L9) |
| `object InstrumentTrendCatalog` | [InstrumentTrendCatalog.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L11) |
| `fun lastReading` | [InstrumentTrendCatalog.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L32) |
| `fun available` | [InstrumentTrendCatalog.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L36) |
| `fun selected` | [InstrumentTrendCatalog.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L39) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun InstrumentsScreen` | [InstrumentsExperience.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L29) |
| `fun closeLayer` | [InstrumentsExperience.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L49) |
| `fun saveLayout` | [InstrumentsExperience.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L58) |
| `fun <T> value` | [InstrumentsExperience.kt:299](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L299) |
| `fun number` | [InstrumentsExperience.kt:300](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L300) |
| `fun bearing` | [InstrumentsExperience.kt:301](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L301) |
| `fun angle` | [InstrumentsExperience.kt:302](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L302) |
| `fun speed` | [InstrumentsExperience.kt:303](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L303) |
| `fun depth` | [InstrumentsExperience.kt:304](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L304) |
| `fun distance` | [InstrumentsExperience.kt:305](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L305) |
| `fun observationStatus` | [InstrumentsExperience.kt:346](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L346) |
| `fun readingStatus` | [InstrumentsExperience.kt:351](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L351) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LibraryScreen` | [LibraryScreen.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt#L17) |
| `fun LibraryFolderScreen` | [LibraryScreen.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt#L53) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LocalNmeaExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LocalNmeaScreen` | [LocalNmeaExperience.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LocalNmeaExperience.kt#L15) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MapSourcePicker` | [MapExperience.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L28) |
| `fun choose` | [MapExperience.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L33) |
| `fun MapSourceOption` | [MapExperience.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L60) |
| `fun MapSourceButton` | [MapExperience.kt:65](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L65) |
| `fun MapPicker` | [MapExperience.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L73) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartAppScreen` | [MarineAppsScreen.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt#L21) |
| `fun RecordingDialog` | [MarineAppsScreen.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt#L32) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselObservation<Double>.liveNumber` | [MarineInstrumentGraphics.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L32) |
| `fun VesselObservation<Double>.displayNumber` | [MarineInstrumentGraphics.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L33) |
| `fun MarineCompass` | [MarineInstrumentGraphics.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L47) |
| `fun WindRose` | [MarineInstrumentGraphics.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L88) |
| `fun AttitudeHorizon` | [MarineInstrumentGraphics.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L130) |
| `fun InstrumentGauge` | [MarineInstrumentGraphics.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L167) |
| `fun scaleLabel` | [MarineInstrumentGraphics.kt:243](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L243) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MapPageHeader` | [MarineMapControls.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L18) |
| `fun MapCrosshairReadout` | [MarineMapControls.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L31) |
| `fun MapZoomControls` | [MarineMapControls.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L39) |
| `fun MapPositionReadout` | [MarineMapControls.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L50) |
| `fun age` | [MarineMapControls.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L53) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MetroColors` | [Metro.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L52) |
| `class ShellHorizontalInsets` | [Metro.kt:56](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L56) |
| `fun shellHorizontalInsets` | [Metro.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L59) |
| `fun safe` | [Metro.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L64) |
| `fun AppBackHandler` | [Metro.kt:71](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L71) |
| `fun MetroTheme` | [Metro.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L77) |
| `fun Label` | [Metro.kt:82](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L82) |
| `fun Glyph` | [Metro.kt:86](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L86) |
| `fun point` | [Metro.kt:89](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L89) |
| `fun line` | [Metro.kt:90](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L90) |
| `fun circle` | [Metro.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L91) |
| `fun IconAction` | [Metro.kt:128](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L128) |
| `fun MetroButton` | [Metro.kt:135](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L135) |
| `fun PageHeader` | [Metro.kt:144](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L144) |
| `fun PageBody` | [Metro.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L163) |
| `fun MenuRow` | [Metro.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L167) |
| `fun Field` | [Metro.kt:175](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L175) |
| `fun Toggle` | [Metro.kt:186](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L186) |
| `fun ChoiceRow` | [Metro.kt:204](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L204) |
| `fun Pivot` | [Metro.kt:215](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L215) |
| `fun MetroProgress` | [Metro.kt:254](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L254) |
| `fun TextDialog` | [Metro.kt:269](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L269) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun PlaceKind.label` | [MySailingExperience.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L16) |
| `fun PlacesScreen` | [MySailingExperience.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L21) |
| `fun apply` | [MySailingExperience.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L105) |
| `fun PlaceScreen` | [MySailingExperience.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L113) |
| `fun CoordinateMapPreview` | [MySailingExperience.kt:145](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L145) |
| `fun CoordinateEditor` | [MySailingExperience.kt:150](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L150) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun chartRoute` | [Navigation.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L26) |
| `fun chartIsNavigating` | [Navigation.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L28) |
| `fun resolveChartRoute` | [Navigation.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L30) |
| `fun cancelRouteDraft` | [Navigation.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L33) |
| `fun resumeOrCreateRouteDraft` | [Navigation.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L37) |
| `class RouteGuidance` | [Navigation.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L42) |
| `fun routeGuidance` | [Navigation.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L49) |
| `fun beginNavigation` | [Navigation.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L77) |
| `fun endNavigation` | [Navigation.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L88) |
| `fun liveNavigationFix` | [Navigation.kt:95](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L95) |
| `fun offsetLabel` | [Navigation.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L101) |
| `fun RouteSketch` | [Navigation.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L111) |
| `fun at` | [Navigation.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L130) |
| `fun StartNavigationDialog` | [Navigation.kt:148](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L148) |
| `fun ChartNavigationCard` | [Navigation.kt:181](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L181) |
| `fun NavigationActionsDialog` | [Navigation.kt:213](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L213) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaFlowScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NmeaScreen` | [NmeaFlowScreen.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaFlowScreen.kt#L20) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NmeaPublicationEditor` | [NmeaPublicationEditor.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L22) |
| `fun PublicationChoice` | [NmeaPublicationEditor.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L84) |
| `fun publicationFeedName` | [NmeaPublicationEditor.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L97) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselSourceSettings` | [NmeaSourcesExperience.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L13) |
| `fun ColumnScope.SourceOverview` | [NmeaSourcesExperience.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L17) |
| `fun ColumnScope.SourceMetricDetail` | [NmeaSourcesExperience.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L57) |
| `fun sourceDisplayName` | [NmeaSourcesExperience.kt:137](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L137) |
| `fun sourceCandidateText` | [NmeaSourcesExperience.kt:153](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L153) |
| `fun sourceObservationText` | [NmeaSourcesExperience.kt:158](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L158) |
| `fun sourceValueText` | [NmeaSourcesExperience.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L163) |
| `fun sourceObservation` | [NmeaSourcesExperience.kt:182](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L182) |
| `fun sourceMetricName` | [NmeaSourcesExperience.kt:202](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L202) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SystemStatusBar` | [NotificationCenter.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L39) |
| `fun NotificationCenter` | [NotificationCenter.kt:78](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L78) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NotificationQuickActions` | [NotificationQuickActions.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L37) |
| `fun point` | [NotificationQuickActions.kt:162](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L162) |
| `fun line` | [NotificationQuickActions.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L163) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RouteScreen` | [PlacesScreen.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L17) |
| `fun preview` | [PlacesScreen.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L31) |
| `fun edit` | [PlacesScreen.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L32) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ReadingTrace` | [ReadingTrace.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L23) |
| `fun position` | [ReadingTrace.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L48) |
| `fun readingsAreContinuous` | [ReadingTrace.kt:78](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L78) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SavedLocationScreen` | [SavedLocationExperience.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L25) |
| `fun mutate` | [SavedLocationExperience.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L45) |
| `fun valid` | [SavedLocationExperience.kt:157](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L157) |
| `fun CollectionScreen` | [SavedLocationExperience.kt:185](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L185) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SettingsScreen` | [SettingsScreen.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L40) |
| `fun title` | [SettingsScreen.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L47) |
| `fun open` | [SettingsScreen.kt:183](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L183) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SystemMarineAlerts` | [SystemAlerts.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L17) |
| `class Alert` | [SystemAlerts.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L30) |
| `fun buildAlerts` | [SystemAlerts.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L31) |
| `fun tr` | [SystemAlerts.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L32) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun TaskCaptureHost` | [TaskSwitching.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L40) |
| `fun AppLaunchCover` | [TaskSwitching.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L53) |
| `fun TaskSwitcher` | [TaskSwitching.kt:63](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L63) |
| `fun closeCard` | [TaskSwitching.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L87) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun TileLibraryScreen` | [TileLibraryExperience.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L37) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TilePreset` | [TilePresentations.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L48) |
| `fun tilePresets` | [TilePresentations.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L60) |
| `fun preset` | [TilePresentations.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L62) |
| `class TileMode` | [TilePresentations.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L76) |
| `fun tileModes` | [TilePresentations.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L77) |
| `fun mode` | [TilePresentations.kt:78](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L78) |
| `fun tilePreferenceContributions` | [TilePresentations.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L94) |
| `fun tilePresentation` | [TilePresentations.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L103) |
| `fun presetTilePresentation` | [TilePresentations.kt:104](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L104) |
| `fun reading` | [TilePresentations.kt:134](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L134) |
| `fun stamp` | [TilePresentations.kt:135](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L135) |
| `fun samples` | [TilePresentations.kt:136](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L136) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LogbookScreen` | [TripExperience.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt#L37) |
| `fun coords` | [TripExperience.kt:284](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt#L284) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VisibleAppRoute.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ReportVisibleAppRoute` | [VisibleAppRoute.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VisibleAppRoute.kt#L9) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeTransport` | [RuntimeContract.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L7) |
| `class RuntimeReadiness` | [RuntimeContract.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L8) |
| `class RuntimeConnection` | [RuntimeContract.kt:9](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L9) |
| `interface RuntimeEndpoint` | [RuntimeContract.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L17) |
| `class PositionSourceRequest` | [RuntimeContract.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L20) |
| `interface RuntimeBindingResult` | [RuntimeContract.kt:23](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L23) |
| `class Available` | [RuntimeContract.kt:24](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L24) |
| `class Unavailable` | [RuntimeContract.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L25) |
| `object RuntimeBindings` | [RuntimeContract.kt:27](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L27) |
| `fun resolve` | [RuntimeContract.kt:28](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L28) |
| `class VoyagePhase` | [RuntimeContract.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L34) |
| `class VoyageSessionState` | [RuntimeContract.kt:36](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L36) |
| `fun elapsedMillis` | [RuntimeContract.kt:48](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L48) |
| `class VoyageAction` | [RuntimeContract.kt:50](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L50) |
| `class VoyageCommandStatus` | [RuntimeContract.kt:51](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L51) |
| `class VoyageCommandEvent` | [RuntimeContract.kt:53](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L53) |
| `interface VoyageSessionService` | [RuntimeContract.kt:59](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L59) |
| `fun start` | [RuntimeContract.kt:62](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L62) |
| `fun pause` | [RuntimeContract.kt:63](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L63) |
| `fun resume` | [RuntimeContract.kt:64](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L64) |
| `fun finish` | [RuntimeContract.kt:65](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L65) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MeasurementUnitSystem` | [AppPreferenceContract.kt:3](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L3) |
| `class MotionPreference` | [AppPreferenceContract.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L4) |
| `class AppPreferenceLabel` | [AppPreferenceContract.kt:6](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L6) |
| `class AppPreferenceKey` | [AppPreferenceContract.kt:14](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L14) |
| `interface AppPreferenceValue` | [AppPreferenceContract.kt:20](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L20) |
| `class Toggle` | [AppPreferenceContract.kt:21](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L21) |
| `class Choice` | [AppPreferenceContract.kt:22](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L22) |
| `interface AppPreferenceDefinition` | [AppPreferenceContract.kt:27](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L27) |
| `class Toggle` | [AppPreferenceContract.kt:32](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L32) |
| `class Choice` | [AppPreferenceContract.kt:38](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L38) |
| `fun accepts` | [AppPreferenceContract.kt:52](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L52) |
| `class AppPreferenceContribution` | [AppPreferenceContract.kt:58](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L58) |
| `fun resolve` | [AppPreferenceContract.kt:75](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L75) |
| `fun compose` | [AppPreferenceContract.kt:84](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L84) |
| `fun encode` | [AppPreferenceContract.kt:99](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/AppPreferenceContract.kt#L99) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherCatalogSnapshot` | [LauncherCatalogContract.kt:3](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt#L3) |
| `class LauncherAppDescriptor` | [LauncherCatalogContract.kt:9](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt#L9) |
| `class LauncherEntryDescriptor` | [LauncherCatalogContract.kt:14](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt#L14) |
| `interface LauncherCatalogContribution` | [LauncherCatalogContract.kt:24](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherCatalogContract.kt#L24) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt

| 声明 | 实现位置 |
| --- | --- |
| `class UiText` | [LauncherHostPort.kt:6](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L6) |
| `class TileBadge` | [LauncherHostPort.kt:9](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L9) |
| `class TileSemanticState` | [LauncherHostPort.kt:11](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L11) |
| `class TileAnimationPolicy` | [LauncherHostPort.kt:16](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L16) |
| `class TileContentSnapshot` | [LauncherHostPort.kt:21](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L21) |
| `class LauncherSystemStatus` | [LauncherHostPort.kt:31](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L31) |
| `interface LaunchResolution` | [LauncherHostPort.kt:33](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L33) |
| `class Internal` | [LauncherHostPort.kt:34](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L34) |
| `class Unresolved` | [LauncherHostPort.kt:39](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L39) |
| `interface LauncherHostPort` | [LauncherHostPort.kt:42](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L42) |
| `fun resolveLaunch` | [LauncherHostPort.kt:47](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherHostPort.kt#L47) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherAppId` | [LauncherIdentifiers.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt#L4) |
| `class LauncherEntryId` | [LauncherIdentifiers.kt:11](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt#L11) |
| `class LaunchToken` | [LauncherIdentifiers.kt:18](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt#L18) |
| `class TileInstanceId` | [LauncherIdentifiers.kt:25](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt#L25) |
| `class PinPolicy` | [LauncherIdentifiers.kt:31](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/LauncherIdentifiers.kt#L31) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineTileContentLayout` | [MarineTile.kt:3](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L3) |
| `class MarineTileSize` | [MarineTile.kt:10](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L10) |
| `fun fromPersistedName` | [MarineTile.kt:24](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L24) |
| `class TilePresentationKind` | [MarineTile.kt:32](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L32) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellInput.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ShellInput` | [ShellInput.kt:7](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellInput.kt#L7) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ShellInsets` | [ShellWindowMetrics.kt:8](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L8) |
| `class ShellRect` | [ShellWindowMetrics.kt:15](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L15) |
| `class ShellRoundedCorner` | [ShellWindowMetrics.kt:17](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L17) |
| `class ShellRoundedCorners` | [ShellWindowMetrics.kt:19](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L19) |
| `class ShellWindowMetrics` | [ShellWindowMetrics.kt:26](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L26) |
| `class ShellSafeBand` | [ShellWindowMetrics.kt:37](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L37) |
| `class ShellHorizontalSegment` | [ShellWindowMetrics.kt:40](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L40) |
| `class ShellChromeSafeBands` | [ShellWindowMetrics.kt:44](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L44) |
| `object ShellSafeBands` | [ShellWindowMetrics.kt:51](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L51) |
| `fun statusSegments` | [ShellWindowMetrics.kt:57](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L57) |
| `fun resolve` | [ShellWindowMetrics.kt:88](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L88) |
| `fun horizontalInsets` | [ShellWindowMetrics.kt:115](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L115) |
| `fun cornerInset` | [ShellWindowMetrics.kt:120](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/ShellWindowMetrics.kt#L120) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface LauncherEngine` | [LauncherEngine.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L19) |
| `fun dispatch` | [LauncherEngine.kt:23](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L23) |
| `class DefaultLauncherEngine` | [LauncherEngine.kt:26](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L26) |
| `fun dispatch` | [LauncherEngine.kt:70](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L70) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PersistedLauncherPage` | [LauncherPersistence.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L12) |
| `class LauncherStartupHealth` | [LauncherPersistence.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L14) |
| `class LauncherPersistedState` | [LauncherPersistence.kt:21](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L21) |
| `class LauncherPersistenceIncident` | [LauncherPersistence.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L37) |
| `class LauncherPersistenceMigrationResult` | [LauncherPersistence.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L46) |
| `object LauncherPersistedStateMigration` | [LauncherPersistence.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L51) |
| `fun migrate` | [LauncherPersistence.kt:58](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L58) |
| `fun normalized` | [LauncherPersistence.kt:82](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L82) |
| `class LauncherTokenAlias` | [LauncherPersistence.kt:127](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L127) |
| `fun migrate` | [LauncherPersistence.kt:137](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L137) |
| `class LauncherProductMigrationStep` | [LauncherPersistence.kt:144](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L144) |
| `class LauncherProductMigrationResult` | [LauncherPersistence.kt:157](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L157) |
| `class LauncherProductMigrationPlan` | [LauncherPersistence.kt:166](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L166) |
| `fun migrate` | [LauncherPersistence.kt:177](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L177) |
| `class LauncherRecoveryDecision` | [LauncherPersistence.kt:227](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L227) |
| `object LauncherRecoveryPolicy` | [LauncherPersistence.kt:232](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L232) |
| `fun beginLaunch` | [LauncherPersistence.kt:236](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L236) |
| `fun markHealthy` | [LauncherPersistence.kt:251](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L251) |
| `interface LauncherPersistencePort` | [LauncherPersistence.kt:259](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L259) |
| `fun load` | [LauncherPersistence.kt:266](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L266) |
| `fun save` | [LauncherPersistence.kt:267](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L267) |
| `fun reset` | [LauncherPersistence.kt:268](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L268) |
| `fun saveDocument` | [LauncherPersistence.kt:270](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L270) |
| `fun savePreferences` | [LauncherPersistence.kt:274](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L274) |
| `fun beginLaunch` | [LauncherPersistence.kt:284](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L284) |
| `fun markLaunchHealthy` | [LauncherPersistence.kt:291](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L291) |
| `class InMemoryLauncherPersistence` | [LauncherPersistence.kt:297](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L297) |
| `fun load` | [LauncherPersistence.kt:309](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L309) |
| `fun save` | [LauncherPersistence.kt:311](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L311) |
| `fun reset` | [LauncherPersistence.kt:316](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L316) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherReducerContext` | [LauncherReducer.kt:27](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L27) |
| `class LauncherReduction` | [LauncherReducer.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L33) |
| `interface LauncherAction` | [LauncherReducer.kt:38](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L38) |
| `object ShowStart : LauncherAction` | [LauncherReducer.kt:39](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L39) |
| `object ShowAllApps : LauncherAction` | [LauncherReducer.kt:40](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L40) |
| `object Back : LauncherAction` | [LauncherReducer.kt:41](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L41) |
| `object ShowDesktop : LauncherAction` | [LauncherReducer.kt:42](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L42) |
| `object OpenSearch : LauncherAction` | [LauncherReducer.kt:43](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L43) |
| `class UpdateSearchQuery` | [LauncherReducer.kt:44](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L44) |
| `object ShowRecents : LauncherAction` | [LauncherReducer.kt:45](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L45) |
| `class ActivateTask` | [LauncherReducer.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L46) |
| `class CloseTask` | [LauncherReducer.kt:47](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L47) |
| `class RestorePersistedDocument` | [LauncherReducer.kt:48](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L48) |
| `object EnterSafeMode : LauncherAction` | [LauncherReducer.kt:49](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L49) |
| `object ExitSafeMode : LauncherAction` | [LauncherReducer.kt:50](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L50) |
| `class Open` | [LauncherReducer.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L52) |
| `class CatalogChanged` | [LauncherReducer.kt:62](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L62) |
| `class ApplyLayoutProposal` | [LauncherReducer.kt:63](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L63) |
| `class BeginLayoutTransaction` | [LauncherReducer.kt:64](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L64) |
| `object CommitLayoutTransaction : LauncherAction` | [LauncherReducer.kt:65](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L65) |
| `object CancelLayoutTransaction : LauncherAction` | [LauncherReducer.kt:66](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L66) |
| `object UndoLayout : LauncherAction` | [LauncherReducer.kt:67](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L67) |
| `class EnterStartEdit` | [LauncherReducer.kt:68](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L68) |
| `class SelectStartTile` | [LauncherReducer.kt:69](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L69) |
| `object ExitStartEdit : LauncherAction` | [LauncherReducer.kt:70](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L70) |
| `class BeginTileDrag` | [LauncherReducer.kt:71](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L71) |
| `class InsertionTargetChanged` | [LauncherReducer.kt:76](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L76) |
| `class TileCellTargetChanged` | [LauncherReducer.kt:77](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L77) |
| `class DropTile` | [LauncherReducer.kt:82](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L82) |
| `object CancelTileOperation : LauncherAction` | [LauncherReducer.kt:83](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L83) |
| `class ResizeTile` | [LauncherReducer.kt:84](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L84) |
| `class MoveTileBy` | [LauncherReducer.kt:85](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L85) |
| `class OpenEntryContextMenu` | [LauncherReducer.kt:86](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L86) |
| `object OpenAlphabetJump : LauncherAction` | [LauncherReducer.kt:87](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L87) |
| `object DismissTransient : LauncherAction` | [LauncherReducer.kt:88](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L88) |
| `class PinEntry` | [LauncherReducer.kt:89](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L89) |
| `class UnpinTile` | [LauncherReducer.kt:90](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L90) |
| `class AcknowledgeStartReveal` | [LauncherReducer.kt:91](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L91) |
| `class TogglePin` | [LauncherReducer.kt:92](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L92) |
| `object ResetStartDocument : LauncherAction` | [LauncherReducer.kt:93](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L93) |
| `class PersistenceIncidentObserved` | [LauncherReducer.kt:94](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L94) |
| `class LauncherHaptic` | [LauncherReducer.kt:97](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L97) |
| `interface LauncherIncident` | [LauncherReducer.kt:99](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L99) |
| `class UnresolvedLaunchToken` | [LauncherReducer.kt:100](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L100) |
| `class InvalidLayoutProposal` | [LauncherReducer.kt:101](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L101) |
| `class CatalogRepair` | [LauncherReducer.kt:102](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L102) |
| `class PersistenceMigration` | [LauncherReducer.kt:103](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L103) |
| `class PersistenceFailure` | [LauncherReducer.kt:104](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L104) |
| `interface LauncherEffect` | [LauncherReducer.kt:107](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L107) |
| `class Launch` | [LauncherReducer.kt:108](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L108) |
| `class PersistDocument` | [LauncherReducer.kt:109](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L109) |
| `class Haptic` | [LauncherReducer.kt:110](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L110) |
| `class AccessibilityAnnouncement` | [LauncherReducer.kt:111](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L111) |
| `class LogIncident` | [LauncherReducer.kt:112](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L112) |
| `class ScrollStartToReveal` | [LauncherReducer.kt:113](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L113) |
| `class CloseAppSession` | [LauncherReducer.kt:115](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L115) |
| `fun ShellInput.toShellAction` | [LauncherReducer.kt:118](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L118) |
| `interface LauncherReducer` | [LauncherReducer.kt:125](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L125) |
| `fun reduce` | [LauncherReducer.kt:126](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L126) |
| `class DefaultLauncherReducer : LauncherReducer` | [LauncherReducer.kt:133](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L133) |
| `fun reduce` | [LauncherReducer.kt:134](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L134) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InternalAppTaskId` | [LauncherState.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L14) |
| `interface ShellVisualSurface` | [LauncherState.kt:16](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L16) |
| `object Desktop : ShellVisualSurface` | [LauncherState.kt:17](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L17) |
| `object ModuleList : ShellVisualSurface` | [LauncherState.kt:18](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L18) |
| `class Search` | [LauncherState.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L19) |
| `object Recents : ShellVisualSurface` | [LauncherState.kt:23](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L23) |
| `class Module` | [LauncherState.kt:24](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L24) |
| `class LauncherRecoveryMode` | [LauncherState.kt:27](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L27) |
| `class StartScreenState` | [LauncherState.kt:29](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L29) |
| `class StartReveal` | [LauncherState.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L37) |
| `class AllAppsState` | [LauncherState.kt:39](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L39) |
| `class InternalAppTask` | [LauncherState.kt:41](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L41) |
| `class LinkedTaskReturn` | [LauncherState.kt:64](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L64) |
| `class InternalTaskState` | [LauncherState.kt:76](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L76) |
| `fun task` | [LauncherState.kt:81](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L81) |
| `interface LauncherTransient` | [LauncherState.kt:88](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L88) |
| `class ContextMenu` | [LauncherState.kt:89](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L89) |
| `object AlphabetJump : LauncherTransient` | [LauncherState.kt:90](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L90) |
| `class UndoLayout` | [LauncherState.kt:91](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L91) |
| `class Notice` | [LauncherState.kt:96](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L96) |
| `class LauncherNotice` | [LauncherState.kt:99](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L99) |
| `interface LauncherSystemOverlay` | [LauncherState.kt:101](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L101) |
| `class LauncherEngineState` | [LauncherState.kt:103](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherState.kt#L103) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ShellTransitionTrigger` | [ShellTransitionResolver.kt:3](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt#L3) |
| `class ShellTransitionKind` | [ShellTransitionResolver.kt:18](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt#L18) |
| `class ShellTransitionRequest` | [ShellTransitionResolver.kt:35](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt#L35) |
| `object ShellTransitionResolver` | [ShellTransitionResolver.kt:43](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt#L43) |
| `fun resolve` | [ShellTransitionResolver.kt:44](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/ShellTransitionResolver.kt#L44) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/catalog/LauncherCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun entry` | [LauncherCatalog.kt:15](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/catalog/LauncherCatalog.kt#L15) |
| `fun entry` | [LauncherCatalog.kt:17](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/catalog/LauncherCatalog.kt#L17) |
| `fun app` | [LauncherCatalog.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/catalog/LauncherCatalog.kt#L19) |
| `fun compose` | [LauncherCatalog.kt:22](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/catalog/LauncherCatalog.kt#L22) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ProfileId` | [WpReferenceProfile.kt:4](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L4) |
| `class ReferenceEvidenceState` | [WpReferenceProfile.kt:10](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L10) |
| `class OuterInsetPolicy` | [WpReferenceProfile.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L12) |
| `class StatusStripProfile` | [WpReferenceProfile.kt:18](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L18) |
| `class WpTypographyProfile` | [WpReferenceProfile.kt:20](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L20) |
| `class TileContentProfile` | [WpReferenceProfile.kt:25](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L25) |
| `class WpMotionProfile` | [WpReferenceProfile.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L33) |
| `class WpInteractionProfile` | [WpReferenceProfile.kt:41](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L41) |
| `class WpLayoutPolicy` | [WpReferenceProfile.kt:48](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L48) |
| `class WpReferenceProfile` | [WpReferenceProfile.kt:53](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L53) |
| `object WpReferenceProfiles` | [WpReferenceProfile.kt:89](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L89) |
| `fun forViewport` | [WpReferenceProfile.kt:147](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L147) |
| `fun require` | [WpReferenceProfile.kt:150](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L150) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class StartViewport` | [WpStartGeometry.kt:5](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L5) |
| `class IntInsets` | [WpStartGeometry.kt:21](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L21) |
| `class IntRect` | [WpStartGeometry.kt:23](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L23) |
| `class ResolvedStartGeometry` | [WpStartGeometry.kt:32](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L32) |
| `fun tileWidthPx` | [WpStartGeometry.kt:41](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L41) |
| `fun tileHeightPx` | [WpStartGeometry.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L46) |
| `object WpStartGeometryCalculator` | [WpStartGeometry.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L52) |
| `fun calculate` | [WpStartGeometry.kt:53](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L53) |
| `fun scale` | [WpStartGeometry.kt:57](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L57) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/DragInteractionPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DragCellHysteresis` | [DragInteractionPolicy.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/DragInteractionPolicy.kt#L12) |
| `fun resolve` | [DragInteractionPolicy.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/DragInteractionPolicy.kt#L19) |
| `class EdgeAutoScrollPolicy` | [DragInteractionPolicy.kt:40](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/DragInteractionPolicy.kt#L40) |
| `fun velocity` | [DragInteractionPolicy.kt:49](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/DragInteractionPolicy.kt#L49) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherPage` | [StartInteractionState.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L9) |
| `class ShellOffset` | [StartInteractionState.kt:10](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L10) |
| `interface StartInteractionState` | [StartInteractionState.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L12) |
| `object Idle : StartInteractionState` | [StartInteractionState.kt:13](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L13) |
| `class Paging` | [StartInteractionState.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L14) |
| `class EditIdle` | [StartInteractionState.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L19) |
| `class Dragging` | [StartInteractionState.kt:20](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L20) |
| `class Settling` | [StartInteractionState.kt:29](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L29) |
| `class Launching` | [StartInteractionState.kt:30](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/StartInteractionState.kt#L30) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileDragCoordinates.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileDragCoordinates` | [TileDragCoordinates.kt:4](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileDragCoordinates.kt#L4) |
| `fun movedTo` | [TileDragCoordinates.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileDragCoordinates.kt#L9) |
| `fun hasMovedBeyond` | [TileDragCoordinates.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileDragCoordinates.kt#L12) |
| `fun contentOffset` | [TileDragCoordinates.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileDragCoordinates.kt#L19) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class EditControlRect` | [TileEditControlGeometry.kt:10](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L10) |
| `fun contains` | [TileEditControlGeometry.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L19) |
| `fun overlaps` | [TileEditControlGeometry.kt:20](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L20) |
| `class TileEditControls` | [TileEditControlGeometry.kt:24](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L24) |
| `fun contains` | [TileEditControlGeometry.kt:25](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L25) |
| `object TileEditControlGeometry` | [TileEditControlGeometry.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L33) |
| `fun resolve` | [TileEditControlGeometry.kt:34](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L34) |
| `fun axis` | [TileEditControlGeometry.kt:56](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L56) |
| `fun score` | [TileEditControlGeometry.kt:64](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/interaction/TileEditControlGeometry.kt#L64) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PackedTilePlacement` | [AdaptiveTilePacker.kt:6](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L6) |
| `class PackedSpacerPlacement` | [AdaptiveTilePacker.kt:7](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L7) |
| `class AdaptivePackedLayout` | [AdaptiveTilePacker.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L9) |
| `fun tile` | [AdaptiveTilePacker.kt:22](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L22) |
| `class InsertionSide` | [AdaptiveTilePacker.kt:25](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L25) |
| `class TileInsertionTarget` | [AdaptiveTilePacker.kt:28](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L28) |
| `object AdaptiveTilePacker` | [AdaptiveTilePacker.kt:31](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L31) |
| `fun pack` | [AdaptiveTilePacker.kt:34](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L34) |
| `fun place` | [AdaptiveTilePacker.kt:58](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L58) |
| `fun insert` | [AdaptiveTilePacker.kt:79](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L79) |
| `fun insert` | [AdaptiveTilePacker.kt:97](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L97) |
| `fun insertionIndexForTarget` | [AdaptiveTilePacker.kt:101](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L101) |
| `fun insertionTargetForCell` | [AdaptiveTilePacker.kt:112](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L112) |
| `fun insertionIndexForCell` | [AdaptiveTilePacker.kt:150](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L150) |
| `fun insertionIndexOf` | [AdaptiveTilePacker.kt:163](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L163) |
| `class Tile` | [AdaptiveTilePacker.kt:189](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L189) |
| `class Gap` | [AdaptiveTilePacker.kt:194](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L194) |
| `fun occupiedCells` | [AdaptiveTilePacker.kt:202](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L202) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt

| 声明 | 实现位置 |
| --- | --- |
| `class GridCell` | [StartDocument.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L8) |
| `class TileDocumentEntry` | [StartDocument.kt:10](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L10) |
| `class Spacer` | [StartDocument.kt:26](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L26) |
| `class TileDocument` | [StartDocument.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L33) |
| `class StartDocument` | [StartDocument.kt:38](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L38) |
| `class LayoutChangeReason` | [StartDocument.kt:49](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L49) |
| `class LayoutTransaction` | [StartDocument.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L51) |
| `class LayoutProposal` | [StartDocument.kt:58](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L58) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object StartDocumentValidator` | [StartDocumentPolicy.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L8) |
| `fun isValid` | [StartDocumentPolicy.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L9) |
| `class StartRepairIncident` | [StartDocumentPolicy.kt:35](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L35) |
| `class StartRepairResult` | [StartDocumentPolicy.kt:45](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L45) |
| `object StartDocumentRepair` | [StartDocumentPolicy.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L51) |
| `fun repair` | [StartDocumentPolicy.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L52) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt

| 声明 | 实现位置 |
| --- | --- |
| `object StartLayoutEditor` | [StartLayoutEditor.kt:13](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L13) |
| `fun resize` | [StartLayoutEditor.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L14) |
| `fun unpin` | [StartLayoutEditor.kt:38](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L38) |
| `fun pin` | [StartLayoutEditor.kt:47](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L47) |
| `fun move` | [StartLayoutEditor.kt:80](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L80) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ConnectionAttemptState` | [LegacyMarineController.kt:179](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L179) |
| `class ConnectionAttempt` | [LegacyMarineController.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L180) |
| `class CentreRecalculationUiState` | [LegacyMarineController.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L181) |
| `class AnchorSetupDraft` | [LegacyMarineController.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L184) |
| `class MainUiState` | [LegacyMarineController.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L213) |
| `class AnchorWatchInput` | [LegacyMarineController.kt:309](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L309) |
| `class LegacyMarineController @Inject constructor` | [LegacyMarineController.kt:317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L317) |
| `fun saveNmeaConnection` | [LegacyMarineController.kt:378](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L378) |
| `fun startNmeaConnection` | [LegacyMarineController.kt:379](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L379) |
| `fun stopNmeaConnection` | [LegacyMarineController.kt:384](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L384) |
| `fun selectNmeaPositionConnection` | [LegacyMarineController.kt:385](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L385) |
| `fun removeNmeaConnection` | [LegacyMarineController.kt:390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L390) |
| `fun setVesselMetricSource` | [LegacyMarineController.kt:392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L392) |
| `fun setNmeaMetricSource` | [LegacyMarineController.kt:402](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L402) |
| `fun consumeSonarGridChanges` | [LegacyMarineController.kt:668](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L668) |
| `fun validateProfile` | [LegacyMarineController.kt:684](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L684) |
| `fun saveAndConnect` | [LegacyMarineController.kt:686](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L686) |
| `fun disconnect` | [LegacyMarineController.kt:731](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L731) |
| `fun reconnectNmea` | [LegacyMarineController.kt:746](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L746) |
| `fun stopActiveWatchAndDisconnect` | [LegacyMarineController.kt:759](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L759) |
| `fun stopNmeaDependenciesAndDisconnect` | [LegacyMarineController.kt:763](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L763) |
| `fun continueTripWithPhoneAndDisconnect` | [LegacyMarineController.kt:767](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L767) |
| `fun clearConnectionAttempt` | [LegacyMarineController.kt:771](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L771) |
| `fun dismissRuntimeFeedback` | [LegacyMarineController.kt:772](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L772) |
| `fun consumeRuntimeFeedback` | [LegacyMarineController.kt:777](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L777) |
| `fun setLanguage` | [LegacyMarineController.kt:785](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L785) |
| `fun setVesselGeometry` | [LegacyMarineController.kt:786](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L786) |
| `fun setVesselIdentity` | [LegacyMarineController.kt:792](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L792) |
| `fun setAlarmSound` | [LegacyMarineController.kt:796](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L796) |
| `fun setAlarmSnoozeMinutes` | [LegacyMarineController.kt:801](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L801) |
| `fun setInstrumentLayout` | [LegacyMarineController.kt:805](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L805) |
| `fun updateSettings` | [LegacyMarineController.kt:809](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L809) |
| `fun completeOnboarding` | [LegacyMarineController.kt:823](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L823) |
| `fun setSonarLayerEnabled` | [LegacyMarineController.kt:824](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L824) |
| `fun exportBackup` | [LegacyMarineController.kt:828](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L828) |
| `fun restoreBackup` | [LegacyMarineController.kt:829](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L829) |
| `fun clearBackupResult` | [LegacyMarineController.kt:830](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L830) |
| `fun importOfflineMap` | [LegacyMarineController.kt:831](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L831) |
| `fun removeOfflineMap` | [LegacyMarineController.kt:844](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L844) |
| `fun setOfflineMapEnabled` | [LegacyMarineController.kt:849](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L849) |
| `fun createOfflineMapProvider` | [LegacyMarineController.kt:850](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L850) |
| `fun exportSupportBundle` | [LegacyMarineController.kt:851](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L851) |
| `fun clearSupportBundleResult` | [LegacyMarineController.kt:852](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L852) |
| `fun clearIncidentLog` | [LegacyMarineController.kt:853](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L853) |
| `fun clearRebuildableCaches` | [LegacyMarineController.kt:861](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L861) |
| `fun refreshStorage` | [LegacyMarineController.kt:869](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L869) |
| `fun confirmAlarmAudible` | [LegacyMarineController.kt:870](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L870) |
| `fun setNmeaSharing` | [LegacyMarineController.kt:877](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L877) |
| `fun saveLocalNmeaServerConfiguration` | [LegacyMarineController.kt:888](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L888) |
| `fun startLocalNmeaServer` | [LegacyMarineController.kt:901](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L901) |
| `fun fail` | [LegacyMarineController.kt:903](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L903) |
| `fun saveLocalNmeaPublicationPolicy` | [LegacyMarineController.kt:916](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L916) |
| `fun stopLocalNmeaServer` | [LegacyMarineController.kt:922](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L922) |
| `fun stopAllNmeaSharing` | [LegacyMarineController.kt:930](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L930) |
| `fun deleteHistorySession` | [LegacyMarineController.kt:940](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L940) |
| `fun setMapType` | [LegacyMarineController.kt:941](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L941) |
| `fun setGpsDataSource` | [LegacyMarineController.kt:942](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L942) |
| `fun switchGpsDataSource` | [LegacyMarineController.kt:943](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L943) |
| `fun setDemoMode` | [LegacyMarineController.kt:961](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L961) |
| `fun updateVesselDataSettings` | [LegacyMarineController.kt:971](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L971) |
| `fun createTripDashboard` | [LegacyMarineController.kt:972](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L972) |
| `fun saveTripDashboard` | [LegacyMarineController.kt:973](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L973) |
| `fun deleteTripDashboard` | [LegacyMarineController.kt:974](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L974) |
| `fun reorderTripDashboards` | [LegacyMarineController.kt:975](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L975) |
| `fun setTripLiveDisplayActive` | [LegacyMarineController.kt:976](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L976) |
| `fun confirmTripAttitudeFrame` | [LegacyMarineController.kt:977](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L977) |
| `fun calibrateVesselMount` | [LegacyMarineController.kt:992](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L992) |
| `fun setPhoneVesselMounted` | [LegacyMarineController.kt:993](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L993) |
| `fun alignPhoneHeadingToBow` | [LegacyMarineController.kt:1005](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1005) |
| `fun alignPhoneHeadingToNmea` | [LegacyMarineController.kt:1017](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1017) |
| `fun setPhoneHeadingAlignment` | [LegacyMarineController.kt:1038](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1038) |
| `fun clearVesselCalibrationFeedback` | [LegacyMarineController.kt:1039](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1039) |
| `fun setNmeaOutputEndpoint` | [LegacyMarineController.kt:1040](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1040) |
| `fun setNmeaPhonePositionPublishing` | [LegacyMarineController.kt:1061](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1061) |
| `fun setNmeaPhoneHeadingPublishing` | [LegacyMarineController.kt:1067](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1067) |
| `fun setNmeaPhoneRateOfTurnPublishing` | [LegacyMarineController.kt:1070](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1070) |
| `fun setNmeaPhoneAttitudePublishing` | [LegacyMarineController.kt:1073](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1073) |
| `fun setNmeaPhonePressurePublishing` | [LegacyMarineController.kt:1076](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1076) |
| `fun setNmeaDerivedWindPublishing` | [LegacyMarineController.kt:1079](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1079) |
| `fun setNmeaOutputPreset` | [LegacyMarineController.kt:1082](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1082) |
| `fun startNmeaOutput` | [LegacyMarineController.kt:1095](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1095) |
| `fun fail` | [LegacyMarineController.kt:1103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1103) |
| `fun stopNmeaOutput` | [LegacyMarineController.kt:1110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1110) |
| `fun testNmeaDeviceOutput` | [LegacyMarineController.kt:1134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1134) |
| `fun testKnownGoodHdgOutput` | [LegacyMarineController.kt:1143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1143) |
| `fun updateDemoConfiguration` | [LegacyMarineController.kt:1150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1150) |
| `fun onPermissionsChanged` | [LegacyMarineController.kt:1158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1158) |
| `fun setAnchorSetupGpsPreview` | [LegacyMarineController.kt:1159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1159) |
| `fun saveAnchorSetupDraft` | [LegacyMarineController.kt:1160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1160) |
| `fun clearAnchorSetupDraft` | [LegacyMarineController.kt:1163](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1163) |
| `fun clearDiagnostics` | [LegacyMarineController.kt:1167](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1167) |
| `fun arm` | [LegacyMarineController.kt:1168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1168) |
| `fun updateAnchorSettings` | [LegacyMarineController.kt:1179](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1179) |
| `fun updateConditionGuards` | [LegacyMarineController.kt:1180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1180) |
| `fun resetWindBaseline` | [LegacyMarineController.kt:1181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1181) |
| `fun pauseWatch` | [LegacyMarineController.kt:1182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1182) |
| `fun resumeWatch` | [LegacyMarineController.kt:1183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1183) |
| `fun liftAnchor` | [LegacyMarineController.kt:1184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1184) |
| `fun stop` | [LegacyMarineController.kt:1185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1185) |
| `fun acknowledge` | [LegacyMarineController.kt:1186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1186) |
| `fun acceptEstimatedCenter` | [LegacyMarineController.kt:1187](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1187) |
| `fun keepCurrentCenter` | [LegacyMarineController.kt:1188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1188) |
| `fun continueEstimatingCenter` | [LegacyMarineController.kt:1189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1189) |
| `fun resetCentreAnalysis` | [LegacyMarineController.kt:1190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1190) |
| `fun recalculateCentreFromTrack` | [LegacyMarineController.kt:1191](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1191) |
| `fun dismissCentreRecalculation` | [LegacyMarineController.kt:1201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1201) |
| `fun keepCurrentRecalculatedCentre` | [LegacyMarineController.kt:1202](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1202) |
| `fun applyRecalculatedCentre` | [LegacyMarineController.kt:1203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1203) |
| `fun saveRecalculatedCentreAsAnchorage` | [LegacyMarineController.kt:1204](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1204) |
| `fun testAlarm` | [LegacyMarineController.kt:1205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1205) |
| `fun stopAlarmTest` | [LegacyMarineController.kt:1206](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1206) |
| `fun startSonarSurvey` | [LegacyMarineController.kt:1207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1207) |
| `fun stopSonarSurvey` | [LegacyMarineController.kt:1211](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1211) |
| `fun startTrip` | [LegacyMarineController.kt:1212](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1212) |
| `fun pauseTrip` | [LegacyMarineController.kt:1217](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1217) |
| `fun resumeTrip` | [LegacyMarineController.kt:1218](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1218) |
| `fun pauseTripAttitude` | [LegacyMarineController.kt:1219](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1219) |
| `fun endTrip` | [LegacyMarineController.kt:1224](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1224) |
| `fun markTripWaypoint` | [LegacyMarineController.kt:1225](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1225) |
| `fun deleteTrip` | [LegacyMarineController.kt:1226](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1226) |
| `fun renameTrip` | [LegacyMarineController.kt:1228](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1228) |
| `fun editTripMoment` | [LegacyMarineController.kt:1229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1229) |
| `fun tripReport` | [LegacyMarineController.kt:1230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1230) |
| `fun anchorReport` | [LegacyMarineController.kt:1231](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1231) |
| `fun tripReplay` | [LegacyMarineController.kt:1232](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1232) |
| `fun tripMapData` | [LegacyMarineController.kt:1233](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1233) |
| `fun openLiveTripMap` | [LegacyMarineController.kt:1234](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1234) |
| `fun openTripMap` | [LegacyMarineController.kt:1235](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1235) |
| `fun closeTripMap` | [LegacyMarineController.kt:1239](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1239) |
| `fun exportTripCsv` | [LegacyMarineController.kt:1240](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1240) |
| `fun exportTripGpx` | [LegacyMarineController.kt:1241](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1241) |
| `fun exportTripKml` | [LegacyMarineController.kt:1242](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1242) |
| `fun exportTripKmz` | [LegacyMarineController.kt:1243](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1243) |
| `fun exportTripEvents` | [LegacyMarineController.kt:1244](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1244) |
| `fun exportTripWaypoints` | [LegacyMarineController.kt:1245](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1245) |
| `fun exportTripCustomMetrics` | [LegacyMarineController.kt:1246](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1246) |
| `fun shareTripLiveSnapshot` | [LegacyMarineController.kt:1247](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1247) |
| `fun shareTripReportSnapshot` | [LegacyMarineController.kt:1248](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1248) |
| `fun exportTripAiSource` | [LegacyMarineController.kt:1249](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1249) |
| `fun exportAnchorAiSource` | [LegacyMarineController.kt:1250](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1250) |
| `fun renameSonarSurvey` | [LegacyMarineController.kt:1251](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1251) |
| `fun deleteSonarSurvey` | [LegacyMarineController.kt:1252](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1252) |
| `fun rebuildSonarSurvey` | [LegacyMarineController.kt:1253](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1253) |
| `fun selectSonarSurvey` | [LegacyMarineController.kt:1254](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1254) |
| `fun selectCorrectedSonarHistory` | [LegacyMarineController.kt:1255](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1255) |
| `fun exportSonarCsv` | [LegacyMarineController.kt:1256](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1256) |
| `fun startGpsProxy` | [LegacyMarineController.kt:1264](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1264) |
| `fun stopGpsProxy` | [LegacyMarineController.kt:1265](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1265) |
| `fun openDeveloperOptions` | [LegacyMarineController.kt:1266](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1266) |
| `fun openAlarmNotificationSettings` | [LegacyMarineController.kt:1267](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1267) |
| `fun openAlarmSoundSettings` | [LegacyMarineController.kt:1268](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1268) |
| `fun openDoNotDisturbSettings` | [LegacyMarineController.kt:1269](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1269) |
| `fun openBatteryOptimization` | [LegacyMarineController.kt:1270](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1270) |
| `fun openFullScreenAlarmSettings` | [LegacyMarineController.kt:1271](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1271) |
| `fun openAnchorInGoogleMaps` | [LegacyMarineController.kt:1275](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1275) |
| `fun openAnchorageInGoogleMaps` | [LegacyMarineController.kt:1279](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1279) |
| `fun openAnchorageCoordinates` | [LegacyMarineController.kt:1280](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1280) |
| `fun approachAnchorageSpot` | [LegacyMarineController.kt:1281](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1281) |
| `fun approachSavedAnchorage` | [LegacyMarineController.kt:1288](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1288) |
| `fun approachAnchorage` | [LegacyMarineController.kt:1292](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1292) |
| `fun confirmAnchorageApproachDisclaimer` | [LegacyMarineController.kt:1301](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1301) |
| `fun dismissAnchorageApproachDisclaimer` | [LegacyMarineController.kt:1312](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1312) |
| `fun setApproachHeadingMode` | [LegacyMarineController.kt:1323](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1323) |
| `fun cancelAnchorageApproach` | [LegacyMarineController.kt:1330](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1330) |
| `fun setPhoneHeadingDisplayActive` | [LegacyMarineController.kt:1332](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1332) |
| `fun setMapHeadingDisplayActive` | [LegacyMarineController.kt:1333](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1333) |
| `fun dismissNearbyAnchorage` | [LegacyMarineController.kt:1334](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1334) |
| `fun shareAnchorageQr` | [LegacyMarineController.kt:1344](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1344) |
| `fun page` | [LegacyMarineController.kt:1362](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1362) |
| `fun rememberAnchorSection` | [LegacyMarineController.kt:1363](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1363) |
| `fun rememberSailSection` | [LegacyMarineController.kt:1364](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1364) |
| `fun openDataSection` | [LegacyMarineController.kt:1365](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1365) |
| `fun rememberDataSection` | [LegacyMarineController.kt:1366](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1366) |
| `fun follow` | [LegacyMarineController.kt:1367](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1367) |
| `fun requestRangeEditor` | [LegacyMarineController.kt:1368](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1368) |
| `fun consumeRangeEditorRequest` | [LegacyMarineController.kt:1369](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1369) |
| `fun loadHistoryEvents` | [LegacyMarineController.kt:1370](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1370) |
| `fun saveAnchorage` | [LegacyMarineController.kt:1371](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1371) |
| `fun dismissAnchorageDuplicate` | [LegacyMarineController.kt:1380](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1380) |
| `fun deleteAnchorage` | [LegacyMarineController.kt:1381](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1381) |
| `fun dismissAnchorageOperationError` | [LegacyMarineController.kt:1390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1390) |
| `fun exportCsv` | [LegacyMarineController.kt:1391](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1391) |
| `fun exportGpx` | [LegacyMarineController.kt:1400](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1400) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MainViewModel @Inject constructor` | [MainViewModel.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L35) |
| `fun saveNmeaConnection` | [MainViewModel.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L46) |
| `fun startNmeaConnection` | [MainViewModel.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L47) |
| `fun stopNmeaConnection` | [MainViewModel.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L48) |
| `fun selectNmeaPositionConnection` | [MainViewModel.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L49) |
| `fun removeNmeaConnection` | [MainViewModel.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L50) |
| `fun setVesselMetricSource` | [MainViewModel.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L51) |
| `fun setNmeaMetricSource` | [MainViewModel.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L52) |
| `fun consumeSonarGridChanges` | [MainViewModel.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L53) |
| `fun validateProfile` | [MainViewModel.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L54) |
| `fun saveAndConnect` | [MainViewModel.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L55) |
| `fun disconnect` | [MainViewModel.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L56) |
| `fun reconnectNmea` | [MainViewModel.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L57) |
| `fun stopActiveWatchAndDisconnect` | [MainViewModel.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L58) |
| `fun stopNmeaDependenciesAndDisconnect` | [MainViewModel.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L59) |
| `fun continueTripWithPhoneAndDisconnect` | [MainViewModel.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L60) |
| `fun clearConnectionAttempt` | [MainViewModel.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L61) |
| `fun dismissRuntimeFeedback` | [MainViewModel.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L62) |
| `fun consumeRuntimeFeedback` | [MainViewModel.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L63) |
| `fun updateSettings` | [MainViewModel.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L64) |
| `fun completeOnboarding` | [MainViewModel.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L65) |
| `fun setSonarLayerEnabled` | [MainViewModel.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L66) |
| `fun exportBackup` | [MainViewModel.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L67) |
| `fun restoreBackup` | [MainViewModel.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L68) |
| `fun clearBackupResult` | [MainViewModel.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L69) |
| `fun importOfflineMap` | [MainViewModel.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L70) |
| `fun removeOfflineMap` | [MainViewModel.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L71) |
| `fun setOfflineMapEnabled` | [MainViewModel.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L72) |
| `fun createOfflineMapProvider` | [MainViewModel.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L73) |
| `fun exportSupportBundle` | [MainViewModel.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L74) |
| `fun clearSupportBundleResult` | [MainViewModel.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L75) |
| `fun clearIncidentLog` | [MainViewModel.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L76) |
| `fun clearRebuildableCaches` | [MainViewModel.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L77) |
| `fun refreshStorage` | [MainViewModel.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L78) |
| `fun confirmAlarmAudible` | [MainViewModel.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L79) |
| `fun setNmeaSharing` | [MainViewModel.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L80) |
| `fun saveLocalNmeaServerConfiguration` | [MainViewModel.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L81) |
| `fun startLocalNmeaServer` | [MainViewModel.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L82) |
| `fun saveLocalNmeaPublicationPolicy` | [MainViewModel.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L83) |
| `fun stopLocalNmeaServer` | [MainViewModel.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L84) |
| `fun stopAllNmeaSharing` | [MainViewModel.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L85) |
| `fun deleteHistorySession` | [MainViewModel.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L86) |
| `fun setMapType` | [MainViewModel.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L87) |
| `fun setGpsDataSource` | [MainViewModel.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L88) |
| `fun switchGpsDataSource` | [MainViewModel.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L89) |
| `fun setDemoMode` | [MainViewModel.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L90) |
| `fun updateVesselDataSettings` | [MainViewModel.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L91) |
| `fun createTripDashboard` | [MainViewModel.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L92) |
| `fun saveTripDashboard` | [MainViewModel.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L93) |
| `fun deleteTripDashboard` | [MainViewModel.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L94) |
| `fun reorderTripDashboards` | [MainViewModel.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L95) |
| `fun setTripLiveDisplayActive` | [MainViewModel.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L96) |
| `fun confirmTripAttitudeFrame` | [MainViewModel.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L97) |
| `fun calibrateVesselMount` | [MainViewModel.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L98) |
| `fun setPhoneVesselMounted` | [MainViewModel.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L99) |
| `fun alignPhoneHeadingToBow` | [MainViewModel.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L100) |
| `fun alignPhoneHeadingToNmea` | [MainViewModel.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L101) |
| `fun setPhoneHeadingAlignment` | [MainViewModel.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L102) |
| `fun clearVesselCalibrationFeedback` | [MainViewModel.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L103) |
| `fun setNmeaOutputEndpoint` | [MainViewModel.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L104) |
| `fun setNmeaPhonePositionPublishing` | [MainViewModel.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L105) |
| `fun setNmeaPhoneHeadingPublishing` | [MainViewModel.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L106) |
| `fun setNmeaPhoneRateOfTurnPublishing` | [MainViewModel.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L107) |
| `fun setNmeaPhoneAttitudePublishing` | [MainViewModel.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L108) |
| `fun setNmeaPhonePressurePublishing` | [MainViewModel.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L109) |
| `fun setNmeaDerivedWindPublishing` | [MainViewModel.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L110) |
| `fun setNmeaOutputPreset` | [MainViewModel.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L111) |
| `fun startNmeaOutput` | [MainViewModel.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L112) |
| `fun stopNmeaOutput` | [MainViewModel.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L113) |
| `fun testNmeaDeviceOutput` | [MainViewModel.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L114) |
| `fun testKnownGoodHdgOutput` | [MainViewModel.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L115) |
| `fun updateDemoConfiguration` | [MainViewModel.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L116) |
| `fun onPermissionsChanged` | [MainViewModel.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L117) |
| `fun setAnchorSetupGpsPreview` | [MainViewModel.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L118) |
| `fun saveAnchorSetupDraft` | [MainViewModel.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L119) |
| `fun clearAnchorSetupDraft` | [MainViewModel.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L120) |
| `fun clearDiagnostics` | [MainViewModel.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L121) |
| `fun arm` | [MainViewModel.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L122) |
| `fun updateAnchorSettings` | [MainViewModel.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L123) |
| `fun updateConditionGuards` | [MainViewModel.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L124) |
| `fun resetWindBaseline` | [MainViewModel.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L125) |
| `fun pauseWatch` | [MainViewModel.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L126) |
| `fun resumeWatch` | [MainViewModel.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L127) |
| `fun liftAnchor` | [MainViewModel.kt:128](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L128) |
| `fun stop` | [MainViewModel.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L129) |
| `fun acknowledge` | [MainViewModel.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L130) |
| `fun acceptEstimatedCenter` | [MainViewModel.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L131) |
| `fun keepCurrentCenter` | [MainViewModel.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L132) |
| `fun continueEstimatingCenter` | [MainViewModel.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L133) |
| `fun resetCentreAnalysis` | [MainViewModel.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L134) |
| `fun recalculateCentreFromTrack` | [MainViewModel.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L135) |
| `fun dismissCentreRecalculation` | [MainViewModel.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L136) |
| `fun keepCurrentRecalculatedCentre` | [MainViewModel.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L137) |
| `fun applyRecalculatedCentre` | [MainViewModel.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L138) |
| `fun saveRecalculatedCentreAsAnchorage` | [MainViewModel.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L139) |
| `fun testAlarm` | [MainViewModel.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L140) |
| `fun stopAlarmTest` | [MainViewModel.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L141) |
| `fun startSonarSurvey` | [MainViewModel.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L142) |
| `fun stopSonarSurvey` | [MainViewModel.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L143) |
| `fun startTrip` | [MainViewModel.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L144) |
| `fun pauseTrip` | [MainViewModel.kt:145](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L145) |
| `fun resumeTrip` | [MainViewModel.kt:146](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L146) |
| `fun pauseTripAttitude` | [MainViewModel.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L147) |
| `fun endTrip` | [MainViewModel.kt:148](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L148) |
| `fun markTripWaypoint` | [MainViewModel.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L149) |
| `fun deleteTrip` | [MainViewModel.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L150) |
| `fun renameTrip` | [MainViewModel.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L151) |
| `fun editTripMoment` | [MainViewModel.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L152) |
| `fun tripReport` | [MainViewModel.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L153) |
| `fun anchorReport` | [MainViewModel.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L154) |
| `fun tripReplay` | [MainViewModel.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L155) |
| `fun tripMapData` | [MainViewModel.kt:156](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L156) |
| `fun openLiveTripMap` | [MainViewModel.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L157) |
| `fun openTripMap` | [MainViewModel.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L158) |
| `fun closeTripMap` | [MainViewModel.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L159) |
| `fun exportTripCsv` | [MainViewModel.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L160) |
| `fun exportTripGpx` | [MainViewModel.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L161) |
| `fun exportTripKml` | [MainViewModel.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L162) |
| `fun exportTripKmz` | [MainViewModel.kt:163](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L163) |
| `fun exportTripEvents` | [MainViewModel.kt:164](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L164) |
| `fun exportTripWaypoints` | [MainViewModel.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L165) |
| `fun exportTripCustomMetrics` | [MainViewModel.kt:166](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L166) |
| `fun shareTripLiveSnapshot` | [MainViewModel.kt:167](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L167) |
| `fun shareTripReportSnapshot` | [MainViewModel.kt:168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L168) |
| `fun exportTripAiSource` | [MainViewModel.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L169) |
| `fun exportAnchorAiSource` | [MainViewModel.kt:170](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L170) |
| `fun renameSonarSurvey` | [MainViewModel.kt:171](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L171) |
| `fun deleteSonarSurvey` | [MainViewModel.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L172) |
| `fun rebuildSonarSurvey` | [MainViewModel.kt:173](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L173) |
| `fun selectSonarSurvey` | [MainViewModel.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L174) |
| `fun selectCorrectedSonarHistory` | [MainViewModel.kt:175](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L175) |
| `fun exportSonarCsv` | [MainViewModel.kt:176](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L176) |
| `fun startGpsProxy` | [MainViewModel.kt:177](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L177) |
| `fun stopGpsProxy` | [MainViewModel.kt:178](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L178) |
| `fun openDeveloperOptions` | [MainViewModel.kt:179](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L179) |
| `fun openAlarmNotificationSettings` | [MainViewModel.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L180) |
| `fun openAlarmSoundSettings` | [MainViewModel.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L181) |
| `fun openDoNotDisturbSettings` | [MainViewModel.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L182) |
| `fun openBatteryOptimization` | [MainViewModel.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L183) |
| `fun openFullScreenAlarmSettings` | [MainViewModel.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L184) |
| `fun openAnchorInGoogleMaps` | [MainViewModel.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L185) |
| `fun openAnchorageInGoogleMaps` | [MainViewModel.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L186) |
| `fun openAnchorageCoordinates` | [MainViewModel.kt:187](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L187) |
| `fun approachAnchorageSpot` | [MainViewModel.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L188) |
| `fun approachSavedAnchorage` | [MainViewModel.kt:189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L189) |
| `fun approachAnchorage` | [MainViewModel.kt:190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L190) |
| `fun confirmAnchorageApproachDisclaimer` | [MainViewModel.kt:191](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L191) |
| `fun dismissAnchorageApproachDisclaimer` | [MainViewModel.kt:192](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L192) |
| `fun setApproachHeadingMode` | [MainViewModel.kt:193](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L193) |
| `fun cancelAnchorageApproach` | [MainViewModel.kt:194](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L194) |
| `fun setPhoneHeadingDisplayActive` | [MainViewModel.kt:195](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L195) |
| `fun setMapHeadingDisplayActive` | [MainViewModel.kt:196](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L196) |
| `fun dismissNearbyAnchorage` | [MainViewModel.kt:197](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L197) |
| `fun shareAnchorageQr` | [MainViewModel.kt:198](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L198) |
| `fun page` | [MainViewModel.kt:199](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L199) |
| `fun rememberAnchorSection` | [MainViewModel.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L200) |
| `fun rememberSailSection` | [MainViewModel.kt:201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L201) |
| `fun openDataSection` | [MainViewModel.kt:202](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L202) |
| `fun rememberDataSection` | [MainViewModel.kt:203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L203) |
| `fun follow` | [MainViewModel.kt:204](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L204) |
| `fun requestRangeEditor` | [MainViewModel.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L205) |
| `fun consumeRangeEditorRequest` | [MainViewModel.kt:206](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L206) |
| `fun loadHistoryEvents` | [MainViewModel.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L207) |
| `fun saveAnchorage` | [MainViewModel.kt:208](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L208) |
| `fun dismissAnchorageDuplicate` | [MainViewModel.kt:209](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L209) |
| `fun deleteAnchorage` | [MainViewModel.kt:210](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L210) |
| `fun dismissAnchorageOperationError` | [MainViewModel.kt:211](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L211) |
| `fun exportCsv` | [MainViewModel.kt:212](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L212) |
| `fun exportGpx` | [MainViewModel.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/MainViewModel.kt#L213) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/DisplayLeaseRegistry.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun acquire` | [DisplayLeaseRegistry.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/DisplayLeaseRegistry.kt#L11) |
| `object : DisplayLease` | [DisplayLeaseRegistry.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/DisplayLeaseRegistry.kt#L14) |
| `fun close` | [DisplayLeaseRegistry.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/DisplayLeaseRegistry.kt#L17) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalMarineContentService @Inject constructor` | [LocalMarineContentService.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L31) |
| `fun import` | [LocalMarineContentService.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L58) |
| `fun delete` | [LocalMarineContentService.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L59) |
| `fun file` | [LocalMarineContentService.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L60) |
| `fun observeRecentAlarmEvents` | [LocalMarineContentService.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L63) |
| `fun observeCollectionMembers` | [LocalMarineContentService.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L65) |
| `fun anchorTrackPage` | [LocalMarineContentService.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L71) |
| `fun bundle` | [LocalMarineContentService.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L74) |
| `fun updatePlace` | [LocalMarineContentService.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L75) |
| `fun updateSpot` | [LocalMarineContentService.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L76) |
| `fun createSpot` | [LocalMarineContentService.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L78) |
| `fun archivePlace` | [LocalMarineContentService.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L86) |
| `fun restorePlace` | [LocalMarineContentService.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L94) |
| `fun createCollection` | [LocalMarineContentService.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L98) |
| `fun toggleCollection` | [LocalMarineContentService.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L106) |
| `fun saveAnchorage` | [LocalMarineContentService.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L114) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalMarineServices @Inject constructor` | [LocalMarineServices.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L41) |
| `fun onPermissionsChanged` | [LocalMarineServices.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L51) |
| `fun switchGpsDataSource` | [LocalMarineServices.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L52) |
| `fun selectNmeaPositionConnection` | [LocalMarineServices.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L53) |
| `fun setVesselMetricSource` | [LocalMarineServices.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L54) |
| `fun confirmTripAttitudeFrame` | [LocalMarineServices.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L55) |
| `fun alignPhoneHeadingToBow` | [LocalMarineServices.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L56) |
| `fun alignPhoneHeadingToNmea` | [LocalMarineServices.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L57) |
| `fun clearVesselCalibrationFeedback` | [LocalMarineServices.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L58) |
| `fun startTrip` | [LocalMarineServices.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L63) |
| `fun pauseTrip` | [LocalMarineServices.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L64) |
| `fun resumeTrip` | [LocalMarineServices.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L65) |
| `fun pauseTripAttitude` | [LocalMarineServices.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L66) |
| `fun endTrip` | [LocalMarineServices.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L67) |
| `fun markTripWaypoint` | [LocalMarineServices.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L68) |
| `fun deleteTrip` | [LocalMarineServices.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L69) |
| `fun renameTrip` | [LocalMarineServices.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L70) |
| `fun editTripMoment` | [LocalMarineServices.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L71) |
| `fun tripReport` | [LocalMarineServices.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L72) |
| `fun tripReplay` | [LocalMarineServices.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L73) |
| `fun tripMapData` | [LocalMarineServices.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L74) |
| `fun exportTripCsv` | [LocalMarineServices.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L75) |
| `fun exportTripGpx` | [LocalMarineServices.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L76) |
| `fun exportTripKml` | [LocalMarineServices.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L77) |
| `fun exportTripKmz` | [LocalMarineServices.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L78) |
| `fun exportTripEvents` | [LocalMarineServices.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L79) |
| `fun exportTripWaypoints` | [LocalMarineServices.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L80) |
| `fun exportTripCustomMetrics` | [LocalMarineServices.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L81) |
| `fun shareTripReportSnapshot` | [LocalMarineServices.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L82) |
| `fun exportTripAiSource` | [LocalMarineServices.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L83) |
| `fun saveAnchorSetupDraft` | [LocalMarineServices.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L88) |
| `fun clearAnchorSetupDraft` | [LocalMarineServices.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L89) |
| `fun arm` | [LocalMarineServices.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L90) |
| `fun updateAnchorSettings` | [LocalMarineServices.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L91) |
| `fun updateConditionGuards` | [LocalMarineServices.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L92) |
| `fun pauseWatch` | [LocalMarineServices.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L93) |
| `fun resumeWatch` | [LocalMarineServices.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L94) |
| `fun liftAnchor` | [LocalMarineServices.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L95) |
| `fun acknowledge` | [LocalMarineServices.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L96) |
| `fun keepCurrentCenter` | [LocalMarineServices.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L97) |
| `fun continueEstimatingCenter` | [LocalMarineServices.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L98) |
| `fun recalculateCentreFromTrack` | [LocalMarineServices.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L99) |
| `fun keepCurrentRecalculatedCentre` | [LocalMarineServices.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L100) |
| `fun acceptEstimatedCenter` | [LocalMarineServices.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L101) |
| `fun applyRecalculatedCentre` | [LocalMarineServices.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L102) |
| `fun loadHistoryEvents` | [LocalMarineServices.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L103) |
| `fun exportCsv` | [LocalMarineServices.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L104) |
| `fun exportGpx` | [LocalMarineServices.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L105) |
| `fun saveNmeaConnection` | [LocalMarineServices.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L111) |
| `fun startNmeaConnection` | [LocalMarineServices.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L112) |
| `fun stopNmeaConnection` | [LocalMarineServices.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L113) |
| `fun removeNmeaConnection` | [LocalMarineServices.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L114) |
| `fun saveAndConnect` | [LocalMarineServices.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L115) |
| `fun disconnect` | [LocalMarineServices.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L116) |
| `fun setNmeaSharing` | [LocalMarineServices.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L121) |
| `fun saveLocalNmeaPublicationPolicy` | [LocalMarineServices.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L122) |
| `fun startLocalNmeaServer` | [LocalMarineServices.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L123) |
| `fun stopLocalNmeaServer` | [LocalMarineServices.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L124) |
| `fun stopAllNmeaSharing` | [LocalMarineServices.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L125) |
| `fun setLanguage` | [LocalMarineServices.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L130) |
| `fun setVesselGeometry` | [LocalMarineServices.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L131) |
| `fun setVesselIdentity` | [LocalMarineServices.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L133) |
| `fun setAlarmSound` | [LocalMarineServices.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L134) |
| `fun setAlarmSnoozeMinutes` | [LocalMarineServices.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L135) |
| `fun setInstrumentLayout` | [LocalMarineServices.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L136) |
| `fun exportBackup` | [LocalMarineServices.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L137) |
| `fun restoreBackup` | [LocalMarineServices.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L138) |
| `fun clearBackupResult` | [LocalMarineServices.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L139) |
| `fun confirmAlarmAudible` | [LocalMarineServices.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L140) |
| `fun testAlarm` | [LocalMarineServices.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L141) |
| `fun stopAlarmTest` | [LocalMarineServices.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L142) |
| `fun openAlarmSoundSettings` | [LocalMarineServices.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L143) |
| `fun openDoNotDisturbSettings` | [LocalMarineServices.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L144) |
| `fun consumeRuntimeFeedback` | [LocalMarineServices.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L150) |
| `fun acquireMapHeading` | [LocalMarineServices.kt:156](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L156) |
| `fun acquireInstruments` | [LocalMarineServices.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L157) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineLibrarySnapshot` | [MarineContentService.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L16) |
| `interface MarinePhotoService` | [MarineContentService.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L25) |
| `fun import` | [MarineContentService.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L26) |
| `fun delete` | [MarineContentService.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L27) |
| `fun file` | [MarineContentService.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L28) |
| `interface MarineContentService` | [MarineContentService.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L36) |
| `fun observeRecentAlarmEvents` | [MarineContentService.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L39) |
| `fun observeCollectionMembers` | [MarineContentService.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L40) |
| `fun anchorTrackPage` | [MarineContentService.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L42) |
| `fun bundle` | [MarineContentService.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L43) |
| `fun updatePlace` | [MarineContentService.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L44) |
| `fun updateSpot` | [MarineContentService.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L45) |
| `fun createSpot` | [MarineContentService.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L46) |
| `fun archivePlace` | [MarineContentService.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L47) |
| `fun restorePlace` | [MarineContentService.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L48) |
| `fun createCollection` | [MarineContentService.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L49) |
| `fun toggleCollection` | [MarineContentService.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L50) |
| `fun saveAnchorage` | [MarineContentService.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L52) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MarineStateReader` | [MarineServices.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L36) |
| `interface DataSourceService : MarineStateReader` | [MarineServices.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L41) |
| `fun onPermissionsChanged` | [MarineServices.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L43) |
| `fun switchGpsDataSource` | [MarineServices.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L44) |
| `fun selectNmeaPositionConnection` | [MarineServices.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L45) |
| `fun setVesselMetricSource` | [MarineServices.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L46) |
| `fun confirmTripAttitudeFrame` | [MarineServices.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L47) |
| `fun alignPhoneHeadingToBow` | [MarineServices.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L48) |
| `fun alignPhoneHeadingToNmea` | [MarineServices.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L49) |
| `fun clearVesselCalibrationFeedback` | [MarineServices.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L50) |
| `interface VoyageService : MarineStateReader` | [MarineServices.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L54) |
| `fun startTrip` | [MarineServices.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L55) |
| `fun pauseTrip` | [MarineServices.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L56) |
| `fun resumeTrip` | [MarineServices.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L57) |
| `fun pauseTripAttitude` | [MarineServices.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L58) |
| `fun endTrip` | [MarineServices.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L59) |
| `fun markTripWaypoint` | [MarineServices.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L60) |
| `fun deleteTrip` | [MarineServices.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L61) |
| `fun renameTrip` | [MarineServices.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L62) |
| `fun editTripMoment` | [MarineServices.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L63) |
| `fun tripReport` | [MarineServices.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L64) |
| `fun tripReplay` | [MarineServices.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L65) |
| `fun tripMapData` | [MarineServices.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L66) |
| `fun exportTripCsv` | [MarineServices.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L67) |
| `fun exportTripGpx` | [MarineServices.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L68) |
| `fun exportTripKml` | [MarineServices.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L69) |
| `fun exportTripKmz` | [MarineServices.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L70) |
| `fun exportTripEvents` | [MarineServices.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L71) |
| `fun exportTripWaypoints` | [MarineServices.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L72) |
| `fun exportTripCustomMetrics` | [MarineServices.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L73) |
| `fun shareTripReportSnapshot` | [MarineServices.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L74) |
| `fun exportTripAiSource` | [MarineServices.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L75) |
| `interface AnchorService : MarineStateReader` | [MarineServices.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L79) |
| `fun saveAnchorSetupDraft` | [MarineServices.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L80) |
| `fun clearAnchorSetupDraft` | [MarineServices.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L81) |
| `fun arm` | [MarineServices.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L82) |
| `fun updateAnchorSettings` | [MarineServices.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L83) |
| `fun updateConditionGuards` | [MarineServices.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L84) |
| `fun pauseWatch` | [MarineServices.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L85) |
| `fun resumeWatch` | [MarineServices.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L86) |
| `fun liftAnchor` | [MarineServices.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L87) |
| `fun acknowledge` | [MarineServices.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L88) |
| `fun keepCurrentCenter` | [MarineServices.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L89) |
| `fun continueEstimatingCenter` | [MarineServices.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L90) |
| `fun recalculateCentreFromTrack` | [MarineServices.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L91) |
| `fun keepCurrentRecalculatedCentre` | [MarineServices.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L92) |
| `fun acceptEstimatedCenter` | [MarineServices.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L93) |
| `fun applyRecalculatedCentre` | [MarineServices.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L94) |
| `fun loadHistoryEvents` | [MarineServices.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L95) |
| `fun exportCsv` | [MarineServices.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L96) |
| `fun exportGpx` | [MarineServices.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L97) |
| `interface NetworkService : MarineStateReader` | [MarineServices.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L101) |
| `fun saveNmeaConnection` | [MarineServices.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L103) |
| `fun startNmeaConnection` | [MarineServices.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L104) |
| `fun stopNmeaConnection` | [MarineServices.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L105) |
| `fun removeNmeaConnection` | [MarineServices.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L106) |
| `fun saveAndConnect` | [MarineServices.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L107) |
| `fun disconnect` | [MarineServices.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L108) |
| `interface SharingService : MarineStateReader` | [MarineServices.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L112) |
| `fun setNmeaSharing` | [MarineServices.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L113) |
| `fun saveLocalNmeaPublicationPolicy` | [MarineServices.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L114) |
| `fun startLocalNmeaServer` | [MarineServices.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L115) |
| `fun stopLocalNmeaServer` | [MarineServices.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L116) |
| `fun stopAllNmeaSharing` | [MarineServices.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L117) |
| `interface VesselPreferencesService : MarineStateReader` | [MarineServices.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L124) |
| `fun setLanguage` | [MarineServices.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L125) |
| `fun setVesselGeometry` | [MarineServices.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L126) |
| `fun setVesselIdentity` | [MarineServices.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L127) |
| `fun setAlarmSound` | [MarineServices.kt:128](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L128) |
| `fun setAlarmSnoozeMinutes` | [MarineServices.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L129) |
| `fun setInstrumentLayout` | [MarineServices.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L130) |
| `fun exportBackup` | [MarineServices.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L131) |
| `fun restoreBackup` | [MarineServices.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L132) |
| `fun clearBackupResult` | [MarineServices.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L133) |
| `fun confirmAlarmAudible` | [MarineServices.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L134) |
| `fun testAlarm` | [MarineServices.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L135) |
| `fun stopAlarmTest` | [MarineServices.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L136) |
| `fun openAlarmSoundSettings` | [MarineServices.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L137) |
| `fun openDoNotDisturbSettings` | [MarineServices.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L138) |
| `interface MarineFeedbackService : MarineStateReader` | [MarineServices.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L142) |
| `fun consumeRuntimeFeedback` | [MarineServices.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L144) |
| `interface DisplayLease : AutoCloseable` | [MarineServices.kt:148](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L148) |
| `fun close` | [MarineServices.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L149) |
| `interface DisplayDemandService` | [MarineServices.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L153) |
| `fun acquireMapHeading` | [MarineServices.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L154) |
| `fun acquireInstruments` | [MarineServices.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L155) |
| `interface MarineServices : MarineStateReader` | [MarineServices.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L162) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AlarmUiRepository @Inject constructor` | [AlarmUiRepository.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L11) |
| `fun publish` | [AlarmUiRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L15) |
| `fun clear` | [AlarmUiRepository.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L16) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaInstrumentState` | [NavigationRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L25) |
| `class NavigationRepository @Inject constructor` | [NavigationRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L39) |
| `fun positionSourcePin` | [NavigationRepository.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L66) |
| `fun positionConnectionId` | [NavigationRepository.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L67) |
| `fun saveConnection` | [NavigationRepository.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L100) |
| `fun cycle` | [NavigationRepository.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L110) |
| `fun removeConnection` | [NavigationRepository.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L118) |
| `fun startConnection` | [NavigationRepository.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L119) |
| `fun stopConnection` | [NavigationRepository.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L147) |
| `fun reconnectConnection` | [NavigationRepository.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L159) |
| `fun connectionEpoch` | [NavigationRepository.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L160) |
| `fun connectionPeer` | [NavigationRepository.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L161) |
| `fun isConnectionOpen` | [NavigationRepository.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L162) |
| `fun inputConnectionIds` | [NavigationRepository.kt:163](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L163) |
| `fun anyRequested` | [NavigationRepository.kt:164](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L164) |
| `fun sourcesCurrent` | [NavigationRepository.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L165) |
| `fun writeConnection` | [NavigationRepository.kt:166](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L166) |
| `fun publishInputCandidates` | [NavigationRepository.kt:187](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L187) |
| `fun recordDroppedOutput` | [NavigationRepository.kt:190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L190) |
| `fun identify` | [NavigationRepository.kt:210](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L210) |
| `fun selectPositionConnection` | [NavigationRepository.kt:251](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L251) |
| `fun ensurePositionConnection` | [NavigationRepository.kt:260](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L260) |
| `fun connectionPriorities` | [NavigationRepository.kt:265](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L265) |
| `fun number` | [NavigationRepository.kt:274](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L274) |
| `fun currentHeading` | [NavigationRepository.kt:275](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L275) |
| `fun wind` | [NavigationRepository.kt:284](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L284) |
| `fun connect` | [NavigationRepository.kt:299](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L299) |
| `fun reconnect` | [NavigationRepository.kt:300](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L300) |
| `fun disconnect` | [NavigationRepository.kt:301](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L301) |
| `fun disconnectAll` | [NavigationRepository.kt:302](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L302) |
| `fun acquireBackgroundConnection` | [NavigationRepository.kt:306](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L306) |
| `fun claimBackgroundConnectionIfConnected` | [NavigationRepository.kt:311](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L311) |
| `fun releaseBackgroundConnection` | [NavigationRepository.kt:312](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L312) |
| `fun clearUserDisconnectLatch` | [NavigationRepository.kt:313](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L313) |
| `fun setSafetyOwnedRetry` | [NavigationRepository.kt:314](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L314) |
| `fun isUserDisconnected` | [NavigationRepository.kt:315](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L315) |
| `fun hasOpenTransport` | [NavigationRepository.kt:316](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L316) |
| `fun activeProfileStableId` | [NavigationRepository.kt:317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L317) |
| `fun connectionGeneration` | [NavigationRepository.kt:318](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L318) |
| `fun pinBoatHeadingSource` | [NavigationRepository.kt:319](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L319) |
| `fun clearDiagnostics` | [NavigationRepository.kt:320](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L320) |
| `fun writeToBoat` | [NavigationRepository.kt:321](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L321) |
| `fun writeToBoatExpected` | [NavigationRepository.kt:322](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L322) |
| `fun accept` | [NavigationRepository.kt:328](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L328) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageApproachRepository @Inject constructor` | [AnchorageApproachRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L24) |
| `fun save` | [AnchorageApproachRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L41) |
| `fun delete` | [AnchorageApproachRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L59) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rebuild` | [AnchorageIntelligenceRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L15) |
| `fun rebuildAll` | [AnchorageIntelligenceRepository.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L20) |
| `fun decode` | [AnchorageIntelligenceRepository.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L21) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageLegacyMigrationHealth` | [AnchorageLegacyMigrationVerifier.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt#L11) |
| `fun verifyOnce` | [AnchorageLegacyMigrationVerifier.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt#L14) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchoragePlaceBundle` | [AnchorageLibraryRepository.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L11) |
| `fun get` | [AnchorageLibraryRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L28) |
| `fun save` | [AnchorageLibraryRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L29) |
| `fun delete` | [AnchorageLibraryRepository.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L32) |
| `fun get` | [AnchorageLibraryRepository.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L37) |
| `fun forPlace` | [AnchorageLibraryRepository.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L38) |
| `fun save` | [AnchorageLibraryRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L39) |
| `fun delete` | [AnchorageLibraryRepository.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L43) |
| `fun save` | [AnchorageLibraryRepository.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L47) |
| `fun forPlace` | [AnchorageLibraryRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L53) |
| `fun bundle` | [AnchorageLibraryRepository.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L63) |
| `fun viewport` | [AnchorageLibraryRepository.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L68) |
| `fun nearby` | [AnchorageLibraryRepository.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L69) |
| `class AnchorageNearbyPlace` | [AnchorageLibraryRepository.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L76) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchoragePhotoRepository @Inject constructor` | [AnchoragePhotoRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L22) |
| `fun import` | [AnchoragePhotoRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L28) |
| `fun delete` | [AnchoragePhotoRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L57) |
| `fun cleanupOrphans` | [AnchoragePhotoRepository.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L63) |
| `fun file` | [AnchoragePhotoRepository.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L68) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SeabedType` | [AnchorageRepository.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L12) |
| `class AnchorageCoordinateSource` | [AnchorageRepository.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L13) |
| `class AnchorageSavePosition` | [AnchorageRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L15) |
| `object AnchorageSavePositionPolicy` | [AnchorageRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L17) |
| `fun resolve` | [AnchorageRepository.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L18) |
| `class DuplicateAnchorageException` | [AnchorageRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L29) |
| `fun get` | [AnchorageRepository.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L37) |
| `fun nearby` | [AnchorageRepository.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L38) |
| `fun duplicate` | [AnchorageRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L39) |
| `fun save` | [AnchorageRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L41) |
| `fun saveFromSession` | [AnchorageRepository.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L50) |
| `fun delete` | [AnchorageRepository.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L55) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageSavePlaceInput` | [AnchorageSaveRepository.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L12) |
| `class AnchorageSaveSpotInput` | [AnchorageSaveRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L23) |
| `class AnchorageSaveRequest` | [AnchorageSaveRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L29) |
| `class AnchorageSaveResult` | [AnchorageSaveRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L30) |
| `object AnchorageSaveDraftFactory` | [AnchorageSaveRepository.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L32) |
| `fun fromSession` | [AnchorageSaveRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L33) |
| `fun fromMap` | [AnchorageSaveRepository.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L37) |
| `class AnchorageSaveRepository @Inject constructor` | [AnchorageSaveRepository.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L40) |
| `fun nearbyPlaceMatches` | [AnchorageSaveRepository.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L48) |
| `fun nearbySpotMatches` | [AnchorageSaveRepository.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L52) |
| `fun save` | [AnchorageSaveRepository.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L54) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSearchRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun search` | [AnchorageSearchRepository.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSearchRepository.kt#L11) |
| `fun rebuildPlace` | [AnchorageSearchRepository.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSearchRepository.kt#L12) |
| `fun removePlace` | [AnchorageSearchRepository.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSearchRepository.kt#L19) |
| `fun rebuildAll` | [AnchorageSearchRepository.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSearchRepository.kt#L20) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageShareCardRow` | [AnchorageShare.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L24) |
| `class AnchorageShareCardModel` | [AnchorageShare.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L25) |
| `class AnchorageShareDestinations` | [AnchorageShare.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L26) |
| `object AnchorageShareContent` | [AnchorageShare.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L29) |
| `fun coordinates` | [AnchorageShare.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L31) |
| `fun googleMapsUrl` | [AnchorageShare.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L34) |
| `fun shareText` | [AnchorageShare.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L37) |
| `fun cardModel` | [AnchorageShare.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L59) |
| `object AnchorageShareQrContract` | [AnchorageShare.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L83) |
| `fun destinations` | [AnchorageShare.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L84) |
| `class AnchorageQrImageGenerator @Inject constructor` | [AnchorageShare.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L92) |
| `fun generate` | [AnchorageShare.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L95) |
| `fun generate` | [AnchorageShare.kt:230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageShare.kt#L230) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageSharePayloadV1` | [AnchorageSharePayloadCodec.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L9) |
| `class EncodedAnchorageShareV1` | [AnchorageSharePayloadCodec.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L25) |
| `class AnchorageSharePayloadV2` | [AnchorageSharePayloadCodec.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L31) |
| `class EncodedAnchorageShareV2` | [AnchorageSharePayloadCodec.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L38) |
| `interface AnchorageQrDecodeResult` | [AnchorageSharePayloadCodec.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L40) |
| `class Full` | [AnchorageSharePayloadCodec.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L41) |
| `class FullV2` | [AnchorageSharePayloadCodec.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L42) |
| `class Coordinate` | [AnchorageSharePayloadCodec.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L43) |
| `class UnsupportedVersion` | [AnchorageSharePayloadCodec.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L44) |
| `class Invalid` | [AnchorageSharePayloadCodec.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L45) |
| `object Unsupported:AnchorageQrDecodeResult` | [AnchorageSharePayloadCodec.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L46) |
| `object AnchorageSharePayloadCodec` | [AnchorageSharePayloadCodec.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L50) |
| `fun encode` | [AnchorageSharePayloadCodec.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L53) |
| `fun pack` | [AnchorageSharePayloadCodec.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L72) |
| `fun encodeV2` | [AnchorageSharePayloadCodec.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L92) |
| `fun decode` | [AnchorageSharePayloadCodec.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L102) |
| `fun toEntity` | [AnchorageSharePayloadCodec.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L134) |
| `fun coordinateEntity` | [AnchorageSharePayloadCodec.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L142) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageSpatialIndexHealth` | [AnchorageSpatialIndexRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L17) |
| `class AnchorageSpatialIndexRepository @Inject constructor` | [AnchorageSpatialIndexRepository.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L20) |
| `fun viewport` | [AnchorageSpatialIndexRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L24) |
| `fun spotsInViewport` | [AnchorageSpatialIndexRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L28) |
| `fun nearbySpots` | [AnchorageSpatialIndexRepository.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L32) |
| `fun upsertPlace` | [AnchorageSpatialIndexRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L41) |
| `fun upsertSpot` | [AnchorageSpatialIndexRepository.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L44) |
| `fun deletePlace` | [AnchorageSpatialIndexRepository.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L48) |
| `fun deleteSpot` | [AnchorageSpatialIndexRepository.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L49) |
| `fun verifyAndRepair` | [AnchorageSpatialIndexRepository.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L51) |
| `fun count` | [AnchorageSpatialIndexRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSpatialIndexRepository.kt#L53) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/AnchorageRegionCandidateService.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun resolve` | [AnchorageRegionCandidateService.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/AnchorageRegionCandidateService.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt

| 声明 | 实现位置 |
| --- | --- |
| `class GazetteerHttpResponse` | [LinzGazetteerProvider.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L17) |
| `class LinzGazetteerTransport @Inject constructor` | [LinzGazetteerProvider.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L18) |
| `fun get` | [LinzGazetteerProvider.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L19) |
| `fun resolveCandidates` | [LinzGazetteerProvider.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L27) |
| `object LinzGazetteerParser` | [LinzGazetteerProvider.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L45) |
| `fun parse` | [LinzGazetteerProvider.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LinzGazetteerProvider.kt#L46) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LocalRegionProviders.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun resolveCandidates` | [LocalRegionProviders.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LocalRegionProviders.kt#L12) |
| `fun resolveCandidates` | [LocalRegionProviders.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LocalRegionProviders.kt#L20) |
| `fun AnchorageRegionEntity.candidate` | [LocalRegionProviders.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LocalRegionProviders.kt#L25) |
| `fun bounds` | [LocalRegionProviders.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/gis/LocalRegionProviders.kt#L31) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BackupAnchorSessionV1` | [BackupDtosV1.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L10) |
| `fun toEntity` | [BackupDtosV1.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L27) |
| `class BackupTrackPointV1` | [BackupDtosV1.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L31) |
| `fun toEntity` | [BackupDtosV1.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L40) |
| `class BackupAlarmEventV1` | [BackupDtosV1.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L44) |
| `fun toEntity` | [BackupDtosV1.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L45) |
| `class BackupSonarSurveyV1` | [BackupDtosV1.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L49) |
| `fun toEntity` | [BackupDtosV1.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L55) |
| `class BackupDepthSampleV1` | [BackupDtosV1.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L59) |
| `fun toEntity` | [BackupDtosV1.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV1.kt#L70) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV2.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BackupAnchorSessionV2` | [BackupDtosV2.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV2.kt#L7) |
| `fun toEntity` | [BackupDtosV2.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV2.kt#L25) |
| `class BackupSavedAnchorageV2` | [BackupDtosV2.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV2.kt#L44) |
| `fun toEntity` | [BackupDtosV2.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV2.kt#L48) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BackupTripSessionV3` | [BackupDtosV3.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L9) |
| `class BackupTripSampleV3` | [BackupDtosV3.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L10) |
| `class BackupTripEventV3` | [BackupDtosV3.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L11) |
| `class BackupTripWaypointV3` | [BackupDtosV3.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L12) |
| `class BackupAnchorTelemetryV3` | [BackupDtosV3.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L13) |
| `class BackupTripCustomMetricV4` | [BackupDtosV3.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L14) |
| `class BackupTripDashboardV4` | [BackupDtosV3.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L15) |
| `class BackupVesselSettingsV3` | [BackupDtosV3.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/BackupDtosV3.kt#L16) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BackupOperationState` | [YokuliBackupManager.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L67) |
| `class BackupManifestV1` | [YokuliBackupManager.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L75) |
| `class BackupRecordV1` | [YokuliBackupManager.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L91) |
| `class BackupSettingsV1` | [YokuliBackupManager.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L92) |
| `class BackupValidation` | [YokuliBackupManager.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L98) |
| `object BackupRestorePolicy` | [YokuliBackupManager.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L134) |
| `fun blockingReason` | [YokuliBackupManager.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L135) |
| `object BackupExternalSettingsPolicy` | [YokuliBackupManager.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L149) |
| `fun reconcileOfflineMap` | [YokuliBackupManager.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L150) |
| `object YokuliBackupArchive` | [YokuliBackupManager.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L155) |
| `fun requiredFor` | [YokuliBackupManager.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L200) |
| `fun sha256` | [YokuliBackupManager.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L205) |
| `fun validateCoordinate` | [YokuliBackupManager.kt:211](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L211) |
| `class YokuliBackupManager @Inject constructor` | [YokuliBackupManager.kt:218](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L218) |
| `fun export` | [YokuliBackupManager.kt:242](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L242) |
| `fun restore` | [YokuliBackupManager.kt:335](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L335) |
| `fun clearResult` | [YokuliBackupManager.kt:414](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L414) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveDepthRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LiveDepthState` | [LiveDepthRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveDepthRepository.kt#L17) |
| `class LiveDepthRepository @Inject constructor` | [LiveDepthRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveDepthRepository.kt#L30) |
| `fun accept` | [LiveDepthRepository.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveDepthRepository.kt#L44) |
| `fun clear` | [LiveDepthRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveDepthRepository.kt#L53) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TimedWindValue` | [LiveWindRepository.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L13) |
| `class LiveWindState` | [LiveWindRepository.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L14) |
| `fun speed` | [LiveWindRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L22) |
| `fun direction` | [LiveWindRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L25) |
| `class LiveWindRepository @Inject constructor` | [LiveWindRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L30) |
| `fun accept` | [LiveWindRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L34) |
| `fun numeric` | [LiveWindRepository.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L36) |
| `fun measured` | [LiveWindRepository.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L37) |
| `fun publishSelected` | [LiveWindRepository.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L56) |
| `fun clear` | [LiveWindRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L57) |
| `fun invalidate` | [LiveWindRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/condition/LiveWindRepository.kt#L58) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt

| 声明 | 实现位置 |
| --- | --- |
| `object AnchorageSpatialSchema` | [AnchorageSpatialSchema.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt#L9) |
| `fun ensure` | [AnchorageSpatialSchema.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt#L10) |
| `object AnchorageDatabaseCallback:RoomDatabase.Callback` | [AnchorageSpatialSchema.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt#L23) |
| `fun onCreate` | [AnchorageSpatialSchema.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt#L24) |
| `fun onOpen` | [AnchorageSpatialSchema.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/AnchorageSpatialSchema.kt#L25) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorSessionEntity` | [Database.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L22) |
| `class SavedAnchorageEntity` | [Database.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L119) |
| `class TrackPointEntity` | [Database.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L150) |
| `class AnchorPointCountSnapshot` | [Database.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L182) |
| `class AlarmEventEntity` | [Database.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L188) |
| `class TripSessionEntity` | [Database.kt:197](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L197) |
| `class TripSampleEntity` | [Database.kt:208](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L208) |
| `class TripEventEntity` | [Database.kt:230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L230) |
| `class TripWaypointEntity` | [Database.kt:233](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L233) |
| `class TripCustomMetricSampleEntity` | [Database.kt:236](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L236) |
| `class TripDashboardEntity` | [Database.kt:239](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L239) |
| `class AnchorTelemetrySampleEntity` | [Database.kt:242](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L242) |
| `class SonarSurveyEntity` | [Database.kt:245](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L245) |
| `class DepthSampleEntity` | [Database.kt:274](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L274) |
| `class TidePredictionCacheEntity` | [Database.kt:320](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L320) |
| `class SonarGridCellEntity` | [Database.kt:333](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L333) |
| `class LinzDepthCacheEntity` | [Database.kt:346](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L346) |
| `class IncidentLogEntity` | [Database.kt:368](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L368) |
| `interface AnchorDao` | [Database.kt:380](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L380) |
| `fun insertSession` | [Database.kt:381](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L381) |
| `fun updateSession` | [Database.kt:382](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L382) |
| `fun updateSessionAndInsertEvent` | [Database.kt:383](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L383) |
| `fun active` | [Database.kt:384](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L384) |
| `fun session` | [Database.kt:385](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L385) |
| `fun sessions` | [Database.kt:386](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L386) |
| `fun deleteCompletedSessionRow` | [Database.kt:389](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L389) |
| `fun deleteCompletedSession` | [Database.kt:390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L390) |
| `fun insertPoint` | [Database.kt:391](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L391) |
| `fun points` | [Database.kt:392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L392) |
| `fun insertEvent` | [Database.kt:396](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L396) |
| `fun events` | [Database.kt:397](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L397) |
| `fun observeRecentAlarmEvents` | [Database.kt:399](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L399) |
| `fun recentEvents` | [Database.kt:400](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L400) |
| `fun allSessionsNow` | [Database.kt:402](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L402) |
| `fun allPointsPage` | [Database.kt:403](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L403) |
| `fun allPointsPageThrough` | [Database.kt:404](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L404) |
| `fun allEventsPage` | [Database.kt:405](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L405) |
| `fun allEventsPageThrough` | [Database.kt:406](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L406) |
| `fun importSessions` | [Database.kt:412](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L412) |
| `fun importPoints` | [Database.kt:413](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L413) |
| `fun importEvents` | [Database.kt:414](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L414) |
| `fun clearEvents` | [Database.kt:415](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L415) |
| `fun clearPoints` | [Database.kt:416](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L416) |
| `fun clearSessions` | [Database.kt:417](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L417) |
| `interface AnchorageDao` | [Database.kt:421](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L421) |
| `fun get` | [Database.kt:423](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L423) |
| `fun insert` | [Database.kt:424](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L424) |
| `fun update` | [Database.kt:425](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L425) |
| `fun delete` | [Database.kt:426](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L426) |
| `fun allNow` | [Database.kt:427](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L427) |
| `fun importAll` | [Database.kt:428](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L428) |
| `fun clear` | [Database.kt:429](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L429) |
| `interface SonarDao` | [Database.kt:433](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L433) |
| `fun insertSurvey` | [Database.kt:434](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L434) |
| `fun updateSurvey` | [Database.kt:435](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L435) |
| `fun active` | [Database.kt:436](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L436) |
| `fun survey` | [Database.kt:437](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L437) |
| `fun surveys` | [Database.kt:438](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L438) |
| `fun samples` | [Database.kt:439](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L439) |
| `fun normalizedHistory` | [Database.kt:440](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L440) |
| `fun usableSamples` | [Database.kt:441](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L441) |
| `fun samplesNow` | [Database.kt:442](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L442) |
| `fun insertSample` | [Database.kt:445](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L445) |
| `fun updateSamples` | [Database.kt:446](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L446) |
| `fun rename` | [Database.kt:448](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L448) |
| `fun finish` | [Database.kt:449](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L449) |
| `fun incrementSampleCount` | [Database.kt:451](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L451) |
| `fun insertSampleAndIncrement` | [Database.kt:452](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L452) |
| `fun deleteCompleted` | [Database.kt:453](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L453) |
| `fun usableSamplesInCell` | [Database.kt:454](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L454) |
| `fun correctedSamplesInCell` | [Database.kt:455](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L455) |
| `fun correctedCellsForSurvey` | [Database.kt:456](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L456) |
| `fun usableCellsForSurvey` | [Database.kt:457](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L457) |
| `fun allCorrectedCells` | [Database.kt:458](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L458) |
| `fun gridCellsNow` | [Database.kt:463](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L463) |
| `fun upsertGridCell` | [Database.kt:465](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L465) |
| `fun deleteGridCell` | [Database.kt:466](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L466) |
| `fun deleteGridScope` | [Database.kt:467](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L467) |
| `fun allSurveysNow` | [Database.kt:468](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L468) |
| `fun allSamplesPage` | [Database.kt:469](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L469) |
| `fun allSamplesPageThrough` | [Database.kt:470](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L470) |
| `fun importSurveys` | [Database.kt:473](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L473) |
| `fun importSamples` | [Database.kt:474](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L474) |
| `fun clearGridCells` | [Database.kt:475](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L475) |
| `fun clearSamples` | [Database.kt:476](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L476) |
| `fun clearSurveys` | [Database.kt:477](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L477) |
| `class GridCoordinate` | [Database.kt:480](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L480) |
| `interface LinzDepthCacheDao` | [Database.kt:483](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L483) |
| `fun get` | [Database.kt:484](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L484) |
| `fun upsert` | [Database.kt:485](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L485) |
| `fun prune` | [Database.kt:486](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L486) |
| `fun clear` | [Database.kt:487](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L487) |
| `interface TidePredictionCacheDao` | [Database.kt:491](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L491) |
| `fun get` | [Database.kt:492](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L492) |
| `fun upsert` | [Database.kt:493](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L493) |
| `fun clear` | [Database.kt:494](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L494) |
| `interface IncidentLogDao` | [Database.kt:498](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L498) |
| `fun insert` | [Database.kt:499](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L499) |
| `fun recent` | [Database.kt:500](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L500) |
| `fun since` | [Database.kt:501](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L501) |
| `fun deleteOlderThan` | [Database.kt:503](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L503) |
| `fun clear` | [Database.kt:505](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L505) |
| `class PressureHistoryEntity` | [Database.kt:513](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L513) |
| `interface PressureHistoryDao` | [Database.kt:522](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L522) |
| `fun upsert` | [Database.kt:523](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L523) |
| `fun since` | [Database.kt:524](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L524) |
| `fun prune` | [Database.kt:525](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L525) |
| `interface TripDao` | [Database.kt:530](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L530) |
| `fun renameCompleted` | [Database.kt:532](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L532) |
| `fun insertSession` | [Database.kt:533](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L533) |
| `fun updateSession` | [Database.kt:534](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L534) |
| `fun insertSessionAndEvent` | [Database.kt:535](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L535) |
| `fun updateSessionAndInsertEvent` | [Database.kt:536](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L536) |
| `fun updateSessionAndInsertEventAndWaypoint` | [Database.kt:537](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L537) |
| `fun active` | [Database.kt:538](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L538) |
| `fun activeFlow` | [Database.kt:539](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L539) |
| `fun sessions` | [Database.kt:540](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L540) |
| `fun allSessionsNow` | [Database.kt:541](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L541) |
| `fun session` | [Database.kt:542](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L542) |
| `fun insertSamples` | [Database.kt:543](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L543) |
| `fun insertEvent` | [Database.kt:544](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L544) |
| `fun insertWaypoint` | [Database.kt:545](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L545) |
| `fun updateWaypoint` | [Database.kt:546](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L546) |
| `fun latestWaypoint` | [Database.kt:547](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L547) |
| `fun insertAnchorTelemetry` | [Database.kt:548](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L548) |
| `fun insertCustomMetrics` | [Database.kt:549](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L549) |
| `fun upsertDashboard` | [Database.kt:550](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L550) |
| `fun dashboards` | [Database.kt:551](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L551) |
| `fun allDashboardsNow` | [Database.kt:552](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L552) |
| `fun dashboard` | [Database.kt:553](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L553) |
| `fun deleteDashboard` | [Database.kt:554](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L554) |
| `fun updateDashboardSort` | [Database.kt:555](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L555) |
| `fun samples` | [Database.kt:556](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L556) |
| `fun events` | [Database.kt:562](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L562) |
| `fun waypoints` | [Database.kt:564](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L564) |
| `fun customMetrics` | [Database.kt:565](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L565) |
| `fun anchorTelemetry` | [Database.kt:567](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L567) |
| `fun allSamplesPageThrough` | [Database.kt:574](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L574) |
| `fun allEventsPageThrough` | [Database.kt:575](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L575) |
| `fun allWaypointsPageThrough` | [Database.kt:576](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L576) |
| `fun allCustomMetricsPageThrough` | [Database.kt:577](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L577) |
| `fun allAnchorTelemetryPageThrough` | [Database.kt:578](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L578) |
| `fun importSessions` | [Database.kt:579](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L579) |
| `fun importSamples` | [Database.kt:580](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L580) |
| `fun importEvents` | [Database.kt:581](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L581) |
| `fun importWaypoints` | [Database.kt:582](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L582) |
| `fun importCustomMetrics` | [Database.kt:583](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L583) |
| `fun importDashboards` | [Database.kt:584](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L584) |
| `fun importAnchorTelemetry` | [Database.kt:585](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L585) |
| `fun clearSamples` | [Database.kt:586](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L586) |
| `fun clearEvents` | [Database.kt:587](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L587) |
| `fun clearWaypoints` | [Database.kt:588](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L588) |
| `fun clearCustomMetrics` | [Database.kt:589](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L589) |
| `fun clearDashboards` | [Database.kt:590](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L590) |
| `fun clearAnchorTelemetry` | [Database.kt:591](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L591) |
| `fun clearSessions` | [Database.kt:592](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L592) |
| `fun deleteCompleted` | [Database.kt:593](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L593) |
| `class AppDatabase : RoomDatabase` | [Database.kt:602](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L602) |
| `fun anchorDao` | [Database.kt:603](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L603) |
| `fun anchorageDao` | [Database.kt:604](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L604) |
| `fun sonarDao` | [Database.kt:605](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L605) |
| `fun linzDepthCacheDao` | [Database.kt:606](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L606) |
| `fun tidePredictionCacheDao` | [Database.kt:607](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L607) |
| `fun incidentLogDao` | [Database.kt:608](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L608) |
| `fun pressureHistoryDao` | [Database.kt:609](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L609) |
| `fun tripDao` | [Database.kt:610](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L610) |
| `fun anchorageRegionDao` | [Database.kt:611](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L611) |
| `fun anchoragePlaceDao` | [Database.kt:612](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L612) |
| `fun anchorageSpotDao` | [Database.kt:613](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L613) |
| `fun anchorageVisitDao` | [Database.kt:614](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L614) |
| `fun anchorageCollectionDao` | [Database.kt:615](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L615) |
| `fun anchorageMetadataDao` | [Database.kt:616](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L616) |
| `fun anchoragePhotoDao` | [Database.kt:617](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L617) |
| `fun anchorageSearchDao` | [Database.kt:618](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L618) |
| `fun anchorageSpatialDao` | [Database.kt:619](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L619) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt

| 声明 | 实现位置 |
| --- | --- |
| `object Migration1To2 : Migration` | [Migrations.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L6) |
| `fun migrate` | [Migrations.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L7) |
| `object Migration2To3 : Migration` | [Migrations.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L21) |
| `fun migrate` | [Migrations.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L22) |
| `object Migration3To4 : Migration` | [Migrations.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L33) |
| `fun migrate` | [Migrations.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L34) |
| `object Migration4To5 : Migration` | [Migrations.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L41) |
| `fun migrate` | [Migrations.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L42) |
| `object Migration5To6 : Migration` | [Migrations.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L52) |
| `fun migrate` | [Migrations.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L53) |
| `object Migration6To7 : Migration` | [Migrations.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L86) |
| `fun migrate` | [Migrations.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L87) |
| `object Migration7To8 : Migration` | [Migrations.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L97) |
| `fun migrate` | [Migrations.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L98) |
| `object Migration8To9 : Migration` | [Migrations.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L110) |
| `fun migrate` | [Migrations.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L111) |
| `object Migration9To10 : Migration` | [Migrations.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L126) |
| `fun migrate` | [Migrations.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L127) |
| `object Migration10To11 : Migration` | [Migrations.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L135) |
| `fun migrate` | [Migrations.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L136) |
| `object Migration11To12:Migration` | [Migrations.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L144) |
| `fun migrate` | [Migrations.kt:145](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L145) |
| `object Migration12To13:Migration` | [Migrations.kt:175](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L175) |
| `fun migrate` | [Migrations.kt:176](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L176) |
| `object Migration13To14:Migration` | [Migrations.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L183) |
| `fun migrate` | [Migrations.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L184) |
| `object Migration14To15:Migration` | [Migrations.kt:199](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L199) |
| `fun migrate` | [Migrations.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L200) |
| `object Migration15To16:Migration` | [Migrations.kt:215](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L215) |
| `fun migrate` | [Migrations.kt:216](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L216) |
| `object Migration16To17:Migration` | [Migrations.kt:228](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L228) |
| `fun migrate` | [Migrations.kt:229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L229) |
| `object Migration17To18:Migration` | [Migrations.kt:245](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L245) |
| `fun migrate` | [Migrations.kt:246](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L246) |
| `object Migration18To19:Migration` | [Migrations.kt:277](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L277) |
| `fun migrate` | [Migrations.kt:278](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L278) |
| `object Migration19To20:Migration` | [Migrations.kt:287](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L287) |
| `fun migrate` | [Migrations.kt:288](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L288) |
| `object Migration20To21:Migration` | [Migrations.kt:359](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L359) |
| `fun migrate` | [Migrations.kt:360](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L360) |
| `object Migration21To22:Migration` | [Migrations.kt:373](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L373) |
| `fun migrate` | [Migrations.kt:374](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L374) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface AnchorageRegionDao` | [AnchorageDaos.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L15) |
| `fun get` | [AnchorageDaos.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L16) |
| `fun observeAll` | [AnchorageDaos.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L17) |
| `fun customRegions` | [AnchorageDaos.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L18) |
| `fun byExternalId` | [AnchorageDaos.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L19) |
| `fun inBounds` | [AnchorageDaos.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L20) |
| `fun upsert` | [AnchorageDaos.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L21) |
| `fun allNow` | [AnchorageDaos.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L23) |
| `fun importAll` | [AnchorageDaos.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L24) |
| `fun clear` | [AnchorageDaos.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L25) |
| `interface AnchoragePlaceDao` | [AnchorageDaos.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L28) |
| `fun get` | [AnchorageDaos.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L29) |
| `fun allNow` | [AnchorageDaos.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L31) |
| `fun byLegacyId` | [AnchorageDaos.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L32) |
| `fun insert` | [AnchorageDaos.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L33) |
| `fun update` | [AnchorageDaos.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L34) |
| `fun delete` | [AnchorageDaos.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L35) |
| `fun importAll` | [AnchorageDaos.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L38) |
| `fun clear` | [AnchorageDaos.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L39) |
| `interface AnchorageSpotDao` | [AnchorageDaos.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L42) |
| `fun get` | [AnchorageDaos.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L43) |
| `fun forPlaceNow` | [AnchorageDaos.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L46) |
| `fun allNow` | [AnchorageDaos.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L47) |
| `fun insert` | [AnchorageDaos.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L48) |
| `fun update` | [AnchorageDaos.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L49) |
| `fun delete` | [AnchorageDaos.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L50) |
| `fun importAll` | [AnchorageDaos.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L53) |
| `fun clear` | [AnchorageDaos.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L54) |
| `interface AnchorageVisitDao` | [AnchorageDaos.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L57) |
| `fun get` | [AnchorageDaos.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L58) |
| `fun observeAll` | [AnchorageDaos.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L59) |
| `fun observeForPlace` | [AnchorageDaos.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L60) |
| `fun forPlaceNow` | [AnchorageDaos.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L61) |
| `fun bySession` | [AnchorageDaos.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L62) |
| `fun insert` | [AnchorageDaos.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L63) |
| `fun update` | [AnchorageDaos.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L64) |
| `fun allNow` | [AnchorageDaos.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L66) |
| `fun importAll` | [AnchorageDaos.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L67) |
| `fun clear` | [AnchorageDaos.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L68) |
| `interface AnchorageCollectionDao` | [AnchorageDaos.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L71) |
| `fun observeAll` | [AnchorageDaos.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L72) |
| `fun allNow` | [AnchorageDaos.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L73) |
| `fun insert` | [AnchorageDaos.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L74) |
| `fun update` | [AnchorageDaos.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L75) |
| `fun delete` | [AnchorageDaos.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L76) |
| `fun setMembership` | [AnchorageDaos.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L77) |
| `fun removeMembership` | [AnchorageDaos.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L78) |
| `fun membershipsNow` | [AnchorageDaos.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L79) |
| `fun forPlace` | [AnchorageDaos.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L80) |
| `fun importAll` | [AnchorageDaos.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L81) |
| `fun importMemberships` | [AnchorageDaos.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L82) |
| `fun clearMemberships` | [AnchorageDaos.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L83) |
| `fun clear` | [AnchorageDaos.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L84) |
| `interface AnchorageMetadataDao` | [AnchorageDaos.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L87) |
| `fun regionsForPlace` | [AnchorageDaos.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L88) |
| `fun upsertPlaceRegions` | [AnchorageDaos.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L89) |
| `fun clearPlaceRegions` | [AnchorageDaos.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L90) |
| `fun rating` | [AnchorageDaos.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L91) |
| `fun observeRatings` | [AnchorageDaos.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L92) |
| `fun upsertRating` | [AnchorageDaos.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L93) |
| `fun protection` | [AnchorageDaos.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L94) |
| `fun upsertProtection` | [AnchorageDaos.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L95) |
| `fun facilities` | [AnchorageDaos.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L96) |
| `fun upsertFacilities` | [AnchorageDaos.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L97) |
| `fun summary` | [AnchorageDaos.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L98) |
| `fun upsertSummary` | [AnchorageDaos.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L99) |
| `fun deleteSummary` | [AnchorageDaos.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L100) |
| `fun meta` | [AnchorageDaos.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L101) |
| `fun upsertMeta` | [AnchorageDaos.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L102) |
| `fun allPlaceRegions` | [AnchorageDaos.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L103) |
| `fun allProtection` | [AnchorageDaos.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L104) |
| `fun allFacilities` | [AnchorageDaos.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L105) |
| `fun allRatings` | [AnchorageDaos.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L106) |
| `fun importPlaceRegions` | [AnchorageDaos.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L107) |
| `fun importProtection` | [AnchorageDaos.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L108) |
| `fun importFacilities` | [AnchorageDaos.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L109) |
| `fun importRatings` | [AnchorageDaos.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L110) |
| `fun clearPlaceRegionsAll` | [AnchorageDaos.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L111) |
| `fun clearProtection` | [AnchorageDaos.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L112) |
| `fun clearFacilities` | [AnchorageDaos.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L113) |
| `fun clearRatings` | [AnchorageDaos.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L114) |
| `fun clearSummaries` | [AnchorageDaos.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L115) |
| `interface AnchoragePhotoDao` | [AnchorageDaos.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L118) |
| `fun allNow` | [AnchorageDaos.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L120) |
| `fun insert` | [AnchorageDaos.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L123) |
| `fun delete` | [AnchorageDaos.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L124) |
| `fun importAll` | [AnchorageDaos.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L126) |
| `fun clear` | [AnchorageDaos.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L127) |
| `interface AnchorageSearchDao` | [AnchorageDaos.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L130) |
| `fun put` | [AnchorageDaos.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L132) |
| `fun deletePlace` | [AnchorageDaos.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L133) |
| `fun clear` | [AnchorageDaos.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L134) |
| `interface AnchorageSpatialDao` | [AnchorageDaos.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L137) |
| `fun places` | [AnchorageDaos.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L138) |
| `fun spots` | [AnchorageDaos.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/dao/AnchorageDaos.kt#L139) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageRegionEntity` | [AnchorageEntities.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L16) |
| `class AnchoragePlaceEntity` | [AnchorageEntities.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L47) |
| `class AnchoragePlaceRegionCrossRef` | [AnchorageEntities.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L85) |
| `class AnchorageSpotEntity` | [AnchorageEntities.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L92) |
| `class AnchorageVisitEntity` | [AnchorageEntities.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L126) |
| `class AnchorageCollectionEntity` | [AnchorageEntities.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L158) |
| `class AnchorageCollectionPlaceCrossRef` | [AnchorageEntities.kt:168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L168) |
| `class AnchorageProtectionSectorEntity` | [AnchorageEntities.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L174) |
| `class AnchorageFacilityEntity` | [AnchorageEntities.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L180) |
| `class AnchoragePersonalRatingEntity` | [AnchorageEntities.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L186) |
| `class AnchoragePhotoEntity` | [AnchorageEntities.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L200) |
| `class AnchoragePlaceSummaryEntity` | [AnchorageEntities.kt:209](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L209) |
| `class AnchorageSearchFtsEntity` | [AnchorageEntities.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L213) |
| `class AnchorageGisMetaEntity` | [AnchorageEntities.kt:224](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L224) |
| `class AnchorageRegionPackEntity` | [AnchorageEntities.kt:230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/entity/AnchorageEntities.kt#L230) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class IncidentSeverity` | [IncidentDiagnostics.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L39) |
| `fun record` | [IncidentDiagnostics.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L55) |
| `fun recordNow` | [IncidentDiagnostics.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L63) |
| `fun exception` | [IncidentDiagnostics.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L90) |
| `class StorageHealth` | [IncidentDiagnostics.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L125) |
| `class SupportBundleState` | [IncidentDiagnostics.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L137) |
| `class StorageHealthRepository @Inject constructor` | [IncidentDiagnostics.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L144) |
| `fun snapshot` | [IncidentDiagnostics.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L152) |
| `fun clearRebuildableCaches` | [IncidentDiagnostics.kt:167](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L167) |
| `fun clearIncidentLog` | [IncidentDiagnostics.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L180) |
| `class SupportBundleManager @Inject constructor` | [IncidentDiagnostics.kt:191](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L191) |
| `fun export` | [IncidentDiagnostics.kt:203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L203) |
| `fun count` | [IncidentDiagnostics.kt:211](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L211) |
| `fun clearResult` | [IncidentDiagnostics.kt:263](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L263) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripExportManager @Inject constructor` | [TripExportManager.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L27) |
| `fun csv` | [TripExportManager.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L34) |
| `fun gpx` | [TripExportManager.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L36) |
| `fun eventsCsv` | [TripExportManager.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L45) |
| `fun waypointsCsv` | [TripExportManager.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L46) |
| `fun customMetricsCsv` | [TripExportManager.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L47) |
| `fun kml` | [TripExportManager.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L49) |
| `fun kmz` | [TripExportManager.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L50) |
| `fun liveSnapshot` | [TripExportManager.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L52) |
| `fun reportSnapshot` | [TripExportManager.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L55) |
| `fun aiZip` | [TripExportManager.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L57) |
| `fun anchorAiZip` | [TripExportManager.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L70) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LinzDepthStatus` | [LinzDepthModels.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L3) |
| `class LinzDepthReference` | [LinzDepthModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L5) |
| `class LinzDepthDiagnostics` | [LinzDepthModels.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L14) |
| `object LinzDepthPresentation` | [LinzDepthModels.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L20) |
| `class Text` | [LinzDepthModels.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L21) |
| `fun text` | [LinzDepthModels.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthModels.kt#L22) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun shouldQuery` | [LinzDepthReferenceRepository.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L21) |
| `fun record` | [LinzDepthReferenceRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L22) |
| `object LinzFinalResultCachePolicy` | [LinzDepthReferenceRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L25) |
| `fun isNear` | [LinzDepthReferenceRepository.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L27) |
| `fun canReuseFresh` | [LinzDepthReferenceRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L29) |
| `fun refresh` | [LinzDepthReferenceRepository.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L38) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt

| 声明 | 实现位置 |
| --- | --- |
| `class HydroFeatureKind` | [LinzHydroFeatureParser.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L7) |
| `class GeoPoint` | [LinzHydroFeatureParser.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L8) |
| `interface HydroGeometry` | [LinzHydroFeatureParser.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L9) |
| `class HydroFeature` | [LinzHydroFeatureParser.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L10) |
| `object LinzHydroFeatureParser` | [LinzHydroFeatureParser.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L12) |
| `fun parse` | [LinzHydroFeatureParser.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L13) |
| `object LinzHydroSelector` | [LinzHydroFeatureParser.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L56) |
| `fun select` | [LinzHydroFeatureParser.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzHydroFeatureParser.kt#L57) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LinzWfsResult` | [LinzWfsClient.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L25) |
| `class LinzHttpResponse` | [LinzWfsClient.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L34) |
| `class LinzWfsHttpTransport @Inject constructor` | [LinzWfsClient.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L37) |
| `fun get` | [LinzWfsClient.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L38) |
| `object LinzWfsRequestBuilder` | [LinzWfsClient.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L69) |
| `fun describe` | [LinzWfsClient.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L70) |
| `fun features` | [LinzWfsClient.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L80) |
| `object LinzFeatureTypeSchemaParser` | [LinzWfsClient.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L127) |
| `fun geometryProperties` | [LinzWfsClient.kt:128](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L128) |
| `class LinzWfsClient @Inject constructor` | [LinzWfsClient.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L184) |
| `fun query` | [LinzWfsClient.kt:196](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzWfsClient.kt#L196) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/MiniJson.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun read` | [MiniJson.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/MiniJson.kt#L6) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Protocol` | [NmeaConnection.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L10) |
| `class ConnectionProfile` | [NmeaConnection.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L11) |
| `class NmeaConnectionRetryPolicy` | [NmeaConnection.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L13) |
| `class NmeaTransportDiagnostics` | [NmeaConnection.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L21) |
| `object NmeaSafetyRetryPolicy` | [NmeaConnection.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L38) |
| `fun delayMillis` | [NmeaConnection.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L40) |
| `class NmeaTransportWriteFailure` | [NmeaConnection.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L43) |
| `class NmeaTransportWriteResult` | [NmeaConnection.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L45) |
| `object NmeaWireBatch` | [NmeaConnection.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L56) |
| `fun encode` | [NmeaConnection.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L57) |
| `class NmeaConnectionManager` | [NmeaConnection.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L60) |
| `fun setSafetyOwnedRetry` | [NmeaConnection.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L81) |
| `fun connect` | [NmeaConnection.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L82) |
| `fun ensureConnected` | [NmeaConnection.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L91) |
| `fun reconnect` | [NmeaConnection.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L97) |
| `fun write` | [NmeaConnection.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L103) |
| `fun writeExpected` | [NmeaConnection.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L106) |
| `fun hasOpenTransport` | [NmeaConnection.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L131) |
| `fun remotePeer` | [NmeaConnection.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L133) |
| `fun disconnect` | [NmeaConnection.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L134) |
| `fun reportValidFix` | [NmeaConnection.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L135) |
| `fun reportStaleFix` | [NmeaConnection.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L136) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaConnectionSpec` | [NmeaConnections.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L16) |
| `fun profile` | [NmeaConnections.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L34) |
| `class NmeaFeed` | [NmeaConnections.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L37) |
| `class NmeaRawFrame` | [NmeaConnections.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L39) |
| `class NmeaConnectionSnapshot` | [NmeaConnections.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L41) |
| `fun requestedIds` | [NmeaConnections.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L60) |
| `fun setRequested` | [NmeaConnections.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L62) |
| `fun clearRequested` | [NmeaConnections.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L67) |
| `fun read` | [NmeaConnections.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L68) |
| `fun save` | [NmeaConnections.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L69) |
| `object NmeaConnectionLeasePolicy` | [NmeaConnections.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L72) |
| `fun restorable` | [NmeaConnections.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L73) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaChecksum` | [NmeaCore.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L12) |
| `fun validate` | [NmeaCore.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L13) |
| `fun append` | [NmeaCore.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L20) |
| `fun feed` | [NmeaCore.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L25) |
| `fun feed` | [NmeaCore.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L26) |
| `class NmeaMeasurementConfirmation` | [NmeaCore.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L34) |
| `class NmeaMetric` | [NmeaCore.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L35) |
| `class NmeaMetricTiming` | [NmeaCore.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L40) |
| `class NmeaUpdate` | [NmeaCore.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L46) |
| `fun measuredAt` | [NmeaCore.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L47) |
| `fun heartbeatAt` | [NmeaCore.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L48) |
| `fun confirmation` | [NmeaCore.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L49) |
| `fun isNumeric` | [NmeaCore.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L50) |
| `class NmeaUpdateRetainer` | [NmeaCore.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L63) |
| `fun accept` | [NmeaCore.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L65) |
| `fun timing` | [NmeaCore.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L105) |
| `fun clear` | [NmeaCore.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L123) |
| `class Nmea0183Parser` | [NmeaCore.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L126) |
| `fun parseEnvelope` | [NmeaCore.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L127) |
| `fun parse` | [NmeaCore.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L132) |
| `class NmeaDiagnostics` | [NmeaCore.kt:194](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L194) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun check` | [NmeaEndpointPreflight.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt#L17) |
| `fun validate` | [NmeaEndpointPreflight.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt#L26) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaFieldSemantic` | [NmeaFieldRepository.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L21) |
| `class NmeaFieldKey` | [NmeaFieldRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L29) |
| `class NmeaFieldObservation` | [NmeaFieldRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L39) |
| `fun isFresh` | [NmeaFieldRepository.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L50) |
| `class NmeaFieldHeartbeat` | [NmeaFieldRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L53) |
| `fun accept` | [NmeaFieldRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L58) |
| `fun expire` | [NmeaFieldRepository.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L69) |
| `fun clear` | [NmeaFieldRepository.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L73) |
| `object NmeaFieldDecoder` | [NmeaFieldRepository.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L81) |
| `fun heartbeat` | [NmeaFieldRepository.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L82) |
| `fun decode` | [NmeaFieldRepository.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L102) |
| `fun number` | [NmeaFieldRepository.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L107) |
| `fun text` | [NmeaFieldRepository.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L111) |
| `fun signed` | [NmeaFieldRepository.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L112) |
| `class NmeaFieldRepository @Inject constructor` | [NmeaFieldRepository.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L172) |
| `fun accept` | [NmeaFieldRepository.kt:189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L189) |
| `fun semantic` | [NmeaFieldRepository.kt:190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L190) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaHeadingCandidate` | [NmeaHeadingResolver.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L8) |
| `class NmeaHeadingResolution` | [NmeaHeadingResolver.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L16) |
| `class NmeaHeadingResolver` | [NmeaHeadingResolver.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L26) |
| `fun pin` | [NmeaHeadingResolver.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L36) |
| `fun accept` | [NmeaHeadingResolver.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L42) |
| `fun resolve` | [NmeaHeadingResolver.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L52) |
| `fun source` | [NmeaHeadingResolver.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L55) |
| `fun <T> pinFor` | [NmeaHeadingResolver.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L61) |
| `fun reset` | [NmeaHeadingResolver.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaHeadingResolver.kt#L74) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaCapability` | [NmeaPublicationPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L9) |
| `object NmeaPublicationPolicy` | [NmeaPublicationPolicy.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L30) |
| `fun selected` | [NmeaPublicationPolicy.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L31) |
| `fun type` | [NmeaPublicationPolicy.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L37) |
| `fun filter` | [NmeaPublicationPolicy.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L40) |
| `fun allowed` | [NmeaPublicationPolicy.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L44) |
| `fun filter` | [NmeaPublicationPolicy.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L89) |
| `class NmeaPeerGuard @Inject constructor` | [NmeaPublicationPolicy.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L97) |
| `fun sameHost` | [NmeaPublicationPolicy.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L101) |
| `fun endpoint` | [NmeaPublicationPolicy.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L119) |
| `fun host` | [NmeaPublicationPolicy.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaPublicationPolicy.kt#L124) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaSourceInvalidation.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaInvalidationReason` | [NmeaSourceInvalidation.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaSourceInvalidation.kt#L5) |
| `class NmeaSourceInvalidation` | [NmeaSourceInvalidation.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaSourceInvalidation.kt#L8) |
| `object NmeaInvalidationPolicy` | [NmeaSourceInvalidation.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaSourceInvalidation.kt#L18) |
| `fun affectedMetrics` | [NmeaSourceInvalidation.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaSourceInvalidation.kt#L19) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class WindSnapshotAccumulator` | [WindSnapshotAccumulator.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L8) |
| `class Snapshot` | [WindSnapshotAccumulator.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L12) |
| `fun update` | [WindSnapshotAccumulator.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L30) |
| `fun measured` | [WindSnapshotAccumulator.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L32) |
| `fun snapshot` | [WindSnapshotAccumulator.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L41) |
| `fun Timed?.coherent` | [WindSnapshotAccumulator.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L45) |
| `fun clear` | [WindSnapshotAccumulator.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/WindSnapshotAccumulator.kt#L59) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaCandidateMapper` | [NmeaCandidateMapper.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L6) |
| `fun map` | [NmeaCandidateMapper.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L7) |
| `fun <T> candidate` | [NmeaCandidateMapper.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L13) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaFieldCandidateMapper.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaFieldCandidateMapper` | [NmeaFieldCandidateMapper.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaFieldCandidateMapper.kt#L9) |
| `fun map` | [NmeaFieldCandidateMapper.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaFieldCandidateMapper.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/ParsedNmeaEnvelope.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ParsedNmeaEnvelope` | [ParsedNmeaEnvelope.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/ParsedNmeaEnvelope.kt#L5) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaTxConnectionState` | [NmeaDeviceOutputConnection.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L30) |
| `class NmeaWriteBackpressureState` | [NmeaDeviceOutputConnection.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L31) |
| `object NmeaWriteBackpressurePolicy` | [NmeaDeviceOutputConnection.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L33) |
| `fun evaluate` | [NmeaDeviceOutputConnection.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L34) |
| `class NmeaPacketPath` | [NmeaDeviceOutputConnection.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L42) |
| `class NmeaPacketStage` | [NmeaDeviceOutputConnection.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L43) |
| `class NmeaPacketPathDiagnostic` | [NmeaDeviceOutputConnection.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L45) |
| `object NmeaOutputEndpointPolicy` | [NmeaDeviceOutputConnection.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L65) |
| `fun resolved` | [NmeaDeviceOutputConnection.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L66) |
| `fun automatic` | [NmeaDeviceOutputConnection.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L70) |
| `fun tcpDestination` | [NmeaDeviceOutputConnection.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L73) |
| `fun isValid` | [NmeaDeviceOutputConnection.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L74) |
| `fun needsInputTransport` | [NmeaDeviceOutputConnection.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L83) |
| `fun duplicateEndpointRisk` | [NmeaDeviceOutputConnection.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L84) |
| `fun opensSecondTransportOnInputEndpoint` | [NmeaDeviceOutputConnection.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L88) |
| `class NmeaTxStatus` | [NmeaDeviceOutputConnection.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L95) |
| `class NmeaStreamTxStatus` | [NmeaDeviceOutputConnection.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L132) |
| `class NmeaOutboundLoopGuard @Inject constructor` | [NmeaDeviceOutputConnection.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L140) |
| `fun beginWrite` | [NmeaDeviceOutputConnection.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L159) |
| `fun completeWrite` | [NmeaDeviceOutputConnection.kt:170](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L170) |
| `fun record` | [NmeaDeviceOutputConnection.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L174) |
| `fun isRecentOutbound` | [NmeaDeviceOutputConnection.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L185) |
| `fun isRecentExactOutbound` | [NmeaDeviceOutputConnection.kt:224](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L224) |
| `fun isRecentExactOutboundForReceiver` | [NmeaDeviceOutputConnection.kt:238](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L238) |
| `fun coordinate` | [NmeaDeviceOutputConnection.kt:275](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L275) |
| `class NmeaSemantic` | [NmeaDeviceOutputConnection.kt:290](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L290) |
| `class NmeaSemanticFingerprint` | [NmeaDeviceOutputConnection.kt:291](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L291) |
| `fun matches` | [NmeaDeviceOutputConnection.kt:297](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L297) |
| `fun quarantineKey` | [NmeaDeviceOutputConnection.kt:302](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L302) |
| `class NmeaOutputStopBarrier` | [NmeaDeviceOutputConnection.kt:314](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L314) |
| `fun <T> withWriteLease` | [NmeaDeviceOutputConnection.kt:316](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L316) |
| `fun <T> stopAndJoin` | [NmeaDeviceOutputConnection.kt:317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L317) |
| `class NmeaDeviceOutputConnection @Inject constructor` | [NmeaDeviceOutputConnection.kt:323](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L323) |
| `fun recordGenerated` | [NmeaDeviceOutputConnection.kt:356](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L356) |
| `fun recordDropped` | [NmeaDeviceOutputConnection.kt:367](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L367) |
| `fun recordSuppressed` | [NmeaDeviceOutputConnection.kt:368](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L368) |
| `fun recordDecision` | [NmeaDeviceOutputConnection.kt:369](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L369) |
| `fun refreshTransportState` | [NmeaDeviceOutputConnection.kt:371](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L371) |
| `fun configure` | [NmeaDeviceOutputConnection.kt:390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L390) |
| `fun currentInputTransportGeneration` | [NmeaDeviceOutputConnection.kt:462](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L462) |
| `fun write` | [NmeaDeviceOutputConnection.kt:464](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L464) |
| `fun test` | [NmeaDeviceOutputConnection.kt:560](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L560) |
| `fun stop` | [NmeaDeviceOutputConnection.kt:571](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L571) |
| `class NmeaUdpClient @Inject constructor` | [NmeaDeviceOutputConnection.kt:588](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L588) |
| `fun write` | [NmeaDeviceOutputConnection.kt:590](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L590) |
| `fun close` | [NmeaDeviceOutputConnection.kt:594](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L594) |
| `class DedicatedNmeaWriteResult` | [NmeaDeviceOutputConnection.kt:597](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L597) |
| `class DedicatedNmeaTcpClient @Inject constructor` | [NmeaDeviceOutputConnection.kt:602](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L602) |
| `fun isConnected` | [NmeaDeviceOutputConnection.kt:610](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L610) |
| `fun write` | [NmeaDeviceOutputConnection.kt:613](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L613) |
| `fun write` | [NmeaDeviceOutputConnection.kt:619](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L619) |
| `fun close` | [NmeaDeviceOutputConnection.kt:651](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L651) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaGeneratedSentenceValidator.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaGeneratedSentenceValidator` | [NmeaGeneratedSentenceValidator.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaGeneratedSentenceValidator.kt#L8) |
| `fun isValid` | [NmeaGeneratedSentenceValidator.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaGeneratedSentenceValidator.kt#L11) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaRawTxConsolePolicy` | [NmeaRawTxConsolePolicy.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt#L3) |
| `fun stream` | [NmeaRawTxConsolePolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt#L5) |
| `fun sentenceType` | [NmeaRawTxConsolePolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt#L6) |
| `fun afterClearMarker` | [NmeaRawTxConsolePolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt#L10) |
| `fun filter` | [NmeaRawTxConsolePolicy.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaRawTxConsolePolicy.kt#L15) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AppSettings` | [SettingsRepository.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L13) |
| `fun setLanguage` | [SettingsRepository.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L147) |
| `fun setVesselGeometry` | [SettingsRepository.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L150) |
| `fun setAlarmSound` | [SettingsRepository.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L159) |
| `fun setAlarmSnoozeMinutes` | [SettingsRepository.kt:167](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L167) |
| `fun setAlarmAudibleConfirmedAt` | [SettingsRepository.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L172) |
| `fun setPositionSource` | [SettingsRepository.kt:178](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L178) |
| `fun clearLegacySharingRequested` | [SettingsRepository.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L186) |
| `fun saveConnectionProfile` | [SettingsRepository.kt:189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L189) |
| `fun save` | [SettingsRepository.kt:201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L201) |
| `fun setMockEnabled` | [SettingsRepository.kt:208](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/preferences/SettingsRepository.kt#L208) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalNmeaServerSettings` | [LocalNmeaServerSettingsRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L30) |
| `class LocalNmeaServerSettingsRepository @Inject constructor` | [LocalNmeaServerSettingsRepository.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L44) |
| `fun saveConfiguration` | [LocalNmeaServerSettingsRepository.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L82) |
| `fun requestStart` | [LocalNmeaServerSettingsRepository.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L96) |
| `fun requestStop` | [LocalNmeaServerSettingsRepository.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L97) |
| `fun resetRuntimeLease` | [LocalNmeaServerSettingsRepository.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L98) |
| `object LocalNmeaServerLeasePolicy` | [LocalNmeaServerSettingsRepository.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L101) |
| `fun restore` | [LocalNmeaServerSettingsRepository.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/LocalNmeaServerSettingsRepository.kt#L102) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NetworkAddressProvider.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NetworkAddressProvider @Inject constructor` | [NetworkAddressProvider.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NetworkAddressProvider.kt#L9) |
| `fun localAddresses` | [NetworkAddressProvider.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NetworkAddressProvider.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaOutputMux @Inject constructor` | [NmeaOutputMux.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L17) |
| `fun acceptedPosition` | [NmeaOutputMux.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L18) |
| `fun phonePosition` | [NmeaOutputMux.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L48) |
| `fun phoneHeading` | [NmeaOutputMux.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L57) |
| `fun phoneMagneticHeading` | [NmeaOutputMux.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L58) |
| `fun derivedTrueWind` | [NmeaOutputMux.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L62) |
| `fun diagnostic` | [NmeaOutputMux.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L66) |
| `fun diagnosticMagneticHeading` | [NmeaOutputMux.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L67) |
| `fun phoneRateOfTurn` | [NmeaOutputMux.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L68) |
| `fun canonicalDepth` | [NmeaOutputMux.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L69) |
| `fun canonicalSpeedThroughWater` | [NmeaOutputMux.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L73) |
| `fun phoneXdr` | [NmeaOutputMux.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L77) |
| `fun selectedXdr` | [NmeaOutputMux.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L79) |
| `fun phoneProprietary` | [NmeaOutputMux.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L87) |
| `fun sentenceType` | [NmeaOutputMux.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L92) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaSelfLoopPolicy` | [NmeaSelfLoopPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt#L6) |
| `fun isLiteralLoop` | [NmeaSelfLoopPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt#L9) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SharingServerState` | [NmeaSharingServer.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L27) |
| `class NmeaSharingClientStatus` | [NmeaSharingServer.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L29) |
| `class NmeaSharingStatus` | [NmeaSharingServer.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L36) |
| `fun start` | [NmeaSharingServer.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L68) |
| `fun stop` | [NmeaSharingServer.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L98) |
| `fun forceRebindForTest` | [NmeaSharingServer.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L113) |
| `fun publish` | [NmeaSharingServer.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L118) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaSonarPositionState` | [NmeaSonarPositionRepository.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L26) |
| `class NmeaSonarPositionRepository @Inject constructor` | [NmeaSonarPositionRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L41) |
| `fun hasFreshPosition` | [NmeaSonarPositionRepository.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L113) |
| `class SonarPositionPairingDecision` | [NmeaSonarPositionRepository.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L133) |
| `object SonarPositionPairingPolicy` | [NmeaSonarPositionRepository.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L140) |
| `fun evaluate` | [NmeaSonarPositionRepository.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/NmeaSonarPositionRepository.kt#L141) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt

| 声明 | 实现位置 |
| --- | --- |
| `object SonarGridScope` | [SonarIncrementalGridUpdater.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L18) |
| `class SonarGridUpdateDiagnostics` | [SonarIncrementalGridUpdater.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L24) |
| `class SonarGridChange` | [SonarIncrementalGridUpdater.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L32) |
| `fun updateCells` | [SonarIncrementalGridUpdater.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L46) |
| `fun rebuildSurvey` | [SonarIncrementalGridUpdater.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L55) |
| `fun rebuildMissing` | [SonarIncrementalGridUpdater.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L69) |
| `fun deleteSurvey` | [SonarIncrementalGridUpdater.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarIncrementalGridUpdater.kt#L85) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SonarRecorderStatus` | [SonarSurveyRecorder.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L50) |
| `fun hasFreshDepth` | [SonarSurveyRecorder.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L72) |
| `fun hasFreshRealDepth` | [SonarSurveyRecorder.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L73) |
| `fun hasFreshNmeaPosition` | [SonarSurveyRecorder.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L74) |
| `fun hasSeenRealDepth` | [SonarSurveyRecorder.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L75) |
| `fun realDepthHoldState` | [SonarSurveyRecorder.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L76) |
| `class SonarSurveyRecorder @Inject constructor` | [SonarSurveyRecorder.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L81) |
| `fun start` | [SonarSurveyRecorder.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L101) |
| `fun restoreActiveSurvey` | [SonarSurveyRecorder.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L122) |
| `fun stop` | [SonarSurveyRecorder.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L130) |
| `fun evaluateDepthHold` | [SonarSurveyRecorder.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L131) |
| `fun rename` | [SonarSurveyRecorder.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L140) |
| `fun delete` | [SonarSurveyRecorder.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L141) |
| `fun rebuild` | [SonarSurveyRecorder.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L143) |
| `fun submitDemo` | [SonarSurveyRecorder.kt:164](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sonar/SonarSurveyRecorder.kt#L164) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LinzTideDownloader @Inject constructor` | [LinzTideDownloader.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt#L13) |
| `fun secondaryPortsUrl` | [LinzTideDownloader.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt#L14) |
| `fun dailyPredictionUrl` | [LinzTideDownloader.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt#L15) |
| `fun download` | [LinzTideDownloader.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt#L20) |
| `fun downloadSecondaryPorts` | [LinzTideDownloader.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideDownloader.kt#L37) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TideRuntimeDiagnosticsSnapshot` | [LinzTideRepository.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L20) |
| `object TideRuntimeDiagnostics` | [LinzTideRepository.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L21) |
| `fun completed` | [LinzTideRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L23) |
| `class LinzTideRepository @Inject constructor` | [LinzTideRepository.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L27) |
| `fun nearestStation` | [LinzTideRepository.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L35) |
| `fun station` | [LinzTideRepository.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L36) |
| `fun refreshStationCatalog` | [LinzTideRepository.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L38) |
| `fun ensure` | [LinzTideRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L53) |
| `fun ensureYear` | [LinzTideRepository.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L60) |
| `fun correctionAt` | [LinzTideRepository.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L73) |
| `fun clearCache` | [LinzTideRepository.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/LinzTideRepository.kt#L94) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCorrection.kt

| 声明 | 实现位置 |
| --- | --- |
| `object SecondaryPortCorrection` | [SecondaryPortCorrection.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCorrection.kt#L7) |
| `fun apply` | [SecondaryPortCorrection.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCorrection.kt#L8) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCsvParser.kt

| 声明 | 实现位置 |
| --- | --- |
| `object SecondaryPortCsvParser` | [SecondaryPortCsvParser.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCsvParser.kt#L9) |
| `fun parse` | [SecondaryPortCsvParser.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCsvParser.kt#L10) |
| `fun parseOffsetMinutes` | [SecondaryPortCsvParser.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/SecondaryPortCsvParser.kt#L43) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideHeightInterpolator.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TideHeightInterpolator` | [TideHeightInterpolator.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideHeightInterpolator.kt#L11) |
| `fun heightAt` | [TideHeightInterpolator.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideHeightInterpolator.kt#L13) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TidePredictionCsvParser.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TidePredictionCsvParser` | [TidePredictionCsvParser.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TidePredictionCsvParser.kt#L9) |
| `fun parse` | [TidePredictionCsvParser.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TidePredictionCsvParser.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TideStationCatalog` | [TideStationCatalog.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt#L8) |
| `fun installOfficialCsv` | [TideStationCatalog.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt#L34) |
| `fun byId` | [TideStationCatalog.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt#L43) |
| `fun nearest` | [TideStationCatalog.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt#L44) |
| `fun reference` | [TideStationCatalog.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/tide/TideStationCatalog.kt#L45) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InstrumentTileSize` | [TripDashboardRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L15) |
| `class InstrumentSourceOverride` | [TripDashboardRepository.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L16) |
| `class DashboardTileBinding` | [TripDashboardRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L17) |
| `fun transformed` | [TripDashboardRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L29) |
| `class TripDashboard` | [TripDashboardRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L31) |
| `object TripCustomMetricRecordingPolicy` | [TripDashboardRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L33) |
| `fun bindings` | [TripDashboardRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L34) |
| `fun save` | [TripDashboardRepository.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L45) |
| `fun create` | [TripDashboardRepository.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L53) |
| `fun delete` | [TripDashboardRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L57) |
| `fun reorder` | [TripDashboardRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L58) |
| `fun decode` | [TripDashboardRepository.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L62) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripReplayPoint` | [TripReplayLoader.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L8) |
| `class TripReplayMarker` | [TripReplayLoader.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L22) |
| `class TripReplayData` | [TripReplayLoader.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L23) |
| `class TripReplayColorMode` | [TripReplayLoader.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L24) |
| `object TripReplayPolicy` | [TripReplayLoader.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L26) |
| `fun nearestIndex` | [TripReplayLoader.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L27) |
| `fun colorBucket` | [TripReplayLoader.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L32) |
| `fun load` | [TripReplayLoader.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripReplayLoader.kt#L48) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripWriterResult` | [TripSampleWriter.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L9) |
| `class PendingTripBatch` | [TripSampleWriter.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L11) |
| `fun enqueue` | [TripSampleWriter.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L17) |
| `fun size` | [TripSampleWriter.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L24) |
| `fun take` | [TripSampleWriter.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L26) |
| `fun restore` | [TripSampleWriter.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L32) |
| `fun enqueue` | [TripSampleWriter.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L49) |
| `fun size` | [TripSampleWriter.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L50) |
| `fun flush` | [TripSampleWriter.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L51) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripTrackPoint` | [TripTrackPipeline.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L18) |
| `class TripTrackSegment` | [TripTrackPipeline.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L31) |
| `class TripMapDestinationType` | [TripTrackPipeline.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L33) |
| `class TripMapDestination` | [TripTrackPipeline.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L34) |
| `class TripMapData` | [TripTrackPipeline.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L35) |
| `class TripTrackSnapshot` | [TripTrackPipeline.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L42) |
| `fun rendered` | [TripTrackPipeline.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L53) |
| `object TripTrackRenderPolicy` | [TripTrackPipeline.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L71) |
| `fun render` | [TripTrackPipeline.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L80) |
| `fun compact` | [TripTrackPipeline.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L93) |
| `fun withBudget` | [TripTrackPipeline.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L126) |
| `fun segment` | [TripTrackPipeline.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L149) |
| `fun flush` | [TripTrackPipeline.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L151) |
| `fun begin` | [TripTrackPipeline.kt:192](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L192) |
| `fun clear` | [TripTrackPipeline.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L205) |
| `fun appendLive` | [TripTrackPipeline.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L207) |
| `fun markPersisted` | [TripTrackPipeline.kt:215](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L215) |
| `fun loadRendered` | [TripTrackPipeline.kt:223](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L223) |
| `fun loadMapData` | [TripTrackPipeline.kt:229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L229) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `object PressureHistoryPolicy` | [PressureHistoryRepository.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L18) |
| `fun bucket` | [PressureHistoryRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L22) |
| `fun validPressure` | [PressureHistoryRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L23) |
| `class PressureHistoryRepository @Inject constructor` | [PressureHistoryRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L29) |
| `fun record` | [PressureHistoryRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L58) |
| `fun trend` | [PressureHistoryRepository.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L65) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TrueWindReference` | [TrueWindResolver.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L8) |
| `class ResolvedTrueWind` | [TrueWindResolver.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L9) |
| `fun select` | [TrueWindResolver.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L26) |
| `fun reset` | [TrueWindResolver.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L35) |
| `object TrueWindResolver` | [TrueWindResolver.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L40) |
| `fun resolve` | [TrueWindResolver.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/TrueWindResolver.kt#L41) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun numeric` | [VesselDataHub.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L95) |
| `fun newlyMeasured` | [VesselDataHub.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L96) |
| `fun textual` | [VesselDataHub.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L97) |
| `fun update` | [VesselDataHub.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L98) |
| `fun hasFreshPhonePosition` | [VesselDataHub.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L123) |
| `fun setTripPositionPreference` | [VesselDataHub.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L124) |
| `fun setShellPositionSource` | [VesselDataHub.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L126) |
| `fun pressureTrend` | [VesselDataHub.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L140) |
| `fun freshValue` | [VesselDataHub.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L147) |
| `fun resolvedWindField` | [VesselDataHub.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L151) |
| `fun selectedTrueWind` | [VesselDataHub.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L174) |
| `fun selectedMetric` | [VesselDataHub.kt:193](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L193) |
| `fun attitudeField` | [VesselDataHub.kt:204](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L204) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselMetricSelectionPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object VesselMetricSelectionPolicy` | [VesselMetricSelectionPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselMetricSelectionPolicy.kt#L7) |
| `fun choose` | [VesselMetricSelectionPolicy.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselMetricSelectionPolicy.kt#L8) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselPositionRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselPositionRepository @Inject constructor` | [VesselPositionRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselPositionRepository.kt#L17) |
| `fun ingestBoat` | [VesselPositionRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselPositionRepository.kt#L29) |
| `fun ingestPhone` | [VesselPositionRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselPositionRepository.kt#L30) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselDataSettings` | [VesselSettingsRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L28) |
| `fun VesselDataSettings.layout` | [VesselSettingsRepository.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L47) |
| `fun VesselDataSettings.withLayout` | [VesselSettingsRepository.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L48) |
| `class NmeaDeviceOutputSettings` | [VesselSettingsRepository.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L56) |
| `class NmeaOutputTransportMode` | [VesselSettingsRepository.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L90) |
| `class PhoneHeadingOutputFormat` | [VesselSettingsRepository.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L91) |
| `class NmeaOutputPreset` | [VesselSettingsRepository.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L100) |
| `fun NmeaDeviceOutputSettings.withPreset` | [VesselSettingsRepository.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L101) |
| `object NmeaOutputLeasePolicy` | [VesselSettingsRepository.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L109) |
| `fun shouldAutoStart` | [VesselSettingsRepository.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L114) |
| `fun afterRestore` | [VesselSettingsRepository.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L115) |
| `fun setVesselIdentity` | [VesselSettingsRepository.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L134) |
| `fun setInstrumentLayout` | [VesselSettingsRepository.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L141) |
| `fun selectMetricSource` | [VesselSettingsRepository.kt:146](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L146) |
| `fun selectPositionConnection` | [VesselSettingsRepository.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L162) |
| `fun pinPositionSourceIfUnselected` | [VesselSettingsRepository.kt:175](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L175) |
| `fun save` | [VesselSettingsRepository.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L185) |
| `fun activateAutoStart` | [VesselSettingsRepository.kt:235](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L235) |
| `fun saveConfiguration` | [VesselSettingsRepository.kt:253](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L253) |
| `fun requestStart` | [VesselSettingsRepository.kt:254](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L254) |
| `fun requestStop` | [VesselSettingsRepository.kt:255](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L255) |
| `fun save` | [VesselSettingsRepository.kt:259](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L259) |
| `object NmeaOutputTransportDefaults` | [VesselSettingsRepository.kt:278](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L278) |
| `fun restore` | [VesselSettingsRepository.kt:283](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L283) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselSourceRegistry @Inject constructor` | [VesselSourceRegistry.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L11) |
| `fun publish` | [VesselSourceRegistry.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L16) |
| `fun publishAll` | [VesselSourceRegistry.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L21) |
| `fun <T> candidates` | [VesselSourceRegistry.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L22) |
| `fun clearTransportGeneration` | [VesselSourceRegistry.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L23) |
| `fun clearNmea` | [VesselSourceRegistry.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L24) |
| `fun clearPhone` | [VesselSourceRegistry.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L25) |
| `fun removeSources` | [VesselSourceRegistry.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L26) |
| `fun invalidate` | [VesselSourceRegistry.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSourceRegistry.kt#L27) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AlarmEngine` | [AlarmEngine.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L13) |
| `fun learn` | [AlarmEngine.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L37) |
| `fun arm` | [AlarmEngine.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L40) |
| `fun stop` | [AlarmEngine.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L61) |
| `fun updateConfig` | [AlarmEngine.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L79) |
| `fun acknowledge` | [AlarmEngine.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L92) |
| `fun onFix` | [AlarmEngine.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L97) |
| `fun tick` | [AlarmEngine.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmEngine.kt#L165) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object AlarmReminderPolicy` | [AlarmReminderPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt#L6) |
| `fun snoozeUntil` | [AlarmReminderPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt#L7) |
| `fun isSnoozed` | [AlarmReminderPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt#L10) |
| `fun shouldSound` | [AlarmReminderPolicy.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt#L13) |
| `fun snoozeAfterTransition` | [AlarmReminderPolicy.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AlarmReminderPolicy.kt#L18) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCenterEstimator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Point` | [AnchorCenterEstimator.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCenterEstimator.kt#L16) |
| `fun estimate` | [AnchorCenterEstimator.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCenterEstimator.kt#L20) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorCentreObservabilityReason` | [AnchorCentreObservability.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L3) |
| `class AnchorCentreObservability` | [AnchorCentreObservability.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L13) |
| `object AnchorCentreObservabilityPolicy` | [AnchorCentreObservability.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L24) |
| `fun evaluate` | [AnchorCentreObservability.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L25) |
| `class AnchorCentreEvidenceBaseline` | [AnchorCentreObservability.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L57) |
| `object AnchorCentreEvidenceGrowthPolicy` | [AnchorCentreObservability.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L59) |
| `fun hasMeaningfulGrowth` | [AnchorCentreObservability.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L60) |
| `object AnchorCentreCandidatePolicy` | [AnchorCentreObservability.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L70) |
| `fun isMeaningfulShift` | [AnchorCentreObservability.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreObservability.kt#L71) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorCentreRecalculationStatus` | [AnchorCentreRecalculator.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L13) |
| `object AnchorCentreApplyPolicy` | [AnchorCentreRecalculator.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L20) |
| `fun mayApply` | [AnchorCentreRecalculator.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L21) |
| `class AnchorCentreRecalculationResult` | [AnchorCentreRecalculator.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L24) |
| `object AnchorCentreRecalculator` | [AnchorCentreRecalculator.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L34) |
| `fun analyze` | [AnchorCentreRecalculator.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorCentreRecalculator.kt#L35) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorContinuousEstimatePolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorContinuousEstimateDecision` | [AnchorContinuousEstimatePolicy.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorContinuousEstimatePolicy.kt#L3) |
| `object AnchorContinuousEstimatePolicy` | [AnchorContinuousEstimatePolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorContinuousEstimatePolicy.kt#L7) |
| `fun evaluate` | [AnchorContinuousEstimatePolicy.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorContinuousEstimatePolicy.kt#L8) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `object AnchorGeometry` | [AnchorGeometry.kt:4](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L4) |
| `fun distanceMeters` | [AnchorGeometry.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L6) |
| `fun bearingDegrees` | [AnchorGeometry.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L7) |
| `fun project` | [AnchorGeometry.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L8) |
| `fun scope` | [AnchorGeometry.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L9) |
| `fun expectedRadius` | [AnchorGeometry.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorGeometry.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorRangeCalculator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorRangeSuggestion` | [AnchorRangeCalculator.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorRangeCalculator.kt#L8) |
| `object AnchorRangeCalculator` | [AnchorRangeCalculator.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorRangeCalculator.kt#L16) |
| `fun advanced` | [AnchorRangeCalculator.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorRangeCalculator.kt#L17) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorSetupDepthPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorDepthSource` | [AnchorSetupDepthPolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorSetupDepthPolicy.kt#L5) |
| `object AnchorSetupDepthPolicy` | [AnchorSetupDepthPolicy.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorSetupDepthPolicy.kt#L13) |
| `fun nmeaAvailable` | [AnchorSetupDepthPolicy.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorSetupDepthPolicy.kt#L20) |
| `fun selectedDepth` | [AnchorSetupDepthPolicy.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/AnchorSetupDepthPolicy.kt#L31) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BackdownCenterEstimator` | [BackdownCenterEstimator.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L24) |
| `class Sample` | [BackdownCenterEstimator.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L25) |
| `fun estimate` | [BackdownCenterEstimator.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L51) |
| `fun estimateSamples` | [BackdownCenterEstimator.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L62) |
| `fun provisionalEstimate` | [BackdownCenterEstimator.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L151) |
| `fun scan` | [BackdownCenterEstimator.kt:204](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/BackdownCenterEstimator.kt#L204) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt

| 声明 | 实现位置 |
| --- | --- |
| `class CandidateCenterObservation` | [CandidateDriftDetector.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L6) |
| `fun encode` | [CandidateDriftDetector.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L12) |
| `fun decode` | [CandidateDriftDetector.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L15) |
| `class CandidateDriftUpdate` | [CandidateDriftDetector.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L30) |
| `class CandidateDriftDetector` | [CandidateDriftDetector.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L36) |
| `fun reset` | [CandidateDriftDetector.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L44) |
| `fun restore` | [CandidateDriftDetector.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L49) |
| `fun refinementSuppressed` | [CandidateDriftDetector.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L57) |
| `fun add` | [CandidateDriftDetector.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CandidateDriftDetector.kt#L59) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CoordinateParser.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ParsedCoordinate` | [CoordinateParser.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CoordinateParser.kt#L3) |
| `object CoordinateParser` | [CoordinateParser.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CoordinateParser.kt#L5) |
| `fun parse` | [CoordinateParser.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/CoordinateParser.kt#L6) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt

| 声明 | 实现位置 |
| --- | --- |
| `object WindAnchorEvidence` | [WindAnchorEvidence.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L17) |
| `class Source` | [WindAnchorEvidence.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L18) |
| `class Sample` | [WindAnchorEvidence.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L20) |
| `class Observation` | [WindAnchorEvidence.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L38) |
| `class Summary` | [WindAnchorEvidence.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L48) |
| `class CentreMatch` | [WindAnchorEvidence.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L58) |
| `fun summarize` | [WindAnchorEvidence.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L64) |
| `fun centreMatch` | [WindAnchorEvidence.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L74) |
| `fun candidateScore` | [WindAnchorEvidence.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchor/WindAnchorEvidence.kt#L84) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SavedAnchorageReference` | [AnchorageApproach.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L11) |
| `class AnchorageCluster` | [AnchorageApproach.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L29) |
| `object AnchorageClusterer` | [AnchorageApproach.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L48) |
| `fun cluster` | [AnchorageApproach.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L53) |
| `class AnchorageClusterDistance` | [AnchorageApproach.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L126) |
| `object AnchorageNearbyPolicy` | [AnchorageApproach.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L132) |
| `fun distances` | [AnchorageApproach.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L136) |
| `object AnchorageClusterIdentityResolver` | [AnchorageApproach.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L153) |
| `fun resolve` | [AnchorageApproach.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L154) |
| `class AnchorageNearbyEpisodeTracker` | [AnchorageApproach.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L169) |
| `fun update` | [AnchorageApproach.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L174) |
| `fun dismiss` | [AnchorageApproach.kt:203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L203) |
| `class ApproachPhase` | [AnchorageApproach.kt:210](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L210) |
| `class ApproachDirectionReference` | [AnchorageApproach.kt:211](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L211) |
| `class ApproachHeadingMode` | [AnchorageApproach.kt:212](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L212) |
| `class ApproachDirection` | [AnchorageApproach.kt:214](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L214) |
| `object ApproachDirectionPolicy` | [AnchorageApproach.kt:220](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L220) |
| `fun vesselModeAvailable` | [AnchorageApproach.kt:229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L229) |
| `fun resolve` | [AnchorageApproach.kt:239](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L239) |
| `fun signedAngle` | [AnchorageApproach.kt:264](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L264) |
| `class AnchorageApproachState` | [AnchorageApproach.kt:267](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L267) |
| `object AnchorageApproachEngine` | [AnchorageApproach.kt:280](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L280) |
| `fun evaluate` | [AnchorageApproach.kt:283](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L283) |
| `object ApproachDistanceFormatter` | [AnchorageApproach.kt:329](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L329) |
| `fun format` | [AnchorageApproach.kt:335](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L335) |
| `interface AnchorageDetailsTarget` | [AnchorageApproach.kt:342](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L342) |
| `class SavedAnchorage` | [AnchorageApproach.kt:343](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L343) |
| `class AnchorageList` | [AnchorageApproach.kt:344](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L344) |
| `object AnchorageDetailsPolicy` | [AnchorageApproach.kt:347](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L347) |
| `fun resolve` | [AnchorageApproach.kt:348](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L348) |
| `fun resolve` | [AnchorageApproach.kt:350](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageApproach.kt#L350) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageGeoPoint` | [AnchorageGeometry.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L6) |
| `class AnchorageBoundingBox` | [AnchorageGeometry.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L10) |
| `fun intersects` | [AnchorageGeometry.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L12) |
| `interface AnchorageGeometry` | [AnchorageGeometry.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L15) |
| `class Point` | [AnchorageGeometry.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L16) |
| `class Circle` | [AnchorageGeometry.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L17) |
| `class Polygon` | [AnchorageGeometry.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L18) |
| `class MultiPolygon` | [AnchorageGeometry.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L19) |
| `object AnchorageGeometryOps` | [AnchorageGeometry.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L22) |
| `fun bbox` | [AnchorageGeometry.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L25) |
| `fun centroid` | [AnchorageGeometry.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L36) |
| `fun contains` | [AnchorageGeometry.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L43) |
| `fun distanceToBoundaryMeters` | [AnchorageGeometry.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L50) |
| `fun simplifyForMap` | [AnchorageGeometry.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L57) |
| `fun envelope` | [AnchorageGeometry.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L63) |
| `fun distance` | [AnchorageGeometry.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L64) |
| `fun ringContains` | [AnchorageGeometry.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometry.kt#L68) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometryCodec.kt

| 声明 | 实现位置 |
| --- | --- |
| `object AnchorageGeometryCodec` | [AnchorageGeometryCodec.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometryCodec.kt#L7) |
| `fun decode` | [AnchorageGeometryCodec.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometryCodec.kt#L11) |
| `fun encode` | [AnchorageGeometryCodec.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageGeometryCodec.kt#L30) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMapModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageMapPlace` | [AnchorageMapModels.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMapModels.kt#L3) |
| `class AnchorageRegionAggregate` | [AnchorageMapModels.kt:4](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMapModels.kt#L4) |
| `object AnchorageVisualClusterer` | [AnchorageMapModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMapModels.kt#L6) |
| `fun aggregate` | [AnchorageMapModels.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMapModels.kt#L9) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageSpotMatch` | [AnchorageMatchEngines.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L6) |
| `class AnchorageSpotMatchCandidate` | [AnchorageMatchEngines.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L7) |
| `class AnchorageSpotMatchResult` | [AnchorageMatchEngines.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L8) |
| `object AnchorageSpotMatchEngine` | [AnchorageMatchEngines.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L10) |
| `fun evaluate` | [AnchorageMatchEngines.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L11) |
| `class AnchoragePlaceMatchCandidate` | [AnchorageMatchEngines.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L30) |
| `class AnchoragePlaceMatchResult` | [AnchorageMatchEngines.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L31) |
| `object AnchoragePlaceMatchEngine` | [AnchorageMatchEngines.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L32) |
| `fun rank` | [AnchorageMatchEngines.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L33) |
| `object AnchorageRegionCandidateRanker` | [AnchorageMatchEngines.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L43) |
| `fun score` | [AnchorageMatchEngines.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L44) |
| `fun rank` | [AnchorageMatchEngines.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L45) |
| `fun resolve` | [AnchorageMatchEngines.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageMatchEngines.kt#L49) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageRegionSource` | [AnchorageModels.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L3) |
| `class AnchorageRegionFeatureType` | [AnchorageModels.kt:4](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L4) |
| `class AnchorageGeometryType` | [AnchorageModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L5) |
| `class AnchoragePlaceType` | [AnchorageModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L6) |
| `class AnchorageVerificationStatus` | [AnchorageModels.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L7) |
| `class AnchoragePlanningStatus` | [AnchorageModels.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L8) |
| `class AnchorageSpotType` | [AnchorageModels.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L9) |
| `class AnchorageVisitKind` | [AnchorageModels.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L10) |
| `class AnchorageProtectionMedium` | [AnchorageModels.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L12) |
| `class AnchorageCompassSector` | [AnchorageModels.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L13) |
| `class AnchorageProtectionRating` | [AnchorageModels.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L14) |
| `class AnchorageInformationSource` | [AnchorageModels.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L15) |
| `class AnchorageFacilityType` | [AnchorageModels.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L16) |
| `class AnchorageFacilityAvailability` | [AnchorageModels.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L17) |
| `class AnchoragePreference` | [AnchorageModels.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L18) |
| `class AnchorageSaveDraft` | [AnchorageModels.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L20) |
| `class AnchorageViewport` | [AnchorageModels.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L38) |
| `fun queryWindows` | [AnchorageModels.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L41) |
| `class AnchorageRegionParentHint` | [AnchorageModels.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L44) |
| `class AnchorageRegionCandidate` | [AnchorageModels.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L45) |
| `interface AnchorageRegionProvider` | [AnchorageModels.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L61) |
| `fun resolveCandidates` | [AnchorageModels.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L63) |
| `class AnchorageForecastInput` | [AnchorageModels.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L66) |
| `class AnchorageConditionFit` | [AnchorageModels.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L74) |
| `class AnchorageWindExperience` | [AnchorageModels.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L81) |
| `class AnchorageSummaryCoverage` | [AnchorageModels.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L82) |
| `class PersonalAnchorageSummary` | [AnchorageModels.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L85) |
| `class AnchorageVisitObservation` | [AnchorageModels.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageModels.kt#L101) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageProtectionObservation` | [AnchorageProtection.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L3) |
| `object AnchorageConditionFitEngine` | [AnchorageProtection.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L5) |
| `fun evaluate` | [AnchorageProtection.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L6) |
| `fun sector` | [AnchorageProtection.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L19) |
| `object PersonalAnchorageSummaryEngine` | [AnchorageProtection.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L22) |
| `fun summarize` | [AnchorageProtection.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L24) |
| `fun valid` | [AnchorageProtection.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/anchorage/AnchorageProtection.kt#L25) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DepthGuardEngine` | [ConditionGuardEngines.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L15) |
| `fun reset` | [ConditionGuardEngines.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L21) |
| `fun update` | [ConditionGuardEngines.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L22) |
| `class WindSpeedGuardEngine` | [ConditionGuardEngines.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L66) |
| `fun reset` | [ConditionGuardEngines.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L70) |
| `fun update` | [ConditionGuardEngines.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L71) |
| `class WindShiftGuardEngine` | [ConditionGuardEngines.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L99) |
| `fun restore` | [ConditionGuardEngines.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L103) |
| `fun reset` | [ConditionGuardEngines.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L104) |
| `fun update` | [ConditionGuardEngines.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionGuardEngines.kt#L105) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DepthGuardStatus` | [ConditionModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L5) |
| `class WindSpeedGuardStatus` | [ConditionModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L6) |
| `class WindShiftGuardStatus` | [ConditionModels.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L7) |
| `class WindSpeedSource` | [ConditionModels.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L8) |
| `class TrueWindDirectionSource` | [ConditionModels.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L9) |
| `class ConditionAlarmSource` | [ConditionModels.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L10) |
| `class ConditionGuardConfig` | [ConditionModels.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L12) |
| `fun validated` | [ConditionModels.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L23) |
| `fun ConditionGuardConfig.hasMeaningfulDiff` | [ConditionModels.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L43) |
| `object ConditionGuardAvailability` | [ConditionModels.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L53) |
| `fun hasInstrumentTraffic` | [ConditionModels.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L57) |
| `class Sensors` | [ConditionModels.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L63) |
| `fun canApply` | [ConditionModels.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L75) |
| `class DepthGuardSnapshot` | [ConditionModels.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L95) |
| `class WindSpeedGuardSnapshot` | [ConditionModels.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L102) |
| `class WindShiftGuardSnapshot` | [ConditionModels.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L111) |
| `class ConditionRuntimeSnapshot` | [ConditionModels.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L123) |
| `class ConditionSnoozeState` | [ConditionModels.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L132) |
| `object ConditionAudibilityPolicy` | [ConditionModels.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L139) |
| `fun audibleSources` | [ConditionModels.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L140) |
| `fun windSnoozeAfterTransition` | [ConditionModels.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L149) |
| `class SafetyAlert` | [ConditionModels.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L153) |
| `object SafetyAlertAggregator` | [ConditionModels.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L160) |
| `fun sorted` | [ConditionModels.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/condition/ConditionModels.kt#L165) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ConfigurationScope` | [ConfigurationOwnership.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt#L5) |
| `class ConfigurationKey` | [ConfigurationOwnership.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt#L7) |
| `class ConfigurationOwnership` | [ConfigurationOwnership.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt#L29) |
| `object ConfigurationOwnershipRegistry` | [ConfigurationOwnership.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt#L36) |
| `fun owner` | [ConfigurationOwnership.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/config/ConfigurationOwnership.kt#L59) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/MarineDestination.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineDestination` | [MarineDestination.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/MarineDestination.kt#L3) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NavigationFix` | [Models.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L5) |
| `class GpsDataSource` | [Models.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L54) |
| `class PositionProvider` | [Models.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L55) |
| `class FixTrust` | [Models.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L56) |
| `class PositionHealth` | [Models.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L57) |
| `class HeadingSource` | [Models.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L58) |
| `class HeadingQuality` | [Models.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L59) |
| `class AppLanguage` | [Models.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L61) |
| `class AlarmSound` | [Models.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L70) |
| `class DemoScenario` | [Models.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L71) |
| `class AnchorPlacementMode` | [Models.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L74) |
| `class AnchorPositionMode` | [Models.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L75) |
| `class KnownAnchorMethod` | [Models.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L76) |
| `class AnchorCenterSource` | [Models.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L77) |
| `class AnchorOriginMode` | [Models.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L79) |
| `class AnchorMonitoringPhase` | [Models.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L81) |
| `class CandidateDecision` | [Models.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L82) |
| `class AnchorCenterStatus` | [Models.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L83) |
| `class AnchorRangeMode` | [Models.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L84) |
| `class AnchorSafetyPreset` | [Models.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L85) |
| `class NmeaConnectionState` | [Models.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L86) |
| `class AlarmState` | [Models.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L87) |
| `class AlarmType` | [Models.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L88) |
| `class Confidence` | [Models.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L89) |
| `class AnchorEstimate` | [Models.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L91) |
| `class BackdownAnchorEstimate` | [Models.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L101) |
| `fun debugSummary` | [Models.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L126) |
| `class AnchorConfig` | [Models.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L129) |
| `class AlarmSnapshot` | [Models.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L140) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TrustedNmeaCourse` | [NmeaCourseTrustGate.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt#L7) |
| `fun isFresh` | [NmeaCourseTrustGate.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt#L12) |
| `class NmeaCourseTrustGate` | [NmeaCourseTrustGate.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt#L20) |
| `fun update` | [NmeaCourseTrustGate.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt#L26) |
| `fun reset` | [NmeaCourseTrustGate.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/navigation/NmeaCourseTrustGate.kt#L65) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/AnchorReport.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorReport` | [AnchorReport.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/AnchorReport.kt#L10) |
| `fun generate` | [AnchorReport.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/AnchorReport.kt#L44) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TackSide` | [SailingAnalytics.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L5) |
| `class PointOfSail` | [SailingAnalytics.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L6) |
| `class SailingAnalyticsSummary` | [SailingAnalytics.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L8) |
| `class SailingAnalyticsAccumulator` | [SailingAnalytics.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L22) |
| `fun add` | [SailingAnalytics.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L38) |
| `fun summary` | [SailingAnalytics.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/SailingAnalytics.kt#L71) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class StreamingStatistics` | [StreamingStatistics.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L15) |
| `fun add` | [StreamingStatistics.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L27) |
| `fun mean` | [StreamingStatistics.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L40) |
| `fun rms` | [StreamingStatistics.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L41) |
| `fun quantile` | [StreamingStatistics.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L42) |
| `class StreamingCircularMean` | [StreamingStatistics.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L53) |
| `fun add` | [StreamingStatistics.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L56) |
| `fun degrees` | [StreamingStatistics.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/StreamingStatistics.kt#L57) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripAttitudeArtifactPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripAttitudeFilterPoint` | [TripAttitudeArtifactPolicy.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripAttitudeArtifactPolicy.kt#L12) |
| `object TripAttitudeArtifactPolicy` | [TripAttitudeArtifactPolicy.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripAttitudeArtifactPolicy.kt#L23) |
| `fun isShortHandlingArtifact` | [TripAttitudeArtifactPolicy.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripAttitudeArtifactPolicy.kt#L24) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripLegBoundary` | [TripLegAnalytics.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L6) |
| `class TripLegPoint` | [TripLegAnalytics.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L7) |
| `class TripLegSummary` | [TripLegAnalytics.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L8) |
| `class TripLegAccumulator` | [TripLegAnalytics.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L11) |
| `fun add` | [TripLegAnalytics.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L16) |
| `fun summaries` | [TripLegAnalytics.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripLegAnalytics.kt#L31) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ReportQuality` | [TripReport.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L15) |
| `class TripTrueWindReference` | [TripReport.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L16) |
| `object TripTrueWindReferenceClassifier` | [TripReport.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L17) |
| `fun from` | [TripReport.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L18) |
| `class TripFinding` | [TripReport.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L28) |
| `class TripSourceTimelineEntry` | [TripReport.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L29) |
| `class HeelDistribution` | [TripReport.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L30) |
| `class WindHeelBand` | [TripReport.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L31) |
| `class SpeedHeelBand` | [TripReport.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L32) |
| `class TripReport` | [TripReport.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L33) |
| `fun generate` | [TripReport.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L72) |
| `class AttitudeFrame` | [TripReport.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L98) |
| `fun filterPoint` | [TripReport.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L124) |
| `fun addAttitudeFrame` | [TripReport.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L129) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/SafetyRecoveryPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SafetyRecoveryDestination` | [SafetyRecoveryPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/SafetyRecoveryPolicy.kt#L6) |
| `object SafetyRecoveryPolicy` | [SafetyRecoveryPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/SafetyRecoveryPolicy.kt#L9) |
| `fun destination` | [SafetyRecoveryPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/SafetyRecoveryPolicy.kt#L10) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SafetyCheckStatus` | [WatchPreflight.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L28) |
| `class SafetyCheck` | [WatchPreflight.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L30) |
| `class WatchSafetyReport` | [WatchPreflight.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L38) |
| `class DeviceSafetySnapshot` | [WatchPreflight.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L45) |
| `class WatchSafetyInput` | [WatchPreflight.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L58) |
| `class AnchorSetupReadinessInput` | [WatchPreflight.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L69) |
| `class AnchorSetupReadiness` | [WatchPreflight.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L81) |
| `object AnchorSetupReadinessEvaluator` | [WatchPreflight.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L86) |
| `fun evaluate` | [WatchPreflight.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L87) |
| `object WatchPreflightEvaluator` | [WatchPreflight.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L104) |
| `fun evaluate` | [WatchPreflight.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L110) |
| `fun snapshot` | [WatchPreflight.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/safety/WatchPreflight.kt#L186) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthIntegrityFilter.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DepthIntegrityFilter` | [DepthIntegrityFilter.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthIntegrityFilter.kt#L12) |
| `fun reset` | [DepthIntegrityFilter.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthIntegrityFilter.kt#L16) |
| `fun evaluate` | [DepthIntegrityFilter.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthIntegrityFilter.kt#L18) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthUiState.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DepthUiState` | [DepthUiState.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/DepthUiState.kt#L5) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SonarDepthHoldState` | [SonarDepthHoldPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L7) |
| `class SonarAutoStopReason` | [SonarDepthHoldPolicy.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L8) |
| `class SonarDepthHoldDecision` | [SonarDepthHoldPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L10) |
| `object SonarDepthHoldPolicy` | [SonarDepthHoldPolicy.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L12) |
| `fun isValidRealDepth` | [SonarDepthHoldPolicy.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L20) |
| `fun belongsToCurrentConnection` | [SonarDepthHoldPolicy.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L23) |
| `fun evaluate` | [SonarDepthHoldPolicy.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L27) |
| `class SonarHeldDepth` | [SonarDepthHoldPolicy.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L40) |
| `class SonarHeldPosition` | [SonarDepthHoldPolicy.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L50) |
| `class SonarDepthHoldTracker` | [SonarDepthHoldPolicy.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L53) |
| `fun acceptRealDepth` | [SonarDepthHoldPolicy.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L57) |
| `fun acceptPosition` | [SonarDepthHoldPolicy.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L60) |
| `fun clearForConnectionGeneration` | [SonarDepthHoldPolicy.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarDepthHoldPolicy.kt#L79) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SonarGridSample` | [SonarGrid.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L16) |
| `class SonarCell` | [SonarGrid.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L17) |
| `class SonarInspection` | [SonarGrid.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L18) |
| `fun applyCell` | [SonarGrid.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L28) |
| `fun cellsInBounds` | [SonarGrid.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L34) |
| `fun inspect` | [SonarGrid.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L46) |
| `fun inspectProjected` | [SonarGrid.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L50) |
| `fun build` | [SonarGrid.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L66) |
| `fun fromPersisted` | [SonarGrid.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L71) |
| `fun aggregateCell` | [SonarGrid.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L74) |
| `fun project` | [SonarGrid.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L81) |
| `fun unproject` | [SonarGrid.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarGrid.kt#L82) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DepthReference` | [SonarModels.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L3) |
| `class DepthSentenceType` | [SonarModels.kt:4](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L4) |
| `class TideMode` | [SonarModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L5) |
| `class DepthDisposition` | [SonarModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L6) |
| `class DepthObservation` | [SonarModels.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L9) |
| `fun belowSurfaceMeters` | [SonarModels.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L17) |
| `class DepthProvenance` | [SonarModels.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L27) |
| `fun from` | [SonarModels.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L34) |
| `class DepthCandidate` | [SonarModels.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L46) |
| `class DepthIntegrityResult` | [SonarModels.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L55) |
| `class NormalizedDepth` | [SonarModels.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L64) |
| `object DepthNormalizer` | [SonarModels.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L71) |
| `fun normalize` | [SonarModels.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L72) |
| `fun surfaceDepth` | [SonarModels.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarModels.kt#L92) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SonarSurveyStartDecision` | [SonarSurveyStartPolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L5) |
| `class SonarSurveyContinuityState` | [SonarSurveyStartPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L7) |
| `object SonarSurveyContinuityPolicy` | [SonarSurveyStartPolicy.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L15) |
| `fun evaluate` | [SonarSurveyStartPolicy.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L16) |
| `object SonarSurveyStartPolicy` | [SonarSurveyStartPolicy.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L32) |
| `fun evaluate` | [SonarSurveyStartPolicy.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L33) |
| `object SonarMapDisplayPolicy` | [SonarSurveyStartPolicy.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L51) |
| `fun isVisible` | [SonarSurveyStartPolicy.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/sonar/SonarSurveyStartPolicy.kt#L52) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TideStationType` | [TideModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L5) |
| `class TideExtremeType` | [TideModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L6) |
| `class TideCorrectionStatus` | [TideModels.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L7) |
| `class TideInterpolationQuality` | [TideModels.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L8) |
| `class TideStation` | [TideModels.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L10) |
| `class TideExtreme` | [TideModels.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L26) |
| `class TideInterpolationResult` | [TideModels.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L32) |
| `class TideCorrectionResult` | [TideModels.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/tide/TideModels.kt#L41) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSampleFreshness.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TripSampleFreshness` | [TripSampleFreshness.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSampleFreshness.kt#L5) |
| `fun trueWindAvailable` | [TripSampleFreshness.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSampleFreshness.kt#L9) |
| `fun apparentWindAvailable` | [TripSampleFreshness.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSampleFreshness.kt#L10) |
| `fun attitudeUsable` | [TripSampleFreshness.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSampleFreshness.kt#L11) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSessionTiming.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TripSessionTiming` | [TripSessionTiming.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSessionTiming.kt#L5) |
| `fun pendingPausedMillis` | [TripSessionTiming.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSessionTiming.kt#L6) |
| `fun end` | [TripSessionTiming.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/trip/TripSessionTiming.kt#L7) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MetricLabel` | [MetricLabelRegistry.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L7) |
| `object MetricLabelRegistry` | [MetricLabelRegistry.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L11) |
| `fun get` | [MetricLabelRegistry.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L50) |
| `fun get` | [MetricLabelRegistry.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L51) |
| `fun localizedName` | [MetricLabelRegistry.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L52) |
| `fun localizedName` | [MetricLabelRegistry.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/MetricLabelRegistry.kt#L61) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PublicationPolicy` | [NmeaPublisherModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L5) |
| `class NmeaOutputPurpose` | [NmeaPublisherModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L6) |
| `class PublisherOwnershipState` | [NmeaPublisherModels.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L14) |
| `class NmeaStreamReadiness` | [NmeaPublisherModels.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L15) |
| `class NmeaSentenceFamily` | [NmeaPublisherModels.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L16) |
| `class NmeaSuppressionReason` | [NmeaPublisherModels.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L17) |
| `class PublicationDecision` | [NmeaPublisherModels.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L18) |
| `class NmeaPublishedStreamStatus` | [NmeaPublisherModels.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L20) |
| `class NmeaDestinationTransport` | [NmeaPublisherModels.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L38) |
| `class NmeaRetryPolicy` | [NmeaPublisherModels.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L39) |
| `class NmeaOutputDestination` | [NmeaPublisherModels.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/NmeaPublisherModels.kt#L40) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PositionSourceConflictState` | [PositionSourceConflictPolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L5) |
| `class PositionSourceAvailabilityReason` | [PositionSourceConflictPolicy.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L11) |
| `object PositionSourceConflictPolicy` | [PositionSourceConflictPolicy.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L18) |
| `fun nmeaPositionAvailability` | [PositionSourceConflictPolicy.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L22) |
| `fun phonePositionOutputAvailability` | [PositionSourceConflictPolicy.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L24) |
| `fun canSelectNmeaPosition` | [PositionSourceConflictPolicy.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L26) |
| `fun canEnablePhonePositionOutput` | [PositionSourceConflictPolicy.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L27) |
| `fun canConsumeBoatNonPositionData` | [PositionSourceConflictPolicy.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PositionSourceConflictPolicy.kt#L30) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PressureTrend` | [PressureTrendEstimator.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L3) |
| `class PressureTrendEstimator` | [PressureTrendEstimator.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L6) |
| `fun add` | [PressureTrendEstimator.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L13) |
| `fun trend` | [PressureTrendEstimator.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L29) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripTransitionInput` | [TripEventTransitions.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt#L3) |
| `class TripTransitionEvent` | [TripEventTransitions.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt#L17) |
| `class TripEventTransitionTracker` | [TripEventTransitions.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt#L24) |
| `fun reset` | [TripEventTransitions.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt#L34) |
| `fun update` | [TripEventTransitions.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripEventTransitions.kt#L39) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripStartSensorPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TripStartSensorPolicy` | [TripStartSensorPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripStartSensorPolicy.kt#L10) |
| `fun phoneMotionEnabled` | [TripStartSensorPolicy.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/TripStartSensorPolicy.kt#L11) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/UkcCompatibilityPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object UkcCompatibilityPolicy` | [UkcCompatibilityPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/UkcCompatibilityPolicy.kt#L6) |
| `fun calculate` | [UkcCompatibilityPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/UkcCompatibilityPolicy.kt#L7) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselDataSource` | [VesselDataModels.kt:3](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L3) |
| `class VesselDataQuality` | [VesselDataModels.kt:4](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L4) |
| `class VesselDataFreshness` | [VesselDataModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L5) |
| `class VesselSourcePreference` | [VesselDataModels.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L6) |
| `class WatchWorkspaceMode` | [VesselDataModels.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L7) |
| `class TripInstrumentPreset` | [VesselDataModels.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L8) |
| `class InstrumentTileId` | [VesselDataModels.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L9) |
| `object InstrumentLayoutPolicy` | [VesselDataModels.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L19) |
| `fun defaults` | [VesselDataModels.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L20) |
| `fun catalog` | [VesselDataModels.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L27) |
| `fun normalized` | [VesselDataModels.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L28) |
| `class VesselPosition` | [VesselDataModels.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L34) |
| `class VesselWindObservation` | [VesselDataModels.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L43) |
| `class VesselAttitude` | [VesselDataModels.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L49) |
| `class MotionPeriodConfidence` | [VesselDataModels.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L57) |
| `class VesselMotion` | [VesselDataModels.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L58) |
| `class VesselDerivedSnapshot` | [VesselDataModels.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L72) |
| `class VesselObservation<T>` | [VesselDataModels.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L84) |
| `class VesselDataSnapshot` | [VesselDataModels.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L101) |
| `object VesselSourceSelector` | [VesselDataModels.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L140) |
| `fun <T> select` | [VesselDataModels.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L141) |
| `class VesselAutoSourceSelector<T>` | [VesselDataModels.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L157) |
| `fun select` | [VesselDataModels.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L165) |
| `fun reset` | [VesselDataModels.kt:177](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L177) |
| `object VesselFreshnessPolicy` | [VesselDataModels.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L181) |
| `fun <T> classify` | [VesselDataModels.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L182) |
| `fun <T> retain` | [VesselDataModels.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDataModels.kt#L200) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDisplayObservationPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object VesselDisplayObservationPolicy` | [VesselDisplayObservationPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDisplayObservationPolicy.kt#L6) |
| `fun retainDerived` | [VesselDisplayObservationPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDisplayObservationPolicy.kt#L7) |
| `fun <T> resolve` | [VesselDisplayObservationPolicy.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDisplayObservationPolicy.kt#L13) |
| `fun attitude` | [VesselDisplayObservationPolicy.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselDisplayObservationPolicy.kt#L31) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselFixProjection.kt

| 声明 | 实现位置 |
| --- | --- |
| `object VesselFixProjection` | [VesselFixProjection.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselFixProjection.kt#L6) |
| `fun compose` | [VesselFixProjection.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselFixProjection.kt#L7) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselMotionAnalyzer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselMotionPoint` | [VesselMotionAnalyzer.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselMotionAnalyzer.kt#L6) |
| `class VesselMotionAnalyzer` | [VesselMotionAnalyzer.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselMotionAnalyzer.kt#L16) |
| `fun add` | [VesselMotionAnalyzer.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselMotionAnalyzer.kt#L24) |
| `fun reset` | [VesselMotionAnalyzer.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselMotionAnalyzer.kt#L35) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselMetricId` | [VesselSourceModels.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L5) |
| `class VesselSourceType` | [VesselSourceModels.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L13) |
| `class VesselSourceClass` | [VesselSourceModels.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L14) |
| `class VesselSourceIdentity` | [VesselSourceModels.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L16) |
| `object VesselSourcePinPolicy` | [VesselSourceModels.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L32) |
| `fun normalize` | [VesselSourceModels.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L34) |
| `fun matches` | [VesselSourceModels.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L35) |
| `fun resolve` | [VesselSourceModels.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L36) |
| `interface VesselReference` | [VesselSourceModels.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L41) |
| `object TrueNorth:VesselReference` | [VesselSourceModels.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L42) |
| `object MagneticNorth:VesselReference` | [VesselSourceModels.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L43) |
| `object WaterReferenced:VesselReference` | [VesselSourceModels.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L44) |
| `object GroundReferenced:VesselReference` | [VesselSourceModels.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L45) |
| `object VesselRelative:VesselReference` | [VesselSourceModels.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L47) |
| `class Depth` | [VesselSourceModels.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L48) |
| `interface VesselProvenance` | [VesselSourceModels.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L51) |
| `class Nmea` | [VesselSourceModels.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L52) |
| `class PhoneSensor` | [VesselSourceModels.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L53) |
| `class Derived` | [VesselSourceModels.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L54) |
| `class CandidateValidity` | [VesselSourceModels.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L57) |
| `class VesselSourceCandidate<T>` | [VesselSourceModels.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L58) |
| `class VesselSourceConflict` | [VesselSourceModels.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L82) |
| `class VesselSourceSelection<T>` | [VesselSourceModels.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L89) |
| `fun VesselDataSource.toSourceClass` | [VesselSourceModels.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L97) |
| `fun VesselSourceClass.toLegacySource` | [VesselSourceModels.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L108) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VmgReferencePolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VmgResult` | [VmgReferencePolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VmgReferencePolicy.kt#L5) |
| `object VmgReferencePolicy` | [VmgReferencePolicy.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VmgReferencePolicy.kt#L8) |
| `fun calculate` | [VmgReferencePolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VmgReferencePolicy.kt#L9) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MetricSourcePreference` | [VesselSourceArbitrator.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L8) |
| `object VesselSourceConflictPolicy` | [VesselSourceArbitrator.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L15) |
| `class Threshold` | [VesselSourceArbitrator.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L16) |
| `fun threshold` | [VesselSourceArbitrator.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L17) |
| `fun difference` | [VesselSourceArbitrator.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L25) |
| `fun effectiveDifferenceThreshold` | [VesselSourceArbitrator.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L33) |
| `class VesselSourceArbitrator` | [VesselSourceArbitrator.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L47) |
| `fun <T> select` | [VesselSourceArbitrator.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L51) |
| `fun reset` | [VesselSourceArbitrator.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L85) |
| `object MetricSourceEligibility` | [VesselSourceArbitrator.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L112) |
| `fun evaluate` | [VesselSourceArbitrator.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L113) |
| `fun measurementLeaseMillis` | [VesselSourceArbitrator.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L132) |
| `fun heartbeatFreshMillis` | [VesselSourceArbitrator.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/source/VesselSourceArbitrator.kt#L144) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AcceptedAnchorPositionReadiness` | [AcceptedAnchorPositionPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L9) |
| `class AnchorRawPositionCandidate` | [AcceptedAnchorPositionPolicy.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L16) |
| `object AnchorRawPositionPrimingPolicy` | [AcceptedAnchorPositionPolicy.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L28) |
| `fun select` | [AcceptedAnchorPositionPolicy.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L29) |
| `object AcceptedAnchorPositionPolicy` | [AcceptedAnchorPositionPolicy.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L58) |
| `fun evaluate` | [AcceptedAnchorPositionPolicy.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L59) |
| `fun no` | [AcceptedAnchorPositionPolicy.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedAnchorPositionPolicy.kt#L69) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AcceptedPositionState` | [AcceptedPositionRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L25) |
| `class AcceptedPositionEvent` | [AcceptedPositionRepository.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L42) |
| `class AcceptedPositionRepository @Inject constructor` | [AcceptedPositionRepository.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L55) |
| `fun selectSource` | [AcceptedPositionRepository.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L82) |
| `fun beginArmAttempt` | [AcceptedPositionRepository.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L99) |
| `fun lockSource` | [AcceptedPositionRepository.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L108) |
| `fun resetEvidenceEpoch` | [AcceptedPositionRepository.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L130) |
| `fun unlockSource` | [AcceptedPositionRepository.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L137) |
| `fun seed` | [AcceptedPositionRepository.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L144) |
| `fun submit` | [AcceptedPositionRepository.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AcceptedPositionRepository.kt#L161) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneVesselHeadingAlignment` | [AnchorHeadingEvidenceRouter.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt#L14) |
| `class AnchorHeadingEvidence` | [AnchorHeadingEvidenceRouter.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt#L19) |
| `object AnchorHeadingEvidenceRouter` | [AnchorHeadingEvidenceRouter.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt#L31) |
| `fun routeSelected` | [AnchorHeadingEvidenceRouter.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt#L34) |
| `fun route` | [AnchorHeadingEvidenceRouter.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AnchorHeadingEvidenceRouter.kt#L53) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DemoGpsStatus` | [DemoLocationRepository.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L16) |
| `class DemoLocationRepository @Inject constructor` | [DemoLocationRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L24) |
| `fun start` | [DemoLocationRepository.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L43) |
| `fun tick` | [DemoLocationRepository.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L49) |
| `fun pause` | [DemoLocationRepository.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L51) |
| `fun resume` | [DemoLocationRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L57) |
| `fun stop` | [DemoLocationRepository.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L65) |
| `fun reconfigure` | [DemoLocationRepository.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L67) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoSonarGenerator.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun observation` | [DemoSonarGenerator.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoSonarGenerator.kt#L16) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DemoTrajectoryPoint` | [DemoTrajectory.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt#L13) |
| `object DemoTrajectory` | [DemoTrajectory.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt#L34) |
| `fun point` | [DemoTrajectory.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt#L43) |
| `fun settled` | [DemoTrajectory.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt#L97) |
| `fun target` | [DemoTrajectory.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoTrajectory.kt#L150) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MockGpsState` | [GlobalMockLocationManager.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L20) |
| `class MockGpsStatus` | [GlobalMockLocationManager.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L21) |
| `interface MockLocationSink` | [GlobalMockLocationManager.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L22) |
| `fun start` | [GlobalMockLocationManager.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L27) |
| `fun publish` | [GlobalMockLocationManager.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L39) |
| `fun stale` | [GlobalMockLocationManager.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L44) |
| `fun stop` | [GlobalMockLocationManager.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L45) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GpsSourceSafety.kt

| 声明 | 实现位置 |
| --- | --- |
| `object GpsSourceSafety` | [GpsSourceSafety.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GpsSourceSafety.kt#L6) |
| `fun blocksSystemGps` | [GpsSourceSafety.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GpsSourceSafety.kt#L7) |
| `fun requiresStopAction` | [GpsSourceSafety.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GpsSourceSafety.kt#L13) |
| `fun allowsSessionSource` | [GpsSourceSafety.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GpsSourceSafety.kt#L22) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/MockGpsPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun start` | [MockGpsPolicy.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/MockGpsPolicy.kt#L5) |
| `fun onValidFix` | [MockGpsPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/MockGpsPolicy.kt#L6) |
| `fun isStale` | [MockGpsPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/MockGpsPolicy.kt#L7) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaPositionAwaiter.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaPositionAwaiter` | [NmeaPositionAwaiter.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaPositionAwaiter.kt#L14) |
| `fun awaitUsable` | [NmeaPositionAwaiter.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaPositionAwaiter.kt#L15) |
| `fun usable` | [NmeaPositionAwaiter.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaPositionAwaiter.kt#L23) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaSourceAvailability` | [NmeaSourceSelectionPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L7) |
| `object NmeaSourceSelectionPolicy` | [NmeaSourceSelectionPolicy.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L15) |
| `fun hasLiveTransportState` | [NmeaSourceSelectionPolicy.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L26) |
| `fun availability` | [NmeaSourceSelectionPolicy.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L29) |
| `fun isUsablePosition` | [NmeaSourceSelectionPolicy.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L54) |
| `object NewAnchorPositionSourcePolicy` | [NmeaSourceSelectionPolicy.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L77) |
| `fun resolve` | [NmeaSourceSelectionPolicy.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/NmeaSourceSelectionPolicy.kt#L78) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingIntegrityMonitor.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneHeadingObservation` | [PhoneHeadingIntegrityMonitor.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingIntegrityMonitor.kt#L10) |
| `class PhoneHeadingIntegrityMonitor` | [PhoneHeadingIntegrityMonitor.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingIntegrityMonitor.kt#L17) |
| `fun reset` | [PhoneHeadingIntegrityMonitor.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingIntegrityMonitor.kt#L30) |
| `fun observe` | [PhoneHeadingIntegrityMonitor.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingIntegrityMonitor.kt#L40) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneHeadingSample` | [PhoneHeadingRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L22) |
| `class PhoneHeadingPresentationQuality` | [PhoneHeadingRepository.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L40) |
| `class DeclinationReferenceState` | [PhoneHeadingRepository.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L42) |
| `class PhoneHeadingRepository @Inject constructor` | [PhoneHeadingRepository.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L51) |
| `fun isAvailable` | [PhoneHeadingRepository.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L94) |
| `fun setPosition` | [PhoneHeadingRepository.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L97) |
| `fun start` | [PhoneHeadingRepository.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L107) |
| `fun stop` | [PhoneHeadingRepository.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L109) |
| `fun setDisplayDemand` | [PhoneHeadingRepository.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L111) |
| `fun setApproachDemand` | [PhoneHeadingRepository.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L113) |
| `fun onSensorChanged` | [PhoneHeadingRepository.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L150) |
| `fun onAccuracyChanged` | [PhoneHeadingRepository.kt:292](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L292) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneMotionState` | [PhoneMotionRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L17) |
| `class PhoneMotionRepository @Inject constructor` | [PhoneMotionRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L33) |
| `fun start` | [PhoneMotionRepository.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L45) |
| `fun stop` | [PhoneMotionRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L57) |
| `fun onSensorChanged` | [PhoneMotionRepository.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L65) |
| `fun onAccuracyChanged` | [PhoneMotionRepository.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L81) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionFaultEpisodeGate.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PositionFaultEpisodeGate` | [PositionFaultEpisodeGate.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionFaultEpisodeGate.kt#L8) |
| `fun shouldRecord` | [PositionFaultEpisodeGate.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionFaultEpisodeGate.kt#L11) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object PositionHealthPolicy` | [PositionHealthPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt#L9) |
| `fun evaluate` | [PositionHealthPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt#L10) |
| `object NmeaFixQualityPolicy` | [PositionHealthPolicy.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt#L21) |
| `fun allowsContinuation` | [PositionHealthPolicy.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt#L23) |
| `fun current` | [PositionHealthPolicy.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionHealthPolicy.kt#L25) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt

| 声明 | 实现位置 |
| --- | --- |
| `class IntegrityAcceptedFix` | [PositionIntegrityFilter.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L11) |
| `interface PositionIntegrityResult` | [PositionIntegrityFilter.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L18) |
| `class Accepted` | [PositionIntegrityFilter.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L19) |
| `class Quarantined` | [PositionIntegrityFilter.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L20) |
| `class Rejected` | [PositionIntegrityFilter.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L21) |
| `class PositionIntegrityFilter` | [PositionIntegrityFilter.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L32) |
| `fun reset` | [PositionIntegrityFilter.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L45) |
| `fun seed` | [PositionIntegrityFilter.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L52) |
| `fun evaluate` | [PositionIntegrityFilter.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L61) |
| `fun fresh` | [PositionIntegrityFilter.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PositionIntegrityFilter.kt#L205) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneLocationPhase` | [SystemLocationRepository.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L29) |
| `class PhoneLocationStatus` | [SystemLocationRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L30) |
| `class SystemLocationRepository @Inject constructor` | [SystemLocationRepository.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L37) |
| `fun onLocationChanged` | [SystemLocationRepository.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L55) |
| `fun onStatusChanged` | [SystemLocationRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L57) |
| `fun onProviderEnabled` | [SystemLocationRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L58) |
| `fun onProviderDisabled` | [SystemLocationRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L59) |
| `fun onReceive` | [SystemLocationRepository.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L63) |
| `fun publishProviderLocationForTest` | [SystemLocationRepository.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L114) |
| `fun hasPermission` | [SystemLocationRepository.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L119) |
| `fun setAppEnabled` | [SystemLocationRepository.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L120) |
| `fun setPreviewEnabled` | [SystemLocationRepository.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L121) |
| `fun setBackgroundEnabled` | [SystemLocationRepository.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L122) |
| `fun refreshPermission` | [SystemLocationRepository.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L123) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DeviceBowAxis` | [PhoneVesselSensors.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L22) |
| `class PhoneVesselMountState` | [PhoneVesselSensors.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L23) |
| `class SensorQuaternion` | [PhoneVesselSensors.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L24) |
| `fun inverse` | [PhoneVesselSensors.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L25) |
| `fun normalized` | [PhoneVesselSensors.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L27) |
| `class VesselMountCalibration` | [PhoneVesselSensors.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L29) |
| `class PhoneVesselOutputBlocker` | [PhoneVesselSensors.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L50) |
| `class PhoneVesselOutputReadiness` | [PhoneVesselSensors.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L51) |
| `object PhoneVesselOutputReadinessPolicy` | [PhoneVesselSensors.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L52) |
| `fun evaluate` | [PhoneVesselSensors.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L53) |
| `class PhoneHeadingAlignmentReference` | [PhoneVesselSensors.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L60) |
| `class PhoneHeadingAlignmentMatch` | [PhoneVesselSensors.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L61) |
| `object PhoneHeadingAlignmentPolicy` | [PhoneVesselSensors.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L66) |
| `fun matchLiveReference` | [PhoneVesselSensors.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L67) |
| `fun shortestOffset` | [PhoneVesselSensors.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L78) |
| `class PhoneSensorCapabilities` | [PhoneVesselSensors.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L85) |
| `class PhoneVesselAttitudeSample` | [PhoneVesselSensors.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L86) |
| `class PhonePressureSample` | [PhoneVesselSensors.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L87) |
| `object PhoneVesselAttitudeFrame` | [PhoneVesselSensors.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L92) |
| `fun resolve` | [PhoneVesselSensors.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L93) |
| `fun save` | [PhoneVesselSensors.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L119) |
| `fun setMountState` | [PhoneVesselSensors.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L120) |
| `fun setHeadingAlignment` | [PhoneVesselSensors.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L121) |
| `fun invalidateAttitudeSegment` | [PhoneVesselSensors.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L122) |
| `fun restore` | [PhoneVesselSensors.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L127) |
| `fun start` | [PhoneVesselSensors.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L151) |
| `fun stop` | [PhoneVesselSensors.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L152) |
| `fun calibrate` | [PhoneVesselSensors.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L153) |
| `fun setMounted` | [PhoneVesselSensors.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L158) |
| `fun alignHeading` | [PhoneVesselSensors.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L159) |
| `fun onSensorChanged` | [PhoneVesselSensors.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L160) |
| `fun onAccuracyChanged` | [PhoneVesselSensors.kt:176](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L176) |
| `class PhonePressureRepository @Inject constructor` | [PhoneVesselSensors.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L180) |
| `fun start` | [PhoneVesselSensors.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L182) |
| `fun stop` | [PhoneVesselSensors.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L183) |
| `fun onSensorChanged` | [PhoneVesselSensors.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L184) |
| `fun onAccuracyChanged` | [PhoneVesselSensors.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L185) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MonotonicClock` | [Clock.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L5) |
| `interface WallClock` | [Clock.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L6) |
| `object SystemMonotonicClock:MonotonicClock` | [Clock.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L17) |
| `object SystemWallClock:WallClock` | [Clock.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L18) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface RuntimeCommand` | [RuntimeCommand.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L17) |
| `class ArmWatch` | [RuntimeCommand.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L18) |
| `class ChangeSystemPosition` | [RuntimeCommand.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L33) |
| `class SelectNmeaPosition` | [RuntimeCommand.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L34) |
| `object NetworkChanged:RuntimeCommand` | [RuntimeCommand.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L35) |
| `object SnoozeAlarm:RuntimeCommand` | [RuntimeCommand.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L36) |
| `object PauseWatch:RuntimeCommand` | [RuntimeCommand.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L37) |
| `object ResumeWatch:RuntimeCommand` | [RuntimeCommand.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L38) |
| `class SwitchWatchGpsSource` | [RuntimeCommand.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L39) |
| `object LiftAnchor:RuntimeCommand` | [RuntimeCommand.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L40) |
| `class UpdateRadius` | [RuntimeCommand.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L41) |
| `object PauseWatchAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L42) |
| `object StopNmeaDependenciesAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L43) |
| `object ContinueTripWithPhoneAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L44) |
| `class Candidate` | [RuntimeCommand.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L45) |
| `class ResetCentreAnalysis` | [RuntimeCommand.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L46) |
| `class ApplyRecalculatedCentre` | [RuntimeCommand.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L47) |
| `class UpdateConditionGuards` | [RuntimeCommand.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L48) |
| `object ResetWindBaseline:RuntimeCommand` | [RuntimeCommand.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L49) |
| `object StartProxy:RuntimeCommand` | [RuntimeCommand.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L50) |
| `object StopProxy:RuntimeCommand` | [RuntimeCommand.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L51) |
| `object TestAlarm:RuntimeCommand` | [RuntimeCommand.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L52) |
| `object StopAlarmTest:RuntimeCommand` | [RuntimeCommand.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L53) |
| `class SetSharing` | [RuntimeCommand.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L54) |
| `object RefreshPhoneSensorOutput:RuntimeCommand` | [RuntimeCommand.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L55) |
| `object RefreshLocalNmeaServer:RuntimeCommand` | [RuntimeCommand.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L56) |
| `object StopAllNmeaSharing:RuntimeCommand` | [RuntimeCommand.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L57) |
| `class StartTrip` | [RuntimeCommand.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L58) |
| `object PauseTrip:RuntimeCommand` | [RuntimeCommand.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L59) |
| `object ResumeTrip:RuntimeCommand` | [RuntimeCommand.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L60) |
| `object ConfirmTripAttitudeFrame:RuntimeCommand` | [RuntimeCommand.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L61) |
| `object PauseTripAttitude:RuntimeCommand` | [RuntimeCommand.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L62) |
| `object EndTrip:RuntimeCommand` | [RuntimeCommand.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L63) |
| `class MarkTripWaypoint` | [RuntimeCommand.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L64) |
| `class StartSonar` | [RuntimeCommand.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L65) |
| `object StopSonar:RuntimeCommand` | [RuntimeCommand.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L66) |
| `object RestoreOnly:RuntimeCommand` | [RuntimeCommand.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L67) |
| `class Unknown` | [RuntimeCommand.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L68) |
| `class CandidateAction` | [RuntimeCommand.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L71) |
| `object RuntimeCommandParser` | [RuntimeCommand.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L73) |
| `fun parse` | [RuntimeCommand.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L74) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeUserFeedback` | [RuntimeDiagnostics.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L26) |
| `class RuntimeFeedbackContext` | [RuntimeDiagnostics.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L40) |
| `class ConditionFeedbackTransition` | [RuntimeDiagnostics.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L48) |
| `object ConditionFeedbackLifecycle` | [RuntimeDiagnostics.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L61) |
| `fun between` | [RuntimeDiagnostics.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L62) |
| `class ArmPositionDiagnostic` | [RuntimeDiagnostics.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L83) |
| `class RuntimeDiagnostics` | [RuntimeDiagnostics.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L105) |
| `class RuntimeDiagnosticsRepository @Inject constructor` | [RuntimeDiagnostics.kt:148](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L148) |
| `fun recordPositionDisposition` | [RuntimeDiagnostics.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L188) |
| `fun recordEstimatorRun` | [RuntimeDiagnostics.kt:194](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L194) |
| `fun recordArmPositionDiagnostic` | [RuntimeDiagnostics.kt:196](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L196) |
| `fun serviceStarting` | [RuntimeDiagnostics.kt:199](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L199) |
| `fun restoring` | [RuntimeDiagnostics.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L200) |
| `fun restoreFailed` | [RuntimeDiagnostics.kt:201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L201) |
| `fun serviceReady` | [RuntimeDiagnostics.kt:202](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L202) |
| `fun serviceStopped` | [RuntimeDiagnostics.kt:203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L203) |
| `fun recordUserFeedback` | [RuntimeDiagnostics.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L207) |
| `fun dismissUserFeedback` | [RuntimeDiagnostics.kt:222](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L222) |
| `fun clearUserFeedback` | [RuntimeDiagnostics.kt:229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L229) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeOwner` | [RuntimeResourceManager.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L17) |
| `class RuntimeRequirement` | [RuntimeResourceManager.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L18) |
| `class RuntimeResourceSnapshot` | [RuntimeResourceManager.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L27) |
| `class RuntimeOwnerRegistry` | [RuntimeResourceManager.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L49) |
| `fun set` | [RuntimeResourceManager.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L51) |
| `fun clear` | [RuntimeResourceManager.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L52) |
| `fun updateKeepWifiAwake` | [RuntimeResourceManager.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L56) |
| `fun snapshot` | [RuntimeResourceManager.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L67) |
| `class RuntimeResourceManager @Inject constructor` | [RuntimeResourceManager.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L85) |
| `fun set` | [RuntimeResourceManager.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L100) |
| `fun release` | [RuntimeResourceManager.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L101) |
| `fun releaseAll` | [RuntimeResourceManager.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L102) |
| `fun updateKeepWifiAwake` | [RuntimeResourceManager.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L103) |
| `fun snapshot` | [RuntimeResourceManager.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L104) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeServiceHost.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface RuntimeServiceHost` | [RuntimeServiceHost.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeServiceHost.kt#L6) |
| `fun notificationPermissionGranted` | [RuntimeServiceHost.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeServiceHost.kt#L7) |
| `fun startForeground` | [RuntimeServiceHost.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeServiceHost.kt#L8) |
| `fun stopForegroundAndSelf` | [RuntimeServiceHost.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeServiceHost.kt#L9) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SerialRuntimeActor` | [SerialRuntimeActor.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L9) |
| `fun submit` | [SerialRuntimeActor.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L32) |
| `fun execute` | [SerialRuntimeActor.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L34) |
| `fun shutdown` | [SerialRuntimeActor.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L40) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class YokuliRuntimeCoordinator @Inject constructor` | [YokuliRuntimeCoordinator.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L66) |
| `fun start` | [YokuliRuntimeCoordinator.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L111) |
| `fun notificationPermissionGranted` | [YokuliRuntimeCoordinator.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L127) |
| `fun enableSystemGps` | [YokuliRuntimeCoordinator.kt:128](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L128) |
| `fun notify` | [YokuliRuntimeCoordinator.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L129) |
| `fun notifyArmFailure` | [YokuliRuntimeCoordinator.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L130) |
| `fun refresh` | [YokuliRuntimeCoordinator.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L131) |
| `fun sound` | [YokuliRuntimeCoordinator.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L132) |
| `fun silence` | [YokuliRuntimeCoordinator.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L133) |
| `fun cancelUrgentNotification` | [YokuliRuntimeCoordinator.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L134) |
| `fun releaseIfIdle` | [YokuliRuntimeCoordinator.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L135) |
| `fun submit` | [YokuliRuntimeCoordinator.kt:272](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L272) |
| `fun ensureCommandForeground` | [YokuliRuntimeCoordinator.kt:636](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L636) |
| `fun shutdown` | [YokuliRuntimeCoordinator.kt:729](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L729) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorRuntimeActor` | [AnchorRuntimeActor.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L13) |
| `fun submit` | [AnchorRuntimeActor.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L41) |
| `fun execute` | [AnchorRuntimeActor.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L44) |
| `fun shutdown` | [AnchorRuntimeActor.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L50) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun configure` | [AnchorTelemetryRuntime.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L19) |
| `fun flush` | [AnchorTelemetryRuntime.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L27) |
| `fun shutdown` | [AnchorTelemetryRuntime.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L40) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ArmRequest` | [AnchorWatchRuntime.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L80) |
| `class AnchorRuntimeSnapshot` | [AnchorWatchRuntime.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L96) |
| `interface AnchorRuntimeHost` | [AnchorWatchRuntime.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L105) |
| `fun notificationPermissionGranted` | [AnchorWatchRuntime.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L106) |
| `fun enableSystemGps` | [AnchorWatchRuntime.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L107) |
| `fun notify` | [AnchorWatchRuntime.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L108) |
| `fun notifyArmFailure` | [AnchorWatchRuntime.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L109) |
| `fun refresh` | [AnchorWatchRuntime.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L110) |
| `fun sound` | [AnchorWatchRuntime.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L111) |
| `fun silence` | [AnchorWatchRuntime.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L112) |
| `fun cancelUrgentNotification` | [AnchorWatchRuntime.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L113) |
| `fun releaseIfIdle` | [AnchorWatchRuntime.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L114) |
| `class AnchorWatchRuntime` | [AnchorWatchRuntime.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L122) |
| `fun snapshot` | [AnchorWatchRuntime.kt:168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L168) |
| `fun activeSession` | [AnchorWatchRuntime.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L169) |
| `fun alarmSnapshot` | [AnchorWatchRuntime.kt:170](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L170) |
| `fun selectIdleSource` | [AnchorWatchRuntime.kt:171](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L171) |
| `fun refreshSessionFromDatabase` | [AnchorWatchRuntime.kt:178](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L178) |
| `fun restore` | [AnchorWatchRuntime.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L183) |
| `fun headingAllowedAt` | [AnchorWatchRuntime.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L213) |
| `fun arm` | [AnchorWatchRuntime.kt:251](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L251) |
| `fun recordArmPositionDecision` | [AnchorWatchRuntime.kt:315](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L315) |
| `fun onNmeaState` | [AnchorWatchRuntime.kt:414](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L414) |
| `fun onPositionHealth` | [AnchorWatchRuntime.kt:430](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L430) |
| `fun submitRawFix` | [AnchorWatchRuntime.kt:437](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L437) |
| `fun onAcceptedPosition` | [AnchorWatchRuntime.kt:482](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L482) |
| `fun acceptCandidate` | [AnchorWatchRuntime.kt:543](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L543) |
| `fun keepCurrentCenter` | [AnchorWatchRuntime.kt:557](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L557) |
| `fun applyRecalculatedCentre` | [AnchorWatchRuntime.kt:571](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L571) |
| `fun continueEstimating` | [AnchorWatchRuntime.kt:586](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L586) |
| `fun resetCentreAnalysis` | [AnchorWatchRuntime.kt:595](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L595) |
| `fun snooze` | [AnchorWatchRuntime.kt:614](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L614) |
| `fun pause` | [AnchorWatchRuntime.kt:620](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L620) |
| `fun switchPausedPositionSource` | [AnchorWatchRuntime.kt:631](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L631) |
| `fun bindSystemPositionSource` | [AnchorWatchRuntime.kt:722](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L722) |
| `fun resume` | [AnchorWatchRuntime.kt:734](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L734) |
| `fun lift` | [AnchorWatchRuntime.kt:762](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L762) |
| `fun updateRadius` | [AnchorWatchRuntime.kt:766](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L766) |
| `fun watchdog` | [AnchorWatchRuntime.kt:796](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L796) |
| `fun logEvent` | [AnchorWatchRuntime.kt:800](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L800) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ConditionRuntime @Inject constructor` | [ConditionRuntime.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L23) |
| `fun sync` | [ConditionRuntime.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L37) |
| `fun updateConfig` | [ConditionRuntime.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L62) |
| `fun resetWindBaseline` | [ConditionRuntime.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L109) |
| `fun snooze` | [ConditionRuntime.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L113) |
| `fun tick` | [ConditionRuntime.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L122) |
| `fun flush` | [ConditionRuntime.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L151) |
| `fun currentSession` | [ConditionRuntime.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L159) |
| `fun audibleSources` | [ConditionRuntime.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/condition/ConditionRuntime.kt#L160) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BatteryHealthState` | [BatteryHealthMonitor.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L9) |
| `fun update` | [BatteryHealthMonitor.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L13) |
| `class BatteryHealthMonitor @Inject constructor` | [BatteryHealthMonitor.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L22) |
| `fun sample` | [BatteryHealthMonitor.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L25) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaManualDisconnectState` | [NmeaManualDisconnectRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L17) |
| `class NmeaManualDisconnectRepository @Inject constructor` | [NmeaManualDisconnectRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L28) |
| `fun current` | [NmeaManualDisconnectRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L41) |
| `fun suppress` | [NmeaManualDisconnectRepository.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L42) |
| `fun clear` | [NmeaManualDisconnectRepository.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L46) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaRuntime @Inject constructor` | [NmeaRuntime.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L15) |
| `fun ensureConnected` | [NmeaRuntime.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L26) |
| `fun ensureSafetyConnected` | [NmeaRuntime.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L32) |
| `fun releaseSafetyRecovery` | [NmeaRuntime.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L36) |
| `fun releaseIfUnowned` | [NmeaRuntime.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L37) |
| `fun userDisconnected` | [NmeaRuntime.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L38) |
| `fun markUserDisconnected` | [NmeaRuntime.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L39) |
| `fun clearUserDisconnect` | [NmeaRuntime.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaRuntime.kt#L40) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AlarmSourceState` | [AlarmAudioArbiter.kt:5](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L5) |
| `class AlarmArbitration` | [AlarmAudioArbiter.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L6) |
| `class AlarmAudioArbiter` | [AlarmAudioArbiter.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L9) |
| `fun setActive` | [AlarmAudioArbiter.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L11) |
| `fun snoozeActive` | [AlarmAudioArbiter.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L12) |
| `fun snooze` | [AlarmAudioArbiter.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L13) |
| `fun clear` | [AlarmAudioArbiter.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L14) |
| `fun clearAll` | [AlarmAudioArbiter.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L15) |
| `fun snapshot` | [AlarmAudioArbiter.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioArbiter.kt#L16) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioController.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AlarmPlayback` | [AlarmAudioController.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioController.kt#L22) |
| `fun start` | [AlarmAudioController.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioController.kt#L33) |
| `fun stop` | [AlarmAudioController.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/AlarmAudioController.kt#L46) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun createChannels` | [NotificationCoordinator.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L20) |
| `fun foregroundNotification` | [NotificationCoordinator.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L33) |
| `fun publishForeground` | [NotificationCoordinator.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L67) |
| `fun publishEvent` | [NotificationCoordinator.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L69) |
| `fun cancelEvent` | [NotificationCoordinator.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L83) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorWatchNmeaStream` | [AnchorWatchNmeaFeedEncoder.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L23) |
| `class AnchorWatchNmeaFeedBatch` | [AnchorWatchNmeaFeedEncoder.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L32) |
| `class PublishedMetricLease<T>` | [AnchorWatchNmeaFeedEncoder.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L43) |
| `class PublishedMetricValue<T>` | [AnchorWatchNmeaFeedEncoder.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L51) |
| `class PhoneAppNmeaMetricLeaseBank @Inject constructor` | [AnchorWatchNmeaFeedEncoder.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L58) |
| `fun <T> resolve` | [AnchorWatchNmeaFeedEncoder.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L62) |
| `fun invalidateSource` | [AnchorWatchNmeaFeedEncoder.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L104) |
| `fun reset` | [AnchorWatchNmeaFeedEncoder.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L105) |
| `class AnchorWatchNmeaFeedEncoder @Inject constructor` | [AnchorWatchNmeaFeedEncoder.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L113) |
| `fun encode` | [AnchorWatchNmeaFeedEncoder.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L120) |
| `fun current` | [AnchorWatchNmeaFeedEncoder.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L159) |
| `fun reset` | [AnchorWatchNmeaFeedEncoder.kt:277](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L277) |
| `class AnchorWatchNmeaHeartbeat` | [AnchorWatchNmeaFeedEncoder.kt:307](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L307) |
| `fun due` | [AnchorWatchNmeaFeedEncoder.kt:309](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L309) |
| `fun reset` | [AnchorWatchNmeaFeedEncoder.kt:313](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L313) |
| `class NmeaPublicationSessionGate` | [AnchorWatchNmeaFeedEncoder.kt:318](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L318) |
| `fun start` | [AnchorWatchNmeaFeedEncoder.kt:321](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L321) |
| `fun stop` | [AnchorWatchNmeaFeedEncoder.kt:322](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L322) |
| `fun current` | [AnchorWatchNmeaFeedEncoder.kt:323](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L323) |
| `fun accepts` | [AnchorWatchNmeaFeedEncoder.kt:324](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/AnchorWatchNmeaFeedEncoder.kt#L324) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/MultiNmeaPublisher.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MultiNmeaPublisher @Inject constructor` | [MultiNmeaPublisher.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/MultiNmeaPublisher.kt#L13) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaPublicationBatch` | [NmeaPublicationEncoder.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L16) |
| `class NmeaPublicationEncoder @Inject constructor` | [NmeaPublicationEncoder.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L19) |
| `fun forward` | [NmeaPublicationEncoder.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L34) |
| `fun leaves` | [NmeaPublicationEncoder.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L46) |
| `fun encode` | [NmeaPublicationEncoder.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L77) |
| `fun encodeBatch` | [NmeaPublicationEncoder.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L78) |
| `fun allowedAndCapture` | [NmeaPublicationEncoder.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L89) |
| `fun number` | [NmeaPublicationEncoder.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaPublicationEncoder.kt#L94) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaStreamReadinessPolicy` | [NmeaStreamReadinessPolicy.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L7) |
| `fun position` | [NmeaStreamReadinessPolicy.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L8) |
| `fun heading` | [NmeaStreamReadinessPolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L10) |
| `fun motion` | [NmeaStreamReadinessPolicy.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L21) |
| `fun sensor` | [NmeaStreamReadinessPolicy.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L27) |
| `fun forSuppression` | [NmeaStreamReadinessPolicy.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/NmeaStreamReadinessPolicy.kt#L32) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneOwnedPublicationProvenancePolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PublicationProvenanceDecision` | [PhoneOwnedPublicationProvenancePolicy.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneOwnedPublicationProvenancePolicy.kt#L10) |
| `object PhoneOwnedPublicationProvenancePolicy` | [PhoneOwnedPublicationProvenancePolicy.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneOwnedPublicationProvenancePolicy.kt#L17) |
| `fun evaluate` | [PhoneOwnedPublicationProvenancePolicy.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneOwnedPublicationProvenancePolicy.kt#L18) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaPublisherConfig` | [PhonePositionNmeaOutputRuntime.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L25) |
| `fun asOutputSettings` | [PhonePositionNmeaOutputRuntime.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L40) |
| `fun from` | [PhonePositionNmeaOutputRuntime.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L48) |
| `fun offer` | [PhonePositionNmeaOutputRuntime.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L72) |
| `fun poll` | [PhonePositionNmeaOutputRuntime.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L73) |
| `fun clear` | [PhonePositionNmeaOutputRuntime.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L74) |
| `fun size` | [PhonePositionNmeaOutputRuntime.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L75) |
| `object NmeaWireAttemptCadence` | [PhonePositionNmeaOutputRuntime.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L83) |
| `fun nextAllowed` | [PhonePositionNmeaOutputRuntime.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L85) |
| `class AnchorWatchNmeaPublisher @Inject constructor` | [PhonePositionNmeaOutputRuntime.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L95) |
| `fun configure` | [PhonePositionNmeaOutputRuntime.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L159) |
| `fun configure` | [PhonePositionNmeaOutputRuntime.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L186) |
| `fun testOutput` | [PhonePositionNmeaOutputRuntime.kt:230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L230) |
| `fun testKnownGoodHdg` | [PhonePositionNmeaOutputRuntime.kt:234](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L234) |
| `fun shutdown` | [PhonePositionNmeaOutputRuntime.kt:238](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L238) |
| `object FormalOutputSessionReadinessPolicy` | [PhonePositionNmeaOutputRuntime.kt:244](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L244) |
| `fun blocksStart` | [PhonePositionNmeaOutputRuntime.kt:247](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L247) |
| `object PhoneOwnedRuntimeSafety` | [PhonePositionNmeaOutputRuntime.kt:255](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L255) |
| `fun suppression` | [PhonePositionNmeaOutputRuntime.kt:256](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L256) |
| `fun NmeaDeviceOutputSettings.publisherConfiguration` | [PhonePositionNmeaOutputRuntime.kt:264](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L264) |
| `fun NmeaDeviceOutputSettings.canonicalPublisherConfiguration` | [PhonePositionNmeaOutputRuntime.kt:295](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L295) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneVesselHeadingPublicationPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `class HeadingPublicationEligibility` | [PhoneVesselHeadingPublicationPolicy.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneVesselHeadingPublicationPolicy.kt#L12) |
| `object PhoneVesselHeadingPublicationPolicy` | [PhoneVesselHeadingPublicationPolicy.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneVesselHeadingPublicationPolicy.kt#L17) |
| `fun evaluate` | [PhoneVesselHeadingPublicationPolicy.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhoneVesselHeadingPublicationPolicy.kt#L20) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SameSocketProvenanceReason` | [SameSocketProvenanceFirewall.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt#L10) |
| `class SameSocketProvenanceDecision` | [SameSocketProvenanceFirewall.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt#L18) |
| `object SameSocketProvenanceFirewall` | [SameSocketProvenanceFirewall.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt#L33) |
| `fun evaluate` | [SameSocketProvenanceFirewall.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt#L42) |
| `fun evaluate` | [SameSocketProvenanceFirewall.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/SameSocketProvenanceFirewall.kt#L65) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ProxyRuntimeResult` | [GpsProxyRuntime.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L30) |
| `class GpsProxyRuntime @Inject constructor` | [GpsProxyRuntime.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L40) |
| `fun start` | [GpsProxyRuntime.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L53) |
| `fun stop` | [GpsProxyRuntime.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L88) |
| `fun restoreIfRequested` | [GpsProxyRuntime.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L93) |
| `fun onAcceptedNmeaFix` | [GpsProxyRuntime.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L124) |
| `fun watchdog` | [GpsProxyRuntime.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L135) |
| `fun shutdown` | [GpsProxyRuntime.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/proxy/GpsProxyRuntime.kt#L141) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SensorRuntimeState` | [SensorRuntime.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L10) |
| `class SensorRuntime @Inject constructor` | [SensorRuntime.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L25) |
| `fun reconcile` | [SensorRuntime.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L32) |
| `fun stop` | [SensorRuntime.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L53) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalNmeaServerRuntimeStatus` | [LocalNmeaServerRuntime.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L21) |
| `class LocalNmeaServerRuntime @Inject constructor` | [LocalNmeaServerRuntime.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L32) |
| `fun configure` | [LocalNmeaServerRuntime.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L51) |
| `fun shutdown` | [LocalNmeaServerRuntime.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L94) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaSharingPositionPublisher @Inject constructor` | [NmeaSharingPositionPublisher.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt#L16) |
| `fun reset` | [NmeaSharingPositionPublisher.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt#L22) |
| `fun accept` | [NmeaSharingPositionPublisher.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt#L30) |
| `fun seed` | [NmeaSharingPositionPublisher.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt#L45) |
| `fun tick` | [NmeaSharingPositionPublisher.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/NmeaSharingPositionPublisher.kt#L62) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SonarRuntimeResult` | [SonarRuntime.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L10) |
| `class SonarRuntime @Inject constructor` | [SonarRuntime.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L13) |
| `fun restore` | [SonarRuntime.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L18) |
| `fun start` | [SonarRuntime.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L19) |
| `fun stop` | [SonarRuntime.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L20) |
| `fun watchdog` | [SonarRuntime.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L21) |
| `fun shutdown` | [SonarRuntime.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sonar/SonarRuntime.kt#L22) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripRuntimeResult` | [TripRuntime.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L33) |
| `class TripRuntime @Inject constructor` | [TripRuntime.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L36) |
| `fun recordAnchorEvent` | [TripRuntime.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L82) |
| `fun recordSystemSourceChange` | [TripRuntime.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L88) |
| `fun activeSession` | [TripRuntime.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L95) |
| `fun restore` | [TripRuntime.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L97) |
| `fun start` | [TripRuntime.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L137) |
| `fun pause` | [TripRuntime.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L188) |
| `fun resume` | [TripRuntime.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L207) |
| `fun confirmAttitudeFrame` | [TripRuntime.kt:230](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L230) |
| `fun pauseAttitude` | [TripRuntime.kt:244](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L244) |
| `fun end` | [TripRuntime.kt:256](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L256) |
| `fun waypoint` | [TripRuntime.kt:277](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L277) |
| `fun shutdown` | [TripRuntime.kt:290](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L290) |
| `fun continueWithPhoneAfterNmeaDisconnect` | [TripRuntime.kt:297](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L297) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorForegroundService : Service` | [AnchorForegroundService.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L24) |
| `fun notificationPermissionGranted` | [AnchorForegroundService.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L28) |
| `fun startForeground` | [AnchorForegroundService.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L35) |
| `fun stopForegroundAndSelf` | [AnchorForegroundService.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L51) |
| `fun onCreate` | [AnchorForegroundService.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L57) |
| `fun onStartCommand` | [AnchorForegroundService.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L62) |
| `fun onDestroy` | [AnchorForegroundService.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L71) |
| `fun onBind` | [AnchorForegroundService.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L76) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BootRestoreReceiver:BroadcastReceiver` | [BootRestoreReceiver.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt#L29) |
| `fun onReceive` | [BootRestoreReceiver.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt#L34) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MarineSystem : RuntimeEndpoint` | [MarineSystem.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L21) |
| `class InProcessMarineSystem @Inject constructor` | [MarineSystem.kt:28](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L28) |
| `object MarineSystemBindings` | [MarineSystem.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L39) |
| `fun system` | [MarineSystem.kt:41](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L41) |
| `fun content` | [MarineSystem.kt:43](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L43) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MarineSystemBootstrap` | [MarineSystemBootstrap.kt:11](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt#L11) |
| `fun initialize` | [MarineSystemBootstrap.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt#L15) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun start` | [VoyageSessionCoordinator.kt:41](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L41) |
| `fun pause` | [VoyageSessionCoordinator.kt:49](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L49) |
| `fun resume` | [VoyageSessionCoordinator.kt:53](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L53) |
| `fun finish` | [VoyageSessionCoordinator.kt:57](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L57) |

## ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InstalledAppBinding<VisualEnvironment>` | [InstalledAppBinding.kt:13](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt#L13) |
| `class InstalledAppRegistry<VisualEnvironment>` | [InstalledAppBinding.kt:36](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt#L36) |
| `fun visualContributions` | [InstalledAppBinding.kt:62](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt#L62) |
| `fun searchContributions` | [InstalledAppBinding.kt:67](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InstalledAppBinding.kt#L67) |

## ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppHost.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InternalAppHost` | [InternalAppHost.kt:11](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppHost.kt#L11) |
| `fun Render` | [InternalAppHost.kt:16](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppHost.kt#L16) |
| `interface InternalAppHostResolver` | [InternalAppHost.kt:19](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppHost.kt#L19) |
| `fun hostFor` | [InternalAppHost.kt:20](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppHost.kt#L20) |

## ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun interface InternalAppInputRegistration : AutoCloseable` | [InternalAppInputRouter.kt:9](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt#L9) |
| `class InternalAppInputRouter` | [InternalAppInputRouter.kt:16](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt#L16) |
| `fun register` | [InternalAppInputRouter.kt:24](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt#L24) |
| `fun dispatch` | [InternalAppInputRouter.kt:33](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt#L33) |
| `fun BindInternalAppInputHandler` | [InternalAppInputRouter.kt:47](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/InternalAppInputRouter.kt#L47) |

## ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun interface LauncherIconRenderer` | [LauncherPresentation.kt:14](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L14) |
| `fun Render` | [LauncherPresentation.kt:16](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L16) |
| `class LauncherTileRenderContext` | [LauncherPresentation.kt:19](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L19) |
| `fun interface LauncherTileRenderer` | [LauncherPresentation.kt:30](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L30) |
| `fun Render` | [LauncherPresentation.kt:32](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L32) |
| `class LauncherEntryVisualContribution` | [LauncherPresentation.kt:38](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L38) |
| `class LauncherSearchResultContribution` | [LauncherPresentation.kt:54](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L54) |
| `class LauncherEntryUiState` | [LauncherPresentation.kt:66](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L66) |
| `fun tileRenderer` | [LauncherPresentation.kt:76](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L76) |
| `object LauncherPresentationValidator` | [LauncherPresentation.kt:83](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L83) |
| `fun validate` | [LauncherPresentation.kt:84](../../ui/shell-compose/src/main/java/com/yokuli/shell/compose/LauncherPresentation.kt#L84) |
