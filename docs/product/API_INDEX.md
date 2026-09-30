# 生产接口与结构声明索引

共 5181 项类型与方法声明；按源文件排序。

由 `python3 scripts/export_api_index.py` 从当前源码生成。包含活动重制应用、共享设计/桌面、Shell 合同和所复用的业务领域/存储/运行时。遗留类中的保留 API 不代表其 UI 或功能仍启用；例如声纳历史类型仅为读取已有数据库而保留。

本索引是源码定位工具，包含类型与方法声明，并非所有声明都是跨应用公共 API。完整参数、中文业务语义、公开边界和生命周期见 [总拓扑](OS_INTERFACE_TOPOLOGY.md) 及链接源码。显式 private 声明、旧 UI 和 Gradle 依赖 API 不在本索引范围。

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LauncherProtoMapper` | [LauncherProtoMapper.kt:31](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L31) |
| `fun encode` | [LauncherProtoMapper.kt:32](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L32) |
| `fun decode` | [LauncherProtoMapper.kt:63](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L63) |
| `fun emptyDefaults` | [LauncherProtoMapper.kt:191](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt#L191) |

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherStateSerializer` | [LauncherStateSerializer.kt:10](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L10) |
| `fun readFrom` | [LauncherStateSerializer.kt:13](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L13) |
| `fun writeTo` | [LauncherStateSerializer.kt:19](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherStateSerializer.kt#L19) |

## adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun load` | [ProtoDataStoreLauncherPersistence.kt:81](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L81) |
| `fun save` | [ProtoDataStoreLauncherPersistence.kt:86](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L86) |
| `fun updateDocument` | [ProtoDataStoreLauncherPersistence.kt:94](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L94) |
| `fun saveTileDraft` | [ProtoDataStoreLauncherPersistence.kt:99](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L99) |
| `fun saveDocument` | [ProtoDataStoreLauncherPersistence.kt:104](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L104) |
| `fun savePreferences` | [ProtoDataStoreLauncherPersistence.kt:108](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L108) |
| `fun updatePreferences` | [ProtoDataStoreLauncherPersistence.kt:119](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L119) |
| `fun beginLaunch` | [ProtoDataStoreLauncherPersistence.kt:136](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L136) |
| `fun markLaunchHealthy` | [ProtoDataStoreLauncherPersistence.kt:147](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L147) |
| `fun reset` | [ProtoDataStoreLauncherPersistence.kt:151](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L151) |
| `fun create` | [ProtoDataStoreLauncherPersistence.kt:184](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L184) |
| `fun create` | [ProtoDataStoreLauncherPersistence.kt:194](../../adapter/shell-storage/src/main/java/com/yokuli/shell/storage/ProtoDataStoreLauncherPersistence.kt#L194) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/BrandArrivalHost.kt

| 声明 | 实现位置 |
| --- | --- |
| `object BrandArrivalSession` | [BrandArrivalHost.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/BrandArrivalHost.kt#L32) |
| `fun claim` | [BrandArrivalHost.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/BrandArrivalHost.kt#L33) |
| `fun BrandArrivalHost` | [BrandArrivalHost.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/BrandArrivalHost.kt#L43) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DurableCommitResult` | [DurableSnapshotStore.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt#L17) |
| `class DurableCommit` | [DurableSnapshotStore.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt#L18) |
| `class PersistenceState` | [DurableSnapshotStore.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt#L21) |
| `fun contentReadRestored` | [DurableSnapshotStore.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt#L74) |
| `fun submit` | [DurableSnapshotStore.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/DurableSnapshotStore.kt#L79) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt

| 声明 | 实现位置 |
| --- | --- |
| `object Gpx` | [Gpx.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L9) |
| `class Contents` | [Gpx.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L18) |
| `class UnsupportedTracks : IllegalArgumentException` | [Gpx.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L21) |
| `fun read` | [Gpx.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L23) |
| `fun readPoint` | [Gpx.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L34) |
| `fun write` | [Gpx.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L92) |
| `fun element` | [Gpx.kt:95](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L95) |
| `fun point` | [Gpx.kt:96](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Gpx.kt#L96) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MainActivity : ComponentActivity` | [MainActivity.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L46) |
| `fun holdAutomaticResidencyForExit` | [MainActivity.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L51) |
| `fun onDisplayAdded` | [MainActivity.kt:56](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L56) |
| `fun onDisplayRemoved` | [MainActivity.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L57) |
| `fun onDisplayChanged` | [MainActivity.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L58) |
| `fun onCreate` | [MainActivity.kt:96](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L96) |
| `fun onNewIntent` | [MainActivity.kt:127](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L127) |
| `fun onWindowFocusChanged` | [MainActivity.kt:165](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L165) |
| `fun onStart` | [MainActivity.kt:169](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L169) |
| `fun onStop` | [MainActivity.kt:173](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L173) |
| `fun onResume` | [MainActivity.kt:177](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L177) |
| `fun dispatchKeyEvent` | [MainActivity.kt:244](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L244) |
| `fun onPause` | [MainActivity.kt:259](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L259) |
| `fun onSaveInstanceState` | [MainActivity.kt:271](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L271) |
| `fun onDestroy` | [MainActivity.kt:275](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MainActivity.kt#L275) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarineNoticeBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AlarmEventEntity.asNotice` | [MarineNoticeBridge.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarineNoticeBridge.kt#L10) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarinePresentationHost.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun Activity.presentMarineRequest` | [MarinePresentationHost.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MarinePresentationHost.kt#L12) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt

| 声明 | 实现位置 |
| --- | --- |
| `class GeoPoint` | [Model.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L24) |
| `fun json` | [Model.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L25) |
| `fun valid` | [Model.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L26) |
| `fun distance` | [Model.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L29) |
| `fun bearing` | [Model.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L34) |
| `fun coordinates` | [Model.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L38) |
| `fun value` | [Model.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L39) |
| `fun decimal` | [Model.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L42) |
| `fun uid` | [Model.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L43) |
| `class PlaceKind` | [Model.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L45) |
| `class PlaceCapture` | [Model.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L47) |
| `fun json` | [Model.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L48) |
| `class Place` | [Model.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L52) |
| `fun json` | [Model.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L54) |
| `class Route` | [Model.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L60) |
| `fun json` | [Model.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L68) |
| `class PlanningDraftUndo` | [Model.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L72) |
| `fun json` | [Model.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L73) |
| `fun JSONArray.ints` | [Model.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L76) |
| `fun JSONArray.objects` | [Model.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L77) |
| `class TileSpec` | [Model.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L79) |
| `class AnchorDraft` | [Model.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L81) |
| `class ChartInteractionSnapshot` | [Model.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L83) |
| `class AppId` | [Model.kt:96](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L96) |
| `class YokuliApplication : com.yokuli.runtime.marine.hardware.MarineHostApplication` | [Model.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L113) |
| `fun onCreate` | [Model.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L122) |
| `class OsStore` | [Model.kt:159](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L159) |
| `fun requestPosition` | [Model.kt:264](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L264) |
| `fun connectSystem` | [Model.kt:265](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L265) |
| `fun commitNavigation` | [Model.kt:317](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L317) |
| `fun navigationCommand` | [Model.kt:323](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L323) |
| `fun t` | [Model.kt:363](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L363) |
| `fun title` | [Model.kt:364](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L364) |
| `fun title` | [Model.kt:365](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L365) |
| `fun notify` | [Model.kt:366](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L366) |
| `fun open` | [Model.kt:371](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L371) |
| `fun openLinked` | [Model.kt:373](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L373) |
| `fun openNotification` | [Model.kt:375](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L375) |
| `fun openSystemDestination` | [Model.kt:391](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L391) |
| `fun home` | [Model.kt:395](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L395) |
| `fun back` | [Model.kt:396](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L396) |
| `fun fly` | [Model.kt:397](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L397) |
| `fun captureChartInteraction` | [Model.kt:398](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L398) |
| `fun restoreChartInteraction` | [Model.kt:405](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L405) |
| `fun mark` | [Model.kt:421](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L421) |
| `fun startRoute` | [Model.kt:426](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L426) |
| `fun advanceRoute` | [Model.kt:427](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L427) |
| `fun save` | [Model.kt:436](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L436) |
| `fun retryContentRead` | [Model.kt:448](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L448) |
| `fun exportRecoveredContent` | [Model.kt:486](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L486) |
| `fun saveWithFeedback` | [Model.kt:510](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/Model.kt#L510) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun placeCommit` | [MySailingRepository.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L18) |
| `fun retryLoading` | [MySailingRepository.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L41) |
| `fun bundle` | [MySailingRepository.kt:65](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L65) |
| `fun updatePlace` | [MySailingRepository.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L66) |
| `fun updateSpot` | [MySailingRepository.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L67) |
| `fun createSpot` | [MySailingRepository.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L68) |
| `fun archivePlace` | [MySailingRepository.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L69) |
| `fun restorePlace` | [MySailingRepository.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L70) |
| `fun createCollection` | [MySailingRepository.kt:71](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L71) |
| `fun toggleCollection` | [MySailingRepository.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L72) |
| `fun observeCollectionMembers` | [MySailingRepository.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L73) |
| `fun anchorTrackPage` | [MySailingRepository.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L74) |
| `fun saveAnchorage` | [MySailingRepository.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L76) |
| `fun put` | [MySailingRepository.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L79) |
| `fun remove` | [MySailingRepository.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L84) |
| `fun putRoute` | [MySailingRepository.kt:89](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L89) |
| `fun removeRoute` | [MySailingRepository.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L94) |
| `fun import` | [MySailingRepository.kt:98](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/MySailingRepository.kt#L98) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NavigationProjection.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun Route.navigationSnapshot` | [NavigationProjection.kt:7](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NavigationProjection.kt#L7) |
| `fun NavigationRouteSnapshot.asRoute` | [NavigationProjection.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NavigationProjection.kt#L14) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NotificationShadePhase` | [NotificationShadeState.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L12) |
| `class NotificationShadePresentation` | [NotificationShadeState.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L14) |
| `class NotificationShadeState` | [NotificationShadeState.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L20) |
| `fun rememberListPosition` | [NotificationShadeState.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L48) |
| `fun open` | [NotificationShadeState.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L53) |
| `fun close` | [NotificationShadeState.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L62) |
| `fun toggle` | [NotificationShadeState.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L68) |
| `fun back` | [NotificationShadeState.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L69) |
| `fun showDetail` | [NotificationShadeState.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L72) |
| `fun updateHeight` | [NotificationShadeState.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L73) |
| `fun touchStarted` | [NotificationShadeState.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L84) |
| `fun touchFinished` | [NotificationShadeState.kt:85](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L85) |
| `fun beginDrag` | [NotificationShadeState.kt:86](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L86) |
| `fun dragBy` | [NotificationShadeState.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L91) |
| `fun release` | [NotificationShadeState.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L97) |
| `fun cancelDrag` | [NotificationShadeState.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L105) |
| `fun attachHost` | [NotificationShadeState.kt:107](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L107) |
| `fun hostDetached` | [NotificationShadeState.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationShadeState.kt#L111) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationUnitBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun OsStore.observeNotificationUnits` | [NotificationUnitBridge.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/NotificationUnitBridge.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ShellContentSnapshot.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ShellContentRead` | [ShellContentSnapshot.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ShellContentSnapshot.kt#L9) |
| `fun readShellContent` | [ShellContentSnapshot.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ShellContentSnapshot.kt#L11) |
| `fun preserveRecoveredShellContent` | [ShellContentSnapshot.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ShellContentSnapshot.kt#L59) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt

| 声明 | 实现位置 |
| --- | --- |
| `class StartBackgroundFailure` | [StartBackgroundStore.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L24) |
| `class StartBackgroundWrite` | [StartBackgroundStore.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L25) |
| `fun startBackgroundFailure` | [StartBackgroundStore.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L27) |
| `fun imageFile` | [StartBackgroundStore.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L46) |
| `fun choose` | [StartBackgroundStore.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L48) |
| `fun mode` | [StartBackgroundStore.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L83) |
| `fun opacity` | [StartBackgroundStore.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L84) |
| `fun crop` | [StartBackgroundStore.kt:86](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L86) |
| `fun remove` | [StartBackgroundStore.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L94) |
| `fun pickerFailed` | [StartBackgroundStore.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L101) |
| `fun clearFailure` | [StartBackgroundStore.kt:102](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L102) |
| `fun load` | [StartBackgroundStore.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/StartBackgroundStore.kt#L111) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NoticeSeverity` | [SystemNotifications.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L17) |
| `class SystemNotice` | [SystemNotifications.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L19) |
| `fun text` | [SystemNotifications.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L26) |
| `fun title` | [SystemNotifications.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L27) |
| `fun body` | [SystemNotifications.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L28) |
| `fun record` | [SystemNotifications.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L29) |
| `fun NoticeRecord.asNotice` | [SystemNotifications.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L33) |
| `fun setAppForeground` | [SystemNotifications.kt:120](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L120) |
| `fun setPresentationVisible` | [SystemNotifications.kt:125](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L125) |
| `fun onAppForeground` | [SystemNotifications.kt:127](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L127) |
| `fun awaitLoaded` | [SystemNotifications.kt:136](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L136) |
| `fun resolvePositionAfterExit` | [SystemNotifications.kt:138](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L138) |
| `fun dismissBanner` | [SystemNotifications.kt:139](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L139) |
| `fun post` | [SystemNotifications.kt:143](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L143) |
| `fun markRead` | [SystemNotifications.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L163) |
| `fun remove` | [SystemNotifications.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L167) |
| `fun clearAll` | [SystemNotifications.kt:170](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L170) |
| `fun clearRead` | [SystemNotifications.kt:171](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L171) |
| `fun restore` | [SystemNotifications.kt:172](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L172) |
| `fun retryPersistence` | [SystemNotifications.kt:173](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L173) |
| `fun recheckPending` | [SystemNotifications.kt:184](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemNotifications.kt#L184) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemPreferenceCommands.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SystemPreferenceStatus` | [SystemPreferenceCommands.kt:4](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemPreferenceCommands.kt#L4) |
| `class SystemPreferenceCommand` | [SystemPreferenceCommands.kt:5](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/SystemPreferenceCommands.kt#L5) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskDismissal.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun shouldDismissTask` | [TaskDismissal.kt:4](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskDismissal.kt#L4) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TaskSnapshot` | [TaskSnapshots.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L24) |
| `class TaskSnapshotStore` | [TaskSnapshots.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L27) |
| `fun bind` | [TaskSnapshots.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L34) |
| `fun unbind` | [TaskSnapshots.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L39) |
| `fun retain` | [TaskSnapshots.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L40) |
| `fun captureCurrent` | [TaskSnapshots.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L42) |
| `fun Context.activity` | [TaskSnapshots.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TaskSnapshots.kt#L83) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileEditorPhase` | [TileWorkshopController.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L23) |
| `class TileEditorPage` | [TileWorkshopController.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L25) |
| `class TileEditorOrigin` | [TileWorkshopController.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L28) |
| `class TileEditorSession` | [TileWorkshopController.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L29) |
| `class TileWorkshopFeedback` | [TileWorkshopController.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L51) |
| `fun beginAdd` | [TileWorkshopController.kt:121](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L121) |
| `fun beginEdit` | [TileWorkshopController.kt:141](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L141) |
| `fun chooseContent` | [TileWorkshopController.kt:158](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L158) |
| `fun setBinding` | [TileWorkshopController.kt:162](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L162) |
| `fun setSize` | [TileWorkshopController.kt:175](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L175) |
| `fun setPresentation` | [TileWorkshopController.kt:181](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L181) |
| `fun chooseStyle` | [TileWorkshopController.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L189) |
| `fun requestClose` | [TileWorkshopController.kt:200](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L200) |
| `fun keepEditing` | [TileWorkshopController.kt:209](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L209) |
| `fun discard` | [TileWorkshopController.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L210) |
| `fun editExisting` | [TileWorkshopController.kt:215](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L215) |
| `fun reloadConflict` | [TileWorkshopController.kt:221](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L221) |
| `fun resume` | [TileWorkshopController.kt:229](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L229) |
| `fun suspendEditor` | [TileWorkshopController.kt:230](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L230) |
| `fun onShellState` | [TileWorkshopController.kt:231](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L231) |
| `fun contentIssue` | [TileWorkshopController.kt:245](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L245) |
| `fun save` | [TileWorkshopController.kt:254](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L254) |
| `fun reveal` | [TileWorkshopController.kt:283](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L283) |
| `fun unpin` | [TileWorkshopController.kt:288](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L288) |
| `fun undo` | [TileWorkshopController.kt:305](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L305) |
| `fun dismissFeedback` | [TileWorkshopController.kt:324](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L324) |
| `fun binding` | [TileWorkshopController.kt:339](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L339) |
| `fun presentation` | [TileWorkshopController.kt:341](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/TileWorkshopController.kt#L341) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun OsExperience` | [WpShellExperience.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L62) |
| `fun ShellAppIcon` | [WpShellExperience.kt:356](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L356) |
| `fun getResources` | [WpShellExperience.kt:432](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L432) |
| `fun onChange` | [WpShellExperience.kt:463](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L463) |
| `object Launcher : ShellMotionTarget` | [WpShellExperience.kt:474](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L474) |
| `object Search : ShellMotionTarget` | [WpShellExperience.kt:475](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L475) |
| `object Recents : ShellMotionTarget` | [WpShellExperience.kt:476](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L476) |
| `class App` | [WpShellExperience.kt:477](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellExperience.kt#L477) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ensureTileContent` | [WpShellRuntime.kt:220](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L220) |
| `fun placementForEntry` | [WpShellRuntime.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L224) |
| `fun pageForToken` | [WpShellRuntime.kt:249](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L249) |
| `fun finishChartRouteEditing` | [WpShellRuntime.kt:264](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L264) |
| `fun showChartRoutePreview` | [WpShellRuntime.kt:277](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L277) |
| `fun hideChartRoutePreview` | [WpShellRuntime.kt:288](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L288) |
| `fun requestSystemPreferences` | [WpShellRuntime.kt:334](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L334) |
| `fun updateSystemPreferences` | [WpShellRuntime.kt:350](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L350) |
| `fun canonicalPage` | [WpShellRuntime.kt:354](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L354) |
| `fun appForPage` | [WpShellRuntime.kt:368](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L368) |
| `fun openLinked` | [WpShellRuntime.kt:374](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L374) |
| `fun reportVisibleRoute` | [WpShellRuntime.kt:408](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L408) |
| `fun visibleRouteForTask` | [WpShellRuntime.kt:413](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L413) |
| `fun openFromNotification` | [WpShellRuntime.kt:417](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L417) |
| `fun openSystemDestination` | [WpShellRuntime.kt:434](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L434) |
| `fun open` | [WpShellRuntime.kt:460](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L460) |
| `fun dispatch` | [WpShellRuntime.kt:478](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L478) |
| `fun backDestination` | [WpShellRuntime.kt:533](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L533) |
| `fun back` | [WpShellRuntime.kt:549](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L549) |
| `fun popRoute` | [WpShellRuntime.kt:557](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L557) |
| `fun home` | [WpShellRuntime.kt:558](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L558) |
| `fun input` | [WpShellRuntime.kt:559](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L559) |
| `fun resetStart` | [WpShellRuntime.kt:594](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L594) |
| `class ShellApp` | [WpShellRuntime.kt:619](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/WpShellRuntime.kt#L619) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun add` | [AnchorSwingCoverage.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt#L25) |
| `fun areas` | [AnchorSwingCoverage.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/AnchorSwingCoverage.kt#L50) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun chartDisplayText` | [ChartLibrary.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L35) |
| `class ChartFile` | [ChartLibrary.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L40) |
| `fun json` | [ChartLibrary.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L48) |
| `fun from` | [ChartLibrary.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L54) |
| `class ChartReader` | [ChartLibrary.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L68) |
| `fun inspect` | [ChartLibrary.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L87) |
| `fun tile` | [ChartLibrary.kt:119](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L119) |
| `fun coverageZoom` | [ChartLibrary.kt:126](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L126) |
| `fun raster` | [ChartLibrary.kt:136](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L136) |
| `fun close` | [ChartLibrary.kt:154](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L154) |
| `fun target` | [ChartLibrary.kt:166](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L166) |
| `fun release` | [ChartLibrary.kt:173](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L173) |
| `fun prune` | [ChartLibrary.kt:178](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L178) |
| `fun copy` | [ChartLibrary.kt:187](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L187) |
| `class ChartFolder` | [ChartLibrary.kt:209](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L209) |
| `fun json` | [ChartLibrary.kt:215](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L215) |
| `fun from` | [ChartLibrary.kt:219](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L219) |
| `fun linked` | [ChartLibrary.kt:222](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L222) |
| `class ChartLayer` | [ChartLibrary.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L228) |
| `fun folderFiles` | [ChartLibrary.kt:270](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L270) |
| `fun errorText` | [ChartLibrary.kt:282](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L282) |
| `fun toggle` | [ChartLibrary.kt:333](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L333) |
| `fun setLayer` | [ChartLibrary.kt:335](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L335) |
| `fun moveFile` | [ChartLibrary.kt:336](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L336) |
| `fun showOnly` | [ChartLibrary.kt:345](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L345) |
| `fun renameFile` | [ChartLibrary.kt:350](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L350) |
| `fun renameFolder` | [ChartLibrary.kt:355](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L355) |
| `fun includeAll` | [ChartLibrary.kt:360](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L360) |
| `fun forget` | [ChartLibrary.kt:364](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L364) |
| `fun restore` | [ChartLibrary.kt:369](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L369) |
| `fun forgetFolder` | [ChartLibrary.kt:375](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L375) |
| `fun rescan` | [ChartLibrary.kt:391](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L391) |
| `fun linkFolder` | [ChartLibrary.kt:392](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L392) |
| `fun importCopy` | [ChartLibrary.kt:542](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L542) |
| `fun register` | [ChartLibrary.kt:603](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L603) |
| `fun raster` | [ChartLibrary.kt:615](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L615) |
| `fun close` | [ChartLibrary.kt:666](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L666) |
| `class FolderTileProvider` | [ChartLibrary.kt:674](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L674) |
| `fun getTile` | [ChartLibrary.kt:677](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L677) |
| `fun close` | [ChartLibrary.kt:681](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartLibrary.kt#L681) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartPortrayalInfo` | [ChartPortrayal.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L13) |
| `class StructuredSceneDrawing` | [ChartPortrayal.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L22) |
| `class ChartPalette` | [ChartPortrayal.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L25) |
| `fun of` | [ChartPortrayal.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L27) |
| `fun structuredScene` | [ChartPortrayal.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L50) |
| `fun tint` | [ChartPortrayal.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L66) |
| `fun geometryLines` | [ChartPortrayal.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L67) |
| `fun geometryAreas` | [ChartPortrayal.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L68) |
| `fun label` | [ChartPortrayal.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L72) |
| `fun name` | [ChartPortrayal.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L77) |
| `fun colors` | [ChartPortrayal.kt:78](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L78) |
| `fun pointSymbol` | [ChartPortrayal.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L79) |
| `fun order` | [ChartPortrayal.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L88) |
| `class Candidate` | [ChartPortrayal.kt:186](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L186) |
| `fun x` | [ChartPortrayal.kt:188](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L188) |
| `fun y` | [ChartPortrayal.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L189) |
| `fun distance` | [ChartPortrayal.kt:201](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L201) |
| `fun xy` | [ChartPortrayal.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L210) |
| `fun slots` | [ChartPortrayal.kt:212](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartPortrayal.kt#L212) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartRasterProbe` | [ChartRasterProbe.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L12) |
| `fun hasRasterAt` | [ChartRasterProbe.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L14) |
| `fun probeChartRaster` | [ChartRasterProbe.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L21) |
| `class Candidate` | [ChartRasterProbe.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L27) |
| `fun includes` | [ChartRasterProbe.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L35) |
| `fun longitude` | [ChartRasterProbe.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartRasterProbe.kt#L42) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface ChartCamera` | [ChartSurface.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L55) |
| `fun project` | [ChartSurface.kt:56](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L56) |
| `fun unproject` | [ChartSurface.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L57) |
| `fun move` | [ChartSurface.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L58) |
| `fun zoom` | [ChartSurface.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L59) |
| `fun orientation` | [ChartSurface.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L60) |
| `fun orient` | [ChartSurface.kt:61](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L61) |
| `fun fit` | [ChartSurface.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L62) |
| `fun onDraw` | [ChartSurface.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L97) |
| `fun onTouchEvent` | [ChartSurface.kt:126](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L126) |
| `fun destination` | [ChartSurface.kt:162](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L162) |
| `fun dispatchTouchEvent` | [ChartSurface.kt:215](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L215) |
| `fun onSizeChanged` | [ChartSurface.kt:232](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L232) |
| `fun screenDistance` | [ChartSurface.kt:262](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L262) |
| `fun project` | [ChartSurface.kt:287](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L287) |
| `fun unproject` | [ChartSurface.kt:288](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L288) |
| `fun move` | [ChartSurface.kt:289](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L289) |
| `fun zoom` | [ChartSurface.kt:290](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L290) |
| `fun orientation` | [ChartSurface.kt:291](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L291) |
| `fun orient` | [ChartSurface.kt:292](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L292) |
| `fun fit` | [ChartSurface.kt:296](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L296) |
| `fun project` | [ChartSurface.kt:326](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L326) |
| `fun unproject` | [ChartSurface.kt:327](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L327) |
| `fun move` | [ChartSurface.kt:328](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L328) |
| `fun zoom` | [ChartSurface.kt:329](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L329) |
| `fun orientation` | [ChartSurface.kt:330](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L330) |
| `fun orient` | [ChartSurface.kt:331](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L331) |
| `fun fit` | [ChartSurface.kt:335](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L335) |
| `fun updateStyle` | [ChartSurface.kt:374](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L374) |
| `fun difference` | [ChartSurface.kt:455](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L455) |
| `fun captureSnapshot` | [ChartSurface.kt:462](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L462) |
| `fun save` | [ChartSurface.kt:470](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L470) |
| `class Candidate` | [ChartSurface.kt:506](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L506) |
| `fun update` | [ChartSurface.kt:532](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L532) |
| `fun lifecycle` | [ChartSurface.kt:542](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L542) |
| `fun retry` | [ChartSurface.kt:549](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L549) |
| `fun destroy` | [ChartSurface.kt:550](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L550) |
| `fun MarineMap` | [ChartSurface.kt:554](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSurface.kt#L554) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MapPoint.portrayalIconKey` | [ChartSymbolPainter.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L11) |
| `object ChartSymbolPainter` | [ChartSymbolPainter.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L14) |
| `fun bitmap` | [ChartSymbolPainter.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L15) |
| `fun path` | [ChartSymbolPainter.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L22) |
| `fun circle` | [ChartSymbolPainter.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L27) |
| `fun cone` | [ChartSymbolPainter.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L28) |
| `fun topmark` | [ChartSymbolPainter.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/ChartSymbolPainter.kt#L29) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/DatasetPreview.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DatasetPreviewDrawing` | [DatasetPreview.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/DatasetPreview.kt#L10) |
| `fun datasetPreviewDrawing` | [DatasetPreview.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/DatasetPreview.kt#L19) |
| `fun norm` | [DatasetPreview.kt:106](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/DatasetPreview.kt#L106) |
| `fun norm` | [DatasetPreview.kt:136](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/DatasetPreview.kt#L136) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LibreSceneGeometry` | [LibreSceneGeometry.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L34) |
| `fun clear` | [LibreSceneGeometry.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L49) |
| `fun render` | [LibreSceneGeometry.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L62) |
| `fun add` | [LibreSceneGeometry.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L76) |
| `fun flush` | [LibreSceneGeometry.kt:179](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L179) |
| `fun clip` | [LibreSceneGeometry.kt:213](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/LibreSceneGeometry.kt#L213) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MapSource` | [MapScene.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L15) |
| `object Offline : MapSource` | [MapScene.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L17) |
| `object Satellite : MapSource` | [MapScene.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L18) |
| `class CustomLayer` | [MapScene.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L20) |
| `class MapVessel` | [MapScene.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L24) |
| `class MapPointStyle` | [MapScene.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L34) |
| `class ChartSymbolKind` | [MapScene.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L36) |
| `class ChartSymbol` | [MapScene.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L37) |
| `class MapPoint` | [MapScene.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L46) |
| `class MapLine` | [MapScene.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L47) |
| `class MapCircle` | [MapScene.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L48) |
| `class MapArea` | [MapScene.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L50) |
| `class MapAisTarget` | [MapScene.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L52) |
| `class MapScene` | [MapScene.kt:61](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L61) |
| `fun MapScene.trafficGeometry` | [MapScene.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L74) |
| `interface MapEvent` | [MapScene.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L87) |
| `class CameraChanged` | [MapScene.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L88) |
| `class ItemSelected` | [MapScene.kt:90](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L90) |
| `class PointMoved` | [MapScene.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L91) |
| `class CoordinateSelected` | [MapScene.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L92) |
| `object GestureStarted : MapEvent` | [MapScene.kt:93](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L93) |
| `class MapCameraRequest` | [MapScene.kt:96](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L96) |
| `class MapOrientationMode` | [MapScene.kt:98](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L98) |
| `class LibraryObjectPreview` | [MapScene.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L101) |
| `class LibraryDatasetPreview` | [MapScene.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L103) |
| `class MapViewState` | [MapScene.kt:106](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L106) |
| `fun fly` | [MapScene.kt:148](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L148) |
| `fun fit` | [MapScene.kt:154](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L154) |
| `fun updatePortrayal` | [MapScene.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L189) |
| `fun selectDataset` | [MapScene.kt:216](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L216) |
| `fun view` | [MapScene.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L224) |
| `fun retainAisViews` | [MapScene.kt:226](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L226) |
| `fun selectedLayer` | [MapScene.kt:227](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L227) |
| `fun customFolderName` | [MapScene.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L228) |
| `fun sourceName` | [MapScene.kt:232](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L232) |
| `fun selectCustom` | [MapScene.kt:237](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L237) |
| `fun select` | [MapScene.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L238) |
| `fun removingLayer` | [MapScene.kt:264](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/MapScene.kt#L264) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun sizeOf` | [NativeSceneRenderer.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L46) |
| `fun invalidate` | [NativeSceneRenderer.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L49) |
| `fun clear` | [NativeSceneRenderer.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L52) |
| `fun render` | [NativeSceneRenderer.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L64) |
| `fun item` | [NativeSceneRenderer.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L83) |
| `fun polygon` | [NativeSceneRenderer.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L92) |
| `fun line` | [NativeSceneRenderer.kt:98](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L98) |
| `fun marker` | [NativeSceneRenderer.kt:135](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSceneRenderer.kt#L135) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSoundingLayer.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun clear` | [NativeSoundingLayer.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSoundingLayer.kt#L26) |
| `fun render` | [NativeSoundingLayer.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSoundingLayer.kt#L33) |
| `fun isCurrent` | [NativeSoundingLayer.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSoundingLayer.kt#L39) |
| `fun layer` | [NativeSoundingLayer.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/NativeSoundingLayer.kt#L69) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt

| 声明 | 实现位置 |
| --- | --- |
| `class OfflineMapLabels` | [OfflineMapLabels.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L44) |
| `fun sizeOf` | [OfflineMapLabels.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L66) |
| `fun attach` | [OfflineMapLabels.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L70) |
| `fun onCameraChanged` | [OfflineMapLabels.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L105) |
| `fun onCameraIdle` | [OfflineMapLabels.kt:112](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L112) |
| `fun clear` | [OfflineMapLabels.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L118) |
| `fun close` | [OfflineMapLabels.kt:138](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L138) |
| `fun containsCamera` | [OfflineMapLabels.kt:298](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L298) |
| `fun containsLabel` | [OfflineMapLabels.kt:302](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L302) |
| `fun longitudeDelta` | [OfflineMapLabels.kt:311](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L311) |
| `fun load` | [OfflineMapLabels.kt:333](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L333) |
| `fun name` | [OfflineMapLabels.kt:350](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineMapLabels.kt#L350) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt

| 声明 | 实现位置 |
| --- | --- |
| `object OfflineWorldStyle` | [OfflineWorldStyle.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt#L11) |
| `fun sources` | [OfflineWorldStyle.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt#L12) |
| `fun layers` | [OfflineWorldStyle.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt#L22) |
| `fun applyPalette` | [OfflineWorldStyle.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt#L47) |
| `fun hex` | [OfflineWorldStyle.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/OfflineWorldStyle.kt#L50) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt

| 声明 | 实现位置 |
| --- | --- |
| `class StructuredChartViewport` | [StructuredChartOverlay.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L12) |
| `fun rememberStructuredChart` | [StructuredChartOverlay.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L15) |
| `fun norm` | [StructuredChartOverlay.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L31) |
| `fun containsRing` | [StructuredChartOverlay.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L70) |
| `fun x` | [StructuredChartOverlay.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L72) |
| `fun chartObjectsAt` | [StructuredChartOverlay.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L77) |
| `fun xy` | [StructuredChartOverlay.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L79) |
| `fun near` | [StructuredChartOverlay.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/StructuredChartOverlay.kt#L80) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/VesselGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun vesselCourseVector` | [VesselGeometry.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/chart/VesselGeometry.kt#L6) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun close` | [MarinePresentationBridge.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L59) |
| `fun syncLanguage` | [MarinePresentationBridge.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L97) |
| `fun startRecording` | [MarinePresentationBridge.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L105) |
| `fun pauseRecording` | [MarinePresentationBridge.kt:106](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L106) |
| `fun resumeRecording` | [MarinePresentationBridge.kt:107](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L107) |
| `fun finishRecording` | [MarinePresentationBridge.kt:108](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/MarinePresentationBridge.kt#L108) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/NavigationDisplayProjection.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselDataSnapshot.withNavigation` | [NavigationDisplayProjection.kt:8](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/NavigationDisplayProjection.kt#L8) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Fix` | [Nmea.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L20) |
| `fun fresh` | [Nmea.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L27) |
| `fun freshSpeed` | [Nmea.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L28) |
| `fun freshCourse` | [Nmea.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L29) |
| `fun freshHeading` | [Nmea.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L31) |
| `class VesselData` | [Nmea.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L35) |
| `fun fix` | [Nmea.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L45) |
| `class DataHub` | [Nmea.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L48) |
| `fun bind` | [Nmea.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L58) |
| `fun retryHistoryStorage` | [Nmea.kt:108](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L108) |
| `fun flushHistory` | [Nmea.kt:109](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L109) |
| `fun update` | [Nmea.kt:110](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L110) |
| `fun resetNmea` | [Nmea.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/data/Nmea.kt#L111) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/AppCenterScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AppCenterScreen` | [AppCenterScreen.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/AppCenterScreen.kt#L21) |
| `fun stage` | [AppCenterScreen.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/AppCenterScreen.kt#L44) |
| `fun permissionTitle` | [AppCenterScreen.kt:132](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/AppCenterScreen.kt#L132) |
| `fun ExtensionScreen` | [AppCenterScreen.kt:188](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/AppCenterScreen.kt#L188) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionHardwareServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun clockJson` | [ExtensionHardwareServices.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionHardwareServices.kt#L17) |
| `fun request` | [ExtensionHardwareServices.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionHardwareServices.kt#L24) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionMarineBridge.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ExtensionBridgeException` | [ExtensionMarineBridge.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionMarineBridge.kt#L27) |
| `fun request` | [ExtensionMarineBridge.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionMarineBridge.kt#L47) |
| `fun accept` | [ExtensionMarineBridge.kt:401](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionMarineBridge.kt#L401) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ExtensionManifest` | [ExtensionPackages.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L26) |
| `class ExtensionInstalled` | [ExtensionPackages.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L38) |
| `class ExtensionCandidate internal constructor` | [ExtensionPackages.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L49) |
| `class ExtensionPackageManager` | [ExtensionPackages.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L60) |
| `fun inspect` | [ExtensionPackages.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L94) |
| `fun discard` | [ExtensionPackages.kt:166](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L166) |
| `fun install` | [ExtensionPackages.kt:170](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L170) |
| `fun uninstall` | [ExtensionPackages.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L210) |
| `fun setGrants` | [ExtensionPackages.kt:223](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L223) |
| `fun readStorage` | [ExtensionPackages.kt:235](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L235) |
| `fun writeStorage` | [ExtensionPackages.kt:247](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L247) |
| `fun close` | [ExtensionPackages.kt:257](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L257) |
| `fun validResourcePath` | [ExtensionPackages.kt:354](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L354) |
| `fun parseManifest` | [ExtensionPackages.kt:358](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L358) |
| `fun parseExtensionJson` | [ExtensionPackages.kt:390](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionPackages.kt#L390) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSdkContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `object ExtensionSdkContract` | [ExtensionSdkContract.kt:7](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSdkContract.kt#L7) |
| `class Method` | [ExtensionSdkContract.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSdkContract.kt#L10) |
| `fun permissionsFor` | [ExtensionSdkContract.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSdkContract.kt#L41) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSystemServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun request` | [ExtensionSystemServices.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionSystemServices.kt#L45) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionTiles.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun extensionTileChoice` | [ExtensionTiles.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionTiles.kt#L16) |
| `fun missingExtensionTileChoice` | [ExtensionTiles.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionTiles.kt#L23) |
| `fun extensionTilePresentation` | [ExtensionTiles.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionTiles.kt#L28) |
| `fun extensionInstancePresentation` | [ExtensionTiles.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionTiles.kt#L32) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ExtensionWebView` | [ExtensionWebView.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L62) |
| `fun shouldInterceptRequest` | [ExtensionWebView.kt:217](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L217) |
| `fun onPermissionRequest` | [ExtensionWebView.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L224) |
| `fun onGeolocationPermissionsShowPrompt` | [ExtensionWebView.kt:225](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L225) |
| `fun onShowFileChooser` | [ExtensionWebView.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L228) |
| `fun onJsAlert` | [ExtensionWebView.kt:232](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L232) |
| `fun onJsConfirm` | [ExtensionWebView.kt:235](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L235) |
| `fun onJsPrompt` | [ExtensionWebView.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L238) |
| `fun shouldOverrideUrlLoading` | [ExtensionWebView.kt:244](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L244) |
| `fun shouldOverrideUrlLoading` | [ExtensionWebView.kt:247](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L247) |
| `fun shouldInterceptRequest` | [ExtensionWebView.kt:249](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L249) |
| `fun onPageStarted` | [ExtensionWebView.kt:254](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L254) |
| `fun onReceivedError` | [ExtensionWebView.kt:264](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L264) |
| `fun onReceivedHttpError` | [ExtensionWebView.kt:268](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L268) |
| `fun onPageFinished` | [ExtensionWebView.kt:272](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L272) |
| `fun doUpdateVisitedHistory` | [ExtensionWebView.kt:279](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L279) |
| `fun onRenderProcessGone` | [ExtensionWebView.kt:281](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L281) |
| `fun saveVisit` | [ExtensionWebView.kt:294](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L294) |
| `fun resume` | [ExtensionWebView.kt:357](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L357) |
| `fun goBack` | [ExtensionWebView.kt:374](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L374) |
| `fun pause` | [ExtensionWebView.kt:376](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L376) |
| `fun close` | [ExtensionWebView.kt:384](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/ExtensionWebView.kt#L384) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/InternalAppInfoDialog.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun InternalAppInfoDialog` | [InternalAppInfoDialog.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/InternalAppInfoDialog.kt#L15) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/SystemYklApps.kt

| 声明 | 实现位置 |
| --- | --- |
| `object SystemYklApps` | [SystemYklApps.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/SystemYklApps.kt#L13) |
| `fun identity` | [SystemYklApps.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/SystemYklApps.kt#L14) |
| `fun Render` | [SystemYklApps.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/SystemYklApps.kt#L62) |
| `fun YklPackageScreen` | [SystemYklApps.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/SystemYklApps.kt#L76) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `class YklRuntime` | [YklPackageCatalog.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L18) |
| `class YklOrigin` | [YklPackageCatalog.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L19) |
| `class YklPackageEntry` | [YklPackageCatalog.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L20) |
| `fun owns` | [YklPackageCatalog.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L29) |
| `class YklPackageCatalog` | [YklPackageCatalog.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L36) |
| `fun resolve` | [YklPackageCatalog.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L59) |
| `fun systemApp` | [YklPackageCatalog.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L60) |
| `fun strings` | [YklPackageCatalog.kt:86](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L86) |
| `fun installedEntry` | [YklPackageCatalog.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/extensions/YklPackageCatalog.kt#L103) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselAttitudeMotion` | [VesselAttitudeMotion.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L18) |
| `fun setTarget` | [VesselAttitudeMotion.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L33) |
| `fun readDrawFrame` | [VesselAttitudeMotion.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L62) |
| `fun resetClock` | [VesselAttitudeMotion.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L64) |
| `fun snapToTarget` | [VesselAttitudeMotion.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L67) |
| `fun advance` | [VesselAttitudeMotion.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L75) |
| `fun transform` | [VesselAttitudeMotion.kt:106](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeMotion.kt#L106) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselAttitudePose` | [VesselAttitudeProjection.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L21) |
| `fun transform` | [VesselAttitudeProjection.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L22) |
| `fun matrix` | [VesselAttitudeProjection.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L34) |
| `class VesselAttitudeDisplayState` | [VesselAttitudeProjection.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L43) |
| `class VesselAttitudeProjection` | [VesselAttitudeProjection.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L46) |
| `fun from` | [VesselAttitudeProjection.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselAttitudeProjection.kt#L60) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselViewPreset` | [VesselScene3D.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L51) |
| `fun VesselScene3D` | [VesselScene3D.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L58) |
| `fun vesselScenePoint` | [VesselScene3D.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L113) |
| `class VesselSceneProjectionCache` | [VesselScene3D.kt:124](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L124) |
| `fun get` | [VesselScene3D.kt:128](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L128) |
| `class VesselSceneProjector` | [VesselScene3D.kt:138](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L138) |
| `fun point` | [VesselScene3D.kt:143](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L143) |
| `fun dot` | [VesselScene3D.kt:154](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L154) |
| `fun cross` | [VesselScene3D.kt:155](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L155) |
| `fun normalized` | [VesselScene3D.kt:156](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L156) |
| `fun onAttachedToWindow` | [VesselScene3D.kt:229](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L229) |
| `fun onSizeChanged` | [VesselScene3D.kt:234](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L234) |
| `fun onDetachedFromWindow` | [VesselScene3D.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L238) |
| `fun onWindowVisibilityChanged` | [VesselScene3D.kt:246](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L246) |
| `fun initialize` | [VesselScene3D.kt:261](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L261) |
| `fun load` | [VesselScene3D.kt:305](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L305) |
| `fun update` | [VesselScene3D.kt:319](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L319) |
| `fun setResumed` | [VesselScene3D.kt:342](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L342) |
| `fun doFrame` | [VesselScene3D.kt:419](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L419) |
| `fun onNativeWindowChanged` | [VesselScene3D.kt:463](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L463) |
| `fun onDetachedFromSurface` | [VesselScene3D.kt:473](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L473) |
| `fun onResized` | [VesselScene3D.kt:482](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L482) |
| `fun fail` | [VesselScene3D.kt:514](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L514) |
| `fun close` | [VesselScene3D.kt:522](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScene3D.kt#L522) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VesselHotspot` | [VesselSceneModel.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L19) |
| `class VesselSceneStatus` | [VesselSceneModel.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L28) |
| `class VesselSceneModel` | [VesselSceneModel.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L37) |
| `fun metric` | [VesselSceneModel.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L43) |
| `fun hotspot` | [VesselSceneModel.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L44) |
| `class VesselSceneHotspot` | [VesselSceneModel.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L48) |
| `class VesselSceneMount` | [VesselSceneModel.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L55) |
| `class VesselSceneMetric` | [VesselSceneModel.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L68) |
| `class VesselSceneCandidate` | [VesselSceneModel.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L103) |
| `class VesselSceneConnection` | [VesselSceneModel.kt:123](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselSceneModel.kt#L123) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt

| 声明 | 实现位置 |
| --- | --- |
| `object VesselScenePresenter` | [VesselScenePresenter.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt#L17) |
| `fun present` | [VesselScenePresenter.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt#L26) |
| `fun hotspot` | [VesselScenePresenter.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt#L75) |
| `fun preferredHeadingMetric` | [VesselScenePresenter.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt#L91) |
| `fun validHeading` | [VesselScenePresenter.kt:93](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/VesselScenePresenter.kt#L93) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AisScenePosition` | [AisSceneModel.kt:8](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L8) |
| `class AisSceneDimensions` | [AisSceneModel.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L13) |
| `class AisSceneKind` | [AisSceneModel.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L17) |
| `class AisSceneTarget` | [AisSceneModel.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L19) |
| `class AisSceneData` | [AisSceneModel.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L38) |
| `class AisScenePreset` | [AisSceneModel.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L51) |
| `class AisSceneCameraState` | [AisSceneModel.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L54) |
| `class AisVector3` | [AisSceneModel.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L77) |
| `fun dot` | [AisSceneModel.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L81) |
| `fun cross` | [AisSceneModel.kt:82](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L82) |
| `fun normalized` | [AisSceneModel.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L83) |
| `class AisLocalFrame` | [AisSceneModel.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L87) |
| `fun removeEldestEntry` | [AisSceneModel.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L97) |
| `fun position` | [AisSceneModel.kt:99](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L99) |
| `class AisSceneCamera` | [AisSceneModel.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L118) |
| `fun depth` | [AisSceneModel.kt:129](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L129) |
| `fun project` | [AisSceneModel.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L130) |
| `fun metersPerPixel` | [AisSceneModel.kt:140](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L140) |
| `fun clipSegment` | [AisSceneModel.kt:144](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L144) |
| `fun validAisBearing` | [AisSceneModel.kt:157](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L157) |
| `class AisSceneFrame` | [AisSceneModel.kt:159](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L159) |
| `fun aisSceneFrame` | [AisSceneModel.kt:165](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L165) |
| `class AisDisplayGeometry` | [AisSceneModel.kt:202](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L202) |
| `fun transform` | [AisSceneModel.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L210) |
| `fun matrix` | [AisSceneModel.kt:215](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L215) |
| `fun boundsFaces` | [AisSceneModel.kt:222](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L222) |
| `fun aisDisplayGeometry` | [AisSceneModel.kt:234](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L234) |
| `fun aisSceneMidpoint` | [AisSceneModel.kt:255](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L255) |
| `class AisSceneCameraMotion` | [AisSceneModel.kt:265](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L265) |
| `fun advance` | [AisSceneModel.kt:269](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L269) |
| `fun mix` | [AisSceneModel.kt:277](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L277) |
| `fun vector` | [AisSceneModel.kt:278](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisSceneModel.kt#L278) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun growFor` | [AisTrafficRenderer3D.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L42) |
| `fun acquire` | [AisTrafficRenderer3D.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L51) |
| `fun transformChanged` | [AisTrafficRenderer3D.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L52) |
| `fun paintChanged` | [AisTrafficRenderer3D.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L57) |
| `fun retain` | [AisTrafficRenderer3D.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L62) |
| `class AisTrafficRenderer3D` | [AisTrafficRenderer3D.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L70) |
| `fun onAttachedToWindow` | [AisTrafficRenderer3D.kt:131](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L131) |
| `fun onDetachedFromWindow` | [AisTrafficRenderer3D.kt:132](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L132) |
| `fun onWindowVisibilityChanged` | [AisTrafficRenderer3D.kt:133](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L133) |
| `fun onSizeChanged` | [AisTrafficRenderer3D.kt:134](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L134) |
| `fun initialize` | [AisTrafficRenderer3D.kt:158](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L158) |
| `fun beginAssetRead` | [AisTrafficRenderer3D.kt:193](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L193) |
| `fun load` | [AisTrafficRenderer3D.kt:195](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L195) |
| `fun update` | [AisTrafficRenderer3D.kt:221](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L221) |
| `fun setResumed` | [AisTrafficRenderer3D.kt:246](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L246) |
| `fun visible` | [AisTrafficRenderer3D.kt:294](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L294) |
| `fun doFrame` | [AisTrafficRenderer3D.kt:385](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L385) |
| `fun onNativeWindowChanged` | [AisTrafficRenderer3D.kt:438](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L438) |
| `fun onDetachedFromSurface` | [AisTrafficRenderer3D.kt:448](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L448) |
| `fun onResized` | [AisTrafficRenderer3D.kt:456](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L456) |
| `fun fail` | [AisTrafficRenderer3D.kt:471](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L471) |
| `fun close` | [AisTrafficRenderer3D.kt:483](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficRenderer3D.kt#L483) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisTrafficScene3D` | [AisTrafficScene3D.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L64) |
| `fun tr` | [AisTrafficScene3D.kt:110](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L110) |
| `fun reset` | [AisTrafficScene3D.kt:125](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L125) |
| `fun point` | [AisTrafficScene3D.kt:355](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L355) |
| `fun line` | [AisTrafficScene3D.kt:356](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L356) |
| `fun project` | [AisTrafficScene3D.kt:504](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L504) |
| `fun path` | [AisTrafficScene3D.kt:505](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L505) |
| `fun vector` | [AisTrafficScene3D.kt:524](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/ais/AisTrafficScene3D.kt#L524) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SpatialNavigationTarget` | [NavigationSpatialModel.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L12) |
| `class SpatialMountMode` | [NavigationSpatialModel.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L21) |
| `class SpatialDirection` | [NavigationSpatialModel.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L23) |
| `class SpatialReferencePosition` | [NavigationSpatialModel.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L24) |
| `class NavigationSpatialSnapshot` | [NavigationSpatialModel.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L30) |
| `class SpatialNorthConversion` | [NavigationSpatialModel.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L52) |
| `fun from` | [NavigationSpatialModel.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L54) |
| `class SpatialVector` | [NavigationSpatialModel.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L66) |
| `fun scale` | [NavigationSpatialModel.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L68) |
| `fun SensorQuaternion.rotate` | [NavigationSpatialModel.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L70) |
| `fun wrapBearing` | [NavigationSpatialModel.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L74) |
| `fun signedBearing` | [NavigationSpatialModel.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L75) |
| `fun bearingVector` | [NavigationSpatialModel.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L76) |
| `fun worldVector` | [NavigationSpatialModel.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L80) |
| `class SpatialCamera` | [NavigationSpatialModel.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L84) |
| `fun resolveSpatialCamera` | [NavigationSpatialModel.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L91) |
| `fun at` | [NavigationSpatialModel.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L101) |
| `class SpatialProjection` | [NavigationSpatialModel.kt:148](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L148) |
| `fun project` | [NavigationSpatialModel.kt:156](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L156) |
| `class ProjectedSpatialPoint` | [NavigationSpatialModel.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L167) |
| `class DeviceViewMotion` | [NavigationSpatialModel.kt:170](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L170) |
| `fun update` | [NavigationSpatialModel.kt:176](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L176) |
| `fun advance` | [NavigationSpatialModel.kt:183](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L183) |
| `class SpatialCameraMotion` | [NavigationSpatialModel.kt:200](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L200) |
| `fun present` | [NavigationSpatialModel.kt:204](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialModel.kt#L204) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SpatialRenderInput` | [NavigationSpatialRenderer.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L32) |
| `class SpatialHit` | [NavigationSpatialRenderer.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L43) |
| `class SpatialPresentedFrame` | [NavigationSpatialRenderer.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L44) |
| `class NavigationSpatialSurface` | [NavigationSpatialRenderer.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L50) |
| `fun update` | [NavigationSpatialRenderer.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L73) |
| `fun orientation` | [NavigationSpatialRenderer.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L79) |
| `fun resetView` | [NavigationSpatialRenderer.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L80) |
| `fun turnBy` | [NavigationSpatialRenderer.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L81) |
| `fun frame` | [NavigationSpatialRenderer.kt:85](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L85) |
| `fun doFrame` | [NavigationSpatialRenderer.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L91) |
| `fun onSurfaceTextureAvailable` | [NavigationSpatialRenderer.kt:108](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L108) |
| `fun onSurfaceTextureSizeChanged` | [NavigationSpatialRenderer.kt:116](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L116) |
| `fun onSurfaceTextureUpdated` | [NavigationSpatialRenderer.kt:117](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L117) |
| `fun onSurfaceTextureDestroyed` | [NavigationSpatialRenderer.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L118) |
| `fun onDetachedFromWindow` | [NavigationSpatialRenderer.kt:123](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L123) |
| `fun close` | [NavigationSpatialRenderer.kt:124](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L124) |
| `fun onTouchEvent` | [NavigationSpatialRenderer.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L130) |
| `fun performClick` | [NavigationSpatialRenderer.kt:153](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L153) |
| `fun draw` | [NavigationSpatialRenderer.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L189) |
| `fun shader` | [NavigationSpatialRenderer.kt:337](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L337) |
| `fun close` | [NavigationSpatialRenderer.kt:348](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/scene/navigation/NavigationSpatialRenderer.kt#L348) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AccentPaletteSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun customAccent` | [AccentPaletteSettings.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AccentPaletteSettings.kt#L32) |
| `fun AccentPaletteSettings` | [AccentPaletteSettings.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AccentPaletteSettings.kt#L37) |
| `fun updateHex` | [AccentPaletteSettings.kt:113](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AccentPaletteSettings.kt#L113) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rememberAisTraffic` | [AisPresentation.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L20) |
| `fun AisPoint.geo` | [AisPresentation.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L23) |
| `fun aisNumber` | [AisPresentation.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L24) |
| `fun aisKind` | [AisPresentation.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L25) |
| `fun aisState` | [AisPresentation.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L38) |
| `fun aisCpaReason` | [AisPresentation.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L50) |
| `fun aisRiskName` | [AisPresentation.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L64) |
| `fun aisInputSummary` | [AisPresentation.kt:71](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L71) |
| `fun aisMapTargets` | [AisPresentation.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L92) |
| `fun OsStore.aisCommand` | [AisPresentation.kt:123](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L123) |
| `fun OsStore.aisPreferences` | [AisPresentation.kt:124](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L124) |
| `fun AisCompactDetail` | [AisPresentation.kt:127](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L127) |
| `fun AisLayerChoice` | [AisPresentation.kt:139](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L139) |
| `fun AisMonitoringSummary` | [AisPresentation.kt:147](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L147) |
| `fun AisMapStatus` | [AisPresentation.kt:155](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisPresentation.kt#L155) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisRadar` | [AisRadar.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L42) |
| `fun observedTrack` | [AisRadar.kt:168](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L168) |
| `fun distance` | [AisRadar.kt:351](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L351) |
| `fun point` | [AisRadar.kt:352](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L352) |
| `fun point` | [AisRadar.kt:353](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L353) |
| `fun RadarInfo` | [AisRadar.kt:444](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRadar.kt#L444) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRangeZoom.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisRangeZoom` | [AisRangeZoom.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisRangeZoom.kt#L34) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisScreen` | [AisScreen.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L51) |
| `fun setRange` | [AisScreen.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L75) |
| `fun saveRange` | [AisScreen.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L80) |
| `fun go` | [AisScreen.kt:108](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L108) |
| `fun openChart` | [AisScreen.kt:109](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L109) |
| `fun viewTarget` | [AisScreen.kt:110](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L110) |
| `fun back` | [AisScreen.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L122) |
| `fun sorted` | [AisScreen.kt:221](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L221) |
| `fun AisRetainSelection` | [AisScreen.kt:268](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L268) |
| `fun permissions` | [AisScreen.kt:325](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L325) |
| `fun AisPoint.scene` | [AisScreen.kt:363](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisScreen.kt#L363) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisTargetDetail.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisTargetDetail` | [AisTargetDetail.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisTargetDetail.kt#L31) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisVesselSheet.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AisVesselSheet` | [AisVesselSheet.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AisVesselSheet.kt#L36) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AnchorExperience` | [AnchorExperience.kt:63](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L63) |
| `fun backInside` | [AnchorExperience.kt:117](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L117) |
| `fun command` | [AnchorExperience.kt:153](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L153) |
| `fun startWatch` | [AnchorExperience.kt:229](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L229) |
| `fun flush` | [AnchorExperience.kt:646](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L646) |
| `fun anchorSamplesConnected` | [AnchorExperience.kt:660](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorExperience.kt#L660) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun effectiveAnchorPoint` | [AnchorSetupPolicy.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt#L9) |
| `fun anchorWatchInput` | [AnchorSetupPolicy.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AnchorSetupPolicy.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppCommandBar.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AppCommand` | [AppCommandBar.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppCommandBar.kt#L35) |
| `fun AppCommandBar` | [AppCommandBar.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppCommandBar.kt#L42) |
| `fun calculatePosition` | [AppCommandBar.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppCommandBar.kt#L67) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppDialogSurface.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AppDialog` | [AppDialogSurface.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppDialogSurface.kt#L21) |
| `fun AppDialogSurface` | [AppDialogSurface.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppDialogSurface.kt#L31) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppPageTransition.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun <T> AppPageTransition` | [AppPageTransition.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppPageTransition.kt#L31) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppSelectionRows.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun AppCheckRow` | [AppSelectionRows.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/AppSelectionRows.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CapturedVoyageMoment.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun captureVoyageMoment` | [CapturedVoyageMoment.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CapturedVoyageMoment.kt#L16) |
| `fun CapturedMomentContent` | [CapturedVoyageMoment.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CapturedVoyageMoment.kt#L19) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartBackgroundChoices.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartBackgroundChoices` | [ChartBackgroundChoices.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartBackgroundChoices.kt#L16) |
| `fun selectChartFolder` | [ChartBackgroundChoices.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartBackgroundChoices.kt#L28) |
| `fun CustomChartFolderSetting` | [ChartBackgroundChoices.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartBackgroundChoices.kt#L36) |
| `fun ChartSourceSaveStatus` | [ChartBackgroundChoices.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartBackgroundChoices.kt#L62) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartDatasetNavigation.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun openDatasetOnChart` | [ChartDatasetNavigation.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartDatasetNavigation.kt#L12) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NativeChart` | [ChartMapAdapter.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt#L21) |
| `fun isCurrent` | [ChartMapAdapter.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt#L27) |
| `fun nearestDraftInsertion` | [ChartMapAdapter.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartMapAdapter.kt#L122) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartNavigationSpatial.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartNavigationSpatial` | [ChartNavigationSpatial.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartNavigationSpatial.kt#L21) |
| `fun direction` | [ChartNavigationSpatial.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartNavigationSpatial.kt#L51) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun featureTitle` | [ChartObjectSheet.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt#L12) |
| `fun depthEvidenceText` | [ChartObjectSheet.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt#L15) |
| `fun ChartObjectSheet` | [ChartObjectSheet.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt#L28) |
| `fun prepareWaypoint` | [ChartObjectSheet.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt#L40) |
| `fun commitWaypoint` | [ChartObjectSheet.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartObjectSheet.kt#L49) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartPortrayalSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartPortrayalSetting` | [ChartPortrayalSettings.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartPortrayalSettings.kt#L11) |
| `fun text` | [ChartPortrayalSettings.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartPortrayalSettings.kt#L48) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartScreen` | [ChartScreen.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L34) |
| `fun openPlanning` | [ChartScreen.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L49) |
| `fun openSpatial` | [ChartScreen.kt:61](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L61) |
| `fun returnToMap` | [ChartScreen.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L62) |
| `fun closeTool` | [ChartScreen.kt:99](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L99) |
| `fun ConfirmDialog` | [ChartScreen.kt:306](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ChartScreen.kt#L306) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun CompositeTileConfiguration` | [CompositeTileConfiguration.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt#L24) |
| `fun TileSizeChoices` | [CompositeTileConfiguration.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt#L72) |
| `fun shortcut` | [CompositeTileConfiguration.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt#L94) |
| `fun tileLaunchDestination` | [CompositeTileConfiguration.kt:114](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt#L114) |
| `fun CompositeTileDestination` | [CompositeTileConfiguration.kt:121](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileConfiguration.kt#L121) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun CompositeTileFace` | [CompositeTileRendering.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L43) |
| `fun TaskSceneTileFace` | [CompositeTileRendering.kt:72](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L72) |
| `fun project` | [CompositeTileRendering.kt:123](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L123) |
| `fun project` | [CompositeTileRendering.kt:169](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L169) |
| `fun project` | [CompositeTileRendering.kt:200](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L200) |
| `fun project` | [CompositeTileRendering.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L224) |
| `fun local` | [CompositeTileRendering.kt:266](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L266) |
| `fun point` | [CompositeTileRendering.kt:282](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L282) |
| `fun vector` | [CompositeTileRendering.kt:300](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CompositeTileRendering.kt#L300) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentRecoveryStatus.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ContentRecoveryStatus` | [ContentRecoveryStatus.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentRecoveryStatus.kt#L14) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentTileRendering.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun instanceTilePresentation` | [ContentTileRendering.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentTileRendering.kt#L23) |
| `fun rememberTileHistory` | [ContentTileRendering.kt:205](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentTileRendering.kt#L205) |
| `class Capture` | [ContentTileRendering.kt:206](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentTileRendering.kt#L206) |
| `fun project` | [ContentTileRendering.kt:280](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ContentTileRendering.kt#L280) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CoordinateEditPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun preservedCoordinate` | [CoordinateEditPolicy.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/CoordinateEditPolicy.kt#L6) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun DataCenterScreen` | [DataCenterExperience.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L29) |
| `fun navigate` | [DataCenterExperience.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L43) |
| `fun pageTitle` | [DataCenterExperience.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L57) |
| `fun ColumnScope.PhoneSourceSettings` | [DataCenterExperience.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L103) |
| `fun angle` | [DataCenterExperience.kt:201](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L201) |
| `fun runCommand` | [DataCenterExperience.kt:208](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L208) |
| `fun phoneCalibrationFeedback` | [DataCenterExperience.kt:319](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DataCenterExperience.kt#L319) |

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
| `fun OsStore.distanceValue` | [DisplayFormats.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L18) |
| `fun OsStore.distanceMeters` | [DisplayFormats.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L19) |
| `fun OsStore.speedValue` | [DisplayFormats.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L20) |
| `fun OsStore.speedKnots` | [DisplayFormats.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L21) |
| `fun OsStore.lengthValue` | [DisplayFormats.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L22) |
| `fun OsStore.lengthMeters` | [DisplayFormats.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L23) |
| `fun OsStore.depthValue` | [DisplayFormats.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L24) |
| `fun OsStore.depthMeters` | [DisplayFormats.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L25) |
| `fun OsStore.temperatureValue` | [DisplayFormats.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L26) |
| `fun OsStore.temperatureCelsius` | [DisplayFormats.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L27) |
| `fun OsStore.pressureValue` | [DisplayFormats.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L28) |
| `fun OsStore.pressureHpa` | [DisplayFormats.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L29) |
| `fun OsStore.formatDistance` | [DisplayFormats.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L31) |
| `fun OsStore.formatLength` | [DisplayFormats.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L32) |
| `fun OsStore.formatDepth` | [DisplayFormats.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L33) |
| `fun OsStore.formatSpeed` | [DisplayFormats.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L34) |
| `fun OsStore.formatTemperature` | [DisplayFormats.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L35) |
| `fun OsStore.formatPressure` | [DisplayFormats.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L36) |
| `fun OsStore.formatPressureChange` | [DisplayFormats.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L37) |
| `fun OsStore.formatBearing` | [DisplayFormats.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L39) |
| `fun OsStore.formatAngle` | [DisplayFormats.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L42) |
| `fun OsStore.displayMetricValue` | [DisplayFormats.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L45) |
| `fun OsStore.displayMetricUnit` | [DisplayFormats.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L53) |
| `fun OsStore.formatMetric` | [DisplayFormats.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L66) |
| `fun OsStore.formatCoordinates` | [DisplayFormats.kt:82](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L82) |
| `fun coordinate` | [DisplayFormats.kt:85](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L85) |
| `fun OsStore.formatLatitude` | [DisplayFormats.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L97) |
| `fun OsStore.formatLongitude` | [DisplayFormats.kt:100](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L100) |
| `fun parseCoordinate` | [DisplayFormats.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L105) |
| `fun durationLabel` | [DisplayFormats.kt:125](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/DisplayFormats.kt#L125) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun HardwareModeBanner` | [HardwareLabScreen.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt#L26) |
| `fun HardwareLabScreen` | [HardwareLabScreen.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt#L41) |
| `fun submit` | [HardwareLabScreen.kt:56](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt#L56) |
| `fun updateScenario` | [HardwareLabScreen.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt#L76) |
| `fun set` | [HardwareLabScreen.kt:206](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/HardwareLabScreen.kt#L206) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentAttitudePanel.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun InstrumentAttitudePanel` | [InstrumentAttitudePanel.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentAttitudePanel.kt#L39) |
| `fun p` | [InstrumentAttitudePanel.kt:216](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentAttitudePanel.kt#L216) |
| `fun p` | [InstrumentAttitudePanel.kt:233](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentAttitudePanel.kt#L233) |
| `fun p` | [InstrumentAttitudePanel.kt:265](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentAttitudePanel.kt#L265) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentDisplayMotion.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rememberInstrumentMotion` | [InstrumentDisplayMotion.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentDisplayMotion.kt#L27) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryDrawing.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun DrawScope.drawInstrumentHistory` | [InstrumentHistoryDrawing.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryDrawing.kt#L14) |
| `fun y` | [InstrumentHistoryDrawing.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryDrawing.kt#L20) |
| `fun x` | [InstrumentHistoryDrawing.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryDrawing.kt#L24) |
| `fun radial` | [InstrumentHistoryDrawing.kt:124](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryDrawing.kt#L124) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InstrumentHistoryKind` | [InstrumentHistoryModel.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L9) |
| `fun instrumentHistoryKind` | [InstrumentHistoryModel.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L11) |
| `class HistoryPoint` | [InstrumentHistoryModel.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L24) |
| `class HistoryBucket` | [InstrumentHistoryModel.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L25) |
| `class InstrumentHistoryFrame` | [InstrumentHistoryModel.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L40) |
| `fun buildInstrumentHistory` | [InstrumentHistoryModel.kt:68](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L68) |
| `fun normalizedHistoryAngle` | [InstrumentHistoryModel.kt:149](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L149) |
| `fun signedHistoryAngle` | [InstrumentHistoryModel.kt:150](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentHistoryModel.kt#L150) |

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

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentSpatialPanels.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NavigationInstrumentPanel` | [InstrumentSpatialPanels.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentSpatialPanels.kt#L30) |
| `fun SailingInstrumentPanel` | [InstrumentSpatialPanels.kt:181](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentSpatialPanels.kt#L181) |
| `fun DepthInstrumentSection` | [InstrumentSpatialPanels.kt:296](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentSpatialPanels.kt#L296) |
| `fun yFor` | [InstrumentSpatialPanels.kt:335](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentSpatialPanels.kt#L335) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `class InstrumentTrendGroup` | [InstrumentTrendCatalog.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L6) |
| `class InstrumentTrendMetric` | [InstrumentTrendCatalog.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L9) |
| `object InstrumentTrendCatalog` | [InstrumentTrendCatalog.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L11) |
| `fun lastReading` | [InstrumentTrendCatalog.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L32) |
| `fun available` | [InstrumentTrendCatalog.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L36) |
| `fun selected` | [InstrumentTrendCatalog.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentTrendCatalog.kt#L39) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentWeatherPanel.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun InstrumentWeatherPanel` | [InstrumentWeatherPanel.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentWeatherPanel.kt#L41) |
| `fun InstrumentPressureHistory` | [InstrumentWeatherPanel.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentWeatherPanel.kt#L69) |
| `fun x` | [InstrumentWeatherPanel.kt:209](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentWeatherPanel.kt#L209) |
| `fun y` | [InstrumentWeatherPanel.kt:210](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentWeatherPanel.kt#L210) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun InstrumentsScreen` | [InstrumentsExperience.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L34) |
| `fun closeLayer` | [InstrumentsExperience.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L62) |
| `fun saveLayout` | [InstrumentsExperience.kt:70](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L70) |
| `fun instrumentSourceMetric` | [InstrumentsExperience.kt:211](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L211) |
| `fun <T> value` | [InstrumentsExperience.kt:316](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L316) |
| `fun number` | [InstrumentsExperience.kt:317](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L317) |
| `fun bearing` | [InstrumentsExperience.kt:318](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L318) |
| `fun angle` | [InstrumentsExperience.kt:319](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L319) |
| `fun speed` | [InstrumentsExperience.kt:320](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L320) |
| `fun depth` | [InstrumentsExperience.kt:321](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L321) |
| `fun distance` | [InstrumentsExperience.kt:322](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L322) |
| `fun observationStatus` | [InstrumentsExperience.kt:363](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L363) |
| `fun readingStatus` | [InstrumentsExperience.kt:368](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L368) |
| `fun instrumentName` | [InstrumentsExperience.kt:379](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/InstrumentsExperience.kt#L379) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryObjectBrowser.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun title` | [LibraryObjectBrowser.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryObjectBrowser.kt#L21) |
| `fun LibraryObjectsScreen` | [LibraryObjectBrowser.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryObjectBrowser.kt#L36) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LibraryScreen` | [LibraryScreen.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt#L17) |
| `fun LibraryFolderScreen` | [LibraryScreen.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibraryScreen.kt#L67) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibrarySourceSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LibraryBackgroundSettings` | [LibrarySourceSettings.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibrarySourceSettings.kt#L12) |
| `fun LibraryDataSettings` | [LibrarySourceSettings.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LibrarySourceSettings.kt#L21) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LinzSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LinzSettingsSection` | [LinzSettings.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LinzSettings.kt#L23) |
| `fun save` | [LinzSettings.kt:31](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LinzSettings.kt#L31) |
| `fun LinzLibrarySource` | [LinzSettings.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LinzSettings.kt#L64) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LocalNmeaExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LocalNmeaScreen` | [LocalNmeaExperience.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/LocalNmeaExperience.kt#L15) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MapSourcePicker` | [MapExperience.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L18) |
| `fun MapSourceOption` | [MapExperience.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L32) |
| `fun MapSourceButton` | [MapExperience.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L37) |
| `fun MapPicker` | [MapExperience.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MapExperience.kt#L45) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ChartAppScreen` | [MarineAppsScreen.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt#L23) |
| `fun RecordingDialog` | [MarineAppsScreen.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineAppsScreen.kt#L34) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselObservation<Double>.liveNumber` | [MarineInstrumentGraphics.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L29) |
| `fun VesselObservation<Double>.displayNumber` | [MarineInstrumentGraphics.kt:30](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L30) |
| `fun MarineCompass` | [MarineInstrumentGraphics.kt:32](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L32) |
| `fun WindRose` | [MarineInstrumentGraphics.kt:75](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L75) |
| `fun AttitudeHorizon` | [MarineInstrumentGraphics.kt:119](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L119) |
| `fun InstrumentGauge` | [MarineInstrumentGraphics.kt:155](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L155) |
| `fun gaugeScaleNumber` | [MarineInstrumentGraphics.kt:270](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineInstrumentGraphics.kt#L270) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MapPageHeader` | [MarineMapControls.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L21) |
| `fun MapCrosshairReadout` | [MarineMapControls.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L37) |
| `fun MapZoomControls` | [MarineMapControls.kt:47](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L47) |
| `fun MapPositionReadout` | [MarineMapControls.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L59) |
| `fun age` | [MarineMapControls.kt:62](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MarineMapControls.kt#L62) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MetroColors` | [Metro.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L60) |
| `class ShellHorizontalInsets` | [Metro.kt:66](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L66) |
| `fun shellHorizontalInsets` | [Metro.kt:69](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L69) |
| `fun safe` | [Metro.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L74) |
| `fun AppBackHandler` | [Metro.kt:81](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L81) |
| `fun MetroTheme` | [Metro.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L87) |
| `fun Label` | [Metro.kt:97](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L97) |
| `fun AppSection` | [Metro.kt:105](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L105) |
| `fun AppDialogTitle` | [Metro.kt:111](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L111) |
| `fun Glyph` | [Metro.kt:114](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L114) |
| `fun point` | [Metro.kt:117](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L117) |
| `fun line` | [Metro.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L118) |
| `fun circle` | [Metro.kt:119](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L119) |
| `fun IconAction` | [Metro.kt:165](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L165) |
| `fun MetroButton` | [Metro.kt:178](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L178) |
| `fun PageHeader` | [Metro.kt:190](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L190) |
| `fun HeaderBackButton` | [Metro.kt:207](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L207) |
| `fun PageBody` | [Metro.kt:219](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L219) |
| `fun MenuRow` | [Metro.kt:223](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L223) |
| `fun Field` | [Metro.kt:239](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L239) |
| `fun Toggle` | [Metro.kt:253](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L253) |
| `fun ChoiceRow` | [Metro.kt:270](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L270) |
| `fun Pivot` | [Metro.kt:284](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L284) |
| `fun PivotHeaders` | [Metro.kt:303](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L303) |
| `fun MetroProgress` | [Metro.kt:362](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L362) |
| `fun TextDialog` | [Metro.kt:378](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Metro.kt#L378) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun PlaceKind.label` | [MySailingExperience.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L17) |
| `fun PlacesScreen` | [MySailingExperience.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L22) |
| `fun apply` | [MySailingExperience.kt:114](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L114) |
| `fun PlaceScreen` | [MySailingExperience.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L122) |
| `fun CoordinateMapPreview` | [MySailingExperience.kt:175](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L175) |
| `fun CoordinateEditor` | [MySailingExperience.kt:180](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L180) |
| `fun PlaceSaveFeedback` | [MySailingExperience.kt:208](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L208) |
| `fun MissingSailingObject` | [MySailingExperience.kt:224](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/MySailingExperience.kt#L224) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun chartRoute` | [Navigation.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L35) |
| `fun chartIsNavigating` | [Navigation.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L37) |
| `fun resolveChartRoute` | [Navigation.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L39) |
| `fun cancelRouteDraft` | [Navigation.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L42) |
| `fun resumeOrCreateRouteDraft` | [Navigation.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L46) |
| `class RouteGuidance` | [Navigation.kt:52](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L52) |
| `fun routeGuidance` | [Navigation.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L59) |
| `fun currentRouteGuidance` | [Navigation.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L74) |
| `fun navigationFailure` | [Navigation.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L76) |
| `fun submit` | [Navigation.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L92) |
| `fun retry` | [Navigation.kt:101](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L101) |
| `fun beginNavigation` | [Navigation.kt:118](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L118) |
| `fun endNavigation` | [Navigation.kt:126](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L126) |
| `fun liveNavigationFix` | [Navigation.kt:141](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L141) |
| `fun offsetLabel` | [Navigation.kt:147](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L147) |
| `fun RouteSketch` | [Navigation.kt:156](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L156) |
| `fun at` | [Navigation.kt:175](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L175) |
| `fun StartNavigationDialog` | [Navigation.kt:263](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L263) |
| `fun ChartNavigationCard` | [Navigation.kt:322](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L322) |
| `fun NavigationActionsDialog` | [Navigation.kt:374](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L374) |
| `fun command` | [Navigation.kt:378](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L378) |
| `fun ExternalNavigationDialog` | [Navigation.kt:453](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L453) |
| `fun command` | [Navigation.kt:457](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/Navigation.kt#L457) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NavigationSpatialView` | [NavigationSpatialView.kt:43](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt#L43) |
| `fun tr` | [NavigationSpatialView.kt:55](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt#L55) |
| `fun rememberNavigationResumed` | [NavigationSpatialView.kt:226](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt#L226) |
| `fun NavigationLiftPreference` | [NavigationSpatialView.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt#L238) |
| `fun NavigationLiftObserver` | [NavigationSpatialView.kt:250](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationSpatialView.kt#L250) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileOverviewReading` | [NavigationTileRendering.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L23) |
| `class TileOverviewFrame` | [NavigationTileRendering.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L24) |
| `fun navigationOverviewTileFrame` | [NavigationTileRendering.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L41) |
| `fun at` | [NavigationTileRendering.kt:48](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L48) |
| `fun reading` | [NavigationTileRendering.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L49) |
| `fun NavigationTileContent` | [NavigationTileRendering.kt:92](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L92) |
| `fun CompositeOverviewPanel` | [NavigationTileRendering.kt:181](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NavigationTileRendering.kt#L181) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaFlowScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NmeaScreen` | [NmeaFlowScreen.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaFlowScreen.kt#L20) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NmeaPublicationEditor` | [NmeaPublicationEditor.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L22) |
| `fun PublicationChoice` | [NmeaPublicationEditor.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L84) |
| `fun publicationFeedName` | [NmeaPublicationEditor.kt:88](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaPublicationEditor.kt#L88) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselSourceSettings` | [NmeaSourcesExperience.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L13) |
| `fun ColumnScope.SourceOverview` | [NmeaSourcesExperience.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L17) |
| `fun ColumnScope.SourceMetricDetail` | [NmeaSourcesExperience.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L57) |
| `fun sourceDisplayName` | [NmeaSourcesExperience.kt:143](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L143) |
| `fun sourceCandidateText` | [NmeaSourcesExperience.kt:159](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L159) |
| `fun sourceObservationText` | [NmeaSourcesExperience.kt:164](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L164) |
| `fun sourceValueText` | [NmeaSourcesExperience.kt:169](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L169) |
| `fun sourceObservation` | [NmeaSourcesExperience.kt:189](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L189) |
| `fun sourceMetricName` | [NmeaSourcesExperience.kt:209](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NmeaSourcesExperience.kt#L209) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationBrightness.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ApplicationBrightness` | [NotificationBrightness.kt:28](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationBrightness.kt#L28) |
| `fun NotificationBrightnessControl` | [NotificationBrightness.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationBrightness.kt#L35) |
| `fun change` | [NotificationBrightness.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationBrightness.kt#L45) |
| `fun save` | [NotificationBrightness.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationBrightness.kt#L46) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SystemStatusBar` | [NotificationCenter.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L58) |
| `fun NotificationCenter` | [NotificationCenter.kt:114](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L114) |
| `fun returnToRest` | [NotificationCenter.kt:383](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L383) |
| `fun dismiss` | [NotificationCenter.kt:392](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationCenter.kt#L392) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NotificationQuickActions` | [NotificationQuickActions.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L40) |
| `fun preferenceText` | [NotificationQuickActions.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L53) |
| `fun NotificationQuickDetail` | [NotificationQuickActions.kt:197](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L197) |
| `fun notificationReadingAge` | [NotificationQuickActions.kt:238](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L238) |
| `fun point` | [NotificationQuickActions.kt:253](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L253) |
| `fun line` | [NotificationQuickActions.kt:254](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationQuickActions.kt#L254) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun Modifier.shadeHandle` | [NotificationShadeGestures.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt#L17) |
| `fun Modifier.trackShadeTouches` | [NotificationShadeGestures.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt#L25) |
| `fun Modifier.shadeListScroll` | [NotificationShadeGestures.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt#L45) |
| `fun onPreScroll` | [NotificationShadeGestures.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt#L49) |
| `fun onPreFling` | [NotificationShadeGestures.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationShadeGestures.kt#L58) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationTasks.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun NotificationTaskCards` | [NotificationTasks.kt:24](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationTasks.kt#L24) |
| `fun AnchorPauseConfirmation` | [NotificationTasks.kt:188](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/NotificationTasks.kt#L188) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PageNavigation.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PageNavigation` | [PageNavigation.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PageNavigation.kt#L11) |
| `fun pageNavigation` | [PageNavigation.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PageNavigation.kt#L21) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun passagePlanningContext` | [PassagePlanningPanel.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L35) |
| `fun OsStore.planningRequest` | [PassagePlanningPanel.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L38) |
| `fun draftMatchesAnalysis` | [PassagePlanningPanel.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L44) |
| `fun draftCalculationBusy` | [PassagePlanningPanel.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L60) |
| `fun requestDraftCalculation` | [PassagePlanningPanel.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L64) |
| `fun DraftPassageAnalysis` | [PassagePlanningPanel.kt:91](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L91) |
| `fun draftVerdict` | [PassagePlanningPanel.kt:119](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L119) |
| `fun PassagePlanningPanel` | [PassagePlanningPanel.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L122) |
| `fun candidateUsable` | [PassagePlanningPanel.kt:151](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L151) |
| `fun candidateCanReplaceNavigation` | [PassagePlanningPanel.kt:163](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L163) |
| `fun persistDraft` | [PassagePlanningPanel.kt:208](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L208) |
| `fun locate` | [PassagePlanningPanel.kt:219](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L219) |
| `fun x` | [PassagePlanningPanel.kt:360](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L360) |
| `fun y` | [PassagePlanningPanel.kt:361](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassagePlanningPanel.kt#L361) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassageReviewDialog.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun PassageReviewDialog` | [PassageReviewDialog.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PassageReviewDialog.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RouteScreen` | [PlacesScreen.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L17) |
| `fun preview` | [PlacesScreen.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L36) |
| `fun edit` | [PlacesScreen.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/PlacesScreen.kt#L44) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ReadingTrace` | [ReadingTrace.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L39) |
| `fun clockLabel` | [ReadingTrace.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L60) |
| `fun number` | [ReadingTrace.kt:253](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L253) |
| `fun actual` | [ReadingTrace.kt:257](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L257) |
| `fun historyTimeLabel` | [ReadingTrace.kt:277](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L277) |
| `fun readingsAreContinuous` | [ReadingTrace.kt:285](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/ReadingTrace.kt#L285) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftControls.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RouteDraftControls` | [RouteDraftControls.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftControls.kt#L11) |
| `fun snapshot` | [RouteDraftControls.kt:21](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftControls.kt#L21) |
| `fun requestLoad` | [RouteDraftControls.kt:22](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftControls.kt#L22) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftDialogs.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RouteDraftSaveDialog` | [RouteDraftDialogs.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftDialogs.kt#L14) |
| `fun RouteDraftWaypoints` | [RouteDraftDialogs.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftDialogs.kt#L45) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun leaveRouteDraft` | [RouteDraftState.kt:6](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L6) |
| `fun discardRouteDraft` | [RouteDraftState.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L10) |
| `fun loadRouteDraft` | [RouteDraftState.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L16) |
| `fun routeDraftControlIndices` | [RouteDraftState.kt:27](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L27) |
| `fun routeDraftControlPoints` | [RouteDraftState.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L33) |
| `fun appendRouteDraftControlPoint` | [RouteDraftState.kt:36](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L36) |
| `fun removeLastRouteDraftControlPoint` | [RouteDraftState.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L46) |
| `fun moveRouteDraftPoint` | [RouteDraftState.kt:67](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L67) |
| `fun insertRouteDraftControlPoint` | [RouteDraftState.kt:79](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L79) |
| `fun deleteRouteDraftPoint` | [RouteDraftState.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftState.kt#L94) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftSummary.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RouteDraftSummary` | [RouteDraftSummary.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RouteDraftSummary.kt#L13) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RoutePreviewCard.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RoutePreviewCard` | [RoutePreviewCard.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RoutePreviewCard.kt#L14) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RuntimeExitSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun RuntimeExitSettings` | [RuntimeExitSettings.kt:16](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RuntimeExitSettings.kt#L16) |
| `fun saveAndExit` | [RuntimeExitSettings.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/RuntimeExitSettings.kt#L26) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SavedLocationScreen` | [SavedLocationExperience.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L25) |
| `fun mutate` | [SavedLocationExperience.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L45) |
| `fun valid` | [SavedLocationExperience.kt:167](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L167) |
| `fun CollectionScreen` | [SavedLocationExperience.kt:196](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SavedLocationExperience.kt#L196) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SettingsScreen` | [SettingsScreen.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L49) |
| `fun title` | [SettingsScreen.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L58) |
| `fun open` | [SettingsScreen.kt:166](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SettingsScreen.kt#L166) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartBackgroundSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rememberStartBackdrop` | [StartBackgroundSettings.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartBackgroundSettings.kt#L35) |
| `fun number` | [StartBackgroundSettings.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartBackgroundSettings.kt#L51) |
| `fun StartBackgroundSettings` | [StartBackgroundSettings.kt:59](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartBackgroundSettings.kt#L59) |
| `fun choosePhoto` | [StartBackgroundSettings.kt:71](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartBackgroundSettings.kt#L71) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartColumnsSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun StartColumnsSettings` | [StartColumnsSettings.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StartColumnsSettings.kt#L20) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun StructuredChartLibraryPane` | [StructuredChartLibrary.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L34) |
| `fun LibraryDatasetScreen` | [StructuredChartLibrary.kt:99](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L99) |
| `fun feedback` | [StructuredChartLibrary.kt:115](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L115) |
| `fun perform` | [StructuredChartLibrary.kt:121](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L121) |
| `fun moveCell` | [StructuredChartLibrary.kt:174](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L174) |
| `fun chartUseLabel` | [StructuredChartLibrary.kt:401](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L401) |
| `fun chartDataError` | [StructuredChartLibrary.kt:408](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/StructuredChartLibrary.kt#L408) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun SystemMarineAlerts` | [SystemAlerts.kt:20](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L20) |
| `class Alert` | [SystemAlerts.kt:82](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L82) |
| `fun buildAlerts` | [SystemAlerts.kt:83](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L83) |
| `fun tr` | [SystemAlerts.kt:84](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/SystemAlerts.kt#L84) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun TaskCaptureHost` | [TaskSwitching.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L44) |
| `fun AppLaunchCover` | [TaskSwitching.kt:58](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L58) |
| `fun TaskSwitcher` | [TaskSwitching.kt:74](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L74) |
| `fun closeCard` | [TaskSwitching.kt:103](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TaskSwitching.kt#L103) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentBindings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun currentTaskTileBinding` | [TileContentBindings.kt:7](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentBindings.kt#L7) |
| `fun savedPlaceTileBinding` | [TileContentBindings.kt:8](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentBindings.kt#L8) |
| `fun savedRouteTileBinding` | [TileContentBindings.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentBindings.kt#L9) |
| `fun aisOverviewTileBinding` | [TileContentBindings.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentBindings.kt#L10) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileContentGroup` | [TileContentCatalog.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L9) |
| `class TileContentStyle` | [TileContentCatalog.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L10) |
| `class TileContentChoice` | [TileContentCatalog.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L11) |
| `fun tileReadingBinding` | [TileContentCatalog.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L37) |
| `fun tileBinding` | [TileContentCatalog.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L38) |
| `fun reading` | [TileContentCatalog.kt:42](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L42) |
| `fun task` | [TileContentCatalog.kt:77](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L77) |
| `fun instruments` | [TileContentCatalog.kt:80](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L80) |
| `fun tileContentChoices` | [TileContentCatalog.kt:115](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L115) |
| `fun tileContentDescriptor` | [TileContentCatalog.kt:129](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L129) |
| `fun tileCompositePanelChoices` | [TileContentCatalog.kt:161](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L161) |
| `fun tileCompositePanelIds` | [TileContentCatalog.kt:165](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileContentCatalog.kt#L165) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileDestinations.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun CurrentNavigationTileDestination` | [TileDestinations.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileDestinations.kt#L11) |
| `fun SavedTileDestination` | [TileDestinations.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileDestinations.kt#L41) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileEditorExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun TileEditorHost` | [TileEditorExperience.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileEditorExperience.kt#L60) |
| `fun TileWorkshopFeedbackHost` | [TileEditorExperience.kt:315](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileEditorExperience.kt#L315) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun TileLibraryScreen` | [TileLibraryExperience.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L34) |
| `fun TileContentPicker` | [TileLibraryExperience.kt:85](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L85) |
| `fun TileIdentityIcon` | [TileLibraryExperience.kt:194](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L194) |
| `fun p` | [TileLibraryExperience.kt:200](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L200) |
| `fun line` | [TileLibraryExperience.kt:201](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L201) |
| `fun tileText` | [TileLibraryExperience.kt:230](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L230) |
| `fun tileSizeName` | [TileLibraryExperience.kt:231](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileLibraryExperience.kt#L231) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun tileInstrumentId` | [TileMetricConfiguration.kt:10](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt#L10) |
| `fun tileHasHistory` | [TileMetricConfiguration.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt#L11) |
| `fun tileHistoryMinutes` | [TileMetricConfiguration.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt#L12) |
| `fun tileDefaultRange` | [TileMetricConfiguration.kt:15](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt#L15) |
| `fun TileMetricConfiguration` | [TileMetricConfiguration.kt:40](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricConfiguration.kt#L40) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileMetricGraphicKind` | [TileMetricGraphics.kt:34](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L34) |
| `class TileMetricGraphic` | [TileMetricGraphics.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L35) |
| `class ReadingTileContent` | [TileMetricGraphics.kt:49](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L49) |
| `fun ReadingTileFace` | [TileMetricGraphics.kt:53](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L53) |
| `fun TileMetricGraphicView` | [TileMetricGraphics.kt:130](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L130) |
| `fun radial` | [TileMetricGraphics.kt:149](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileMetricGraphics.kt#L149) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentationState.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun tilePresentationActive` | [TilePresentationState.kt:23](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentationState.kt#L23) |
| `fun <T> activeTileValue` | [TilePresentationState.kt:35](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentationState.kt#L35) |
| `fun tileElapsed` | [TilePresentationState.kt:41](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentationState.kt#L41) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TilePreset` | [TilePresentations.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L64) |
| `fun tilePresets` | [TilePresentations.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L76) |
| `fun preset` | [TilePresentations.kt:78](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L78) |
| `class TileMode` | [TilePresentations.kt:93](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L93) |
| `fun tileModes` | [TilePresentations.kt:94](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L94) |
| `fun mode` | [TilePresentations.kt:95](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L95) |
| `fun tilePreferenceContributions` | [TilePresentations.kt:112](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L112) |
| `fun tilePresentation` | [TilePresentations.kt:121](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L121) |
| `fun presetTilePresentation` | [TilePresentations.kt:122](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L122) |
| `class TileFrame` | [TilePresentations.kt:125](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L125) |
| `fun LegacyInstanceTile` | [TilePresentations.kt:145](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L145) |
| `fun reading` | [TilePresentations.kt:218](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L218) |
| `fun stamp` | [TilePresentations.kt:219](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L219) |
| `fun historyCaption` | [TilePresentations.kt:228](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L228) |
| `fun TileFace` | [TilePresentations.kt:334](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TilePresentations.kt#L334) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileReadingDisplayDemand.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun tileReadingDisplayDemand` | [TileReadingDisplayDemand.kt:14](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileReadingDisplayDemand.kt#L14) |
| `fun tileInstrumentDisplayDemand` | [TileReadingDisplayDemand.kt:29](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileReadingDisplayDemand.kt#L29) |
| `fun close` | [TileReadingDisplayDemand.kt:45](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileReadingDisplayDemand.kt#L45) |
| `fun reconcile` | [TileReadingDisplayDemand.kt:46](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TileReadingDisplayDemand.kt#L46) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LogbookScreen` | [TripExperience.kt:38](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt#L38) |
| `fun coords` | [TripExperience.kt:320](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/TripExperience.kt#L320) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt

| 声明 | 实现位置 |
| --- | --- |
| `class UnitNumberDraft internal constructor` | [UnitNumberDraft.kt:19](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L19) |
| `fun edit` | [UnitNumberDraft.kt:33](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L33) |
| `fun setCanonical` | [UnitNumberDraft.kt:39](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L39) |
| `fun useUnit` | [UnitNumberDraft.kt:44](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L44) |
| `fun saved` | [UnitNumberDraft.kt:54](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L54) |
| `fun rememberUnitNumberDraft` | [UnitNumberDraft.kt:64](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L64) |
| `fun rememberUnitNumberDraft` | [UnitNumberDraft.kt:73](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitNumberDraft.kt#L73) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ColumnScope.UnitSettings` | [UnitSettings.kt:13](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitSettings.kt#L13) |
| `fun distanceName` | [UnitSettings.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitSettings.kt#L17) |
| `fun speedName` | [UnitSettings.kt:25](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/UnitSettings.kt#L25) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rememberVesselScene` | [VesselDataDetail.kt:37](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L37) |
| `fun vesselHotspotTitle` | [VesselDataDetail.kt:50](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L50) |
| `fun vesselMetricValue` | [VesselDataDetail.kt:57](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L57) |
| `fun vesselStatusText` | [VesselDataDetail.kt:60](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L60) |
| `fun VesselWindDetail` | [VesselDataDetail.kt:76](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L76) |
| `fun ColumnScope.VesselMetricSummary` | [VesselDataDetail.kt:135](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L135) |
| `fun ColumnScope.VesselSourceActions` | [VesselDataDetail.kt:233](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L233) |
| `fun vesselReferenceText` | [VesselDataDetail.kt:297](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselDataDetail.kt#L297) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselHeadingSelection.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun preferredHeadingMetric` | [VesselHeadingSelection.kt:11](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselHeadingSelection.kt#L11) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselOverview.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ColumnScope.MyVesselOverview` | [VesselOverview.kt:26](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselOverview.kt#L26) |
| `fun p` | [VesselOverview.kt:142](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselOverview.kt#L142) |
| `fun polygon` | [VesselOverview.kt:146](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselOverview.kt#L146) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselProfileSettings.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselProfileSettings` | [VesselProfileSettings.kt:18](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselProfileSettings.kt#L18) |
| `fun edit` | [VesselProfileSettings.kt:51](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselProfileSettings.kt#L51) |
| `fun awaitWrite` | [VesselProfileSettings.kt:87](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VesselProfileSettings.kt#L87) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VisibleAppRoute.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun ReportVisibleAppRoute` | [VisibleAppRoute.kt:9](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VisibleAppRoute.kt#L9) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageCommandFeedback.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun mayFinishVoyage` | [VoyageCommandFeedback.kt:8](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageCommandFeedback.kt#L8) |
| `fun VoyageCommandFeedback` | [VoyageCommandFeedback.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageCommandFeedback.kt#L12) |

## app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageShareActions.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VoyageShareActions` | [VoyageShareActions.kt:12](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageShareActions.kt#L12) |
| `fun export` | [VoyageShareActions.kt:17](../../app-shell/src/rebuild/java/com/yokuli/marine/shell/rebuild/ui/VoyageShareActions.kt#L17) |

## core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartPackageManifest` | [YokuliChartPackage.kt:23](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L23) |
| `class ChartPackageFile` | [YokuliChartPackage.kt:46](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L46) |
| `class ChartPackageException` | [YokuliChartPackage.kt:60](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L60) |
| `object YokuliChartPackage` | [YokuliChartPackage.kt:63](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L63) |
| `fun readManifest` | [YokuliChartPackage.kt:77](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L77) |
| `fun extract` | [YokuliChartPackage.kt:85](../../core/chart-package/src/main/kotlin/com/yokuli/chartpackage/YokuliChartPackage.kt#L85) |

## core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt

| 声明 | 实现位置 |
| --- | --- |
| `class StartBackdropMode` | [StartBackdrop.kt:28](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L28) |
| `class StartBackdrop` | [StartBackdrop.kt:29](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L29) |
| `fun startTileForeground` | [StartBackdrop.kt:54](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L54) |
| `fun LauncherWallpaperSurface` | [StartBackdrop.kt:61](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L61) |
| `fun StartWallpaperSurface` | [StartBackdrop.kt:71](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L71) |
| `fun Modifier.startTileBackground` | [StartBackdrop.kt:121](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L121) |
| `fun StartTilePreviewSurface` | [StartBackdrop.kt:149](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L149) |
| `class StartBackdropPlacement` | [StartBackdrop.kt:176](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L176) |
| `fun overflow` | [StartBackdrop.kt:177](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L177) |
| `fun startBackdropPlacement` | [StartBackdrop.kt:179](../../core/design/src/main/java/com/yokuli/marine/core/design/StartBackdrop.kt#L179) |

## core/design/src/main/java/com/yokuli/marine/core/design/W10MobileControls.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun W10ToggleIndicator` | [W10MobileControls.kt:32](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileControls.kt#L32) |
| `fun W10RadioIndicator` | [W10MobileControls.kt:51](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileControls.kt#L51) |
| `fun W10CheckIndicator` | [W10MobileControls.kt:63](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileControls.kt#L63) |
| `fun W10ProgressRing` | [W10MobileControls.kt:86](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileControls.kt#L86) |

## core/design/src/main/java/com/yokuli/marine/core/design/W10MobileDesign.kt

| 声明 | 实现位置 |
| --- | --- |
| `object W10MobileMetrics` | [W10MobileDesign.kt:7](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileDesign.kt#L7) |
| `object W10MobileMotion` | [W10MobileDesign.kt:24](../../core/design/src/main/java/com/yokuli/marine/core/design/W10MobileDesign.kt#L24) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpDisplayPreferences.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MarineDisplayUnits` | [WpDisplayPreferences.kt:13](../../core/design/src/main/java/com/yokuli/marine/core/design/WpDisplayPreferences.kt#L13) |
| `fun distanceFromNauticalMiles` | [WpDisplayPreferences.kt:16](../../core/design/src/main/java/com/yokuli/marine/core/design/WpDisplayPreferences.kt#L16) |
| `fun distanceFromMeters` | [WpDisplayPreferences.kt:21](../../core/design/src/main/java/com/yokuli/marine/core/design/WpDisplayPreferences.kt#L21) |
| `fun speedFromKnots` | [WpDisplayPreferences.kt:26](../../core/design/src/main/java/com/yokuli/marine/core/design/WpDisplayPreferences.kt#L26) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PresentationCadence` | [WpLive.kt:31](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L31) |
| `fun interface PresentationClock` | [WpLive.kt:45](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L45) |
| `fun nowMillis` | [WpLive.kt:46](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L46) |
| `class PresentationChange` | [WpLive.kt:49](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L49) |
| `interface PresentationDecision<out T>` | [WpLive.kt:60](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L60) |
| `class Presented<T>` | [WpLive.kt:61](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L61) |
| `class Deferred` | [WpLive.kt:62](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L62) |
| `object Unchanged : PresentationDecision<Nothing>` | [WpLive.kt:63](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L63) |
| `class CadencedPresentation<T>` | [WpLive.kt:70](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L70) |
| `fun submit` | [WpLive.kt:84](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L84) |
| `fun flush` | [WpLive.kt:96](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L96) |
| `class LatestWinsBatchBuffer<T>` | [WpLive.kt:120](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L120) |
| `fun submit` | [WpLive.kt:132](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L132) |
| `fun flush` | [WpLive.kt:137](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L137) |
| `fun <T> rememberCadencedLiveValue` | [WpLive.kt:145](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L145) |
| `fun WpLiveField` | [WpLive.kt:164](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L164) |
| `fun WpLiveConsole` | [WpLive.kt:201](../../core/design/src/main/java/com/yokuli/marine/core/design/WpLive.kt#L201) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpMotion.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun <T> WpSurfaceTransitionHost` | [WpMotion.kt:43](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotion.kt#L43) |
| `fun Modifier.wpEntrance` | [WpMotion.kt:132](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotion.kt#L132) |
| `fun Modifier.wpTilt` | [WpMotion.kt:157](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotion.kt#L157) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class WpSurfaceTransitionKind` | [WpMotionContract.kt:6](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L6) |
| `class WpMotionFamily` | [WpMotionContract.kt:24](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L24) |
| `class WpMotionEvidence` | [WpMotionContract.kt:26](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L26) |
| `class WpMotionPlan` | [WpMotionContract.kt:34](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L34) |
| `class WpMotionTimings` | [WpMotionContract.kt:62](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L62) |
| `class WpPressPlan` | [WpMotionContract.kt:72](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L72) |
| `object WpPressPolicy` | [WpMotionContract.kt:78](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L78) |
| `fun resolve` | [WpMotionContract.kt:79](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L79) |
| `object WpMotionPolicy` | [WpMotionContract.kt:99](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L99) |
| `fun resolve` | [WpMotionContract.kt:100](../../core/design/src/main/java/com/yokuli/marine/core/design/WpMotionContract.kt#L100) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun WpText` | [WpPrimitives.kt:34](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L34) |
| `fun WpPageHeader` | [WpPrimitives.kt:48](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L48) |
| `class WpAppBarAction` | [WpPrimitives.kt:61](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L61) |
| `fun WpApplicationBar` | [WpPrimitives.kt:68](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L68) |
| `fun WpCircleButton` | [WpPrimitives.kt:102](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L102) |
| `fun line` | [WpPrimitives.kt:127](../../core/design/src/main/java/com/yokuli/marine/core/design/WpPrimitives.kt#L127) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt

| 声明 | 实现位置 |
| --- | --- |
| `class WpThemeMode` | [WpTheme.kt:10](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L10) |
| `class WpAccent` | [WpTheme.kt:12](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L12) |
| `class WpThemeSpec` | [WpTheme.kt:16](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L16) |
| `class WpColorScheme` | [WpTheme.kt:19](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L19) |
| `object WpThemePolicy` | [WpTheme.kt:30](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L30) |
| `fun resolve` | [WpTheme.kt:32](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L32) |
| `fun foregroundFraction` | [WpTheme.kt:37](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L37) |
| `fun YokuliTheme` | [WpTheme.kt:56](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTheme.kt#L56) |

## core/design/src/main/java/com/yokuli/marine/core/design/WpTypography.kt

| 声明 | 实现位置 |
| --- | --- |
| `object WpTypeScale` | [WpTypography.kt:22](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTypography.kt#L22) |
| `fun lineHeight` | [WpTypography.kt:36](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTypography.kt#L36) |
| `fun weight` | [WpTypography.kt:46](../../core/design/src/main/java/com/yokuli/marine/core/design/WpTypography.kt#L46) |

## core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt

| 声明 | 实现位置 |
| --- | --- |
| `object YokuliBrandColors` | [YokuliBrand.kt:26](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt#L26) |
| `fun YokuliBrandMark` | [YokuliBrand.kt:46](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt#L46) |
| `fun YokuliBrandWordmark` | [YokuliBrand.kt:60](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt#L60) |
| `fun YokuliBrandSignature` | [YokuliBrand.kt:71](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt#L71) |
| `fun YokuliBrandArrival` | [YokuliBrand.kt:91](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliBrand.kt#L91) |

## core/design/src/main/java/com/yokuli/marine/core/design/YokuliDesign.kt

| 声明 | 实现位置 |
| --- | --- |
| `object YokuliColors` | [YokuliDesign.kt:6](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliDesign.kt#L6) |
| `object YokuliMetrics` | [YokuliDesign.kt:24](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliDesign.kt#L24) |

## core/design/src/main/java/com/yokuli/marine/core/design/YokuliPixelGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BrandPixel` | [YokuliPixelGeometry.kt:4](../../core/design/src/main/java/com/yokuli/marine/core/design/YokuliPixelGeometry.kt#L4) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorCommandType` | [AnchorCommands.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L6) |
| `class AnchorCommandStatus` | [AnchorCommands.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L7) |
| `class AnchorCommandSnapshot` | [AnchorCommands.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L14) |
| `interface AnchorCommandMonitor` | [AnchorCommands.kt:30](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L30) |
| `fun recheck` | [AnchorCommands.kt:33](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L33) |
| `fun acknowledgeResult` | [AnchorCommands.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/AnchorCommands.kt#L34) |

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
| `class VoyageRequestStatus` | [RuntimeContract.kt:56](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L56) |
| `class VoyageRequest` | [RuntimeContract.kt:57](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L57) |
| `class VoyageCommandReceipt` | [RuntimeContract.kt:64](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L64) |
| `interface VoyageSessionService` | [RuntimeContract.kt:77](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L77) |
| `fun snapshot` | [RuntimeContract.kt:80](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L80) |
| `fun receipt` | [RuntimeContract.kt:85](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L85) |
| `fun request` | [RuntimeContract.kt:86](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L86) |
| `fun recheck` | [RuntimeContract.kt:88](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L88) |
| `fun start` | [RuntimeContract.kt:89](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L89) |
| `fun pause` | [RuntimeContract.kt:90](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L90) |
| `fun resume` | [RuntimeContract.kt:91](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L91) |
| `fun finish` | [RuntimeContract.kt:92](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeContract.kt#L92) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimePresentationService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeUnitPreferences` | [RuntimePresentationService.kt:4](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimePresentationService.kt#L4) |
| `interface RuntimePresentationService` | [RuntimePresentationService.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimePresentationService.kt#L16) |
| `fun updateUnits` | [RuntimePresentationService.kt:18](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimePresentationService.kt#L18) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeResidencyPhase` | [RuntimeResidency.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L6) |
| `class RuntimeResidencyState` | [RuntimeResidency.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L7) |
| `class RuntimeExitResult` | [RuntimeResidency.kt:27](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L27) |
| `interface RuntimeResidencyService` | [RuntimeResidency.kt:28](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L28) |
| `fun startFromForeground` | [RuntimeResidency.kt:31](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L31) |
| `fun retryRecovery` | [RuntimeResidency.kt:33](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L33) |
| `fun exit` | [RuntimeResidency.kt:35](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/RuntimeResidency.kt#L35) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface AisTrafficService` | [AisContract.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L6) |
| `fun command` | [AisContract.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L8) |
| `class AisPoint` | [AisContract.kt:11](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L11) |
| `class AisSource` | [AisContract.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L13) |
| `class AisFrame` | [AisContract.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L16) |
| `class AisEntityKind` | [AisContract.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L17) |
| `class AisDistressState` | [AisContract.kt:18](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L18) |
| `class AisTargetState` | [AisContract.kt:19](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L19) |
| `class AisView` | [AisContract.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L20) |
| `class AisOrientation` | [AisContract.kt:21](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L21) |
| `class AisInputState` | [AisContract.kt:22](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L22) |
| `class AisCpaState` | [AisContract.kt:23](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L23) |
| `class AisRiskKind` | [AisContract.kt:24](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L24) |
| `class AisRiskLevel` | [AisContract.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L25) |
| `class AisApproachTrend` | [AisContract.kt:26](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L26) |
| `class AisDimensions` | [AisContract.kt:29](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L29) |
| `class AisStaticValue<T>` | [AisContract.kt:35](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L35) |
| `class AisStaticData` | [AisContract.kt:36](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L36) |
| `class AisDynamicReport` | [AisContract.kt:50](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L50) |
| `class AisTrackPoint` | [AisContract.kt:69](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L69) |
| `class AisSafetyMessage` | [AisContract.kt:70](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L70) |
| `class AisRawMessage` | [AisContract.kt:71](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L71) |
| `class AisOwnship` | [AisContract.kt:73](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L73) |
| `class AisRelativeMetrics` | [AisContract.kt:80](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L80) |
| `class AisRiskEvent` | [AisContract.kt:89](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L89) |
| `class AisTarget` | [AisContract.kt:96](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L96) |
| `class AisInputHealth` | [AisContract.kt:112](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L112) |
| `class AisPreferences` | [AisContract.kt:119](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L119) |
| `class TrafficSnapshot` | [AisContract.kt:132](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L132) |
| `class AisOwnReport` | [AisContract.kt:140](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L140) |
| `class AisCachedTarget` | [AisContract.kt:142](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L142) |
| `interface AisCommand` | [AisContract.kt:143](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L143) |
| `class UpdatePreferences` | [AisContract.kt:146](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L146) |
| `class Watch` | [AisContract.kt:147](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L147) |
| `class Alias` | [AisContract.kt:148](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L148) |
| `class Acknowledge` | [AisContract.kt:149](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L149) |
| `class Snooze` | [AisContract.kt:150](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L150) |
| `class RetainTarget` | [AisContract.kt:151](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L151) |
| `class AisCommandResult` | [AisContract.kt:153](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L153) |
| `class AisRuntimeStatus` | [AisContract.kt:156](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L156) |
| `class AisNotice` | [AisContract.kt:163](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisContract.kt#L163) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DecodedAis` | [AisDecoder.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L6) |
| `interface AisDecodeResult` | [AisDecoder.kt:12](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L12) |
| `class Message` | [AisDecoder.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L13) |
| `class Rejected` | [AisDecoder.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L14) |
| `object Pending : AisDecodeResult` | [AisDecoder.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L15) |
| `object NotAis : AisDecodeResult` | [AisDecoder.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L16) |
| `class AisDecoder` | [AisDecoder.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L20) |
| `fun expire` | [AisDecoder.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L25) |
| `fun accept` | [AisDecoder.kt:32](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L32) |
| `fun ambiguous` | [AisDecoder.kt:98](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L98) |
| `fun <T> field` | [AisDecoder.kt:166](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L166) |
| `fun pos` | [AisDecoder.kt:171](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L171) |
| `fun speed` | [AisDecoder.kt:176](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L176) |
| `fun angle` | [AisDecoder.kt:182](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L182) |
| `fun dims` | [AisDecoder.kt:186](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L186) |
| `fun matches` | [AisDecoder.kt:230](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L230) |
| `fun u` | [AisDecoder.kt:253](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L253) |
| `fun s` | [AisDecoder.kt:259](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L259) |
| `fun flag` | [AisDecoder.kt:260](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L260) |
| `fun text` | [AisDecoder.kt:261](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisDecoder.kt#L261) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AisEngine` | [AisEngine.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L7) |
| `fun accept` | [AisEngine.kt:43](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L43) |
| `fun tick` | [AisEngine.kt:104](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L104) |
| `fun retain` | [AisEngine.kt:211](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L211) |
| `fun acknowledge` | [AisEngine.kt:217](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L217) |
| `fun snooze` | [AisEngine.kt:222](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L222) |
| `fun restoreAcknowledgements` | [AisEngine.kt:227](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L227) |
| `fun exportCache` | [AisEngine.kt:230](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L230) |
| `fun restoreCache` | [AisEngine.kt:231](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L231) |
| `fun <T> old` | [AisEngine.kt:234](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L234) |
| `fun active` | [AisEngine.kt:260](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisEngine.kt#L260) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt

| 声明 | 实现位置 |
| --- | --- |
| `object AisGeometry` | [AisRelativeMotion.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L6) |
| `fun valid` | [AisRelativeMotion.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L8) |
| `fun distanceMeters` | [AisRelativeMotion.kt:9](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L9) |
| `fun bearingDegrees` | [AisRelativeMotion.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L15) |
| `fun localMeters` | [AisRelativeMotion.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L20) |
| `fun offset` | [AisRelativeMotion.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L25) |
| `fun normalized` | [AisRelativeMotion.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L34) |
| `fun wrapped` | [AisRelativeMotion.kt:35](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L35) |
| `object AisAgePolicy` | [AisRelativeMotion.kt:39](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L39) |
| `fun expectedMillis` | [AisRelativeMotion.kt:40](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L40) |
| `fun currentMillis` | [AisRelativeMotion.kt:55](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L55) |
| `fun lostMillis` | [AisRelativeMotion.kt:56](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L56) |
| `fun motionMillis` | [AisRelativeMotion.kt:57](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L57) |
| `object AisRelativeMotion` | [AisRelativeMotion.kt:61](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L61) |
| `fun calculate` | [AisRelativeMotion.kt:62](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L62) |
| `fun velocity` | [AisRelativeMotion.kt:85](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/ais/AisRelativeMotion.kt#L85) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartPoint` | [ChartDataContract.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L6) |
| `class ChartBounds` | [ChartDataContract.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L8) |
| `fun split` | [ChartDataContract.kt:10](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L10) |
| `class ChartGeometryKind` | [ChartDataContract.kt:12](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L12) |
| `class ChartGeometryPart` | [ChartDataContract.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L14) |
| `class ChartGeometry` | [ChartDataContract.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L15) |
| `class NauticalFeatureKind` | [ChartDataContract.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L16) |
| `class DepthEvidenceKind` | [ChartDataContract.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L17) |
| `class DepthEvidence` | [ChartDataContract.kt:19](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L19) |
| `class ChartUse` | [ChartDataContract.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L20) |
| `class DataEligibility` | [ChartDataContract.kt:22](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L22) |
| `fun allowsAnalysis` | [ChartDataContract.kt:23](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L23) |
| `class ChartFeatureSource` | [ChartDataContract.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L25) |
| `class NauticalFeature` | [ChartDataContract.kt:26](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L26) |
| `class CoverageEvidence` | [ChartDataContract.kt:28](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L28) |
| `class ChartCellRevision` | [ChartDataContract.kt:30](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L30) |
| `class ChartDataset` | [ChartDataContract.kt:32](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L32) |
| `class ChartDataSnapshot` | [ChartDataContract.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L34) |
| `class ChartFeaturePage` | [ChartDataContract.kt:37](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L37) |
| `class ChartFeatureFilter` | [ChartDataContract.kt:39](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L39) |
| `class ChartImportPhase` | [ChartDataContract.kt:40](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L40) |
| `class ChartImportJob` | [ChartDataContract.kt:41](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L41) |
| `class ChartDataState` | [ChartDataContract.kt:42](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L42) |
| `class ChartImportRequest` | [ChartDataContract.kt:43](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L43) |
| `fun isBlockingChartIssue` | [ChartDataContract.kt:45](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L45) |
| `class ChartRasterWindow` | [ChartDataContract.kt:47](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L47) |
| `interface ChartCommandResult` | [ChartDataContract.kt:48](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L48) |
| `class Accepted` | [ChartDataContract.kt:49](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L49) |
| `class Saved` | [ChartDataContract.kt:50](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L50) |
| `class Failed` | [ChartDataContract.kt:51](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L51) |
| `object Busy:ChartCommandResult` | [ChartDataContract.kt:52](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L52) |
| `class LinzOnlineStatus` | [ChartDataContract.kt:57](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L57) |
| `interface ChartDataService` | [ChartDataContract.kt:60](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L60) |
| `fun importPackage` | [ChartDataContract.kt:62](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L62) |
| `fun retryImport` | [ChartDataContract.kt:63](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L63) |
| `fun cancelImport` | [ChartDataContract.kt:64](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L64) |
| `fun rename` | [ChartDataContract.kt:65](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L65) |
| `fun reorderCells` | [ChartDataContract.kt:67](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L67) |
| `fun updateEligibility` | [ChartDataContract.kt:68](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L68) |
| `fun remove` | [ChartDataContract.kt:69](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L69) |
| `fun acquireSnapshot` | [ChartDataContract.kt:71](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L71) |
| `fun query` | [ChartDataContract.kt:72](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L72) |
| `fun browse` | [ChartDataContract.kt:74](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L74) |
| `fun readFeature` | [ChartDataContract.kt:76](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L76) |
| `fun rasterWindows` | [ChartDataContract.kt:78](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L78) |
| `fun releaseSnapshot` | [ChartDataContract.kt:79](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L79) |
| `fun retryRestore` | [ChartDataContract.kt:80](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L80) |
| `fun configureLinz` | [ChartDataContract.kt:82](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L82) |
| `fun refreshLinz` | [ChartDataContract.kt:84](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartDataContract.kt#L84) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartPortrayal.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartDisplayCategory` | [ChartPortrayal.kt:4](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartPortrayal.kt#L4) |
| `class ChartColorMode` | [ChartPortrayal.kt:5](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartPortrayal.kt#L5) |
| `class ChartPortrayalPreferences` | [ChartPortrayal.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartPortrayal.kt#L6) |
| `fun normalized` | [ChartPortrayal.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/ChartPortrayal.kt#L20) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RasterBathymetryGrid` | [RasterBathymetry.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt#L6) |
| `fun pixelAt` | [RasterBathymetry.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt#L25) |
| `fun centre` | [RasterBathymetry.kt:32](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt#L32) |
| `class RasterBathymetryWindow` | [RasterBathymetry.kt:37](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt#L37) |
| `fun elevationAt` | [RasterBathymetry.kt:41](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/chart/RasterBathymetry.kt#L41) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DeviceBackend` | [DeviceRuntimeService.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L6) |
| `class DeviceKind` | [DeviceRuntimeService.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L7) |
| `class DeviceAvailability` | [DeviceRuntimeService.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L8) |
| `class DeviceHealth` | [DeviceRuntimeService.kt:11](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L11) |
| `class DeviceProvenance` | [DeviceRuntimeService.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L14) |
| `class MarineDeviceDescriptor` | [DeviceRuntimeService.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L25) |
| `class DeviceCatalogSnapshot` | [DeviceRuntimeService.kt:45](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L45) |
| `interface DeviceRuntimeService` | [DeviceRuntimeService.kt:55](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/device/DeviceRuntimeService.kt#L55) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object HardwareFaultPolicy` | [HardwareFaultPolicy.kt:4](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L4) |
| `fun allowed` | [HardwareFaultPolicy.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L8) |
| `fun magnitudeRange` | [HardwareFaultPolicy.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L16) |
| `fun magnitudeUnit` | [HardwareFaultPolicy.kt:27](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L27) |
| `fun defaultMagnitude` | [HardwareFaultPolicy.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L34) |
| `fun effectiveMagnitude` | [HardwareFaultPolicy.kt:45](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L45) |
| `fun requireValid` | [HardwareFaultPolicy.kt:46](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareFaultPolicy.kt#L46) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface HardwareLabService` | [HardwareLabService.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L6) |
| `fun execute` | [HardwareLabService.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L8) |
| `fun readRecording` | [HardwareLabService.kt:10](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L10) |
| `class HardwareMode` | [HardwareLabService.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L13) |
| `class LabAction` | [HardwareLabService.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L14) |
| `class HardwareLabCommand` | [HardwareLabService.kt:21](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L21) |
| `class HardwareLabResult` | [HardwareLabService.kt:35](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L35) |
| `class HardwareLabSnapshot` | [HardwareLabService.kt:37](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L37) |
| `class LabDevice` | [HardwareLabService.kt:60](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L60) |
| `class HardwareScenario` | [HardwareLabService.kt:63](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L63) |
| `class ScenarioPoint` | [HardwareLabService.kt:84](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L84) |
| `class ScenarioAisTarget` | [HardwareLabService.kt:85](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L85) |
| `class ScenarioAction` | [HardwareLabService.kt:87](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L87) |
| `class ScenarioEvent` | [HardwareLabService.kt:88](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L88) |
| `class DeviceFault` | [HardwareLabService.kt:92](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L92) |
| `class HardwareFault` | [HardwareLabService.kt:93](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L93) |
| `class HardwarePower` | [HardwareLabService.kt:94](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L94) |
| `class StorageFault` | [HardwareLabService.kt:96](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L96) |
| `class HardwareStorage` | [HardwareLabService.kt:97](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L97) |
| `class HardwareRecording` | [HardwareLabService.kt:98](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L98) |
| `class RecordingChunk` | [HardwareLabService.kt:100](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/HardwareLabService.kt#L100) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt

| 声明 | 实现位置 |
| --- | --- |
| `class HardwareSensorType` | [MarineDeviceBus.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L14) |
| `class HardwarePayload` | [MarineDeviceBus.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L17) |
| `fun isValid` | [MarineDeviceBus.kt:34](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L34) |
| `class HardwareDeviceSpec` | [MarineDeviceBus.kt:46](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L46) |
| `class HardwareDevice` | [MarineDeviceBus.kt:55](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L55) |
| `class HardwareBusSnapshot` | [MarineDeviceBus.kt:64](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L64) |
| `class HardwareFrame` | [MarineDeviceBus.kt:71](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L71) |
| `object MarineDeviceBus` | [MarineDeviceBus.kt:88](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L88) |
| `fun subscribe` | [MarineDeviceBus.kt:104](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L104) |
| `fun onBackendChanged` | [MarineDeviceBus.kt:111](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L111) |
| `fun onCatalogChanged` | [MarineDeviceBus.kt:120](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L120) |
| `fun subscribeRecorder` | [MarineDeviceBus.kt:129](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L129) |
| `fun enterBackend` | [MarineDeviceBus.kt:140](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L140) |
| `fun attach` | [MarineDeviceBus.kt:150](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L150) |
| `fun detach` | [MarineDeviceBus.kt:166](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L166) |
| `fun isCurrent` | [MarineDeviceBus.kt:178](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L178) |
| `fun publish` | [MarineDeviceBus.kt:183](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/MarineDeviceBus.kt#L183) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `object VirtualHostServices` | [VirtualHostServices.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L8) |
| `fun configure` | [VirtualHostServices.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L15) |
| `fun power` | [VirtualHostServices.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L20) |
| `fun storage` | [VirtualHostServices.kt:21](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L21) |
| `fun beforeRead` | [VirtualHostServices.kt:22](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L22) |
| `fun beforeWrite` | [VirtualHostServices.kt:30](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/hardware/VirtualHostServices.kt#L30) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NavigationPoint` | [NavigationContract.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L6) |
| `class NavigationWaypoint` | [NavigationContract.kt:9](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L9) |
| `class NavigationRouteSnapshot` | [NavigationContract.kt:11](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L11) |
| `class NavigationSource` | [NavigationContract.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L17) |
| `class NavigationPhase` | [NavigationContract.kt:18](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L18) |
| `class WaypointAdvanceMode` | [NavigationContract.kt:19](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L19) |
| `class NavigationSegmentRelation` | [NavigationContract.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L20) |
| `class NavigationEtaBasis` | [NavigationContract.kt:21](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L21) |
| `class NavigationSettings` | [NavigationContract.kt:22](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L22) |
| `class NavigationSession` | [NavigationContract.kt:29](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L29) |
| `class NavigationGuidance` | [NavigationContract.kt:54](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L54) |
| `class NavigationExternalSource` | [NavigationContract.kt:91](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L91) |
| `class NavigationState` | [NavigationContract.kt:92](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L92) |
| `class NavigationAction` | [NavigationContract.kt:101](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L101) |
| `class NavigationCommand` | [NavigationContract.kt:102](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L102) |
| `class NavigationResult` | [NavigationContract.kt:113](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L113) |
| `class NavigationReceipt` | [NavigationContract.kt:114](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L114) |
| `interface NavigationSessionService` | [NavigationContract.kt:116](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L116) |
| `fun execute` | [NavigationContract.kt:120](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L120) |
| `fun importLegacy` | [NavigationContract.kt:122](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L122) |
| `fun retryRead` | [NavigationContract.kt:123](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L123) |
| `class NavigationFixSnapshot` | [NavigationContract.kt:127](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/navigation/NavigationContract.kt#L127) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NotificationProtocol` | [NotificationContract.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L6) |
| `class NoticeCapability` | [NotificationContract.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L7) |
| `class NoticeServiceInfo` | [NotificationContract.kt:8](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L8) |
| `class NoticeLevel` | [NotificationContract.kt:9](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L9) |
| `class NoticeActionKind` | [NotificationContract.kt:11](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L11) |
| `class NoticeAction` | [NotificationContract.kt:12](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L12) |
| `class NoticeTarget` | [NotificationContract.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L13) |
| `class NoticeText` | [NotificationContract.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L14) |
| `class NoticeRecord` | [NotificationContract.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L16) |
| `class NoticeConnection` | [NotificationContract.kt:27](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L27) |
| `class NotificationSnapshot` | [NotificationContract.kt:28](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L28) |
| `class NoticeCommandStatus` | [NotificationContract.kt:30](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L30) |
| `class NoticeCommandResult` | [NotificationContract.kt:31](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L31) |
| `class NoticePublishMode` | [NotificationContract.kt:32](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L32) |
| `class NoticeOperation` | [NotificationContract.kt:33](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L33) |
| `class NoticeCommand` | [NotificationContract.kt:36](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L36) |
| `interface NotificationClient` | [NotificationContract.kt:41](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L41) |
| `fun execute` | [NotificationContract.kt:46](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L46) |
| `fun result` | [NotificationContract.kt:47](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L47) |
| `fun setForeground` | [NotificationContract.kt:49](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L49) |
| `fun close` | [NotificationContract.kt:50](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/notification/NotificationContract.kt#L50) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PassageRoute` | [PassageContract.kt:9](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L9) |
| `class PassageVessel` | [PassageContract.kt:11](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L11) |
| `class PassageAvoidance` | [PassageContract.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L13) |
| `class PassageEnvironmentReference` | [PassageContract.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L15) |
| `class PassageRequest` | [PassageContract.kt:16](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L16) |
| `class PassageSeverity` | [PassageContract.kt:17](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L17) |
| `class PassageIssueKind` | [PassageContract.kt:18](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L18) |
| `class PassageIssue` | [PassageContract.kt:20](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L20) |
| `class PassageStripSpan` | [PassageContract.kt:22](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L22) |
| `class PassageAnalysis` | [PassageContract.kt:23](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L23) |
| `class PassageJobPhase` | [PassageContract.kt:24](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L24) |
| `class PassageJob` | [PassageContract.kt:25](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L25) |
| `class PassageCandidate` | [PassageContract.kt:26](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L26) |
| `class PassagePlan` | [PassageContract.kt:31](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L31) |
| `class PassageReview` | [PassageContract.kt:33](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L33) |
| `class PassageState` | [PassageContract.kt:35](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L35) |
| `interface RouteAnalysisService` | [PassageContract.kt:41](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L41) |
| `fun analyze` | [PassageContract.kt:43](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L43) |
| `fun cancel` | [PassageContract.kt:44](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L44) |
| `fun saveAvoidance` | [PassageContract.kt:45](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L45) |
| `fun removeAvoidance` | [PassageContract.kt:46](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L46) |
| `fun saveReview` | [PassageContract.kt:48](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L48) |
| `fun retryRestore` | [PassageContract.kt:49](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L49) |
| `interface RoutePlanningService` | [PassageContract.kt:51](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L51) |
| `fun plan` | [PassageContract.kt:54](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L54) |
| `fun cancel` | [PassageContract.kt:55](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassageContract.kt#L55) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PassageReadinessStatus` | [PassagePlanningReadiness.kt:6](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L6) |
| `class PassageReadinessReason` | [PassagePlanningReadiness.kt:7](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L7) |
| `class PassagePlanningEvidence` | [PassagePlanningReadiness.kt:13](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L13) |
| `class PassagePlanningReadiness` | [PassagePlanningReadiness.kt:14](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L14) |
| `fun ChartDataset.allowsPassageDrafting` | [PassagePlanningReadiness.kt:53](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L53) |
| `object PassagePlanningEligibility` | [PassagePlanningReadiness.kt:62](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L62) |
| `fun evaluate` | [PassagePlanningReadiness.kt:63](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L63) |
| `fun blocked` | [PassagePlanningReadiness.kt:65](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/planning/PassagePlanningReadiness.kt#L65) |

## core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ClockSnapshot` | [MarineTime.kt:15](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L15) |
| `object MarineTime` | [MarineTime.kt:31](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L31) |
| `fun installHost` | [MarineTime.kt:43](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L43) |
| `fun hostElapsedMillis` | [MarineTime.kt:53](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L53) |
| `fun hostUtcMillis` | [MarineTime.kt:54](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L54) |
| `fun snapshot` | [MarineTime.kt:55](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L55) |
| `fun nowElapsedMillis` | [MarineTime.kt:56](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L56) |
| `fun nowUtcMillis` | [MarineTime.kt:60](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L60) |
| `fun fromHostElapsedMillis` | [MarineTime.kt:66](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L66) |
| `fun enterVirtual` | [MarineTime.kt:74](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L74) |
| `fun setPaused` | [MarineTime.kt:81](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L81) |
| `fun setRate` | [MarineTime.kt:86](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L86) |
| `fun step` | [MarineTime.kt:95](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L95) |
| `fun returnToReal` | [MarineTime.kt:101](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L101) |
| `fun applyRemote` | [MarineTime.kt:106](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L106) |
| `fun sleep` | [MarineTime.kt:113](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L113) |
| `fun sleepUntil` | [MarineTime.kt:118](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L118) |
| `fun <T> withTimeoutOrNull` | [MarineTime.kt:135](../../core/runtime-contract/src/main/kotlin/com/yokuli/runtime/contract/time/MarineTime.kt#L135) |

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
| `class MarineTileSize` | [MarineTile.kt:11](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L11) |
| `fun fromPersistedName` | [MarineTile.kt:26](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L26) |
| `class TilePresentationKind` | [MarineTile.kt:34](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt#L34) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineUnitFormats` | [MarineUnitFormats.kt:9](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L9) |
| `fun distanceValue` | [MarineUnitFormats.kt:21](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L21) |
| `fun distanceMeters` | [MarineUnitFormats.kt:22](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L22) |
| `fun speedValue` | [MarineUnitFormats.kt:23](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L23) |
| `fun speedKnots` | [MarineUnitFormats.kt:24](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L24) |
| `fun lengthValue` | [MarineUnitFormats.kt:25](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L25) |
| `fun lengthMeters` | [MarineUnitFormats.kt:26](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L26) |
| `fun depthValue` | [MarineUnitFormats.kt:27](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L27) |
| `fun depthMeters` | [MarineUnitFormats.kt:28](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L28) |
| `fun temperatureValue` | [MarineUnitFormats.kt:29](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L29) |
| `fun temperatureCelsius` | [MarineUnitFormats.kt:30](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L30) |
| `fun pressureValue` | [MarineUnitFormats.kt:31](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L31) |
| `fun pressureHpa` | [MarineUnitFormats.kt:32](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L32) |
| `fun distance` | [MarineUnitFormats.kt:35](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L35) |
| `fun length` | [MarineUnitFormats.kt:40](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L40) |
| `fun speed` | [MarineUnitFormats.kt:41](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L41) |
| `fun depth` | [MarineUnitFormats.kt:42](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L42) |
| `fun temperature` | [MarineUnitFormats.kt:43](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L43) |
| `fun pressure` | [MarineUnitFormats.kt:44](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L44) |
| `fun scaleBar` | [MarineUnitFormats.kt:54](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L54) |
| `class DistanceScaleBar` | [MarineUnitFormats.kt:81](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitFormats.kt#L81) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LengthUnit` | [MarineUnitPreferences.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L4) |
| `class DepthUnit` | [MarineUnitPreferences.kt:5](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L5) |
| `class TemperatureUnit` | [MarineUnitPreferences.kt:6](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L6) |
| `class PressureUnit` | [MarineUnitPreferences.kt:7](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L7) |
| `class DistanceUnit` | [MarineUnitPreferences.kt:9](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L9) |
| `class SpeedUnit` | [MarineUnitPreferences.kt:14](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L14) |
| `class MarineUnitPreferences` | [MarineUnitPreferences.kt:23](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L23) |
| `fun fromStored` | [MarineUnitPreferences.kt:36](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L36) |
| `fun value` | [MarineUnitPreferences.kt:39](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineUnitPreferences.kt#L39) |

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

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileBinding.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileBindingKind` | [TileBinding.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileBinding.kt#L4) |
| `class TileBinding` | [TileBinding.kt:6](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileBinding.kt#L6) |
| `class TilePresentation` | [TileBinding.kt:22](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileBinding.kt#L22) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TileCompositePolicy` | [TileCompositePolicy.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L4) |
| `fun isAllowedTarget` | [TileCompositePolicy.kt:19](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L19) |
| `fun validPresentation` | [TileCompositePolicy.kt:27](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L27) |
| `fun validPanels` | [TileCompositePolicy.kt:33](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L33) |
| `fun canonicalBinding` | [TileCompositePolicy.kt:37](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L37) |
| `fun isCanonical` | [TileCompositePolicy.kt:42](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L42) |
| `fun supportsContentId` | [TileCompositePolicy.kt:45](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileCompositePolicy.kt#L45) |

## core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileReadingPresentationPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TileReadingPresentationPolicy` | [TileReadingPresentationPolicy.kt:4](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileReadingPresentationPolicy.kt#L4) |
| `fun styles` | [TileReadingPresentationPolicy.kt:16](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileReadingPresentationPolicy.kt#L16) |
| `fun hasValidOptions` | [TileReadingPresentationPolicy.kt:26](../../core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/TileReadingPresentationPolicy.kt#L26) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface LauncherEngine` | [LauncherEngine.kt:30](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L30) |
| `fun dispatch` | [LauncherEngine.kt:34](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L34) |
| `fun commitTile` | [LauncherEngine.kt:35](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L35) |
| `fun removeTile` | [LauncherEngine.kt:36](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L36) |
| `fun undoTile` | [LauncherEngine.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L37) |
| `class DefaultLauncherEngine` | [LauncherEngine.kt:40](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L40) |
| `class Action` | [LauncherEngine.kt:50](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L50) |
| `class Tile` | [LauncherEngine.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L51) |
| `fun dispatch` | [LauncherEngine.kt:104](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L104) |
| `fun commitTile` | [LauncherEngine.kt:112](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L112) |
| `fun removeTile` | [LauncherEngine.kt:116](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L116) |
| `fun undoTile` | [LauncherEngine.kt:120](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherEngine.kt#L120) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PersistedLauncherPage` | [LauncherPersistence.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L14) |
| `class LauncherStartupHealth` | [LauncherPersistence.kt:16](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L16) |
| `class LauncherPersistedState` | [LauncherPersistence.kt:23](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L23) |
| `class LauncherPersistenceIncident` | [LauncherPersistence.kt:42](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L42) |
| `class LauncherPersistenceMigrationResult` | [LauncherPersistence.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L52) |
| `object LauncherPersistedStateMigration` | [LauncherPersistence.kt:57](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L57) |
| `fun migrate` | [LauncherPersistence.kt:64](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L64) |
| `fun normalized` | [LauncherPersistence.kt:88](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L88) |
| `class LauncherTokenAlias` | [LauncherPersistence.kt:133](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L133) |
| `fun migrate` | [LauncherPersistence.kt:143](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L143) |
| `class LauncherProductMigrationStep` | [LauncherPersistence.kt:150](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L150) |
| `class LauncherProductMigrationResult` | [LauncherPersistence.kt:163](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L163) |
| `class LauncherProductMigrationPlan` | [LauncherPersistence.kt:172](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L172) |
| `fun migrate` | [LauncherPersistence.kt:183](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L183) |
| `class LauncherRecoveryDecision` | [LauncherPersistence.kt:227](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L227) |
| `object LauncherRecoveryPolicy` | [LauncherPersistence.kt:232](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L232) |
| `fun beginLaunch` | [LauncherPersistence.kt:236](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L236) |
| `fun markHealthy` | [LauncherPersistence.kt:251](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L251) |
| `interface LauncherPersistencePort` | [LauncherPersistence.kt:259](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L259) |
| `fun load` | [LauncherPersistence.kt:266](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L266) |
| `fun save` | [LauncherPersistence.kt:267](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L267) |
| `fun reset` | [LauncherPersistence.kt:268](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L268) |
| `fun updateDocument` | [LauncherPersistence.kt:270](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L270) |
| `fun saveTileDraft` | [LauncherPersistence.kt:276](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L276) |
| `fun saveDocument` | [LauncherPersistence.kt:281](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L281) |
| `fun savePreferences` | [LauncherPersistence.kt:285](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L285) |
| `fun beginLaunch` | [LauncherPersistence.kt:295](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L295) |
| `fun markLaunchHealthy` | [LauncherPersistence.kt:302](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L302) |
| `class InMemoryLauncherPersistence` | [LauncherPersistence.kt:308](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L308) |
| `fun load` | [LauncherPersistence.kt:320](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L320) |
| `fun save` | [LauncherPersistence.kt:322](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L322) |
| `fun reset` | [LauncherPersistence.kt:327](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherPersistence.kt#L327) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherReducerContext` | [LauncherReducer.kt:27](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L27) |
| `class LauncherReduction` | [LauncherReducer.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L33) |
| `interface LauncherAction` | [LauncherReducer.kt:38](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L38) |
| `object ShowStart : LauncherAction` | [LauncherReducer.kt:39](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L39) |
| `object ShowAllApps : LauncherAction` | [LauncherReducer.kt:40](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L40) |
| `object Back : LauncherAction` | [LauncherReducer.kt:41](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L41) |
| `class CompleteLinkedVisit` | [LauncherReducer.kt:43](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L43) |
| `object ShowDesktop : LauncherAction` | [LauncherReducer.kt:44](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L44) |
| `object OpenSearch : LauncherAction` | [LauncherReducer.kt:45](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L45) |
| `class UpdateSearchQuery` | [LauncherReducer.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L46) |
| `object ShowRecents : LauncherAction` | [LauncherReducer.kt:47](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L47) |
| `class ActivateTask` | [LauncherReducer.kt:48](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L48) |
| `class CloseTask` | [LauncherReducer.kt:49](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L49) |
| `class RestorePersistedDocument` | [LauncherReducer.kt:50](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L50) |
| `object EnterSafeMode : LauncherAction` | [LauncherReducer.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L51) |
| `object ExitSafeMode : LauncherAction` | [LauncherReducer.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L52) |
| `class Open` | [LauncherReducer.kt:54](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L54) |
| `class CatalogChanged` | [LauncherReducer.kt:64](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L64) |
| `class ApplyLayoutProposal` | [LauncherReducer.kt:65](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L65) |
| `class BeginLayoutTransaction` | [LauncherReducer.kt:66](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L66) |
| `object CommitLayoutTransaction : LauncherAction` | [LauncherReducer.kt:67](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L67) |
| `object CancelLayoutTransaction : LauncherAction` | [LauncherReducer.kt:68](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L68) |
| `object UndoLayout : LauncherAction` | [LauncherReducer.kt:69](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L69) |
| `class EnterStartEdit` | [LauncherReducer.kt:70](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L70) |
| `class SelectStartTile` | [LauncherReducer.kt:71](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L71) |
| `object ExitStartEdit : LauncherAction` | [LauncherReducer.kt:72](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L72) |
| `class BeginTileDrag` | [LauncherReducer.kt:73](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L73) |
| `class InsertionTargetChanged` | [LauncherReducer.kt:78](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L78) |
| `class TileCellTargetChanged` | [LauncherReducer.kt:79](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L79) |
| `class DropTile` | [LauncherReducer.kt:84](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L84) |
| `object CancelTileOperation : LauncherAction` | [LauncherReducer.kt:85](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L85) |
| `class ResizeTile` | [LauncherReducer.kt:86](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L86) |
| `class MoveTileBy` | [LauncherReducer.kt:87](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L87) |
| `class OpenEntryContextMenu` | [LauncherReducer.kt:88](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L88) |
| `object OpenAlphabetJump : LauncherAction` | [LauncherReducer.kt:89](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L89) |
| `object DismissTransient : LauncherAction` | [LauncherReducer.kt:90](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L90) |
| `class PinEntry` | [LauncherReducer.kt:91](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L91) |
| `class UnpinTile` | [LauncherReducer.kt:92](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L92) |
| `class AcknowledgeStartReveal` | [LauncherReducer.kt:93](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L93) |
| `class RevealTile` | [LauncherReducer.kt:95](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L95) |
| `class TogglePin` | [LauncherReducer.kt:96](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L96) |
| `object ResetStartDocument : LauncherAction` | [LauncherReducer.kt:97](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L97) |
| `class SetStartColumns` | [LauncherReducer.kt:99](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L99) |
| `class PersistenceIncidentObserved` | [LauncherReducer.kt:100](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L100) |
| `class LauncherHaptic` | [LauncherReducer.kt:103](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L103) |
| `interface LauncherIncident` | [LauncherReducer.kt:105](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L105) |
| `class UnresolvedLaunchToken` | [LauncherReducer.kt:106](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L106) |
| `class InvalidLayoutProposal` | [LauncherReducer.kt:107](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L107) |
| `class CatalogRepair` | [LauncherReducer.kt:108](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L108) |
| `class PersistenceMigration` | [LauncherReducer.kt:109](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L109) |
| `class PersistenceFailure` | [LauncherReducer.kt:110](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L110) |
| `interface LauncherEffect` | [LauncherReducer.kt:113](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L113) |
| `class Launch` | [LauncherReducer.kt:114](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L114) |
| `class PersistDocument` | [LauncherReducer.kt:115](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L115) |
| `class Haptic` | [LauncherReducer.kt:116](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L116) |
| `class AccessibilityAnnouncement` | [LauncherReducer.kt:117](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L117) |
| `class LogIncident` | [LauncherReducer.kt:118](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L118) |
| `class ScrollStartToReveal` | [LauncherReducer.kt:119](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L119) |
| `class CloseAppSession` | [LauncherReducer.kt:121](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L121) |
| `fun ShellInput.toShellAction` | [LauncherReducer.kt:124](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L124) |
| `interface LauncherReducer` | [LauncherReducer.kt:131](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L131) |
| `fun reduce` | [LauncherReducer.kt:132](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L132) |
| `class DefaultLauncherReducer : LauncherReducer` | [LauncherReducer.kt:139](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L139) |
| `fun reduce` | [LauncherReducer.kt:140](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L140) |
| `fun sameFields` | [LauncherReducer.kt:916](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LauncherReducer.kt#L916) |

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

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LegacyTileInstanceMigration.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LegacyTileInstanceMigration` | [LegacyTileInstanceMigration.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LegacyTileInstanceMigration.kt#L8) |
| `fun migrate` | [LegacyTileInstanceMigration.kt:17](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/LegacyTileInstanceMigration.kt#L17) |

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
| `fun withColumns` | [WpReferenceProfile.kt:160](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L160) |
| `fun forViewport` | [WpReferenceProfile.kt:171](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L171) |
| `fun require` | [WpReferenceProfile.kt:174](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpReferenceProfile.kt#L174) |

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
| `fun tileContentScale` | [WpStartGeometry.kt:58](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L58) |
| `fun calculate` | [WpStartGeometry.kt:65](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L65) |
| `fun scale` | [WpStartGeometry.kt:69](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/geometry/WpStartGeometry.kt#L69) |

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
| `fun insert` | [AdaptiveTilePacker.kt:118](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L118) |
| `fun insert` | [AdaptiveTilePacker.kt:136](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L136) |
| `fun insertionIndexForTarget` | [AdaptiveTilePacker.kt:140](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L140) |
| `fun insertionTargetForCell` | [AdaptiveTilePacker.kt:151](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L151) |
| `fun insertionIndexForCell` | [AdaptiveTilePacker.kt:189](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L189) |
| `fun insertionIndexOf` | [AdaptiveTilePacker.kt:202](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L202) |
| `class Tile` | [AdaptiveTilePacker.kt:229](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L229) |
| `class Gap` | [AdaptiveTilePacker.kt:234](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L234) |
| `fun occupiedCells` | [AdaptiveTilePacker.kt:242](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/AdaptiveTilePacker.kt#L242) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartColumnLayout.kt

| 声明 | 实现位置 |
| --- | --- |
| `object StartColumnLayout` | [StartColumnLayout.kt:7](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartColumnLayout.kt#L7) |
| `fun change` | [StartColumnLayout.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartColumnLayout.kt#L8) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt

| 声明 | 实现位置 |
| --- | --- |
| `class GridCell` | [StartDocument.kt:11](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L11) |
| `class TileDocumentEntry` | [StartDocument.kt:13](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L13) |
| `class Spacer` | [StartDocument.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L37) |
| `class TileDocument` | [StartDocument.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L46) |
| `class StartDocument` | [StartDocument.kt:51](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L51) |
| `class LayoutChangeReason` | [StartDocument.kt:68](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L68) |
| `class LayoutTransaction` | [StartDocument.kt:70](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L70) |
| `class LayoutProposal` | [StartDocument.kt:77](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocument.kt#L77) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object StartDocumentValidator` | [StartDocumentPolicy.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L8) |
| `fun isValid` | [StartDocumentPolicy.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L9) |
| `class StartRepairIncident` | [StartDocumentPolicy.kt:33](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L33) |
| `class StartRepairResult` | [StartDocumentPolicy.kt:46](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L46) |
| `object StartDocumentRepair` | [StartDocumentPolicy.kt:52](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L52) |
| `fun repair` | [StartDocumentPolicy.kt:53](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartDocumentPolicy.kt#L53) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt

| 声明 | 实现位置 |
| --- | --- |
| `object StartLayoutEditor` | [StartLayoutEditor.kt:13](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L13) |
| `fun resize` | [StartLayoutEditor.kt:14](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L14) |
| `fun unpin` | [StartLayoutEditor.kt:28](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L28) |
| `fun pin` | [StartLayoutEditor.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L37) |
| `fun move` | [StartLayoutEditor.kt:69](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/StartLayoutEditor.kt#L69) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TileCommitRequest` | [TileCommit.kt:9](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L9) |
| `interface TileCommitResult` | [TileCommit.kt:19](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L19) |
| `class Saved` | [TileCommit.kt:20](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L20) |
| `class AlreadyPinned` | [TileCommit.kt:21](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L21) |
| `class Conflict` | [TileCommit.kt:22](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L22) |
| `class Failed` | [TileCommit.kt:23](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L23) |
| `class TileCommitReceipt` | [TileCommit.kt:27](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L27) |
| `class TileRemovalRecord` | [TileCommit.kt:37](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommit.kt#L37) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TileCommitPolicy` | [TileCommitPolicy.kt:8](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L8) |
| `class Decision` | [TileCommitPolicy.kt:10](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L10) |
| `fun save` | [TileCommitPolicy.kt:12](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L12) |
| `fun remove` | [TileCommitPolicy.kt:63](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L63) |
| `fun undo` | [TileCommitPolicy.kt:74](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L74) |
| `fun restoreEntry` | [TileCommitPolicy.kt:93](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L93) |
| `fun cells` | [TileCommitPolicy.kt:98](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L98) |
| `fun receipt` | [TileCommitPolicy.kt:120](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileCommitPolicy.kt#L120) |

## core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileLayoutPreview.kt

| 声明 | 实现位置 |
| --- | --- |
| `object TileLayoutPreview` | [TileLayoutPreview.kt:4](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileLayoutPreview.kt#L4) |
| `fun remove` | [TileLayoutPreview.kt:6](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileLayoutPreview.kt#L6) |
| `fun place` | [TileLayoutPreview.kt:18](../../core/shell-engine/src/main/kotlin/com/yokuli/shell/engine/layout/TileLayoutPreview.kt#L18) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LauncherPagerPage` | [InteractiveLauncherPager.kt:18](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt#L18) |
| `fun from` | [InteractiveLauncherPager.kt:24](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt#L24) |
| `fun InteractiveLauncherPager` | [InteractiveLauncherPager.kt:38](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/InteractiveLauncherPager.kt#L38) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherNameOrder.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LauncherNameOrder` | [LauncherNameOrder.kt:7](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherNameOrder.kt#L7) |
| `fun removeEldestEntry` | [LauncherNameOrder.kt:10](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherNameOrder.kt#L10) |
| `fun key` | [LauncherNameOrder.kt:12](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherNameOrder.kt#L12) |
| `fun initial` | [LauncherNameOrder.kt:17](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherNameOrder.kt#L17) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherRecoverySurface.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun LauncherRecoverySurface` | [LauncherRecoverySurface.kt:26](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherRecoverySurface.kt#L26) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineIconKind` | [LauncherUiContract.kt:22](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L22) |
| `class LauncherUiState` | [LauncherUiContract.kt:24](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L24) |
| `interface LauncherUiAction` | [LauncherUiContract.kt:35](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L35) |
| `class Open` | [LauncherUiContract.kt:36](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L36) |
| `object ShowAllApps : LauncherUiAction` | [LauncherUiContract.kt:37](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L37) |
| `class ProposeLayout` | [LauncherUiContract.kt:38](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L38) |
| `class EnterStartEdit` | [LauncherUiContract.kt:39](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L39) |
| `class SelectStartTile` | [LauncherUiContract.kt:40](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L40) |
| `object ExitStartEdit : LauncherUiAction` | [LauncherUiContract.kt:41](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L41) |
| `class BeginTileDrag` | [LauncherUiContract.kt:42](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L42) |
| `class InsertionTargetChanged` | [LauncherUiContract.kt:43](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L43) |
| `class TileCellTargetChanged` | [LauncherUiContract.kt:44](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L44) |
| `class DropTile` | [LauncherUiContract.kt:45](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L45) |
| `object CancelTileOperation : LauncherUiAction` | [LauncherUiContract.kt:46](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L46) |
| `class ResizeTile` | [LauncherUiContract.kt:47](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L47) |
| `class MoveTileBy` | [LauncherUiContract.kt:48](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L48) |
| `class OpenEntryContextMenu` | [LauncherUiContract.kt:49](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L49) |
| `object OpenAlphabetJump : LauncherUiAction` | [LauncherUiContract.kt:50](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L50) |
| `object DismissTransient : LauncherUiAction` | [LauncherUiContract.kt:51](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L51) |
| `class PinEntry` | [LauncherUiContract.kt:52](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L52) |
| `class UnpinTile` | [LauncherUiContract.kt:53](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L53) |
| `class AcknowledgeStartReveal` | [LauncherUiContract.kt:54](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L54) |
| `object UndoLayout : LauncherUiAction` | [LauncherUiContract.kt:55](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L55) |
| `class UpdateSearchQuery` | [LauncherUiContract.kt:56](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L56) |
| `class ActivateTask` | [LauncherUiContract.kt:57](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L57) |
| `class CloseTask` | [LauncherUiContract.kt:58](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L58) |
| `class ShowAppInfo` | [LauncherUiContract.kt:59](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L59) |
| `fun productionLauncherUiState` | [LauncherUiContract.kt:67](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/LauncherUiContract.kt#L67) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/MarineIcon.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun MarineIcon` | [MarineIcon.kt:15](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/MarineIcon.kt#L15) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpAppList.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun WpAppList` | [WpAppList.kt:50](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpAppList.kt#L50) |
| `fun WpAlphabetJumpOverlay` | [WpAppList.kt:258](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpAppList.kt#L258) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpLauncherFeedback.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun WpLauncherFeedback` | [WpLauncherFeedback.kt:35](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpLauncherFeedback.kt#L35) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSpatialStartLayout.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun WpSpatialStartLayout` | [WpSpatialStartLayout.kt:41](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSpatialStartLayout.kt#L41) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun YokuliStartScreen` | [WpStartScreen.kt:133](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt#L133) |
| `fun updateDrag` | [WpStartScreen.kt:261](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt#L261) |
| `fun Control` | [WpStartScreen.kt:600](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStartScreen.kt#L600) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt

| 声明 | 实现位置 |
| --- | --- |
| `class WpStatusStripItem` | [WpStatusStrip.kt:59](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt#L59) |
| `fun WpStatusStrip` | [WpStatusStrip.kt:73](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt#L73) |
| `fun update` | [WpStatusStrip.kt:91](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt#L91) |
| `fun onReceive` | [WpStatusStrip.kt:99](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpStatusStrip.kt#L99) |

## feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun WpSystemKeyBar` | [WpSystemKeyBar.kt:74](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt#L74) |
| `fun SearchGlyph` | [WpSystemKeyBar.kt:192](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt#L192) |
| `fun WpSearchSurface` | [WpSystemKeyBar.kt:211](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt#L211) |
| `fun WpRecentsSurface` | [WpSystemKeyBar.kt:331](../../feature/desktop/src/main/java/com/yokuli/marine/feature/desktop/WpSystemKeyBar.kt#L331) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ConnectionAttemptState` | [LegacyMarineController.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L181) |
| `class ConnectionAttempt` | [LegacyMarineController.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L182) |
| `class CentreRecalculationUiState` | [LegacyMarineController.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L183) |
| `class AnchorSetupDraft` | [LegacyMarineController.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L186) |
| `class MainUiState` | [LegacyMarineController.kt:219](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L219) |
| `class AnchorWatchInput` | [LegacyMarineController.kt:316](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L316) |
| `class LegacyMarineController @Inject constructor` | [LegacyMarineController.kt:324](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L324) |
| `fun saveNmeaConnection` | [LegacyMarineController.kt:398](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L398) |
| `fun startNmeaConnection` | [LegacyMarineController.kt:399](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L399) |
| `fun stopNmeaConnection` | [LegacyMarineController.kt:404](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L404) |
| `fun selectNmeaPositionConnection` | [LegacyMarineController.kt:405](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L405) |
| `fun removeNmeaConnection` | [LegacyMarineController.kt:410](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L410) |
| `fun setVesselMetricSource` | [LegacyMarineController.kt:412](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L412) |
| `fun setNmeaMetricSource` | [LegacyMarineController.kt:422](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L422) |
| `fun consumeSonarGridChanges` | [LegacyMarineController.kt:690](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L690) |
| `fun validateProfile` | [LegacyMarineController.kt:706](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L706) |
| `fun saveAndConnect` | [LegacyMarineController.kt:708](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L708) |
| `fun disconnect` | [LegacyMarineController.kt:753](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L753) |
| `fun reconnectNmea` | [LegacyMarineController.kt:768](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L768) |
| `fun stopActiveWatchAndDisconnect` | [LegacyMarineController.kt:781](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L781) |
| `fun stopNmeaDependenciesAndDisconnect` | [LegacyMarineController.kt:785](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L785) |
| `fun continueTripWithPhoneAndDisconnect` | [LegacyMarineController.kt:789](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L789) |
| `fun clearConnectionAttempt` | [LegacyMarineController.kt:793](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L793) |
| `fun dismissRuntimeFeedback` | [LegacyMarineController.kt:794](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L794) |
| `fun consumeRuntimeFeedback` | [LegacyMarineController.kt:799](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L799) |
| `fun setLanguage` | [LegacyMarineController.kt:807](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L807) |
| `fun setVesselGeometry` | [LegacyMarineController.kt:808](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L808) |
| `fun setPassageGeometry` | [LegacyMarineController.kt:814](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L814) |
| `fun setVesselIdentity` | [LegacyMarineController.kt:818](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L818) |
| `fun setAlarmSound` | [LegacyMarineController.kt:822](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L822) |
| `fun setAlarmSnoozeMinutes` | [LegacyMarineController.kt:827](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L827) |
| `fun setInstrumentLayout` | [LegacyMarineController.kt:831](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L831) |
| `fun updateSettings` | [LegacyMarineController.kt:835](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L835) |
| `fun completeOnboarding` | [LegacyMarineController.kt:849](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L849) |
| `fun setSonarLayerEnabled` | [LegacyMarineController.kt:850](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L850) |
| `fun exportBackup` | [LegacyMarineController.kt:854](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L854) |
| `fun restoreBackup` | [LegacyMarineController.kt:855](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L855) |
| `fun clearBackupResult` | [LegacyMarineController.kt:856](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L856) |
| `fun importOfflineMap` | [LegacyMarineController.kt:857](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L857) |
| `fun removeOfflineMap` | [LegacyMarineController.kt:870](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L870) |
| `fun setOfflineMapEnabled` | [LegacyMarineController.kt:875](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L875) |
| `fun createOfflineMapProvider` | [LegacyMarineController.kt:876](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L876) |
| `fun exportSupportBundle` | [LegacyMarineController.kt:877](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L877) |
| `fun clearSupportBundleResult` | [LegacyMarineController.kt:878](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L878) |
| `fun clearIncidentLog` | [LegacyMarineController.kt:879](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L879) |
| `fun clearRebuildableCaches` | [LegacyMarineController.kt:887](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L887) |
| `fun refreshStorage` | [LegacyMarineController.kt:895](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L895) |
| `fun confirmAlarmAudible` | [LegacyMarineController.kt:896](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L896) |
| `fun setNmeaSharing` | [LegacyMarineController.kt:903](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L903) |
| `fun saveLocalNmeaServerConfiguration` | [LegacyMarineController.kt:914](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L914) |
| `fun startLocalNmeaServer` | [LegacyMarineController.kt:927](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L927) |
| `fun fail` | [LegacyMarineController.kt:929](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L929) |
| `fun saveLocalNmeaPublicationPolicy` | [LegacyMarineController.kt:942](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L942) |
| `fun stopLocalNmeaServer` | [LegacyMarineController.kt:948](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L948) |
| `fun stopAllNmeaSharing` | [LegacyMarineController.kt:956](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L956) |
| `fun deleteHistorySession` | [LegacyMarineController.kt:966](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L966) |
| `fun setMapType` | [LegacyMarineController.kt:967](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L967) |
| `fun setGpsDataSource` | [LegacyMarineController.kt:968](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L968) |
| `fun switchGpsDataSource` | [LegacyMarineController.kt:969](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L969) |
| `fun setDemoMode` | [LegacyMarineController.kt:987](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L987) |
| `fun updateVesselDataSettings` | [LegacyMarineController.kt:997](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L997) |
| `fun createTripDashboard` | [LegacyMarineController.kt:998](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L998) |
| `fun saveTripDashboard` | [LegacyMarineController.kt:999](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L999) |
| `fun deleteTripDashboard` | [LegacyMarineController.kt:1000](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1000) |
| `fun reorderTripDashboards` | [LegacyMarineController.kt:1001](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1001) |
| `fun setTripLiveDisplayActive` | [LegacyMarineController.kt:1002](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1002) |
| `fun confirmTripAttitudeFrame` | [LegacyMarineController.kt:1003](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1003) |
| `fun calibrateVesselMount` | [LegacyMarineController.kt:1018](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1018) |
| `fun setPhoneVesselMounted` | [LegacyMarineController.kt:1019](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1019) |
| `fun alignPhoneHeadingToBow` | [LegacyMarineController.kt:1031](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1031) |
| `fun alignPhoneHeadingToNmea` | [LegacyMarineController.kt:1043](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1043) |
| `fun confirmFixedPhoneMount` | [LegacyMarineController.kt:1069](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1069) |
| `fun setPhoneHeadingAlignment` | [LegacyMarineController.kt:1091](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1091) |
| `fun setPhoneAttitudeAlignment` | [LegacyMarineController.kt:1106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1106) |
| `fun invalidateFixedPhoneMount` | [LegacyMarineController.kt:1121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1121) |
| `fun clearVesselCalibrationFeedback` | [LegacyMarineController.kt:1137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1137) |
| `fun setNmeaOutputEndpoint` | [LegacyMarineController.kt:1138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1138) |
| `fun setNmeaPhonePositionPublishing` | [LegacyMarineController.kt:1159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1159) |
| `fun setNmeaPhoneHeadingPublishing` | [LegacyMarineController.kt:1165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1165) |
| `fun setNmeaPhoneRateOfTurnPublishing` | [LegacyMarineController.kt:1168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1168) |
| `fun setNmeaPhoneAttitudePublishing` | [LegacyMarineController.kt:1171](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1171) |
| `fun setNmeaPhonePressurePublishing` | [LegacyMarineController.kt:1174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1174) |
| `fun setNmeaDerivedWindPublishing` | [LegacyMarineController.kt:1177](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1177) |
| `fun setNmeaOutputPreset` | [LegacyMarineController.kt:1180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1180) |
| `fun startNmeaOutput` | [LegacyMarineController.kt:1193](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1193) |
| `fun fail` | [LegacyMarineController.kt:1201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1201) |
| `fun stopNmeaOutput` | [LegacyMarineController.kt:1208](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1208) |
| `fun testNmeaDeviceOutput` | [LegacyMarineController.kt:1232](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1232) |
| `fun testKnownGoodHdgOutput` | [LegacyMarineController.kt:1241](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1241) |
| `fun updateDemoConfiguration` | [LegacyMarineController.kt:1248](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1248) |
| `fun onPermissionsChanged` | [LegacyMarineController.kt:1256](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1256) |
| `fun setAnchorSetupGpsPreview` | [LegacyMarineController.kt:1257](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1257) |
| `fun saveAnchorSetupDraft` | [LegacyMarineController.kt:1258](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1258) |
| `fun clearAnchorSetupDraft` | [LegacyMarineController.kt:1263](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1263) |
| `fun clearDiagnostics` | [LegacyMarineController.kt:1268](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1268) |
| `fun arm` | [LegacyMarineController.kt:1269](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1269) |
| `fun requestArm` | [LegacyMarineController.kt:1270](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1270) |
| `fun updateAnchorSettings` | [LegacyMarineController.kt:1281](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1281) |
| `fun updateConditionGuards` | [LegacyMarineController.kt:1282](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1282) |
| `fun resetWindBaseline` | [LegacyMarineController.kt:1283](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1283) |
| `fun pauseWatch` | [LegacyMarineController.kt:1284](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1284) |
| `fun resumeWatch` | [LegacyMarineController.kt:1285](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1285) |
| `fun liftAnchor` | [LegacyMarineController.kt:1286](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1286) |
| `fun requestPauseWatch` | [LegacyMarineController.kt:1287](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1287) |
| `fun requestResumeWatch` | [LegacyMarineController.kt:1288](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1288) |
| `fun requestLiftAnchor` | [LegacyMarineController.kt:1289](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1289) |
| `fun deliver` | [LegacyMarineController.kt:1296](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1296) |
| `fun stop` | [LegacyMarineController.kt:1311](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1311) |
| `fun acknowledge` | [LegacyMarineController.kt:1312](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1312) |
| `fun acceptEstimatedCenter` | [LegacyMarineController.kt:1313](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1313) |
| `fun keepCurrentCenter` | [LegacyMarineController.kt:1314](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1314) |
| `fun continueEstimatingCenter` | [LegacyMarineController.kt:1315](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1315) |
| `fun resetCentreAnalysis` | [LegacyMarineController.kt:1316](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1316) |
| `fun recalculateCentreFromTrack` | [LegacyMarineController.kt:1317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1317) |
| `fun dismissCentreRecalculation` | [LegacyMarineController.kt:1327](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1327) |
| `fun keepCurrentRecalculatedCentre` | [LegacyMarineController.kt:1328](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1328) |
| `fun applyRecalculatedCentre` | [LegacyMarineController.kt:1329](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1329) |
| `fun saveRecalculatedCentreAsAnchorage` | [LegacyMarineController.kt:1330](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1330) |
| `fun testAlarm` | [LegacyMarineController.kt:1331](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1331) |
| `fun stopAlarmTest` | [LegacyMarineController.kt:1332](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1332) |
| `fun startSonarSurvey` | [LegacyMarineController.kt:1333](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1333) |
| `fun stopSonarSurvey` | [LegacyMarineController.kt:1337](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1337) |
| `fun voyageCommandReceipt` | [LegacyMarineController.kt:1339](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1339) |
| `fun recheckVoyageCommand` | [LegacyMarineController.kt:1341](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1341) |
| `fun requestVoyageCommand` | [LegacyMarineController.kt:1349](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1349) |
| `fun startTrip` | [LegacyMarineController.kt:1364](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1364) |
| `fun pauseTrip` | [LegacyMarineController.kt:1369](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1369) |
| `fun resumeTrip` | [LegacyMarineController.kt:1370](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1370) |
| `fun pauseTripAttitude` | [LegacyMarineController.kt:1371](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1371) |
| `fun endTrip` | [LegacyMarineController.kt:1376](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1376) |
| `fun markTripWaypoint` | [LegacyMarineController.kt:1377](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1377) |
| `fun deleteTrip` | [LegacyMarineController.kt:1378](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1378) |
| `fun renameTrip` | [LegacyMarineController.kt:1380](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1380) |
| `fun editTripMoment` | [LegacyMarineController.kt:1381](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1381) |
| `fun tripReport` | [LegacyMarineController.kt:1382](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1382) |
| `fun anchorReport` | [LegacyMarineController.kt:1383](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1383) |
| `fun tripReplay` | [LegacyMarineController.kt:1384](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1384) |
| `fun tripMapData` | [LegacyMarineController.kt:1385](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1385) |
| `fun openLiveTripMap` | [LegacyMarineController.kt:1386](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1386) |
| `fun openTripMap` | [LegacyMarineController.kt:1387](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1387) |
| `fun closeTripMap` | [LegacyMarineController.kt:1391](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1391) |
| `fun exportTripCsv` | [LegacyMarineController.kt:1392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1392) |
| `fun exportTripGpx` | [LegacyMarineController.kt:1393](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1393) |
| `fun exportTripKml` | [LegacyMarineController.kt:1394](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1394) |
| `fun exportTripKmz` | [LegacyMarineController.kt:1395](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1395) |
| `fun exportTripEvents` | [LegacyMarineController.kt:1396](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1396) |
| `fun exportTripWaypoints` | [LegacyMarineController.kt:1397](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1397) |
| `fun exportTripCustomMetrics` | [LegacyMarineController.kt:1398](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1398) |
| `fun shareTripLiveSnapshot` | [LegacyMarineController.kt:1399](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1399) |
| `fun shareTripReportSnapshot` | [LegacyMarineController.kt:1400](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1400) |
| `fun exportTripAiSource` | [LegacyMarineController.kt:1401](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1401) |
| `fun exportAnchorAiSource` | [LegacyMarineController.kt:1402](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1402) |
| `fun renameSonarSurvey` | [LegacyMarineController.kt:1403](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1403) |
| `fun deleteSonarSurvey` | [LegacyMarineController.kt:1404](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1404) |
| `fun rebuildSonarSurvey` | [LegacyMarineController.kt:1405](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1405) |
| `fun selectSonarSurvey` | [LegacyMarineController.kt:1406](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1406) |
| `fun selectCorrectedSonarHistory` | [LegacyMarineController.kt:1407](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1407) |
| `fun exportSonarCsv` | [LegacyMarineController.kt:1408](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1408) |
| `fun startGpsProxy` | [LegacyMarineController.kt:1416](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1416) |
| `fun stopGpsProxy` | [LegacyMarineController.kt:1417](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1417) |
| `fun openDeveloperOptions` | [LegacyMarineController.kt:1418](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1418) |
| `fun openAlarmNotificationSettings` | [LegacyMarineController.kt:1419](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1419) |
| `fun openAlarmSoundSettings` | [LegacyMarineController.kt:1420](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1420) |
| `fun openDoNotDisturbSettings` | [LegacyMarineController.kt:1423](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1423) |
| `fun openBatteryOptimization` | [LegacyMarineController.kt:1426](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1426) |
| `fun openFullScreenAlarmSettings` | [LegacyMarineController.kt:1427](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1427) |
| `fun openAnchorInGoogleMaps` | [LegacyMarineController.kt:1431](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1431) |
| `fun openAnchorageInGoogleMaps` | [LegacyMarineController.kt:1435](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1435) |
| `fun openAnchorageCoordinates` | [LegacyMarineController.kt:1436](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1436) |
| `fun approachAnchorageSpot` | [LegacyMarineController.kt:1437](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1437) |
| `fun approachSavedAnchorage` | [LegacyMarineController.kt:1444](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1444) |
| `fun approachAnchorage` | [LegacyMarineController.kt:1448](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1448) |
| `fun confirmAnchorageApproachDisclaimer` | [LegacyMarineController.kt:1457](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1457) |
| `fun dismissAnchorageApproachDisclaimer` | [LegacyMarineController.kt:1468](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1468) |
| `fun setApproachHeadingMode` | [LegacyMarineController.kt:1479](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1479) |
| `fun cancelAnchorageApproach` | [LegacyMarineController.kt:1486](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1486) |
| `fun setPhoneHeadingDisplayActive` | [LegacyMarineController.kt:1488](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1488) |
| `fun setMapHeadingDisplayActive` | [LegacyMarineController.kt:1489](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1489) |
| `fun dismissNearbyAnchorage` | [LegacyMarineController.kt:1490](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1490) |
| `fun shareAnchorageQr` | [LegacyMarineController.kt:1499](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1499) |
| `fun page` | [LegacyMarineController.kt:1514](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1514) |
| `fun rememberAnchorSection` | [LegacyMarineController.kt:1515](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1515) |
| `fun rememberSailSection` | [LegacyMarineController.kt:1516](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1516) |
| `fun openDataSection` | [LegacyMarineController.kt:1517](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1517) |
| `fun rememberDataSection` | [LegacyMarineController.kt:1518](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1518) |
| `fun follow` | [LegacyMarineController.kt:1519](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1519) |
| `fun requestRangeEditor` | [LegacyMarineController.kt:1520](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1520) |
| `fun consumeRangeEditorRequest` | [LegacyMarineController.kt:1521](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1521) |
| `fun loadHistoryEvents` | [LegacyMarineController.kt:1522](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1522) |
| `fun saveAnchorage` | [LegacyMarineController.kt:1523](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1523) |
| `fun dismissAnchorageDuplicate` | [LegacyMarineController.kt:1532](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1532) |
| `fun deleteAnchorage` | [LegacyMarineController.kt:1533](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1533) |
| `fun dismissAnchorageOperationError` | [LegacyMarineController.kt:1542](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1542) |
| `fun exportCsv` | [LegacyMarineController.kt:1543](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1543) |
| `fun exportGpx` | [LegacyMarineController.kt:1552](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/LegacyMarineController.kt#L1552) |

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
| `class LocalMarineContentService @Inject constructor` | [LocalMarineContentService.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L41) |
| `fun observePressureSources` | [LocalMarineContentService.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L71) |
| `fun observePressureHistory` | [LocalMarineContentService.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L81) |
| `fun import` | [LocalMarineContentService.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L91) |
| `fun delete` | [LocalMarineContentService.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L92) |
| `fun file` | [LocalMarineContentService.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L93) |
| `fun observeRecentAlarmEvents` | [LocalMarineContentService.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L96) |
| `fun observeCollectionMembers` | [LocalMarineContentService.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L98) |
| `fun anchorTrackPage` | [LocalMarineContentService.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L104) |
| `fun bundle` | [LocalMarineContentService.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L107) |
| `fun updatePlace` | [LocalMarineContentService.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L108) |
| `fun updateSpot` | [LocalMarineContentService.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L109) |
| `fun createSpot` | [LocalMarineContentService.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L111) |
| `fun archivePlace` | [LocalMarineContentService.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L119) |
| `fun restorePlace` | [LocalMarineContentService.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L127) |
| `fun createCollection` | [LocalMarineContentService.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L131) |
| `fun toggleCollection` | [LocalMarineContentService.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L139) |
| `fun saveAnchorage` | [LocalMarineContentService.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineContentService.kt#L147) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalMarineServices @Inject constructor` | [LocalMarineServices.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L41) |
| `fun onPermissionsChanged` | [LocalMarineServices.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L54) |
| `fun switchGpsDataSource` | [LocalMarineServices.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L55) |
| `fun selectNmeaPositionConnection` | [LocalMarineServices.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L56) |
| `fun setVesselMetricSource` | [LocalMarineServices.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L57) |
| `fun confirmTripAttitudeFrame` | [LocalMarineServices.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L58) |
| `fun alignPhoneHeadingToBow` | [LocalMarineServices.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L59) |
| `fun alignPhoneHeadingToNmea` | [LocalMarineServices.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L60) |
| `fun confirmFixedPhoneMount` | [LocalMarineServices.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L61) |
| `fun setPhoneHeadingAlignment` | [LocalMarineServices.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L62) |
| `fun setPhoneAttitudeAlignment` | [LocalMarineServices.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L63) |
| `fun invalidateFixedPhoneMount` | [LocalMarineServices.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L64) |
| `fun clearVesselCalibrationFeedback` | [LocalMarineServices.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L65) |
| `fun commandReceipt` | [LocalMarineServices.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L70) |
| `fun requestCommand` | [LocalMarineServices.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L71) |
| `fun recheckCommand` | [LocalMarineServices.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L72) |
| `fun startTrip` | [LocalMarineServices.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L74) |
| `fun pauseTrip` | [LocalMarineServices.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L75) |
| `fun resumeTrip` | [LocalMarineServices.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L76) |
| `fun pauseTripAttitude` | [LocalMarineServices.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L77) |
| `fun endTrip` | [LocalMarineServices.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L78) |
| `fun captureMoment` | [LocalMarineServices.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L80) |
| `fun restoreMoment` | [LocalMarineServices.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L81) |
| `fun retryMoment` | [LocalMarineServices.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L82) |
| `fun editCapturedMoment` | [LocalMarineServices.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L83) |
| `fun markTripWaypoint` | [LocalMarineServices.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L84) |
| `fun deleteTrip` | [LocalMarineServices.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L85) |
| `fun renameTrip` | [LocalMarineServices.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L86) |
| `fun editTripMoment` | [LocalMarineServices.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L87) |
| `fun tripReport` | [LocalMarineServices.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L88) |
| `fun tripReplay` | [LocalMarineServices.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L89) |
| `fun tripMapData` | [LocalMarineServices.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L90) |
| `fun exportTripCsv` | [LocalMarineServices.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L91) |
| `fun exportTripGpx` | [LocalMarineServices.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L92) |
| `fun exportTripKml` | [LocalMarineServices.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L93) |
| `fun exportTripKmz` | [LocalMarineServices.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L94) |
| `fun exportTripEvents` | [LocalMarineServices.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L95) |
| `fun exportTripWaypoints` | [LocalMarineServices.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L96) |
| `fun exportTripCustomMetrics` | [LocalMarineServices.kt:97](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L97) |
| `fun shareTripReportSnapshot` | [LocalMarineServices.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L98) |
| `fun exportTripAiSource` | [LocalMarineServices.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L99) |
| `fun saveAnchorSetupDraft` | [LocalMarineServices.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L104) |
| `fun clearAnchorSetupDraft` | [LocalMarineServices.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L105) |
| `fun arm` | [LocalMarineServices.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L106) |
| `fun requestArm` | [LocalMarineServices.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L107) |
| `fun requestPauseWatch` | [LocalMarineServices.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L108) |
| `fun requestResumeWatch` | [LocalMarineServices.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L109) |
| `fun requestLiftAnchor` | [LocalMarineServices.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L110) |
| `fun updateAnchorSettings` | [LocalMarineServices.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L111) |
| `fun updateConditionGuards` | [LocalMarineServices.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L112) |
| `fun pauseWatch` | [LocalMarineServices.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L113) |
| `fun resumeWatch` | [LocalMarineServices.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L114) |
| `fun liftAnchor` | [LocalMarineServices.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L115) |
| `fun acknowledge` | [LocalMarineServices.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L116) |
| `fun keepCurrentCenter` | [LocalMarineServices.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L117) |
| `fun continueEstimatingCenter` | [LocalMarineServices.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L118) |
| `fun recalculateCentreFromTrack` | [LocalMarineServices.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L119) |
| `fun keepCurrentRecalculatedCentre` | [LocalMarineServices.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L120) |
| `fun acceptEstimatedCenter` | [LocalMarineServices.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L121) |
| `fun applyRecalculatedCentre` | [LocalMarineServices.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L122) |
| `fun loadHistoryEvents` | [LocalMarineServices.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L123) |
| `fun exportCsv` | [LocalMarineServices.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L124) |
| `fun exportGpx` | [LocalMarineServices.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L125) |
| `fun saveNmeaConnection` | [LocalMarineServices.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L131) |
| `fun startNmeaConnection` | [LocalMarineServices.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L132) |
| `fun stopNmeaConnection` | [LocalMarineServices.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L133) |
| `fun removeNmeaConnection` | [LocalMarineServices.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L134) |
| `fun saveAndConnect` | [LocalMarineServices.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L135) |
| `fun disconnect` | [LocalMarineServices.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L136) |
| `fun setNmeaSharing` | [LocalMarineServices.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L141) |
| `fun saveLocalNmeaPublicationPolicy` | [LocalMarineServices.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L142) |
| `fun startLocalNmeaServer` | [LocalMarineServices.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L143) |
| `fun stopLocalNmeaServer` | [LocalMarineServices.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L144) |
| `fun stopAllNmeaSharing` | [LocalMarineServices.kt:145](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L145) |
| `fun setLanguage` | [LocalMarineServices.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L150) |
| `fun setVesselGeometry` | [LocalMarineServices.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L151) |
| `fun setVesselIdentity` | [LocalMarineServices.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L153) |
| `fun setPassageGeometry` | [LocalMarineServices.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L154) |
| `fun setAlarmSound` | [LocalMarineServices.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L155) |
| `fun setAlarmSnoozeMinutes` | [LocalMarineServices.kt:156](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L156) |
| `fun setInstrumentLayout` | [LocalMarineServices.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L157) |
| `fun exportBackup` | [LocalMarineServices.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L158) |
| `fun restoreBackup` | [LocalMarineServices.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L159) |
| `fun clearBackupResult` | [LocalMarineServices.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L160) |
| `fun confirmAlarmAudible` | [LocalMarineServices.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L161) |
| `fun testAlarm` | [LocalMarineServices.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L162) |
| `fun stopAlarmTest` | [LocalMarineServices.kt:163](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L163) |
| `fun openAlarmSoundSettings` | [LocalMarineServices.kt:164](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L164) |
| `fun openDoNotDisturbSettings` | [LocalMarineServices.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L165) |
| `fun acknowledgePresentation` | [LocalMarineServices.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L172) |
| `fun consumeRuntimeFeedback` | [LocalMarineServices.kt:173](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L173) |
| `fun acquireMapHeading` | [LocalMarineServices.kt:179](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L179) |
| `fun acquireInstruments` | [LocalMarineServices.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L180) |
| `fun acquireDeviceViewOrientation` | [LocalMarineServices.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/LocalMarineServices.kt#L182) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineLibrarySnapshot` | [MarineContentService.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L16) |
| `class MarinePressureSource` | [MarineContentService.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L25) |
| `class MarinePressurePoint` | [MarineContentService.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L26) |
| `interface MarinePhotoService` | [MarineContentService.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L31) |
| `fun import` | [MarineContentService.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L32) |
| `fun delete` | [MarineContentService.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L33) |
| `fun file` | [MarineContentService.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L34) |
| `interface MarineContentService` | [MarineContentService.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L42) |
| `fun observePressureSources` | [MarineContentService.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L48) |
| `fun observePressureHistory` | [MarineContentService.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L49) |
| `fun observeRecentAlarmEvents` | [MarineContentService.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L50) |
| `fun observeCollectionMembers` | [MarineContentService.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L51) |
| `fun anchorTrackPage` | [MarineContentService.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L53) |
| `fun bundle` | [MarineContentService.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L54) |
| `fun updatePlace` | [MarineContentService.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L55) |
| `fun updateSpot` | [MarineContentService.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L56) |
| `fun createSpot` | [MarineContentService.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L57) |
| `fun archivePlace` | [MarineContentService.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L58) |
| `fun restorePlace` | [MarineContentService.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L59) |
| `fun createCollection` | [MarineContentService.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L60) |
| `fun toggleCollection` | [MarineContentService.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L61) |
| `fun saveAnchorage` | [MarineContentService.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineContentService.kt#L63) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarinePresentationRequest.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarinePresentationAction` | [MarinePresentationRequest.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarinePresentationRequest.kt#L6) |
| `class MarinePresentationRequest` | [MarinePresentationRequest.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarinePresentationRequest.kt#L7) |

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
| `fun confirmFixedPhoneMount` | [MarineServices.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L51) |
| `fun setPhoneHeadingAlignment` | [MarineServices.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L53) |
| `fun setPhoneAttitudeAlignment` | [MarineServices.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L55) |
| `fun invalidateFixedPhoneMount` | [MarineServices.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L57) |
| `fun clearVesselCalibrationFeedback` | [MarineServices.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L58) |
| `interface VoyageService : MarineStateReader` | [MarineServices.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L62) |
| `fun commandReceipt` | [MarineServices.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L66) |
| `fun requestCommand` | [MarineServices.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L68) |
| `fun recheckCommand` | [MarineServices.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L69) |
| `fun startTrip` | [MarineServices.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L70) |
| `fun pauseTrip` | [MarineServices.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L71) |
| `fun resumeTrip` | [MarineServices.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L72) |
| `fun pauseTripAttitude` | [MarineServices.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L73) |
| `fun endTrip` | [MarineServices.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L74) |
| `fun captureMoment` | [MarineServices.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L77) |
| `fun restoreMoment` | [MarineServices.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L78) |
| `fun retryMoment` | [MarineServices.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L79) |
| `fun editCapturedMoment` | [MarineServices.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L80) |
| `fun markTripWaypoint` | [MarineServices.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L81) |
| `fun deleteTrip` | [MarineServices.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L82) |
| `fun renameTrip` | [MarineServices.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L83) |
| `fun editTripMoment` | [MarineServices.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L84) |
| `fun tripReport` | [MarineServices.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L85) |
| `fun tripReplay` | [MarineServices.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L86) |
| `fun tripMapData` | [MarineServices.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L87) |
| `fun exportTripCsv` | [MarineServices.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L88) |
| `fun exportTripGpx` | [MarineServices.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L89) |
| `fun exportTripKml` | [MarineServices.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L90) |
| `fun exportTripKmz` | [MarineServices.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L91) |
| `fun exportTripEvents` | [MarineServices.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L92) |
| `fun exportTripWaypoints` | [MarineServices.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L93) |
| `fun exportTripCustomMetrics` | [MarineServices.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L94) |
| `fun shareTripReportSnapshot` | [MarineServices.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L95) |
| `fun exportTripAiSource` | [MarineServices.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L96) |
| `interface AnchorService : MarineStateReader` | [MarineServices.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L100) |
| `fun saveAnchorSetupDraft` | [MarineServices.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L101) |
| `fun clearAnchorSetupDraft` | [MarineServices.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L102) |
| `fun arm` | [MarineServices.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L103) |
| `fun requestArm` | [MarineServices.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L105) |
| `fun requestPauseWatch` | [MarineServices.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L106) |
| `fun requestResumeWatch` | [MarineServices.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L107) |
| `fun requestLiftAnchor` | [MarineServices.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L108) |
| `fun updateAnchorSettings` | [MarineServices.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L109) |
| `fun updateConditionGuards` | [MarineServices.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L110) |
| `fun pauseWatch` | [MarineServices.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L111) |
| `fun resumeWatch` | [MarineServices.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L112) |
| `fun liftAnchor` | [MarineServices.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L113) |
| `fun acknowledge` | [MarineServices.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L114) |
| `fun keepCurrentCenter` | [MarineServices.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L115) |
| `fun continueEstimatingCenter` | [MarineServices.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L116) |
| `fun recalculateCentreFromTrack` | [MarineServices.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L117) |
| `fun keepCurrentRecalculatedCentre` | [MarineServices.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L118) |
| `fun acceptEstimatedCenter` | [MarineServices.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L119) |
| `fun applyRecalculatedCentre` | [MarineServices.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L120) |
| `fun loadHistoryEvents` | [MarineServices.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L121) |
| `fun exportCsv` | [MarineServices.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L122) |
| `fun exportGpx` | [MarineServices.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L123) |
| `interface NetworkService : MarineStateReader` | [MarineServices.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L127) |
| `fun saveNmeaConnection` | [MarineServices.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L129) |
| `fun startNmeaConnection` | [MarineServices.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L130) |
| `fun stopNmeaConnection` | [MarineServices.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L131) |
| `fun removeNmeaConnection` | [MarineServices.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L132) |
| `fun saveAndConnect` | [MarineServices.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L133) |
| `fun disconnect` | [MarineServices.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L134) |
| `interface SharingService : MarineStateReader` | [MarineServices.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L138) |
| `fun setNmeaSharing` | [MarineServices.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L139) |
| `fun saveLocalNmeaPublicationPolicy` | [MarineServices.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L140) |
| `fun startLocalNmeaServer` | [MarineServices.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L141) |
| `fun stopLocalNmeaServer` | [MarineServices.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L142) |
| `fun stopAllNmeaSharing` | [MarineServices.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L143) |
| `interface VesselPreferencesService : MarineStateReader` | [MarineServices.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L150) |
| `fun setLanguage` | [MarineServices.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L151) |
| `fun setVesselGeometry` | [MarineServices.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L152) |
| `fun setVesselIdentity` | [MarineServices.kt:153](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L153) |
| `fun setPassageGeometry` | [MarineServices.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L154) |
| `fun setAlarmSound` | [MarineServices.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L155) |
| `fun setAlarmSnoozeMinutes` | [MarineServices.kt:156](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L156) |
| `fun setInstrumentLayout` | [MarineServices.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L157) |
| `fun exportBackup` | [MarineServices.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L158) |
| `fun restoreBackup` | [MarineServices.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L159) |
| `fun clearBackupResult` | [MarineServices.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L160) |
| `fun confirmAlarmAudible` | [MarineServices.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L161) |
| `fun testAlarm` | [MarineServices.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L162) |
| `fun stopAlarmTest` | [MarineServices.kt:163](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L163) |
| `fun openAlarmSoundSettings` | [MarineServices.kt:164](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L164) |
| `fun openDoNotDisturbSettings` | [MarineServices.kt:165](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L165) |
| `interface MarineFeedbackService : MarineStateReader` | [MarineServices.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L169) |
| `fun acknowledgePresentation` | [MarineServices.kt:173](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L173) |
| `fun consumeRuntimeFeedback` | [MarineServices.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L174) |
| `interface DisplayLease : AutoCloseable` | [MarineServices.kt:178](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L178) |
| `fun close` | [MarineServices.kt:179](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L179) |
| `interface DisplayDemandService` | [MarineServices.kt:183](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L183) |
| `fun acquireMapHeading` | [MarineServices.kt:184](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L184) |
| `fun acquireInstruments` | [MarineServices.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L185) |
| `fun acquireDeviceViewOrientation` | [MarineServices.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L188) |
| `interface MarineServices : MarineStateReader` | [MarineServices.kt:195](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/MarineServices.kt#L195) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/NavigationReadingProjection.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun VesselDataSnapshot.withNavigation` | [NavigationReadingProjection.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/NavigationReadingProjection.kt#L7) |
| `fun <T> observation` | [NavigationReadingProjection.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/NavigationReadingProjection.kt#L12) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Reading` | [ReadingHistoryService.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L10) |
| `fun fresh` | [ReadingHistoryService.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L22) |
| `class HistoryStorageState` | [ReadingHistoryService.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L25) |
| `class ReadingHistorySnapshot` | [ReadingHistoryService.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L34) |
| `class ReadingHistorySlice` | [ReadingHistoryService.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L41) |
| `interface ReadingHistoryService` | [ReadingHistoryService.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L42) |
| `fun slice` | [ReadingHistoryService.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L45) |
| `fun slices` | [ReadingHistoryService.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L47) |
| `fun retryStorage` | [ReadingHistoryService.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L48) |
| `fun flush` | [ReadingHistoryService.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/api/ReadingHistoryService.kt#L49) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AlarmUiRepository @Inject constructor` | [AlarmUiRepository.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L11) |
| `fun publish` | [AlarmUiRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L15) |
| `fun clear` | [AlarmUiRepository.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/AlarmUiRepository.kt#L16) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaInstrumentState` | [NavigationRepository.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L27) |
| `class NavigationRepository @Inject constructor` | [NavigationRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L41) |
| `fun positionSourcePin` | [NavigationRepository.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L69) |
| `fun positionConnectionId` | [NavigationRepository.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L70) |
| `fun saveConnection` | [NavigationRepository.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L121) |
| `fun cycle` | [NavigationRepository.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L132) |
| `fun removeConnection` | [NavigationRepository.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L140) |
| `fun startConnection` | [NavigationRepository.kt:141](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L141) |
| `fun stopConnection` | [NavigationRepository.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L172) |
| `fun reconnectConnection` | [NavigationRepository.kt:185](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L185) |
| `fun connectionEpoch` | [NavigationRepository.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L186) |
| `fun connectionPeer` | [NavigationRepository.kt:187](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L187) |
| `fun isConnectionOpen` | [NavigationRepository.kt:188](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L188) |
| `fun inputConnectionIds` | [NavigationRepository.kt:189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L189) |
| `fun anyRequested` | [NavigationRepository.kt:190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L190) |
| `fun sourcesCurrent` | [NavigationRepository.kt:191](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L191) |
| `fun writeConnection` | [NavigationRepository.kt:192](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L192) |
| `fun publishInputCandidates` | [NavigationRepository.kt:214](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L214) |
| `fun recordDroppedOutput` | [NavigationRepository.kt:221](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L221) |
| `fun identify` | [NavigationRepository.kt:275](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L275) |
| `fun positionSelectionReady` | [NavigationRepository.kt:317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L317) |
| `fun selectPositionConnection` | [NavigationRepository.kt:328](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L328) |
| `fun ensurePositionConnection` | [NavigationRepository.kt:335](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L335) |
| `fun connectionPriorities` | [NavigationRepository.kt:340](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L340) |
| `fun number` | [NavigationRepository.kt:349](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L349) |
| `fun currentHeading` | [NavigationRepository.kt:350](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L350) |
| `fun wind` | [NavigationRepository.kt:359](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L359) |
| `fun connect` | [NavigationRepository.kt:374](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L374) |
| `fun reconnect` | [NavigationRepository.kt:375](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L375) |
| `fun disconnect` | [NavigationRepository.kt:376](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L376) |
| `fun disconnectAll` | [NavigationRepository.kt:377](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L377) |
| `fun acquireBackgroundConnection` | [NavigationRepository.kt:381](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L381) |
| `fun claimBackgroundConnectionIfConnected` | [NavigationRepository.kt:386](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L386) |
| `fun releaseBackgroundConnection` | [NavigationRepository.kt:387](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L387) |
| `fun clearUserDisconnectLatch` | [NavigationRepository.kt:388](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L388) |
| `fun setSafetyOwnedRetry` | [NavigationRepository.kt:389](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L389) |
| `fun isUserDisconnected` | [NavigationRepository.kt:390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L390) |
| `fun hasOpenTransport` | [NavigationRepository.kt:391](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L391) |
| `fun activeProfileStableId` | [NavigationRepository.kt:392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L392) |
| `fun connectionGeneration` | [NavigationRepository.kt:393](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L393) |
| `fun pinBoatHeadingSource` | [NavigationRepository.kt:394](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L394) |
| `fun clearDiagnostics` | [NavigationRepository.kt:395](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L395) |
| `fun writeToBoat` | [NavigationRepository.kt:396](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L396) |
| `fun writeToBoatExpected` | [NavigationRepository.kt:397](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L397) |
| `fun accept` | [NavigationRepository.kt:403](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/NavigationRepository.kt#L403) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageApproachRepository @Inject constructor` | [AnchorageApproachRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L24) |
| `fun save` | [AnchorageApproachRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L41) |
| `fun delete` | [AnchorageApproachRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageApproachRepository.kt#L59) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun rebuild` | [AnchorageIntelligenceRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L17) |
| `fun rebuildAll` | [AnchorageIntelligenceRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L22) |
| `fun decode` | [AnchorageIntelligenceRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageIntelligenceRepository.kt#L23) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageLegacyMigrationHealth` | [AnchorageLegacyMigrationVerifier.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt#L11) |
| `fun verifyOnce` | [AnchorageLegacyMigrationVerifier.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLegacyMigrationVerifier.kt#L14) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchoragePlaceBundle` | [AnchorageLibraryRepository.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L13) |
| `fun get` | [AnchorageLibraryRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L30) |
| `fun save` | [AnchorageLibraryRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L31) |
| `fun delete` | [AnchorageLibraryRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L34) |
| `fun get` | [AnchorageLibraryRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L39) |
| `fun forPlace` | [AnchorageLibraryRepository.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L40) |
| `fun save` | [AnchorageLibraryRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L41) |
| `fun delete` | [AnchorageLibraryRepository.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L45) |
| `fun save` | [AnchorageLibraryRepository.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L49) |
| `fun forPlace` | [AnchorageLibraryRepository.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L55) |
| `fun bundle` | [AnchorageLibraryRepository.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L65) |
| `fun viewport` | [AnchorageLibraryRepository.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L70) |
| `fun nearby` | [AnchorageLibraryRepository.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L71) |
| `class AnchorageNearbyPlace` | [AnchorageLibraryRepository.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageLibraryRepository.kt#L78) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchoragePhotoRepository @Inject constructor` | [AnchoragePhotoRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L24) |
| `fun import` | [AnchoragePhotoRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L30) |
| `fun delete` | [AnchoragePhotoRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L59) |
| `fun cleanupOrphans` | [AnchoragePhotoRepository.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L65) |
| `fun file` | [AnchoragePhotoRepository.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchoragePhotoRepository.kt#L70) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SeabedType` | [AnchorageRepository.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L14) |
| `class AnchorageCoordinateSource` | [AnchorageRepository.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L15) |
| `class AnchorageSavePosition` | [AnchorageRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L17) |
| `object AnchorageSavePositionPolicy` | [AnchorageRepository.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L19) |
| `fun resolve` | [AnchorageRepository.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L20) |
| `class DuplicateAnchorageException` | [AnchorageRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L31) |
| `fun get` | [AnchorageRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L39) |
| `fun nearby` | [AnchorageRepository.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L40) |
| `fun duplicate` | [AnchorageRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L41) |
| `fun save` | [AnchorageRepository.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L43) |
| `fun saveFromSession` | [AnchorageRepository.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L52) |
| `fun delete` | [AnchorageRepository.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageRepository.kt#L57) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorageSavePlaceInput` | [AnchorageSaveRepository.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L14) |
| `class AnchorageSaveSpotInput` | [AnchorageSaveRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L25) |
| `class AnchorageSaveRequest` | [AnchorageSaveRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L31) |
| `class AnchorageSaveResult` | [AnchorageSaveRepository.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L32) |
| `object AnchorageSaveDraftFactory` | [AnchorageSaveRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L34) |
| `fun fromSession` | [AnchorageSaveRepository.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L35) |
| `fun fromMap` | [AnchorageSaveRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L39) |
| `class AnchorageSaveRepository @Inject constructor` | [AnchorageSaveRepository.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L42) |
| `fun nearbyPlaceMatches` | [AnchorageSaveRepository.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L50) |
| `fun nearbySpotMatches` | [AnchorageSaveRepository.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L54) |
| `fun save` | [AnchorageSaveRepository.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSaveRepository.kt#L56) |

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
| `class AnchorageSharePayloadV1` | [AnchorageSharePayloadCodec.kt:11](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L11) |
| `class EncodedAnchorageShareV1` | [AnchorageSharePayloadCodec.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L27) |
| `class AnchorageSharePayloadV2` | [AnchorageSharePayloadCodec.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L33) |
| `class EncodedAnchorageShareV2` | [AnchorageSharePayloadCodec.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L40) |
| `interface AnchorageQrDecodeResult` | [AnchorageSharePayloadCodec.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L42) |
| `class Full` | [AnchorageSharePayloadCodec.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L43) |
| `class FullV2` | [AnchorageSharePayloadCodec.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L44) |
| `class Coordinate` | [AnchorageSharePayloadCodec.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L45) |
| `class UnsupportedVersion` | [AnchorageSharePayloadCodec.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L46) |
| `class Invalid` | [AnchorageSharePayloadCodec.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L47) |
| `object Unsupported:AnchorageQrDecodeResult` | [AnchorageSharePayloadCodec.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L48) |
| `object AnchorageSharePayloadCodec` | [AnchorageSharePayloadCodec.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L52) |
| `fun encode` | [AnchorageSharePayloadCodec.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L55) |
| `fun pack` | [AnchorageSharePayloadCodec.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L74) |
| `fun encodeV2` | [AnchorageSharePayloadCodec.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L94) |
| `fun decode` | [AnchorageSharePayloadCodec.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L104) |
| `fun toEntity` | [AnchorageSharePayloadCodec.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L136) |
| `fun coordinateEntity` | [AnchorageSharePayloadCodec.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/anchorage/AnchorageSharePayloadCodec.kt#L144) |

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
| `class BackupRecordV1` | [YokuliBackupManager.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L93) |
| `class BackupSettingsV1` | [YokuliBackupManager.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L94) |
| `class BackupValidation` | [YokuliBackupManager.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L100) |
| `object BackupRestorePolicy` | [YokuliBackupManager.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L136) |
| `fun blockingReason` | [YokuliBackupManager.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L137) |
| `object BackupExternalSettingsPolicy` | [YokuliBackupManager.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L151) |
| `fun reconcileOfflineMap` | [YokuliBackupManager.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L152) |
| `object YokuliBackupArchive` | [YokuliBackupManager.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L157) |
| `fun requiredFor` | [YokuliBackupManager.kt:202](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L202) |
| `fun sha256` | [YokuliBackupManager.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L207) |
| `fun validateCoordinate` | [YokuliBackupManager.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L213) |
| `class YokuliBackupManager @Inject constructor` | [YokuliBackupManager.kt:220](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L220) |
| `fun export` | [YokuliBackupManager.kt:244](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L244) |
| `fun restore` | [YokuliBackupManager.kt:339](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L339) |
| `fun clearResult` | [YokuliBackupManager.kt:418](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L418) |
| `fun normalizeVersion` | [YokuliBackupManager.kt:466](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt#L466) |

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
| `fun insertCommandSession` | [Database.kt:383](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L383) |
| `fun sessionForStartCommand` | [Database.kt:390](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L390) |
| `fun sessionForCommandEffect` | [Database.kt:391](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L391) |
| `fun insertCommandEffect` | [Database.kt:392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L392) |
| `fun updateCommandSession` | [Database.kt:396](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L396) |
| `fun updateSession` | [Database.kt:399](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L399) |
| `fun updateSessionAndInsertEvent` | [Database.kt:400](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L400) |
| `fun active` | [Database.kt:401](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L401) |
| `fun session` | [Database.kt:402](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L402) |
| `fun sessions` | [Database.kt:403](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L403) |
| `fun deleteCompletedSessionRow` | [Database.kt:406](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L406) |
| `fun deleteCompletedSession` | [Database.kt:407](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L407) |
| `fun insertPoint` | [Database.kt:408](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L408) |
| `fun points` | [Database.kt:409](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L409) |
| `fun insertEvent` | [Database.kt:413](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L413) |
| `fun events` | [Database.kt:414](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L414) |
| `fun observeRecentAlarmEvents` | [Database.kt:416](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L416) |
| `fun recentEvents` | [Database.kt:417](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L417) |
| `fun allSessionsNow` | [Database.kt:419](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L419) |
| `fun allPointsPage` | [Database.kt:420](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L420) |
| `fun allPointsPageThrough` | [Database.kt:421](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L421) |
| `fun allEventsPage` | [Database.kt:422](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L422) |
| `fun allEventsPageThrough` | [Database.kt:423](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L423) |
| `fun importSessions` | [Database.kt:429](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L429) |
| `fun importPoints` | [Database.kt:430](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L430) |
| `fun importEvents` | [Database.kt:431](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L431) |
| `fun clearEvents` | [Database.kt:432](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L432) |
| `fun clearPoints` | [Database.kt:433](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L433) |
| `fun clearSessions` | [Database.kt:434](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L434) |
| `interface AnchorageDao` | [Database.kt:438](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L438) |
| `fun get` | [Database.kt:440](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L440) |
| `fun insert` | [Database.kt:441](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L441) |
| `fun update` | [Database.kt:442](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L442) |
| `fun delete` | [Database.kt:443](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L443) |
| `fun allNow` | [Database.kt:444](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L444) |
| `fun importAll` | [Database.kt:445](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L445) |
| `fun clear` | [Database.kt:446](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L446) |
| `interface SonarDao` | [Database.kt:450](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L450) |
| `fun insertSurvey` | [Database.kt:451](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L451) |
| `fun updateSurvey` | [Database.kt:452](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L452) |
| `fun active` | [Database.kt:453](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L453) |
| `fun survey` | [Database.kt:454](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L454) |
| `fun surveys` | [Database.kt:455](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L455) |
| `fun samples` | [Database.kt:456](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L456) |
| `fun normalizedHistory` | [Database.kt:457](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L457) |
| `fun usableSamples` | [Database.kt:458](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L458) |
| `fun samplesNow` | [Database.kt:459](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L459) |
| `fun insertSample` | [Database.kt:462](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L462) |
| `fun updateSamples` | [Database.kt:463](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L463) |
| `fun rename` | [Database.kt:465](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L465) |
| `fun finish` | [Database.kt:466](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L466) |
| `fun incrementSampleCount` | [Database.kt:468](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L468) |
| `fun insertSampleAndIncrement` | [Database.kt:469](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L469) |
| `fun deleteCompleted` | [Database.kt:470](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L470) |
| `fun usableSamplesInCell` | [Database.kt:471](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L471) |
| `fun correctedSamplesInCell` | [Database.kt:472](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L472) |
| `fun correctedCellsForSurvey` | [Database.kt:473](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L473) |
| `fun usableCellsForSurvey` | [Database.kt:474](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L474) |
| `fun allCorrectedCells` | [Database.kt:475](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L475) |
| `fun gridCellsNow` | [Database.kt:480](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L480) |
| `fun upsertGridCell` | [Database.kt:482](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L482) |
| `fun deleteGridCell` | [Database.kt:483](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L483) |
| `fun deleteGridScope` | [Database.kt:484](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L484) |
| `fun allSurveysNow` | [Database.kt:485](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L485) |
| `fun allSamplesPage` | [Database.kt:486](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L486) |
| `fun allSamplesPageThrough` | [Database.kt:487](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L487) |
| `fun importSurveys` | [Database.kt:490](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L490) |
| `fun importSamples` | [Database.kt:491](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L491) |
| `fun clearGridCells` | [Database.kt:492](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L492) |
| `fun clearSamples` | [Database.kt:493](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L493) |
| `fun clearSurveys` | [Database.kt:494](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L494) |
| `class GridCoordinate` | [Database.kt:497](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L497) |
| `interface LinzDepthCacheDao` | [Database.kt:500](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L500) |
| `fun get` | [Database.kt:501](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L501) |
| `fun upsert` | [Database.kt:502](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L502) |
| `fun prune` | [Database.kt:503](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L503) |
| `fun clear` | [Database.kt:504](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L504) |
| `interface TidePredictionCacheDao` | [Database.kt:508](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L508) |
| `fun get` | [Database.kt:509](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L509) |
| `fun upsert` | [Database.kt:510](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L510) |
| `fun clear` | [Database.kt:511](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L511) |
| `interface IncidentLogDao` | [Database.kt:515](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L515) |
| `fun insert` | [Database.kt:516](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L516) |
| `fun recent` | [Database.kt:517](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L517) |
| `fun since` | [Database.kt:518](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L518) |
| `fun deleteOlderThan` | [Database.kt:520](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L520) |
| `fun clear` | [Database.kt:522](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L522) |
| `class PressureHistoryEntity` | [Database.kt:530](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L530) |
| `class PressureHistorySourceRow` | [Database.kt:542](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L542) |
| `interface PressureHistoryDao` | [Database.kt:545](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L545) |
| `fun sourcesSince` | [Database.kt:547](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L547) |
| `fun sourceSince` | [Database.kt:549](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L549) |
| `fun insertObservation` | [Database.kt:551](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L551) |
| `fun observation` | [Database.kt:553](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L553) |
| `fun since` | [Database.kt:554](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L554) |
| `fun prune` | [Database.kt:555](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L555) |
| `interface TripDao` | [Database.kt:560](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L560) |
| `fun renameCompleted` | [Database.kt:562](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L562) |
| `fun insertSession` | [Database.kt:563](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L563) |
| `fun updateSession` | [Database.kt:564](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L564) |
| `fun insertSessionAndEvent` | [Database.kt:565](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L565) |
| `fun sessionForStartCommand` | [Database.kt:567](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L567) |
| `fun sessionForCommandEffect` | [Database.kt:568](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L568) |
| `fun insertCommandEffect` | [Database.kt:569](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L569) |
| `fun updateCommandSession` | [Database.kt:572](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L572) |
| `fun updateSessionAndInsertEvent` | [Database.kt:575](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L575) |
| `fun updateSessionAndInsertEventAndWaypoint` | [Database.kt:576](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L576) |
| `fun active` | [Database.kt:577](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L577) |
| `fun activeFlow` | [Database.kt:578](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L578) |
| `fun sessions` | [Database.kt:579](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L579) |
| `fun allSessionsNow` | [Database.kt:580](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L580) |
| `fun session` | [Database.kt:581](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L581) |
| `fun insertSamples` | [Database.kt:582](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L582) |
| `fun insertRecoveredSamples` | [Database.kt:584](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L584) |
| `fun commitRecordingBatch` | [Database.kt:586](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L586) |
| `fun greater` | [Database.kt:591](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L591) |
| `fun smaller` | [Database.kt:592](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L592) |
| `fun capturedMoment` | [Database.kt:601](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L601) |
| `fun updateCapturedMoment` | [Database.kt:602](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L602) |
| `fun insertCapturedMoment` | [Database.kt:603](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L603) |
| `fun insertEvent` | [Database.kt:604](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L604) |
| `fun insertWaypoint` | [Database.kt:605](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L605) |
| `fun updateWaypoint` | [Database.kt:606](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L606) |
| `fun latestWaypoint` | [Database.kt:607](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L607) |
| `fun insertAnchorTelemetry` | [Database.kt:608](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L608) |
| `fun insertCustomMetrics` | [Database.kt:609](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L609) |
| `fun upsertDashboard` | [Database.kt:610](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L610) |
| `fun dashboards` | [Database.kt:611](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L611) |
| `fun allDashboardsNow` | [Database.kt:612](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L612) |
| `fun dashboard` | [Database.kt:613](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L613) |
| `fun deleteDashboard` | [Database.kt:614](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L614) |
| `fun updateDashboardSort` | [Database.kt:615](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L615) |
| `fun samples` | [Database.kt:616](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L616) |
| `fun events` | [Database.kt:623](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L623) |
| `fun waypoints` | [Database.kt:625](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L625) |
| `fun customMetrics` | [Database.kt:626](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L626) |
| `fun anchorTelemetry` | [Database.kt:628](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L628) |
| `fun allSamplesPageThrough` | [Database.kt:635](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L635) |
| `fun allEventsPageThrough` | [Database.kt:636](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L636) |
| `fun allWaypointsPageThrough` | [Database.kt:637](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L637) |
| `fun allCustomMetricsPageThrough` | [Database.kt:638](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L638) |
| `fun allAnchorTelemetryPageThrough` | [Database.kt:639](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L639) |
| `fun importSessions` | [Database.kt:640](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L640) |
| `fun importSamples` | [Database.kt:641](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L641) |
| `fun importEvents` | [Database.kt:642](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L642) |
| `fun importWaypoints` | [Database.kt:643](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L643) |
| `fun importCustomMetrics` | [Database.kt:644](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L644) |
| `fun importDashboards` | [Database.kt:645](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L645) |
| `fun importAnchorTelemetry` | [Database.kt:646](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L646) |
| `fun clearSamples` | [Database.kt:647](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L647) |
| `fun clearEvents` | [Database.kt:648](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L648) |
| `fun clearWaypoints` | [Database.kt:649](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L649) |
| `fun clearCustomMetrics` | [Database.kt:650](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L650) |
| `fun clearDashboards` | [Database.kt:651](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L651) |
| `fun clearAnchorTelemetry` | [Database.kt:652](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L652) |
| `fun clearSessions` | [Database.kt:653](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L653) |
| `fun deleteCompleted` | [Database.kt:654](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L654) |
| `class AppDatabase : RoomDatabase` | [Database.kt:663](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L663) |
| `fun anchorDao` | [Database.kt:664](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L664) |
| `fun anchorageDao` | [Database.kt:665](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L665) |
| `fun sonarDao` | [Database.kt:666](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L666) |
| `fun linzDepthCacheDao` | [Database.kt:667](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L667) |
| `fun tidePredictionCacheDao` | [Database.kt:668](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L668) |
| `fun incidentLogDao` | [Database.kt:669](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L669) |
| `fun pressureHistoryDao` | [Database.kt:670](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L670) |
| `fun tripDao` | [Database.kt:671](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L671) |
| `fun anchorageRegionDao` | [Database.kt:672](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L672) |
| `fun anchoragePlaceDao` | [Database.kt:673](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L673) |
| `fun anchorageSpotDao` | [Database.kt:674](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L674) |
| `fun anchorageVisitDao` | [Database.kt:675](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L675) |
| `fun anchorageCollectionDao` | [Database.kt:676](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L676) |
| `fun anchorageMetadataDao` | [Database.kt:677](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L677) |
| `fun anchoragePhotoDao` | [Database.kt:678](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L678) |
| `fun anchorageSearchDao` | [Database.kt:679](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L679) |
| `fun anchorageSpatialDao` | [Database.kt:680](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt#L680) |

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
| `object Migration22To23:Migration` | [Migrations.kt:382](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L382) |
| `fun migrate` | [Migrations.kt:383](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Migrations.kt#L383) |

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
| `class IncidentSeverity` | [IncidentDiagnostics.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L41) |
| `fun record` | [IncidentDiagnostics.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L57) |
| `fun recordNow` | [IncidentDiagnostics.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L65) |
| `fun exception` | [IncidentDiagnostics.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L92) |
| `class StorageHealth` | [IncidentDiagnostics.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L127) |
| `class SupportBundleState` | [IncidentDiagnostics.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L139) |
| `class StorageHealthRepository @Inject constructor` | [IncidentDiagnostics.kt:146](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L146) |
| `fun snapshot` | [IncidentDiagnostics.kt:154](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L154) |
| `fun clearRebuildableCaches` | [IncidentDiagnostics.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L169) |
| `fun clearIncidentLog` | [IncidentDiagnostics.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L182) |
| `class SupportBundleManager @Inject constructor` | [IncidentDiagnostics.kt:193](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L193) |
| `fun export` | [IncidentDiagnostics.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L205) |
| `fun count` | [IncidentDiagnostics.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L213) |
| `fun clearResult` | [IncidentDiagnostics.kt:269](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/diagnostics/IncidentDiagnostics.kt#L269) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripExportManager @Inject constructor` | [TripExportManager.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L31) |
| `fun csv` | [TripExportManager.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L38) |
| `fun gpx` | [TripExportManager.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L40) |
| `fun eventsCsv` | [TripExportManager.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L53) |
| `fun waypointsCsv` | [TripExportManager.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L54) |
| `fun customMetricsCsv` | [TripExportManager.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L55) |
| `fun kml` | [TripExportManager.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L57) |
| `fun kmz` | [TripExportManager.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L58) |
| `fun liveSnapshot` | [TripExportManager.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L60) |
| `fun reportSnapshot` | [TripExportManager.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L63) |
| `fun aiZip` | [TripExportManager.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L65) |
| `fun anchorAiZip` | [TripExportManager.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L78) |
| `fun value` | [TripExportManager.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L101) |
| `fun source` | [TripExportManager.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/export/TripExportManager.kt#L102) |

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
| `fun shouldQuery` | [LinzDepthReferenceRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L22) |
| `fun record` | [LinzDepthReferenceRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L23) |
| `object LinzFinalResultCachePolicy` | [LinzDepthReferenceRepository.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L26) |
| `fun isNear` | [LinzDepthReferenceRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L28) |
| `fun canReuseFresh` | [LinzDepthReferenceRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L30) |
| `fun refresh` | [LinzDepthReferenceRepository.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/linz/LinzDepthReferenceRepository.kt#L39) |

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
| `class Protocol` | [NmeaConnection.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L12) |
| `class ConnectionProfile` | [NmeaConnection.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L13) |
| `class NmeaConnectionRetryPolicy` | [NmeaConnection.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L15) |
| `class NmeaTransportDiagnostics` | [NmeaConnection.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L23) |
| `object NmeaSafetyRetryPolicy` | [NmeaConnection.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L40) |
| `fun delayMillis` | [NmeaConnection.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L42) |
| `class NmeaTransportWriteFailure` | [NmeaConnection.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L45) |
| `class NmeaTransportWriteResult` | [NmeaConnection.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L47) |
| `object NmeaWireBatch` | [NmeaConnection.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L58) |
| `fun encode` | [NmeaConnection.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L59) |
| `class NmeaConnectionManager` | [NmeaConnection.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L62) |
| `fun setSafetyOwnedRetry` | [NmeaConnection.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L83) |
| `fun connect` | [NmeaConnection.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L84) |
| `fun ensureConnected` | [NmeaConnection.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L93) |
| `fun reconnect` | [NmeaConnection.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L99) |
| `fun write` | [NmeaConnection.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L105) |
| `fun writeExpected` | [NmeaConnection.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L108) |
| `fun hasOpenTransport` | [NmeaConnection.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L133) |
| `fun remotePeer` | [NmeaConnection.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L135) |
| `fun disconnect` | [NmeaConnection.kt:136](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L136) |
| `fun reportValidFix` | [NmeaConnection.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L137) |
| `fun reportValidMarineData` | [NmeaConnection.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L139) |
| `fun reportStaleFix` | [NmeaConnection.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnection.kt#L140) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaConnectionSpec` | [NmeaConnections.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L16) |
| `fun profile` | [NmeaConnections.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L37) |
| `class NmeaFeed` | [NmeaConnections.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L40) |
| `class NmeaRawFrame` | [NmeaConnections.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L42) |
| `class NmeaConnectionSnapshot` | [NmeaConnections.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L53) |
| `fun requestedIds` | [NmeaConnections.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L74) |
| `fun setRequested` | [NmeaConnections.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L76) |
| `fun clearRequested` | [NmeaConnections.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L81) |
| `fun read` | [NmeaConnections.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L82) |
| `fun save` | [NmeaConnections.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L83) |
| `object NmeaConnectionLeasePolicy` | [NmeaConnections.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L86) |
| `fun restorable` | [NmeaConnections.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaConnections.kt#L87) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaChecksum` | [NmeaCore.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L14) |
| `fun validate` | [NmeaCore.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L15) |
| `fun append` | [NmeaCore.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L22) |
| `fun feed` | [NmeaCore.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L27) |
| `fun feed` | [NmeaCore.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L28) |
| `class NmeaWireEnvelope` | [NmeaCore.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L39) |
| `fun decode` | [NmeaCore.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L41) |
| `class NmeaMeasurementConfirmation` | [NmeaCore.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L69) |
| `class NmeaMetric` | [NmeaCore.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L70) |
| `class NmeaMetricTiming` | [NmeaCore.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L75) |
| `class NmeaUpdate` | [NmeaCore.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L81) |
| `fun measuredAt` | [NmeaCore.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L82) |
| `fun heartbeatAt` | [NmeaCore.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L83) |
| `fun confirmation` | [NmeaCore.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L84) |
| `fun isNumeric` | [NmeaCore.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L85) |
| `class NmeaUpdateRetainer` | [NmeaCore.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L98) |
| `fun accept` | [NmeaCore.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L100) |
| `fun timing` | [NmeaCore.kt:140](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L140) |
| `fun clear` | [NmeaCore.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L158) |
| `class Nmea0183Parser` | [NmeaCore.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L161) |
| `fun parseEnvelope` | [NmeaCore.kt:162](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L162) |
| `fun parse` | [NmeaCore.kt:167](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L167) |
| `class NmeaDiagnostics` | [NmeaCore.kt:229](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaCore.kt#L229) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun check` | [NmeaEndpointPreflight.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt#L17) |
| `fun validate` | [NmeaEndpointPreflight.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaEndpointPreflight.kt#L26) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaFieldSemantic` | [NmeaFieldRepository.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L22) |
| `class NmeaFieldKey` | [NmeaFieldRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L30) |
| `class NmeaFieldObservation` | [NmeaFieldRepository.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L40) |
| `fun isFresh` | [NmeaFieldRepository.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L51) |
| `class NmeaFieldHeartbeat` | [NmeaFieldRepository.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L54) |
| `fun accept` | [NmeaFieldRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L59) |
| `fun expire` | [NmeaFieldRepository.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L70) |
| `fun clear` | [NmeaFieldRepository.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L74) |
| `object NmeaFieldDecoder` | [NmeaFieldRepository.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L82) |
| `fun heartbeat` | [NmeaFieldRepository.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L83) |
| `fun decode` | [NmeaFieldRepository.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L103) |
| `fun number` | [NmeaFieldRepository.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L108) |
| `fun text` | [NmeaFieldRepository.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L112) |
| `fun signed` | [NmeaFieldRepository.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L113) |
| `fun trueBearing` | [NmeaFieldRepository.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L115) |
| `fun crossTrack` | [NmeaFieldRepository.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L116) |
| `class NmeaFieldRepository @Inject constructor` | [NmeaFieldRepository.kt:180](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L180) |
| `fun accept` | [NmeaFieldRepository.kt:197](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L197) |
| `fun semantic` | [NmeaFieldRepository.kt:198](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/NmeaFieldRepository.kt#L198) |

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
| `object NmeaCandidateMapper` | [NmeaCandidateMapper.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L8) |
| `fun map` | [NmeaCandidateMapper.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L9) |
| `fun <T> candidate` | [NmeaCandidateMapper.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/input/NmeaCandidateMapper.kt#L15) |

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
| `fun recordGenerated` | [NmeaDeviceOutputConnection.kt:358](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L358) |
| `fun recordDropped` | [NmeaDeviceOutputConnection.kt:369](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L369) |
| `fun recordSuppressed` | [NmeaDeviceOutputConnection.kt:370](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L370) |
| `fun recordDecision` | [NmeaDeviceOutputConnection.kt:371](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L371) |
| `fun refreshTransportState` | [NmeaDeviceOutputConnection.kt:373](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L373) |
| `fun configure` | [NmeaDeviceOutputConnection.kt:392](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L392) |
| `fun currentInputTransportGeneration` | [NmeaDeviceOutputConnection.kt:464](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L464) |
| `fun write` | [NmeaDeviceOutputConnection.kt:466](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L466) |
| `fun test` | [NmeaDeviceOutputConnection.kt:563](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L563) |
| `fun stop` | [NmeaDeviceOutputConnection.kt:574](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L574) |
| `class NmeaUdpClient @Inject constructor` | [NmeaDeviceOutputConnection.kt:591](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L591) |
| `fun write` | [NmeaDeviceOutputConnection.kt:593](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L593) |
| `fun close` | [NmeaDeviceOutputConnection.kt:597](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L597) |
| `class DedicatedNmeaWriteResult` | [NmeaDeviceOutputConnection.kt:600](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L600) |
| `class DedicatedNmeaTcpClient @Inject constructor` | [NmeaDeviceOutputConnection.kt:605](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L605) |
| `fun isConnected` | [NmeaDeviceOutputConnection.kt:613](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L613) |
| `fun write` | [NmeaDeviceOutputConnection.kt:616](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L616) |
| `fun write` | [NmeaDeviceOutputConnection.kt:622](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L622) |
| `fun close` | [NmeaDeviceOutputConnection.kt:654](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/nmea/output/NmeaDeviceOutputConnection.kt#L654) |

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
| `class NmeaOutputMux @Inject constructor` | [NmeaOutputMux.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L19) |
| `fun acceptedPosition` | [NmeaOutputMux.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L20) |
| `fun phonePosition` | [NmeaOutputMux.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L50) |
| `fun phoneHeading` | [NmeaOutputMux.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L59) |
| `fun phoneMagneticHeading` | [NmeaOutputMux.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L60) |
| `fun derivedTrueWind` | [NmeaOutputMux.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L64) |
| `fun diagnostic` | [NmeaOutputMux.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L68) |
| `fun diagnosticMagneticHeading` | [NmeaOutputMux.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L69) |
| `fun phoneRateOfTurn` | [NmeaOutputMux.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L70) |
| `fun canonicalDepth` | [NmeaOutputMux.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L71) |
| `fun canonicalSpeedThroughWater` | [NmeaOutputMux.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L75) |
| `fun phoneXdr` | [NmeaOutputMux.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L79) |
| `fun selectedXdr` | [NmeaOutputMux.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L81) |
| `fun phoneProprietary` | [NmeaOutputMux.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L89) |
| `fun sentenceType` | [NmeaOutputMux.kt:94](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaOutputMux.kt#L94) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NmeaSelfLoopPolicy` | [NmeaSelfLoopPolicy.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt#L6) |
| `fun isLiteralLoop` | [NmeaSelfLoopPolicy.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSelfLoopPolicy.kt#L9) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt

| 声明 | 实现位置 |
| --- | --- |
| `class SharingServerState` | [NmeaSharingServer.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L29) |
| `class NmeaSharingClientStatus` | [NmeaSharingServer.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L31) |
| `class NmeaSharingStatus` | [NmeaSharingServer.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L38) |
| `fun start` | [NmeaSharingServer.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L70) |
| `fun stop` | [NmeaSharingServer.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L101) |
| `fun forceRebindForTest` | [NmeaSharingServer.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L116) |
| `fun publish` | [NmeaSharingServer.kt:121](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/sharing/NmeaSharingServer.kt#L121) |

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
| `class InstrumentTileSize` | [TripDashboardRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L17) |
| `class InstrumentSourceOverride` | [TripDashboardRepository.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L18) |
| `class DashboardTileBinding` | [TripDashboardRepository.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L19) |
| `fun transformed` | [TripDashboardRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L31) |
| `class TripDashboard` | [TripDashboardRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L33) |
| `object TripCustomMetricRecordingPolicy` | [TripDashboardRepository.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L35) |
| `fun bindings` | [TripDashboardRepository.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L36) |
| `fun save` | [TripDashboardRepository.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L47) |
| `fun create` | [TripDashboardRepository.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L55) |
| `fun delete` | [TripDashboardRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L59) |
| `fun reorder` | [TripDashboardRepository.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L60) |
| `fun decode` | [TripDashboardRepository.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripDashboardRepository.kt#L64) |

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
| `class TripWriterResult` | [TripSampleWriter.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L19) |
| `class PendingTripBatch` | [TripSampleWriter.kt:21](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L21) |
| `fun enqueue` | [TripSampleWriter.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L27) |
| `fun size` | [TripSampleWriter.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L34) |
| `fun take` | [TripSampleWriter.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L36) |
| `fun restore` | [TripSampleWriter.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L42) |
| `fun retryStorage` | [TripSampleWriter.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L75) |
| `fun enqueue` | [TripSampleWriter.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L80) |
| `fun size` | [TripSampleWriter.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L89) |
| `fun flush` | [TripSampleWriter.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripSampleWriter.kt#L90) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripTrackPoint` | [TripTrackPipeline.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L18) |
| `class TripTrackSegment` | [TripTrackPipeline.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L37) |
| `class TripMapDestinationType` | [TripTrackPipeline.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L39) |
| `class TripMapDestination` | [TripTrackPipeline.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L40) |
| `class TripMapData` | [TripTrackPipeline.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L41) |
| `class TripTrackSnapshot` | [TripTrackPipeline.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L48) |
| `fun rendered` | [TripTrackPipeline.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L59) |
| `object TripTrackRenderPolicy` | [TripTrackPipeline.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L77) |
| `fun render` | [TripTrackPipeline.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L86) |
| `fun compact` | [TripTrackPipeline.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L99) |
| `fun withBudget` | [TripTrackPipeline.kt:132](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L132) |
| `fun segment` | [TripTrackPipeline.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L155) |
| `fun flush` | [TripTrackPipeline.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L157) |
| `fun begin` | [TripTrackPipeline.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L200) |
| `fun clear` | [TripTrackPipeline.kt:213](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L213) |
| `fun appendLive` | [TripTrackPipeline.kt:215](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L215) |
| `fun markPersisted` | [TripTrackPipeline.kt:223](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L223) |
| `fun loadRendered` | [TripTrackPipeline.kt:231](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L231) |
| `fun loadMapData` | [TripTrackPipeline.kt:237](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/trip/TripTrackPipeline.kt#L237) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `object PressureHistoryPolicy` | [PressureHistoryRepository.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L26) |
| `fun bucket` | [PressureHistoryRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L30) |
| `fun validPressure` | [PressureHistoryRepository.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L31) |
| `class PressureHistoryRepository @Inject constructor` | [PressureHistoryRepository.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L36) |
| `fun historyReadFailed` | [PressureHistoryRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L59) |
| `fun historyReadRecovered` | [PressureHistoryRepository.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L63) |
| `fun record` | [PressureHistoryRepository.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L98) |
| `fun trend` | [PressureHistoryRepository.kt:135](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/PressureHistoryRepository.kt#L135) |

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
| `fun numeric` | [VesselDataHub.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L125) |
| `fun textual` | [VesselDataHub.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L126) |
| `fun update` | [VesselDataHub.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L127) |
| `fun hasFreshPhonePosition` | [VesselDataHub.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L151) |
| `fun setTripPositionPreference` | [VesselDataHub.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L152) |
| `fun setShellPositionSource` | [VesselDataHub.kt:155](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L155) |
| `fun pressureTrend` | [VesselDataHub.kt:182](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L182) |
| `fun freshValue` | [VesselDataHub.kt:189](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L189) |
| `fun resolvedWindField` | [VesselDataHub.kt:193](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L193) |
| `fun selectedTrueWind` | [VesselDataHub.kt:216](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L216) |
| `fun selectedMetric` | [VesselDataHub.kt:235](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L235) |
| `fun attitudeField` | [VesselDataHub.kt:246](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselDataHub.kt#L246) |

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
| `class PassageGeometry` | [VesselSettingsRepository.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L63) |
| `fun requireValid` | [VesselSettingsRepository.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L72) |
| `fun VesselDataSettings.passageGeometry` | [VesselSettingsRepository.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L78) |
| `fun VesselDataSettings.layout` | [VesselSettingsRepository.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L80) |
| `fun VesselDataSettings.withLayout` | [VesselSettingsRepository.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L81) |
| `class NmeaDeviceOutputSettings` | [VesselSettingsRepository.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L89) |
| `class NmeaOutputTransportMode` | [VesselSettingsRepository.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L123) |
| `class PhoneHeadingOutputFormat` | [VesselSettingsRepository.kt:124](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L124) |
| `class NmeaOutputPreset` | [VesselSettingsRepository.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L133) |
| `fun NmeaDeviceOutputSettings.withPreset` | [VesselSettingsRepository.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L134) |
| `object NmeaOutputLeasePolicy` | [VesselSettingsRepository.kt:142](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L142) |
| `fun shouldAutoStart` | [VesselSettingsRepository.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L147) |
| `fun afterRestore` | [VesselSettingsRepository.kt:148](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L148) |
| `fun setVesselIdentity` | [VesselSettingsRepository.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L174) |
| `fun setPassageGeometry` | [VesselSettingsRepository.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L181) |
| `fun setInstrumentLayout` | [VesselSettingsRepository.kt:194](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L194) |
| `fun selectMetricSource` | [VesselSettingsRepository.kt:199](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L199) |
| `fun selectPositionConnection` | [VesselSettingsRepository.kt:215](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L215) |
| `fun pinPositionSourceIfUnselected` | [VesselSettingsRepository.kt:228](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L228) |
| `fun save` | [VesselSettingsRepository.kt:238](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L238) |
| `fun activateAutoStart` | [VesselSettingsRepository.kt:291](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L291) |
| `fun saveConfiguration` | [VesselSettingsRepository.kt:309](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L309) |
| `fun requestStart` | [VesselSettingsRepository.kt:310](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L310) |
| `fun requestStop` | [VesselSettingsRepository.kt:314](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L314) |
| `fun save` | [VesselSettingsRepository.kt:321](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L321) |
| `object NmeaOutputTransportDefaults` | [VesselSettingsRepository.kt:340](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L340) |
| `fun restore` | [VesselSettingsRepository.kt:345](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/vessel/VesselSettingsRepository.kt#L345) |

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
| `class GpsDataSource` | [Models.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L58) |
| `class PositionProvider` | [Models.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L59) |
| `class FixTrust` | [Models.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L60) |
| `class PositionHealth` | [Models.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L61) |
| `class HeadingSource` | [Models.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L62) |
| `class HeadingQuality` | [Models.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L63) |
| `class AppLanguage` | [Models.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L65) |
| `class AlarmSound` | [Models.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L74) |
| `class DemoScenario` | [Models.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L75) |
| `class AnchorPlacementMode` | [Models.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L78) |
| `class AnchorPositionMode` | [Models.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L79) |
| `class KnownAnchorMethod` | [Models.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L80) |
| `class AnchorCenterSource` | [Models.kt:81](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L81) |
| `class AnchorOriginMode` | [Models.kt:83](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L83) |
| `class AnchorMonitoringPhase` | [Models.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L85) |
| `class CandidateDecision` | [Models.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L86) |
| `class AnchorCenterStatus` | [Models.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L87) |
| `class AnchorRangeMode` | [Models.kt:88](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L88) |
| `class AnchorSafetyPreset` | [Models.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L89) |
| `class NmeaConnectionState` | [Models.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L90) |
| `class AlarmState` | [Models.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L91) |
| `class AlarmType` | [Models.kt:92](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L92) |
| `class Confidence` | [Models.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L93) |
| `class AnchorEstimate` | [Models.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L95) |
| `class BackdownAnchorEstimate` | [Models.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L105) |
| `fun debugSummary` | [Models.kt:130](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L130) |
| `class AnchorConfig` | [Models.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L133) |
| `class AlarmSnapshot` | [Models.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/model/Models.kt#L144) |

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
| `class AnchorReport` | [AnchorReport.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/AnchorReport.kt#L12) |
| `fun generate` | [AnchorReport.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/AnchorReport.kt#L46) |

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
| `class ReportQuality` | [TripReport.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L17) |
| `class TripTrueWindReference` | [TripReport.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L18) |
| `object TripTrueWindReferenceClassifier` | [TripReport.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L19) |
| `fun from` | [TripReport.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L20) |
| `class TripFinding` | [TripReport.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L30) |
| `class TripSourceTimelineEntry` | [TripReport.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L31) |
| `class HeelDistribution` | [TripReport.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L32) |
| `class WindHeelBand` | [TripReport.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L33) |
| `class SpeedHeelBand` | [TripReport.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L34) |
| `class TripReport` | [TripReport.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L35) |
| `fun generate` | [TripReport.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L74) |
| `class AttitudeFrame` | [TripReport.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L100) |
| `fun filterPoint` | [TripReport.kt:126](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L126) |
| `fun addAttitudeFrame` | [TripReport.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/report/TripReport.kt#L131) |

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
| `class PressureTrendEstimator` | [PressureTrendEstimator.kt:8](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L8) |
| `fun add` | [PressureTrendEstimator.kt:15](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L15) |
| `fun trend` | [PressureTrendEstimator.kt:31](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/PressureTrendEstimator.kt#L31) |

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
| `object VesselSourcePinPolicy` | [VesselSourceModels.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L36) |
| `fun normalize` | [VesselSourceModels.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L38) |
| `fun matches` | [VesselSourceModels.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L39) |
| `fun resolve` | [VesselSourceModels.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L40) |
| `interface VesselReference` | [VesselSourceModels.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L45) |
| `object TrueNorth:VesselReference` | [VesselSourceModels.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L46) |
| `object MagneticNorth:VesselReference` | [VesselSourceModels.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L47) |
| `object WaterReferenced:VesselReference` | [VesselSourceModels.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L48) |
| `object GroundReferenced:VesselReference` | [VesselSourceModels.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L49) |
| `object VesselRelative:VesselReference` | [VesselSourceModels.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L51) |
| `class Depth` | [VesselSourceModels.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L52) |
| `interface VesselProvenance` | [VesselSourceModels.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L55) |
| `class Nmea` | [VesselSourceModels.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L56) |
| `class PhoneSensor` | [VesselSourceModels.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L57) |
| `class Derived` | [VesselSourceModels.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L58) |
| `class CandidateValidity` | [VesselSourceModels.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L61) |
| `class VesselSourceCandidate<T>` | [VesselSourceModels.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L62) |
| `class VesselSourceConflict` | [VesselSourceModels.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L86) |
| `class VesselSourceSelection<T>` | [VesselSourceModels.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L93) |
| `fun VesselDataSource.toSourceClass` | [VesselSourceModels.kt:101](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L101) |
| `fun VesselSourceClass.toLegacySource` | [VesselSourceModels.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/domain/vessel/VesselSourceModels.kt#L112) |

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

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AndroidMarineSensorDriver @Inject constructor` | [AndroidMarineSensorDriver.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt#L18) |
| `fun request` | [AndroidMarineSensorDriver.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt#L32) |
| `fun release` | [AndroidMarineSensorDriver.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt#L39) |
| `fun onSensorChanged` | [AndroidMarineSensorDriver.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt#L66) |
| `fun onAccuracyChanged` | [AndroidMarineSensorDriver.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/AndroidMarineSensorDriver.kt#L84) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DemoGpsStatus` | [DemoLocationRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L17) |
| `class DemoLocationRepository @Inject constructor` | [DemoLocationRepository.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L25) |
| `fun start` | [DemoLocationRepository.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L44) |
| `fun tick` | [DemoLocationRepository.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L50) |
| `fun pause` | [DemoLocationRepository.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L52) |
| `fun resume` | [DemoLocationRepository.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L58) |
| `fun stop` | [DemoLocationRepository.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L66) |
| `fun reconfigure` | [DemoLocationRepository.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/DemoLocationRepository.kt#L68) |

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
| `fun publish` | [GlobalMockLocationManager.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L40) |
| `fun stale` | [GlobalMockLocationManager.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L46) |
| `fun stop` | [GlobalMockLocationManager.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt#L47) |

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
| `class PhoneHeadingSample` | [PhoneHeadingRepository.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L24) |
| `class PhoneHeadingPresentationQuality` | [PhoneHeadingRepository.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L50) |
| `class DeclinationReferenceState` | [PhoneHeadingRepository.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L52) |
| `class PhoneHeadingRepository @Inject constructor` | [PhoneHeadingRepository.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L61) |
| `fun isAvailable` | [PhoneHeadingRepository.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L113) |
| `fun setPosition` | [PhoneHeadingRepository.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L116) |
| `fun start` | [PhoneHeadingRepository.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L125) |
| `fun stop` | [PhoneHeadingRepository.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L127) |
| `fun setDisplayDemand` | [PhoneHeadingRepository.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L129) |
| `fun setApproachDemand` | [PhoneHeadingRepository.kt:131](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L131) |
| `fun onSensorChanged` | [PhoneHeadingRepository.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L151) |
| `fun onAccuracyChanged` | [PhoneHeadingRepository.kt:291](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneHeadingRepository.kt#L291) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PhoneMotionState` | [PhoneMotionRepository.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L18) |
| `class PhoneMotionRepository @Inject constructor` | [PhoneMotionRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L34) |
| `fun start` | [PhoneMotionRepository.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L54) |
| `fun stop` | [PhoneMotionRepository.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L59) |
| `fun onSensorChanged` | [PhoneMotionRepository.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L60) |
| `fun onAccuracyChanged` | [PhoneMotionRepository.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/PhoneMotionRepository.kt#L79) |

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
| `class PhoneLocationPhase` | [SystemLocationRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L33) |
| `class PhoneLocationStatus` | [SystemLocationRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L34) |
| `class SystemLocationRepository @Inject constructor` | [SystemLocationRepository.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L42) |
| `fun onLocationChanged` | [SystemLocationRepository.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L67) |
| `fun onStatusChanged` | [SystemLocationRepository.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L69) |
| `fun onProviderEnabled` | [SystemLocationRepository.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L70) |
| `fun onProviderDisabled` | [SystemLocationRepository.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L71) |
| `fun onReceive` | [SystemLocationRepository.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L86) |
| `fun publishProviderLocationForTest` | [SystemLocationRepository.kt:133](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L133) |
| `fun preparePositionSelection` | [SystemLocationRepository.kt:138](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L138) |
| `fun preparedPositionIsReady` | [SystemLocationRepository.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L147) |
| `fun finishPositionSelection` | [SystemLocationRepository.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L150) |
| `fun hasPermission` | [SystemLocationRepository.kt:157](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L157) |
| `fun setAppEnabled` | [SystemLocationRepository.kt:158](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L158) |
| `fun setPreviewEnabled` | [SystemLocationRepository.kt:159](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L159) |
| `fun setBackgroundEnabled` | [SystemLocationRepository.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L160) |
| `fun refreshPermission` | [SystemLocationRepository.kt:161](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/SystemLocationRepository.kt#L161) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/DeviceViewOrientationProvider.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DeviceViewOrientationSample` | [DeviceViewOrientationProvider.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/DeviceViewOrientationProvider.kt#L17) |
| `class DeviceViewOrientationProvider @Inject constructor` | [DeviceViewOrientationProvider.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/DeviceViewOrientationProvider.kt#L29) |
| `fun acquire` | [DeviceViewOrientationProvider.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/DeviceViewOrientationProvider.kt#L38) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt

| 声明 | 实现位置 |
| --- | --- |
| `class DeviceBowAxis` | [PhoneVesselSensors.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L25) |
| `class PhoneVesselMountState` | [PhoneVesselSensors.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L26) |
| `class SensorQuaternion` | [PhoneVesselSensors.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L27) |
| `fun inverse` | [PhoneVesselSensors.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L28) |
| `fun normalized` | [PhoneVesselSensors.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L30) |
| `class VesselMountCalibration` | [PhoneVesselSensors.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L32) |
| `class PhoneVesselOutputBlocker` | [PhoneVesselSensors.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L60) |
| `class PhoneVesselOutputReadiness` | [PhoneVesselSensors.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L61) |
| `object PhoneVesselOutputReadinessPolicy` | [PhoneVesselSensors.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L62) |
| `fun evaluate` | [PhoneVesselSensors.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L63) |
| `class PhoneHeadingAlignmentReference` | [PhoneVesselSensors.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L73) |
| `class PhoneHeadingAlignmentMatch` | [PhoneVesselSensors.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L74) |
| `object PhoneHeadingAlignmentPolicy` | [PhoneVesselSensors.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L79) |
| `fun matchLiveReference` | [PhoneVesselSensors.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L80) |
| `fun shortestOffset` | [PhoneVesselSensors.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L91) |
| `class PhoneSensorCapabilities` | [PhoneVesselSensors.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L98) |
| `class PhoneVesselAttitudeSample` | [PhoneVesselSensors.kt:99](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L99) |
| `class PhonePressureSample` | [PhoneVesselSensors.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L100) |
| `object PhoneVesselAttitudeFrame` | [PhoneVesselSensors.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L106) |
| `fun vesselRotation` | [PhoneVesselSensors.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L118) |
| `fun magneticHeading` | [PhoneVesselSensors.kt:123](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L123) |
| `fun fromMatrix` | [PhoneVesselSensors.kt:129](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L129) |
| `fun resolve` | [PhoneVesselSensors.kt:139](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L139) |
| `fun resolve` | [PhoneVesselSensors.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L151) |
| `fun save` | [PhoneVesselSensors.kt:168](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L168) |
| `fun setMountState` | [PhoneVesselSensors.kt:169](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L169) |
| `fun setHeadingAlignment` | [PhoneVesselSensors.kt:170](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L170) |
| `fun confirmFixedMount` | [PhoneVesselSensors.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L172) |
| `fun setAttitudeOffsets` | [PhoneVesselSensors.kt:194](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L194) |
| `fun invalidateFixedMount` | [PhoneVesselSensors.kt:200](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L200) |
| `fun invalidateAttitudeSegment` | [PhoneVesselSensors.kt:207](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L207) |
| `fun restore` | [PhoneVesselSensors.kt:212](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L212) |
| `fun start` | [PhoneVesselSensors.kt:245](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L245) |
| `fun stop` | [PhoneVesselSensors.kt:252](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L252) |
| `fun calibrate` | [PhoneVesselSensors.kt:253](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L253) |
| `fun confirmFixedMount` | [PhoneVesselSensors.kt:258](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L258) |
| `fun invalidateFixedMount` | [PhoneVesselSensors.kt:266](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L266) |
| `fun setMounted` | [PhoneVesselSensors.kt:267](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L267) |
| `fun alignHeading` | [PhoneVesselSensors.kt:268](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L268) |
| `fun alignAttitude` | [PhoneVesselSensors.kt:269](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L269) |
| `fun onSensorChanged` | [PhoneVesselSensors.kt:270](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L270) |
| `fun onAccuracyChanged` | [PhoneVesselSensors.kt:301](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L301) |
| `fun start` | [PhoneVesselSensors.kt:317](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L317) |
| `fun stop` | [PhoneVesselSensors.kt:324](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L324) |
| `fun onSensorChanged` | [PhoneVesselSensors.kt:329](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L329) |
| `fun onAccuracyChanged` | [PhoneVesselSensors.kt:338](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/vessel/PhoneVesselSensors.kt#L338) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun requireReadable` | [AnchorCommandRegistry.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L44) |
| `fun retryStorage` | [AnchorCommandRegistry.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L47) |
| `fun create` | [AnchorCommandRegistry.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L67) |
| `fun retainDelivery` | [AnchorCommandRegistry.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L84) |
| `fun setReconciler` | [AnchorCommandRegistry.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L86) |
| `fun recheck` | [AnchorCommandRegistry.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L87) |
| `fun serviceInterrupted` | [AnchorCommandRegistry.kt:100](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L100) |
| `fun isExecuting` | [AnchorCommandRegistry.kt:105](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L105) |
| `fun get` | [AnchorCommandRegistry.kt:106](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L106) |
| `fun begin` | [AnchorCommandRegistry.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L109) |
| `fun finish` | [AnchorCommandRegistry.kt:118](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L118) |
| `fun unknown` | [AnchorCommandRegistry.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L134) |
| `fun acknowledgeResult` | [AnchorCommandRegistry.kt:137](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/AnchorCommandRegistry.kt#L137) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MonotonicClock` | [Clock.kt:6](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L6) |
| `interface WallClock` | [Clock.kt:7](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L7) |
| `object SystemMonotonicClock:MonotonicClock` | [Clock.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L12) |
| `object SystemWallClock:WallClock` | [Clock.kt:13](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/Clock.kt#L13) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/DurableRuntimeFile.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun read` | [DurableRuntimeFile.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/DurableRuntimeFile.kt#L17) |
| `fun write` | [DurableRuntimeFile.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/DurableRuntimeFile.kt#L25) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarinePresentationRequests.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarinePresentationRequests @Inject constructor` | [MarinePresentationRequests.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarinePresentationRequests.kt#L19) |
| `fun enqueue` | [MarinePresentationRequests.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarinePresentationRequests.kt#L27) |
| `fun acknowledge` | [MarinePresentationRequests.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarinePresentationRequests.kt#L34) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarineRecoveryBarrier.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineRecoveryBarrier @Inject constructor` | [MarineRecoveryBarrier.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarineRecoveryBarrier.kt#L27) |
| `class Recovery` | [MarineRecoveryBarrier.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarineRecoveryBarrier.kt#L35) |
| `fun retryRecovery` | [MarineRecoveryBarrier.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarineRecoveryBarrier.kt#L45) |
| `fun ensureRecovered` | [MarineRecoveryBarrier.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/MarineRecoveryBarrier.kt#L53) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface RuntimeCommand` | [RuntimeCommand.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L17) |
| `class ArmWatch` | [RuntimeCommand.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L18) |
| `class ChangeSystemPosition` | [RuntimeCommand.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L33) |
| `class SelectNmeaPosition` | [RuntimeCommand.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L34) |
| `object RefreshResidency:RuntimeCommand` | [RuntimeCommand.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L35) |
| `object NetworkChanged:RuntimeCommand` | [RuntimeCommand.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L36) |
| `object SnoozeAlarm:RuntimeCommand` | [RuntimeCommand.kt:37](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L37) |
| `object PauseWatch:RuntimeCommand` | [RuntimeCommand.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L38) |
| `object ResumeWatch:RuntimeCommand` | [RuntimeCommand.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L39) |
| `class SwitchWatchGpsSource` | [RuntimeCommand.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L40) |
| `object LiftAnchor:RuntimeCommand` | [RuntimeCommand.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L41) |
| `class UpdateRadius` | [RuntimeCommand.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L42) |
| `object PauseWatchAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L43) |
| `object StopNmeaDependenciesAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L44) |
| `object ContinueTripWithPhoneAndDisconnect:RuntimeCommand` | [RuntimeCommand.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L45) |
| `class Candidate` | [RuntimeCommand.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L46) |
| `class ResetCentreAnalysis` | [RuntimeCommand.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L47) |
| `class ApplyRecalculatedCentre` | [RuntimeCommand.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L48) |
| `class UpdateConditionGuards` | [RuntimeCommand.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L49) |
| `object ResetWindBaseline:RuntimeCommand` | [RuntimeCommand.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L50) |
| `object StartProxy:RuntimeCommand` | [RuntimeCommand.kt:51](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L51) |
| `object StopProxy:RuntimeCommand` | [RuntimeCommand.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L52) |
| `object TestAlarm:RuntimeCommand` | [RuntimeCommand.kt:53](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L53) |
| `object StopAlarmTest:RuntimeCommand` | [RuntimeCommand.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L54) |
| `class SetSharing` | [RuntimeCommand.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L55) |
| `object RefreshPhoneSensorOutput:RuntimeCommand` | [RuntimeCommand.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L56) |
| `object RefreshLocalNmeaServer:RuntimeCommand` | [RuntimeCommand.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L57) |
| `object StopAllNmeaSharing:RuntimeCommand` | [RuntimeCommand.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L58) |
| `class Voyage` | [RuntimeCommand.kt:59](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L59) |
| `class QueryAnchor` | [RuntimeCommand.kt:60](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L60) |
| `class QueryVoyage` | [RuntimeCommand.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L61) |
| `class StartTrip` | [RuntimeCommand.kt:62](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L62) |
| `object PauseTrip:RuntimeCommand` | [RuntimeCommand.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L63) |
| `object ResumeTrip:RuntimeCommand` | [RuntimeCommand.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L64) |
| `object ConfirmTripAttitudeFrame:RuntimeCommand` | [RuntimeCommand.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L65) |
| `object PauseTripAttitude:RuntimeCommand` | [RuntimeCommand.kt:66](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L66) |
| `object EndTrip:RuntimeCommand` | [RuntimeCommand.kt:67](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L67) |
| `class MarkTripWaypoint` | [RuntimeCommand.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L68) |
| `class StartSonar` | [RuntimeCommand.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L69) |
| `object StopSonar:RuntimeCommand` | [RuntimeCommand.kt:70](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L70) |
| `object RestoreOnly:RuntimeCommand` | [RuntimeCommand.kt:71](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L71) |
| `class Unknown` | [RuntimeCommand.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L72) |
| `class CandidateAction` | [RuntimeCommand.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L75) |
| `object RuntimeCommandParser` | [RuntimeCommand.kt:77](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L77) |
| `fun parse` | [RuntimeCommand.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeCommand.kt#L78) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeUserFeedback` | [RuntimeDiagnostics.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L28) |
| `class RuntimeFeedbackContext` | [RuntimeDiagnostics.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L42) |
| `class ConditionFeedbackTransition` | [RuntimeDiagnostics.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L50) |
| `object ConditionFeedbackLifecycle` | [RuntimeDiagnostics.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L63) |
| `fun between` | [RuntimeDiagnostics.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L64) |
| `class ArmPositionDiagnostic` | [RuntimeDiagnostics.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L85) |
| `class RuntimeDiagnostics` | [RuntimeDiagnostics.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L107) |
| `class RuntimeDiagnosticsRepository @Inject constructor` | [RuntimeDiagnostics.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L150) |
| `fun recordPositionDisposition` | [RuntimeDiagnostics.kt:190](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L190) |
| `fun recordEstimatorRun` | [RuntimeDiagnostics.kt:196](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L196) |
| `fun recordArmPositionDiagnostic` | [RuntimeDiagnostics.kt:198](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L198) |
| `fun serviceStarting` | [RuntimeDiagnostics.kt:201](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L201) |
| `fun restoring` | [RuntimeDiagnostics.kt:202](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L202) |
| `fun restoreFailed` | [RuntimeDiagnostics.kt:203](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L203) |
| `fun serviceReady` | [RuntimeDiagnostics.kt:204](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L204) |
| `fun serviceStopped` | [RuntimeDiagnostics.kt:205](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L205) |
| `fun recordUserFeedback` | [RuntimeDiagnostics.kt:209](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L209) |
| `fun dismissUserFeedback` | [RuntimeDiagnostics.kt:224](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L224) |
| `fun clearUserFeedback` | [RuntimeDiagnostics.kt:231](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeDiagnostics.kt#L231) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeResidencyRepository @Inject constructor` | [RuntimeResidencyRepository.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L17) |
| `fun startRequest` | [RuntimeResidencyRepository.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L23) |
| `fun stopRequest` | [RuntimeResidencyRepository.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L28) |
| `fun recoveryStarted` | [RuntimeResidencyRepository.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L33) |
| `fun recoveryFinished` | [RuntimeResidencyRepository.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L34) |
| `fun recoveryFailed` | [RuntimeResidencyRepository.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L35) |
| `fun phase` | [RuntimeResidencyRepository.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L36) |
| `fun capabilitiesReady` | [RuntimeResidencyRepository.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L41) |
| `fun resources` | [RuntimeResidencyRepository.kt:45](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResidencyRepository.kt#L45) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeOwner` | [RuntimeResourceManager.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L17) |
| `class RuntimeRequirement` | [RuntimeResourceManager.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L18) |
| `class RuntimeResourceSnapshot` | [RuntimeResourceManager.kt:29](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L29) |
| `class RuntimeOwnerRegistry` | [RuntimeResourceManager.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L54) |
| `fun set` | [RuntimeResourceManager.kt:56](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L56) |
| `fun clear` | [RuntimeResourceManager.kt:57](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L57) |
| `fun updateKeepWifiAwake` | [RuntimeResourceManager.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L61) |
| `fun snapshot` | [RuntimeResourceManager.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L72) |
| `class RuntimeResourceManager @Inject constructor` | [RuntimeResourceManager.kt:91](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L91) |
| `fun set` | [RuntimeResourceManager.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L111) |
| `fun release` | [RuntimeResourceManager.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L112) |
| `fun releaseAll` | [RuntimeResourceManager.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L113) |
| `fun releaseLegacyServiceOwners` | [RuntimeResourceManager.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L115) |
| `fun updateKeepWifiAwake` | [RuntimeResourceManager.kt:119](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L119) |
| `fun snapshot` | [RuntimeResourceManager.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/RuntimeResourceManager.kt#L120) |

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
| `class SerialRuntimeActor` | [SerialRuntimeActor.kt:12](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L12) |
| `fun submit` | [SerialRuntimeActor.kt:46](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L46) |
| `fun execute` | [SerialRuntimeActor.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L48) |
| `fun shutdown` | [SerialRuntimeActor.kt:54](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/SerialRuntimeActor.kt#L54) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class VoyageCommandRegistry @Inject constructor` | [VoyageCommandRegistry.kt:18](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L18) |
| `fun requireReadable` | [VoyageCommandRegistry.kt:39](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L39) |
| `fun retryStorage` | [VoyageCommandRegistry.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L41) |
| `fun register` | [VoyageCommandRegistry.kt:61](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L61) |
| `fun isExecuting` | [VoyageCommandRegistry.kt:79](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L79) |
| `fun get` | [VoyageCommandRegistry.kt:80](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L80) |
| `fun readReceipt` | [VoyageCommandRegistry.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L82) |
| `fun associateSession` | [VoyageCommandRegistry.kt:87](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L87) |
| `fun begin` | [VoyageCommandRegistry.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L90) |
| `fun finish` | [VoyageCommandRegistry.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L98) |
| `fun unknown` | [VoyageCommandRegistry.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L114) |
| `fun serviceInterrupted` | [VoyageCommandRegistry.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/VoyageCommandRegistry.kt#L117) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt

| 声明 | 实现位置 |
| --- | --- |
| `class YokuliRuntimeCoordinator @Inject constructor` | [YokuliRuntimeCoordinator.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L75) |
| `fun start` | [YokuliRuntimeCoordinator.kt:127](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L127) |
| `fun notificationPermissionGranted` | [YokuliRuntimeCoordinator.kt:143](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L143) |
| `fun enableSystemGps` | [YokuliRuntimeCoordinator.kt:144](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L144) |
| `fun notify` | [YokuliRuntimeCoordinator.kt:145](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L145) |
| `fun displayLength` | [YokuliRuntimeCoordinator.kt:146](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L146) |
| `fun notifyArmFailure` | [YokuliRuntimeCoordinator.kt:147](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L147) |
| `fun refresh` | [YokuliRuntimeCoordinator.kt:148](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L148) |
| `fun sound` | [YokuliRuntimeCoordinator.kt:149](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L149) |
| `fun silence` | [YokuliRuntimeCoordinator.kt:150](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L150) |
| `fun cancelUrgentNotification` | [YokuliRuntimeCoordinator.kt:151](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L151) |
| `fun releaseIfIdle` | [YokuliRuntimeCoordinator.kt:152](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L152) |
| `fun submit` | [YokuliRuntimeCoordinator.kt:310](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L310) |
| `fun ensureCommandForeground` | [YokuliRuntimeCoordinator.kt:909](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L909) |
| `fun stopForExplicitExit` | [YokuliRuntimeCoordinator.kt:1010](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L1010) |
| `fun shutdownIfOwnedBy` | [YokuliRuntimeCoordinator.kt:1041](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L1041) |
| `fun shutdown` | [YokuliRuntimeCoordinator.kt:1047](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/YokuliRuntimeCoordinator.kt#L1047) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorRuntimeActor` | [AnchorRuntimeActor.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L16) |
| `fun submit` | [AnchorRuntimeActor.kt:55](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L55) |
| `fun execute` | [AnchorRuntimeActor.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L58) |
| `fun shutdown` | [AnchorRuntimeActor.kt:64](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorRuntimeActor.kt#L64) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun configure` | [AnchorTelemetryRuntime.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L20) |
| `fun flush` | [AnchorTelemetryRuntime.kt:28](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L28) |
| `fun shutdown` | [AnchorTelemetryRuntime.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorTelemetryRuntime.kt#L41) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ArmRequest` | [AnchorWatchRuntime.kt:82](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L82) |
| `class AnchorRuntimeSnapshot` | [AnchorWatchRuntime.kt:98](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L98) |
| `interface AnchorRuntimeHost` | [AnchorWatchRuntime.kt:107](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L107) |
| `fun notificationPermissionGranted` | [AnchorWatchRuntime.kt:108](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L108) |
| `fun enableSystemGps` | [AnchorWatchRuntime.kt:109](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L109) |
| `fun notify` | [AnchorWatchRuntime.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L110) |
| `fun displayLength` | [AnchorWatchRuntime.kt:111](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L111) |
| `fun notifyArmFailure` | [AnchorWatchRuntime.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L112) |
| `fun refresh` | [AnchorWatchRuntime.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L113) |
| `fun sound` | [AnchorWatchRuntime.kt:114](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L114) |
| `fun silence` | [AnchorWatchRuntime.kt:115](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L115) |
| `fun cancelUrgentNotification` | [AnchorWatchRuntime.kt:116](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L116) |
| `fun releaseIfIdle` | [AnchorWatchRuntime.kt:117](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L117) |
| `class AnchorWatchRuntime` | [AnchorWatchRuntime.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L125) |
| `fun snapshot` | [AnchorWatchRuntime.kt:171](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L171) |
| `fun activeSession` | [AnchorWatchRuntime.kt:172](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L172) |
| `fun alarmSnapshot` | [AnchorWatchRuntime.kt:173](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L173) |
| `fun selectIdleSource` | [AnchorWatchRuntime.kt:174](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L174) |
| `fun refreshSessionFromDatabase` | [AnchorWatchRuntime.kt:181](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L181) |
| `fun restore` | [AnchorWatchRuntime.kt:186](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L186) |
| `fun headingAllowedAt` | [AnchorWatchRuntime.kt:216](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L216) |
| `fun arm` | [AnchorWatchRuntime.kt:252](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L252) |
| `fun recordArmPositionDecision` | [AnchorWatchRuntime.kt:316](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L316) |
| `fun onNmeaState` | [AnchorWatchRuntime.kt:416](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L416) |
| `fun onPositionHealth` | [AnchorWatchRuntime.kt:432](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L432) |
| `fun submitRawFix` | [AnchorWatchRuntime.kt:439](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L439) |
| `fun onAcceptedPosition` | [AnchorWatchRuntime.kt:484](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L484) |
| `fun acceptCandidate` | [AnchorWatchRuntime.kt:545](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L545) |
| `fun keepCurrentCenter` | [AnchorWatchRuntime.kt:559](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L559) |
| `fun applyRecalculatedCentre` | [AnchorWatchRuntime.kt:573](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L573) |
| `fun continueEstimating` | [AnchorWatchRuntime.kt:588](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L588) |
| `fun resetCentreAnalysis` | [AnchorWatchRuntime.kt:597](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L597) |
| `fun snooze` | [AnchorWatchRuntime.kt:616](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L616) |
| `fun pause` | [AnchorWatchRuntime.kt:622](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L622) |
| `fun switchPausedPositionSource` | [AnchorWatchRuntime.kt:633](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L633) |
| `fun bindSystemPositionSource` | [AnchorWatchRuntime.kt:724](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L724) |
| `fun resume` | [AnchorWatchRuntime.kt:736](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L736) |
| `fun lift` | [AnchorWatchRuntime.kt:764](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L764) |
| `fun updateRadius` | [AnchorWatchRuntime.kt:768](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L768) |
| `fun watchdog` | [AnchorWatchRuntime.kt:798](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L798) |
| `fun logEvent` | [AnchorWatchRuntime.kt:802](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/anchor/AnchorWatchRuntime.kt#L802) |

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
| `class BatteryHealthState` | [BatteryHealthMonitor.kt:10](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L10) |
| `fun update` | [BatteryHealthMonitor.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L14) |
| `class BatteryHealthMonitor @Inject constructor` | [BatteryHealthMonitor.kt:23](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L23) |
| `fun sample` | [BatteryHealthMonitor.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/health/BatteryHealthMonitor.kt#L26) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NmeaManualDisconnectState` | [NmeaManualDisconnectRepository.kt:19](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L19) |
| `class NmeaManualDisconnectRepository @Inject constructor` | [NmeaManualDisconnectRepository.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L30) |
| `fun current` | [NmeaManualDisconnectRepository.kt:43](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L43) |
| `fun suppress` | [NmeaManualDisconnectRepository.kt:44](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L44) |
| `fun clear` | [NmeaManualDisconnectRepository.kt:48](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/nmea/NmeaManualDisconnectRepository.kt#L48) |

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
| `fun installUnitFormats` | [NotificationCoordinator.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L24) |
| `fun createChannels` | [NotificationCoordinator.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L27) |
| `fun foregroundNotification` | [NotificationCoordinator.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L40) |
| `fun publishForeground` | [NotificationCoordinator.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L74) |
| `fun publishEvent` | [NotificationCoordinator.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L76) |
| `fun cancelEvent` | [NotificationCoordinator.kt:90](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L90) |
| `fun createAisChannels` | [NotificationCoordinator.kt:93](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L93) |
| `fun aisForegroundNotification` | [NotificationCoordinator.kt:103](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L103) |
| `fun publishAisForeground` | [NotificationCoordinator.kt:110](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L110) |
| `fun publishAisEvent` | [NotificationCoordinator.kt:112](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L112) |
| `fun cancelAisEvent` | [NotificationCoordinator.kt:120](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L120) |
| `fun aisNotificationAllowed` | [NotificationCoordinator.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L122) |
| `fun aisSoundAllowed` | [NotificationCoordinator.kt:125](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationCoordinator.kt#L125) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationUnitFormats.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NotificationUnitFormats` | [NotificationUnitFormats.kt:9](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/notification/NotificationUnitFormats.kt#L9) |

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
| `class MultiNmeaPublisher @Inject constructor` | [MultiNmeaPublisher.kt:14](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/MultiNmeaPublisher.kt#L14) |

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
| `class NmeaPublisherConfig` | [PhonePositionNmeaOutputRuntime.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L26) |
| `fun asOutputSettings` | [PhonePositionNmeaOutputRuntime.kt:41](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L41) |
| `fun from` | [PhonePositionNmeaOutputRuntime.kt:49](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L49) |
| `fun offer` | [PhonePositionNmeaOutputRuntime.kt:73](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L73) |
| `fun poll` | [PhonePositionNmeaOutputRuntime.kt:74](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L74) |
| `fun clear` | [PhonePositionNmeaOutputRuntime.kt:75](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L75) |
| `fun size` | [PhonePositionNmeaOutputRuntime.kt:76](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L76) |
| `object NmeaWireAttemptCadence` | [PhonePositionNmeaOutputRuntime.kt:84](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L84) |
| `fun nextAllowed` | [PhonePositionNmeaOutputRuntime.kt:86](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L86) |
| `class AnchorWatchNmeaPublisher @Inject constructor` | [PhonePositionNmeaOutputRuntime.kt:96](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L96) |
| `fun configure` | [PhonePositionNmeaOutputRuntime.kt:160](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L160) |
| `fun configure` | [PhonePositionNmeaOutputRuntime.kt:187](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L187) |
| `fun testOutput` | [PhonePositionNmeaOutputRuntime.kt:231](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L231) |
| `fun testKnownGoodHdg` | [PhonePositionNmeaOutputRuntime.kt:235](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L235) |
| `fun shutdown` | [PhonePositionNmeaOutputRuntime.kt:239](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L239) |
| `object FormalOutputSessionReadinessPolicy` | [PhonePositionNmeaOutputRuntime.kt:245](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L245) |
| `fun blocksStart` | [PhonePositionNmeaOutputRuntime.kt:248](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L248) |
| `object PhoneOwnedRuntimeSafety` | [PhonePositionNmeaOutputRuntime.kt:256](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L256) |
| `fun suppression` | [PhonePositionNmeaOutputRuntime.kt:257](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L257) |
| `fun NmeaDeviceOutputSettings.publisherConfiguration` | [PhonePositionNmeaOutputRuntime.kt:265](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L265) |
| `fun NmeaDeviceOutputSettings.canonicalPublisherConfiguration` | [PhonePositionNmeaOutputRuntime.kt:296](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/output/PhonePositionNmeaOutputRuntime.kt#L296) |

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
| `class SensorRuntime @Inject constructor` | [SensorRuntime.kt:26](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L26) |
| `fun reconcile` | [SensorRuntime.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L33) |
| `fun stop` | [SensorRuntime.kt:50](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sensor/SensorRuntime.kt#L50) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalNmeaServerRuntimeStatus` | [LocalNmeaServerRuntime.kt:22](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L22) |
| `class LocalNmeaServerRuntime @Inject constructor` | [LocalNmeaServerRuntime.kt:33](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L33) |
| `fun configure` | [LocalNmeaServerRuntime.kt:52](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L52) |
| `fun shutdown` | [LocalNmeaServerRuntime.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/sharing/LocalNmeaServerRuntime.kt#L95) |

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

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareDataStore.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun updateData` | [FaultAwareDataStore.kt:20](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareDataStore.kt#L20) |
| `fun faultAwarePreferencesDataStore` | [FaultAwareDataStore.kt:34](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareDataStore.kt#L34) |
| `fun getValue` | [FaultAwareDataStore.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareDataStore.kt#L38) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareSQLiteFactory.kt

| 声明 | 实现位置 |
| --- | --- |
| `class FaultAwareSQLiteFactory` | [FaultAwareSQLiteFactory.kt:24](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareSQLiteFactory.kt#L24) |
| `fun create` | [FaultAwareSQLiteFactory.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/storage/FaultAwareSQLiteFactory.kt#L27) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripMomentStatus` | [TripMomentCapture.kt:16](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L16) |
| `class TripMomentReceipt` | [TripMomentCapture.kt:17](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L17) |
| `class TripMomentContent` | [TripMomentCapture.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L25) |
| `fun from` | [TripMomentCapture.kt:27](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L27) |
| `class TripMomentJournal @Inject constructor` | [TripMomentCapture.kt:36](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L36) |
| `fun save` | [TripMomentCapture.kt:42](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L42) |
| `fun read` | [TripMomentCapture.kt:58](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L58) |
| `fun pendingIds` | [TripMomentCapture.kt:65](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L65) |
| `fun remove` | [TripMomentCapture.kt:68](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L68) |
| `fun captureTripMoment` | [TripMomentCapture.kt:72](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L72) |
| `fun evidence` | [TripMomentCapture.kt:78](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L78) |
| `fun numeric` | [TripMomentCapture.kt:85](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripMomentCapture.kt#L85) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt

| 声明 | 实现位置 |
| --- | --- |
| `class TripRuntimeResult` | [TripRuntime.kt:35](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L35) |
| `class TripRuntime @Inject constructor` | [TripRuntime.kt:38](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L38) |
| `fun recordAnchorEvent` | [TripRuntime.kt:89](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L89) |
| `fun recordSystemSourceChange` | [TripRuntime.kt:95](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L95) |
| `fun activeSession` | [TripRuntime.kt:102](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L102) |
| `fun restore` | [TripRuntime.kt:104](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L104) |
| `fun start` | [TripRuntime.kt:146](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L146) |
| `fun pause` | [TripRuntime.kt:198](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L198) |
| `fun resume` | [TripRuntime.kt:217](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L217) |
| `fun confirmAttitudeFrame` | [TripRuntime.kt:240](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L240) |
| `fun pauseAttitude` | [TripRuntime.kt:254](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L254) |
| `fun end` | [TripRuntime.kt:266](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L266) |
| `fun waypoint` | [TripRuntime.kt:287](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L287) |
| `fun captureMoment` | [TripRuntime.kt:321](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L321) |
| `fun restoreMoment` | [TripRuntime.kt:336](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L336) |
| `fun retryMoment` | [TripRuntime.kt:341](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L341) |
| `fun editCapturedMoment` | [TripRuntime.kt:395](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L395) |
| `fun amended` | [TripRuntime.kt:399](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L399) |
| `fun shutdown` | [TripRuntime.kt:425](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L425) |
| `fun continueWithPhoneAfterNmeaDisconnect` | [TripRuntime.kt:433](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/runtime/trip/TripRuntime.kt#L433) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AnchorForegroundService : Service` | [AnchorForegroundService.kt:30](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L30) |
| `fun notificationPermissionGranted` | [AnchorForegroundService.kt:40](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L40) |
| `fun startForeground` | [AnchorForegroundService.kt:47](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L47) |
| `fun stopForegroundAndSelf` | [AnchorForegroundService.kt:63](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L63) |
| `fun onCreate` | [AnchorForegroundService.kt:69](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L69) |
| `fun onStartCommand` | [AnchorForegroundService.kt:113](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L113) |
| `fun onDestroy` | [AnchorForegroundService.kt:122](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L122) |
| `fun onBind` | [AnchorForegroundService.kt:134](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/AnchorForegroundService.kt#L134) |

## legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class BootRestoreReceiver : BroadcastReceiver` | [BootRestoreReceiver.kt:25](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt#L25) |
| `fun onReceive` | [BootRestoreReceiver.kt:32](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/service/BootRestoreReceiver.kt#L32) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimePresentationService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalRuntimePresentationService @Inject constructor` | [LocalRuntimePresentationService.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimePresentationService.kt#L23) |
| `fun updateUnits` | [LocalRuntimePresentationService.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimePresentationService.kt#L37) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimeResidencyService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalRuntimeResidencyService @Inject constructor` | [LocalRuntimeResidencyService.kt:26](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimeResidencyService.kt#L26) |
| `fun startFromForeground` | [LocalRuntimeResidencyService.kt:38](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimeResidencyService.kt#L38) |
| `fun retryRecovery` | [LocalRuntimeResidencyService.kt:55](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimeResidencyService.kt#L55) |
| `fun exit` | [LocalRuntimeResidencyService.kt:62](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/LocalRuntimeResidencyService.kt#L62) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt

| 声明 | 实现位置 |
| --- | --- |
| `interface MarineSystem : RuntimeEndpoint` | [MarineSystem.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L21) |
| `class InProcessMarineSystem @Inject constructor` | [MarineSystem.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L39) |
| `object MarineSystemBindings` | [MarineSystem.kt:68](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L68) |
| `fun system` | [MarineSystem.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L70) |
| `fun content` | [MarineSystem.kt:79](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystem.kt#L79) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MarineSystemBootstrap` | [MarineSystemBootstrap.kt:11](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt#L11) |
| `fun initialize` | [MarineSystemBootstrap.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/MarineSystemBootstrap.kt#L15) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun snapshot` | [VoyageSessionCoordinator.kt:63](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L63) |
| `fun request` | [VoyageSessionCoordinator.kt:64](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L64) |
| `fun receipt` | [VoyageSessionCoordinator.kt:65](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L65) |
| `fun recheck` | [VoyageSessionCoordinator.kt:66](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L66) |
| `fun start` | [VoyageSessionCoordinator.kt:67](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L67) |
| `fun pause` | [VoyageSessionCoordinator.kt:68](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L68) |
| `fun resume` | [VoyageSessionCoordinator.kt:69](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L69) |
| `fun finish` | [VoyageSessionCoordinator.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/VoyageSessionCoordinator.kt#L70) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AisMonitoringService : Service` | [AisMonitoringService.kt:17](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt#L17) |
| `fun onStartCommand` | [AisMonitoringService.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt#L23) |
| `fun start` | [AisMonitoringService.kt:27](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt#L27) |
| `fun onDestroy` | [AisMonitoringService.kt:53](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt#L53) |
| `fun onBind` | [AisMonitoringService.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisMonitoringService.kt#L58) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisRestoreReceiver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class AisRestoreReceiver : BroadcastReceiver` | [AisRestoreReceiver.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisRestoreReceiver.kt#L13) |
| `fun onReceive` | [AisRestoreReceiver.kt:14](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/AisRestoreReceiver.kt#L14) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalAisTrafficService @Inject constructor` | [LocalAisTrafficService.kt:47](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L47) |
| `fun command` | [LocalAisTrafficService.kt:168](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L168) |
| `fun foregroundChanged` | [LocalAisTrafficService.kt:242](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L242) |
| `fun monitoringRequested` | [LocalAisTrafficService.kt:258](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L258) |
| `fun wantsLocation` | [LocalAisTrafficService.kt:260](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L260) |
| `fun numeric` | [LocalAisTrafficService.kt:416](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L416) |
| `fun chinese` | [LocalAisTrafficService.kt:470](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ais/LocalAisTrafficService.kt#L470) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ChartDrawingResult` | [ChartDrawingClipper.kt:10](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L10) |
| `object ChartDrawingClipper` | [ChartDrawingClipper.kt:12](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L12) |
| `fun compose` | [ChartDrawingClipper.kt:14](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L14) |
| `fun union` | [ChartDrawingClipper.kt:78](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L78) |
| `fun boundsGeometry` | [ChartDrawingClipper.kt:79](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L79) |
| `fun viewport` | [ChartDrawingClipper.kt:86](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L86) |
| `fun geometry` | [ChartDrawingClipper.kt:92](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L92) |
| `fun ring` | [ChartDrawingClipper.kt:93](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L93) |
| `fun contract` | [ChartDrawingClipper.kt:111](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L111) |
| `fun append` | [ChartDrawingClipper.kt:114](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartDrawingClipper.kt#L114) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt

| 声明 | 实现位置 |
| --- | --- |
| `object ChartFeatureIndex` | [ChartFeatureIndex.kt:12](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt#L12) |
| `fun create` | [ChartFeatureIndex.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt#L13) |
| `fun insert` | [ChartFeatureIndex.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt#L39) |
| `fun normalized` | [ChartFeatureIndex.kt:59](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt#L59) |
| `fun matches` | [ChartFeatureIndex.kt:61](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/ChartFeatureIndex.kt#L61) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun grid` | [GebcoTiff.kt:117](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt#L117) |
| `fun readWindow` | [GebcoTiff.kt:120](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt#L120) |
| `fun code` | [GebcoTiff.kt:183](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt#L183) |
| `fun close` | [GebcoTiff.kt:226](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt#L226) |
| `fun open` | [GebcoTiff.kt:231](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GebcoTiff.kt#L231) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt

| 声明 | 实现位置 |
| --- | --- |
| `object GeoPackageChartImporter` | [GeoPackageChartImporter.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt#L21) |
| `fun prepare` | [GeoPackageChartImporter.kt:34](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt#L34) |
| `fun value` | [GeoPackageChartImporter.kt:245](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt#L245) |
| `fun alias` | [GeoPackageChartImporter.kt:250](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt#L250) |
| `fun number` | [GeoPackageChartImporter.kt:269](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageChartImporter.kt#L269) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Result` | [GeoPackageGeometryReader.kt:12](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L12) |
| `fun read` | [GeoPackageGeometryReader.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L15) |
| `fun filter` | [GeoPackageGeometryReader.kt:54](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L54) |
| `fun isDone` | [GeoPackageGeometryReader.kt:62](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L62) |
| `fun isGeometryChanged` | [GeoPackageGeometryReader.kt:63](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L63) |
| `fun line` | [GeoPackageGeometryReader.kt:67](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L67) |
| `fun add` | [GeoPackageGeometryReader.kt:68](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L68) |
| `fun geometry` | [GeoPackageGeometryReader.kt:89](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L89) |
| `fun coordinate` | [GeoPackageGeometryReader.kt:102](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L102) |
| `fun points` | [GeoPackageGeometryReader.kt:110](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/GeoPackageGeometryReader.kt#L110) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzKeyStore.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LinzKeyStore` | [LinzKeyStore.kt:16](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzKeyStore.kt#L16) |
| `fun read` | [LinzKeyStore.kt:27](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzKeyStore.kt#L27) |
| `fun save` | [LinzKeyStore.kt:36](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzKeyStore.kt#L36) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LinzLdsAdapter` | [LinzLdsAdapter.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L13) |
| `class Layer` | [LinzLdsAdapter.kt:18](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L18) |
| `fun recognizeTitle` | [LinzLdsAdapter.kt:68](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L68) |
| `fun scaleBandSortDenominator` | [LinzLdsAdapter.kt:71](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L71) |
| `fun recognize` | [LinzLdsAdapter.kt:80](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L80) |
| `fun acceptsGeometry` | [LinzLdsAdapter.kt:95](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L95) |
| `fun adapt` | [LinzLdsAdapter.kt:103](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L103) |
| `fun featureKey` | [LinzLdsAdapter.kt:132](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzLdsAdapter.kt#L132) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt

| 声明 | 实现位置 |
| --- | --- |
| `object LinzOnlineDownload` | [LinzOnlineDownload.kt:30](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L30) |
| `fun validate` | [LinzOnlineDownload.kt:36](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L36) |
| `fun download` | [LinzOnlineDownload.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L39) |
| `fun check` | [LinzOnlineDownload.kt:43](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L43) |
| `fun get` | [LinzOnlineDownload.kt:45](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L45) |
| `fun point` | [LinzOnlineDownload.kt:175](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L175) |
| `fun line` | [LinzOnlineDownload.kt:181](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L181) |
| `fun polygon` | [LinzOnlineDownload.kt:182](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LinzOnlineDownload.kt#L182) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun retryRestore` | [LocalChartDataService.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L58) |
| `fun importPackage` | [LocalChartDataService.kt:85](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L85) |
| `fun configureLinz` | [LocalChartDataService.kt:101](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L101) |
| `fun refreshLinz` | [LocalChartDataService.kt:107](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L107) |
| `fun ensureLinz` | [LocalChartDataService.kt:115](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L115) |
| `fun cancelImport` | [LocalChartDataService.kt:134](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L134) |
| `fun retryImport` | [LocalChartDataService.kt:137](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L137) |
| `fun check` | [LocalChartDataService.kt:149](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L149) |
| `fun sourceIdentity` | [LocalChartDataService.kt:161](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L161) |
| `fun sourceName` | [LocalChartDataService.kt:162](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L162) |
| `fun reorderCells` | [LocalChartDataService.kt:325](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L325) |
| `fun rename` | [LocalChartDataService.kt:330](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L330) |
| `fun updateEligibility` | [LocalChartDataService.kt:334](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L334) |
| `fun remove` | [LocalChartDataService.kt:343](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L343) |
| `fun acquireSnapshot` | [LocalChartDataService.kt:348](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L348) |
| `fun releaseSnapshot` | [LocalChartDataService.kt:356](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L356) |
| `fun query` | [LocalChartDataService.kt:427](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L427) |
| `fun rasterWindows` | [LocalChartDataService.kt:448](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L448) |
| `fun browse` | [LocalChartDataService.kt:485](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L485) |
| `fun readFeature` | [LocalChartDataService.kt:525](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L525) |
| `fun walk` | [LocalChartDataService.kt:543](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L543) |
| `fun copy` | [LocalChartDataService.kt:569](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L569) |
| `fun geometryBounds` | [LocalChartDataService.kt:629](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/LocalChartDataService.kt#L629) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt

| 声明 | 实现位置 |
| --- | --- |
| `object RasterBathymetryImporter` | [RasterBathymetryImporter.kt:16](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L16) |
| `fun accepts` | [RasterBathymetryImporter.kt:20](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L20) |
| `fun prepare` | [RasterBathymetryImporter.kt:22](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L22) |
| `fun number` | [RasterBathymetryImporter.kt:106](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L106) |
| `class RasterManifest` | [RasterBathymetryImporter.kt:137](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L137) |
| `class RasterFileEntry` | [RasterBathymetryImporter.kt:138](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L138) |
| `fun readWindow` | [RasterBathymetryImporter.kt:152](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L152) |
| `fun sample` | [RasterBathymetryImporter.kt:169](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L169) |
| `fun close` | [RasterBathymetryImporter.kt:174](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L174) |
| `fun open` | [RasterBathymetryImporter.kt:176](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L176) |
| `fun parseRasterNumber` | [RasterBathymetryImporter.kt:193](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L193) |
| `fun normalizeElevation` | [RasterBathymetryImporter.kt:198](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L198) |
| `fun validatedGrid` | [RasterBathymetryImporter.kt:204](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L204) |
| `fun lon` | [RasterBathymetryImporter.kt:209](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L209) |
| `fun next` | [RasterBathymetryImporter.kt:224](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L224) |
| `fun close` | [RasterBathymetryImporter.kt:230](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/RasterBathymetryImporter.kt#L230) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt

| 声明 | 实现位置 |
| --- | --- |
| `class Header` | [S57Reader.kt:11](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L11) |
| `class Parameters` | [S57Reader.kt:12](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L12) |
| `class Record` | [S57Reader.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L13) |
| `class Cell` | [S57Reader.kt:20](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L20) |
| `class Transfer` | [S57Reader.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L21) |
| `fun read` | [S57Reader.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L23) |
| `class Entry` | [S57Reader.kt:44](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L44) |
| `fun group` | [S57Reader.kt:108](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L108) |
| `fun base` | [S57Reader.kt:129](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L129) |
| `fun apply` | [S57Reader.kt:135](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L135) |
| `fun features` | [S57Reader.kt:187](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L187) |
| `fun depth` | [S57Reader.kt:223](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L223) |
| `fun coordinates` | [S57Reader.kt:240](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L240) |
| `fun referenced` | [S57Reader.kt:250](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L250) |
| `fun edge` | [S57Reader.kt:251](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L251) |
| `fun kind` | [S57Reader.kt:302](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L302) |
| `fun attributePairs` | [S57Reader.kt:322](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L322) |
| `fun tuples` | [S57Reader.kt:332](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L332) |
| `class S57Dictionaries` | [S57Reader.kt:341](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L341) |
| `fun skip` | [S57Reader.kt:347](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L347) |
| `fun byte` | [S57Reader.kt:348](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L348) |
| `fun short` | [S57Reader.kt:349](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L349) |
| `fun fixed` | [S57Reader.kt:350](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L350) |
| `fun text` | [S57Reader.kt:351](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L351) |
| `fun ByteArray.u8` | [S57Reader.kt:353](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L353) |
| `fun ByteArray.u16` | [S57Reader.kt:354](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L354) |
| `fun ByteArray.u32` | [S57Reader.kt:355](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L355) |
| `fun ByteArray.i32` | [S57Reader.kt:356](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/chart/S57Reader.kt#L356) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/device/LocalDeviceRuntimeService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalDeviceRuntimeService @Inject constructor` | [LocalDeviceRuntimeService.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/device/LocalDeviceRuntimeService.kt#L39) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LabBootState` | [HardwareLabBoot.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L13) |
| `object HardwareLabBoot` | [HardwareLabBoot.kt:29](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L29) |
| `fun hostFiles` | [HardwareLabBoot.kt:34](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L34) |
| `fun directory` | [HardwareLabBoot.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L35) |
| `fun initialize` | [HardwareLabBoot.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L39) |
| `fun save` | [HardwareLabBoot.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L70) |
| `fun write` | [HardwareLabBoot.kt:73](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L73) |
| `fun lastSimulation` | [HardwareLabBoot.kt:84](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L84) |
| `fun files` | [HardwareLabBoot.kt:108](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L108) |
| `fun database` | [HardwareLabBoot.kt:110](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L110) |
| `fun preferenceName` | [HardwareLabBoot.kt:115](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L115) |
| `fun seedWorld` | [HardwareLabBoot.kt:118](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/HardwareLabBoot.kt#L118) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalHardwareLabService @Inject constructor` | [LocalHardwareLabService.kt:30](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt#L30) |
| `fun execute` | [LocalHardwareLabService.kt:122](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt#L122) |
| `fun save` | [LocalHardwareLabService.kt:137](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt#L137) |
| `fun virtual` | [LocalHardwareLabService.kt:154](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt#L154) |
| `fun readRecording` | [LocalHardwareLabService.kt:226](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/LocalHardwareLabService.kt#L226) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineHostApplication : Application` | [MarineHostApplication.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L15) |
| `fun getFilesDir` | [MarineHostApplication.kt:17](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L17) |
| `fun getNoBackupFilesDir` | [MarineHostApplication.kt:18](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L18) |
| `fun getDatabasePath` | [MarineHostApplication.kt:19](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L19) |
| `fun openOrCreateDatabase` | [MarineHostApplication.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L21) |
| `fun openOrCreateDatabase` | [MarineHostApplication.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L23) |
| `fun deleteDatabase` | [MarineHostApplication.kt:25](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L25) |
| `fun getSharedPreferences` | [MarineHostApplication.kt:26](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/MarineHostApplication.kt#L26) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ScenarioCheckpoint` | [ScenarioEngine.kt:18](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L18) |
| `class ScenarioFrozenFrame` | [ScenarioEngine.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L37) |
| `class ScenarioEngineState` | [ScenarioEngine.kt:39](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L39) |
| `class ScenarioEngine` | [ScenarioEngine.kt:52](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L52) |
| `fun checkpoint` | [ScenarioEngine.kt:115](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L115) |
| `fun setScenario` | [ScenarioEngine.kt:118](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L118) |
| `fun setFault` | [ScenarioEngine.kt:131](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L131) |
| `fun clearFault` | [ScenarioEngine.kt:135](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L135) |
| `fun attach` | [ScenarioEngine.kt:139](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L139) |
| `fun detach` | [ScenarioEngine.kt:143](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L143) |
| `fun close` | [ScenarioEngine.kt:147](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L147) |
| `fun sensor` | [ScenarioEngine.kt:321](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L321) |
| `fun put` | [ScenarioEngine.kt:433](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L433) |
| `fun put` | [ScenarioEngine.kt:434](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L434) |
| `fun text` | [ScenarioEngine.kt:435](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L435) |
| `fun armour` | [ScenarioEngine.kt:439](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L439) |
| `fun bit` | [ScenarioEngine.kt:454](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L454) |
| `fun normal` | [ScenarioEngine.kt:472](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L472) |
| `fun shortest` | [ScenarioEngine.kt:473](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L473) |
| `fun decimal` | [ScenarioEngine.kt:474](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L474) |
| `fun coordinate` | [ScenarioEngine.kt:475](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L475) |
| `fun distance` | [ScenarioEngine.kt:482](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L482) |
| `fun bearing` | [ScenarioEngine.kt:488](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L488) |
| `fun destination` | [ScenarioEngine.kt:492](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L492) |
| `fun quaternion` | [ScenarioEngine.kt:500](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L500) |
| `fun rotateInverse` | [ScenarioEngine.kt:504](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioEngine.kt#L504) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt

| 声明 | 实现位置 |
| --- | --- |
| `object ScenarioValidation` | [ScenarioValidation.kt:6](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L6) |
| `fun requireValid` | [ScenarioValidation.kt:10](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L10) |
| `fun requireDevice` | [ScenarioValidation.kt:53](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L53) |
| `fun requireValidPower` | [ScenarioValidation.kt:54](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L54) |
| `fun requireValidFault` | [ScenarioValidation.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L58) |
| `fun target` | [ScenarioValidation.kt:60](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L60) |
| `fun point` | [ScenarioValidation.kt:67](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/ScenarioValidation.kt#L67) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RecordedEvent` | [SystemRecordingStore.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L15) |
| `class RecordingHeader` | [SystemRecordingStore.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L21) |
| `fun list` | [SystemRecordingStore.kt:54](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L54) |
| `fun header` | [SystemRecordingStore.kt:55](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L55) |
| `fun get` | [SystemRecordingStore.kt:62](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L62) |
| `fun start` | [SystemRecordingStore.kt:63](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L63) |
| `fun command` | [SystemRecordingStore.kt:116](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L116) |
| `fun power` | [SystemRecordingStore.kt:117](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L117) |
| `fun storage` | [SystemRecordingStore.kt:118](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L118) |
| `fun stop` | [SystemRecordingStore.kt:119](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L119) |
| `fun delete` | [SystemRecordingStore.kt:120](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L120) |
| `fun chunk` | [SystemRecordingStore.kt:121](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L121) |
| `fun replay` | [SystemRecordingStore.kt:135](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L135) |
| `fun attach` | [SystemRecordingStore.kt:138](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/hardware/SystemRecordingStore.kt#L138) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalReadingHistoryService @Inject constructor` | [LocalReadingHistoryService.kt:22](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L22) |
| `fun restore` | [LocalReadingHistoryService.kt:40](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L40) |
| `fun retryStorage` | [LocalReadingHistoryService.kt:83](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L83) |
| `fun flush` | [LocalReadingHistoryService.kt:84](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L84) |
| `fun slice` | [LocalReadingHistoryService.kt:89](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L89) |
| `fun slices` | [LocalReadingHistoryService.kt:95](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L95) |
| `fun add` | [LocalReadingHistoryService.kt:136](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L136) |
| `fun motionReading` | [LocalReadingHistoryService.kt:179](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/LocalReadingHistoryService.kt#L179) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt

| 声明 | 实现位置 |
| --- | --- |
| `class ReadingHistoryCache` | [ReadingHistoryCache.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L15) |
| `class Loaded` | [ReadingHistoryCache.kt:18](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L18) |
| `fun read` | [ReadingHistoryCache.kt:20](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L20) |
| `fun string` | [ReadingHistoryCache.kt:34](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L34) |
| `fun write` | [ReadingHistoryCache.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L58) |
| `fun declare` | [ReadingHistoryCache.kt:62](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L62) |
| `fun write` | [ReadingHistoryCache.kt:75](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L75) |
| `fun write` | [ReadingHistoryCache.kt:76](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L76) |
| `fun string` | [ReadingHistoryCache.kt:80](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/history/ReadingHistoryCache.kt#L80) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun close` | [BinderMarineSystem.kt:59](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L59) |
| `fun shared` | [BinderMarineSystem.kt:62](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L62) |
| `class MarineCoreUnavailableException` | [BinderMarineSystem.kt:67](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L67) |
| `fun onTransact` | [BinderMarineSystem.kt:97](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L97) |
| `fun onServiceConnected` | [BinderMarineSystem.kt:107](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L107) |
| `fun current` | [BinderMarineSystem.kt:110](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L110) |
| `fun onServiceDisconnected` | [BinderMarineSystem.kt:136](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L136) |
| `fun onBindingDied` | [BinderMarineSystem.kt:137](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L137) |
| `fun onNullBinding` | [BinderMarineSystem.kt:138](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L138) |
| `fun <T> proxy` | [BinderMarineSystem.kt:211](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L211) |
| `fun acquire` | [BinderMarineSystem.kt:409](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L409) |
| `fun close` | [BinderMarineSystem.kt:417](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L417) |
| `fun close` | [BinderMarineSystem.kt:450](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/BinderMarineSystem.kt#L450) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MainStateWire.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MainStateWire` | [MainStateWire.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MainStateWire.kt#L13) |
| `fun difference` | [MainStateWire.kt:103](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MainStateWire.kt#L103) |
| `fun apply` | [MainStateWire.kt:114](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MainStateWire.kt#L114) |
| `fun <T> changed` | [MainStateWire.kt:118](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MainStateWire.kt#L118) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineCoreBinderService : Service` | [MarineCoreBinderService.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L35) |
| `fun send` | [MarineCoreBinderService.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L58) |
| `fun onTransact` | [MarineCoreBinderService.kt:73](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L73) |
| `fun onCreate` | [MarineCoreBinderService.kt:135](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L135) |
| `fun onBind` | [MarineCoreBinderService.kt:141](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L141) |
| `fun onDestroy` | [MarineCoreBinderService.kt:282](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreBinderService.kt#L282) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MarineCoreCodec` | [MarineCoreCodec.kt:22](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L22) |
| `fun write` | [MarineCoreCodec.kt:31](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L31) |
| `fun read` | [MarineCoreCodec.kt:32](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L32) |
| `fun serialize` | [MarineCoreCodec.kt:61](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L61) |
| `fun deserialize` | [MarineCoreCodec.kt:65](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L65) |
| `fun serialize` | [MarineCoreCodec.kt:74](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L74) |
| `fun deserialize` | [MarineCoreCodec.kt:75](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L75) |
| `fun <R> create` | [MarineCoreCodec.kt:82](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L82) |
| `fun write` | [MarineCoreCodec.kt:86](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L86) |
| `fun read` | [MarineCoreCodec.kt:94](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L94) |
| `fun <T> create` | [MarineCoreCodec.kt:108](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L108) |
| `fun adapter` | [MarineCoreCodec.kt:110](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L110) |
| `fun write` | [MarineCoreCodec.kt:119](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L119) |
| `fun read` | [MarineCoreCodec.kt:122](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreCodec.kt#L122) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt

| 声明 | 实现位置 |
| --- | --- |
| `object MarineCoreProcess` | [MarineCoreProtocol.kt:30](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L30) |
| `fun isShell` | [MarineCoreProtocol.kt:31](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L31) |
| `fun isCore` | [MarineCoreProtocol.kt:32](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L32) |
| `class CoreCall` | [MarineCoreProtocol.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L35) |
| `class CorePacket` | [MarineCoreProtocol.kt:36](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L36) |
| `class CoreHello` | [MarineCoreProtocol.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L37) |
| `object MarineCorePorts` | [MarineCoreProtocol.kt:40](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L40) |
| `fun key` | [MarineCoreProtocol.kt:67](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L67) |
| `fun method` | [MarineCoreProtocol.kt:69](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L69) |
| `fun nested` | [MarineCoreProtocol.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L70) |
| `fun target` | [MarineCoreProtocol.kt:71](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L71) |
| `fun isSuspend` | [MarineCoreProtocol.kt:89](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L89) |
| `fun valueType` | [MarineCoreProtocol.kt:90](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L90) |
| `fun argumentTypes` | [MarineCoreProtocol.kt:100](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L100) |
| `fun cancellableRead` | [MarineCoreProtocol.kt:102](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L102) |
| `object CoreWire` | [MarineCoreProtocol.kt:113](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L113) |
| `fun version` | [MarineCoreProtocol.kt:132](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L132) |
| `fun writePayload` | [MarineCoreProtocol.kt:135](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L135) |
| `fun write` | [MarineCoreProtocol.kt:152](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L152) |
| `fun write` | [MarineCoreProtocol.kt:153](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L153) |
| `fun flush` | [MarineCoreProtocol.kt:163](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L163) |
| `fun close` | [MarineCoreProtocol.kt:164](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L164) |
| `fun <T> readPayload` | [MarineCoreProtocol.kt:167](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/ipc/MarineCoreProtocol.kt#L167) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class LocalNavigationSessionService @Inject constructor` | [LocalNavigationSessionService.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L35) |
| `fun execute` | [LocalNavigationSessionService.kt:102](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L102) |
| `fun importLegacy` | [LocalNavigationSessionService.kt:109](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L109) |
| `fun retryRead` | [LocalNavigationSessionService.kt:133](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L133) |
| `fun result` | [LocalNavigationSessionService.kt:196](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L196) |
| `fun coordinate` | [LocalNavigationSessionService.kt:343](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L343) |
| `fun metric` | [LocalNavigationSessionService.kt:355](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L355) |
| `fun number` | [LocalNavigationSessionService.kt:366](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L366) |
| `fun foreground` | [LocalNavigationSessionService.kt:458](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L458) |
| `fun activeSession` | [LocalNavigationSessionService.kt:463](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L463) |
| `fun chinese` | [LocalNavigationSessionService.kt:464](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L464) |
| `fun needsPhoneLocation` | [LocalNavigationSessionService.kt:465](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/LocalNavigationSessionService.kt#L465) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NavigationGeometry` | [NavigationGeometry.kt:8](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L8) |
| `fun distance` | [NavigationGeometry.kt:9](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L9) |
| `fun bearing` | [NavigationGeometry.kt:10](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L10) |
| `class Segment` | [NavigationGeometry.kt:24](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L24) |
| `fun segment` | [NavigationGeometry.kt:25](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L25) |
| `fun separation` | [NavigationGeometry.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L37) |
| `fun guidance` | [NavigationGeometry.kt:57](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L57) |
| `fun eta` | [NavigationGeometry.kt:87](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationGeometry.kt#L87) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NavigationTrackingService : Service` | [NavigationTrackingService.kt:24](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt#L24) |
| `fun onStartCommand` | [NavigationTrackingService.kt:29](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt#L29) |
| `fun promote` | [NavigationTrackingService.kt:56](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt#L56) |
| `fun onDestroy` | [NavigationTrackingService.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt#L70) |
| `fun onBind` | [NavigationTrackingService.kt:75](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/navigation/NavigationTrackingService.kt#L75) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun setForeground` | [AndroidNoticePresenter.kt:28](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt#L28) |
| `fun close` | [AndroidNoticePresenter.kt:43](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt#L43) |
| `fun present` | [AndroidNoticePresenter.kt:89](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt#L89) |
| `class NoticeDismissReceiver : BroadcastReceiver` | [AndroidNoticePresenter.kt:157](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt#L157) |
| `fun onReceive` | [AndroidNoticePresenter.kt:158](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/AndroidNoticePresenter.kt#L158) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun onTransact` | [BinderNotificationClient.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L37) |
| `fun onServiceConnected` | [BinderNotificationClient.kt:47](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L47) |
| `fun onServiceDisconnected` | [BinderNotificationClient.kt:69](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L69) |
| `fun onBindingDied` | [BinderNotificationClient.kt:70](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L70) |
| `fun onNullBinding` | [BinderNotificationClient.kt:71](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L71) |
| `fun execute` | [BinderNotificationClient.kt:130](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L130) |
| `fun executeIfCurrent` | [BinderNotificationClient.kt:133](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L133) |
| `fun result` | [BinderNotificationClient.kt:165](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L165) |
| `fun setForeground` | [BinderNotificationClient.kt:191](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L191) |
| `fun close` | [BinderNotificationClient.kt:205](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L205) |
| `fun shared` | [BinderNotificationClient.kt:219](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/BinderNotificationClient.kt#L219) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/MarineNotificationEvents.kt

| 声明 | 实现位置 |
| --- | --- |
| `class MarineNotificationEvents @Inject constructor` | [MarineNotificationEvents.kt:27](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/MarineNotificationEvents.kt#L27) |
| `fun start` | [MarineNotificationEvents.kt:37](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/MarineNotificationEvents.kt#L37) |
| `fun AlarmEventEntity.toNoticeRecord` | [MarineNotificationEvents.kt:141](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/MarineNotificationEvents.kt#L141) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt

| 声明 | 实现位置 |
| --- | --- |
| `object NoticeWire` | [NotificationBinderService.kt:14](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L14) |
| `class NoticePage` | [NotificationBinderService.kt:28](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L28) |
| `object NotificationProcessRole` | [NotificationBinderService.kt:31](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L31) |
| `fun isNotificationProcess` | [NotificationBinderService.kt:33](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L33) |
| `class NotificationBinderService : Service` | [NotificationBinderService.kt:36](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L36) |
| `fun onCreate` | [NotificationBinderService.kt:43](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L43) |
| `fun onBind` | [NotificationBinderService.kt:72](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L72) |
| `fun onDestroy` | [NotificationBinderService.kt:73](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L73) |
| `fun onTransact` | [NotificationBinderService.kt:86](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationBinderService.kt#L86) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt

| 声明 | 实现位置 |
| --- | --- |
| `class NotificationRepository` | [NotificationRepository.kt:20](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L20) |
| `fun initialize` | [NotificationRepository.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L35) |
| `fun receipt` | [NotificationRepository.kt:77](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L77) |
| `fun execute` | [NotificationRepository.kt:82](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L82) |
| `fun validRecord` | [NotificationRepository.kt:202](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L202) |
| `fun legacyNoticeTarget` | [NotificationRepository.kt:225](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L225) |
| `fun NoticeTarget.legacyRoute` | [NotificationRepository.kt:229](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/NotificationRepository.kt#L229) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/PositionAvailabilityNotices.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun positionNeedsAttention` | [PositionAvailabilityNotices.kt:15](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/PositionAvailabilityNotices.kt#L15) |
| `fun watchPositionAvailability` | [PositionAvailabilityNotices.kt:32](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/PositionAvailabilityNotices.kt#L32) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/RuntimeRecoveryNoticeReceiver.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RuntimeRecoveryNoticeReceiver : BroadcastReceiver` | [RuntimeRecoveryNoticeReceiver.kt:10](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/RuntimeRecoveryNoticeReceiver.kt#L10) |
| `fun onReceive` | [RuntimeRecoveryNoticeReceiver.kt:11](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/notification/RuntimeRecoveryNoticeReceiver.kt#L11) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt

| 声明 | 实现位置 |
| --- | --- |
| `fun retryRestore` | [LocalPassagePlanningService.kt:68](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L68) |
| `fun saveAvoidance` | [LocalPassagePlanningService.kt:100](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L100) |
| `fun removeAvoidance` | [LocalPassagePlanningService.kt:103](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L103) |
| `fun saveReview` | [LocalPassagePlanningService.kt:104](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L104) |
| `fun analyze` | [LocalPassagePlanningService.kt:116](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L116) |
| `fun plan` | [LocalPassagePlanningService.kt:117](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L117) |
| `fun navigationReferenceRejection` | [LocalPassagePlanningService.kt:119](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L119) |
| `fun cancel` | [LocalPassagePlanningService.kt:155](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L155) |
| `fun elevation` | [LocalPassagePlanningService.kt:233](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L233) |
| `fun terrainWater` | [LocalPassagePlanningService.kt:241](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L241) |
| `fun outsideAvoidance` | [LocalPassagePlanningService.kt:255](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L255) |
| `fun safe` | [LocalPassagePlanningService.kt:257](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L257) |
| `fun clear` | [LocalPassagePlanningService.kt:264](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L264) |
| `fun x` | [LocalPassagePlanningService.kt:269](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L269) |
| `fun crossings` | [LocalPassagePlanningService.kt:290](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L290) |
| `fun water` | [LocalPassagePlanningService.kt:302](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L302) |
| `fun simplifyShape` | [LocalPassagePlanningService.kt:325](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L325) |
| `fun preserveEndpoints` | [LocalPassagePlanningService.kt:337](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L337) |
| `fun endpoint` | [LocalPassagePlanningService.kt:347](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L347) |
| `fun searchSingleRasterPixels` | [LocalPassagePlanningService.kt:363](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L363) |
| `fun localPixel` | [LocalPassagePlanningService.kt:368](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L368) |
| `fun pixelId` | [LocalPassagePlanningService.kt:375](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L375) |
| `fun pixelX` | [LocalPassagePlanningService.kt:376](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L376) |
| `fun pixelY` | [LocalPassagePlanningService.kt:377](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L377) |
| `fun pixelElevation` | [LocalPassagePlanningService.kt:378](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L378) |
| `fun pixelPoint` | [LocalPassagePlanningService.kt:379](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L379) |
| `fun traversable` | [LocalPassagePlanningService.kt:381](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L381) |
| `fun removeEldestEntry` | [LocalPassagePlanningService.kt:405](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L405) |
| `fun lineKey` | [LocalPassagePlanningService.kt:407](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L407) |
| `fun lineCost` | [LocalPassagePlanningService.kt:416](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L416) |
| `fun heuristic` | [LocalPassagePlanningService.kt:436](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L436) |
| `class PixelNode` | [LocalPassagePlanningService.kt:437](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L437) |
| `fun coord` | [LocalPassagePlanningService.kt:504](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L504) |
| `fun id` | [LocalPassagePlanningService.kt:505](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L505) |
| `fun halfCoordinate` | [LocalPassagePlanningService.kt:513](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L513) |
| `fun safeHalf` | [LocalPassagePlanningService.kt:514](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L514) |
| `fun nodeSafe` | [LocalPassagePlanningService.kt:523](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L523) |
| `fun edgePassable` | [LocalPassagePlanningService.kt:527](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L527) |
| `class RasterNode` | [LocalPassagePlanningService.kt:541](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L541) |
| `fun controlConnectorClear` | [LocalPassagePlanningService.kt:542](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L542) |
| `fun nearestNode` | [LocalPassagePlanningService.kt:553](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L553) |
| `fun evaluate` | [LocalPassagePlanningService.kt:602](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/LocalPassagePlanningService.kt#L602) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class PassageProjection` | [PassageGeometry.kt:18](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L18) |
| `fun xy` | [PassageGeometry.kt:20](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L20) |
| `fun point` | [PassageGeometry.kt:21](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L21) |
| `fun line` | [PassageGeometry.kt:22](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L22) |
| `fun geometry` | [PassageGeometry.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L23) |
| `fun ring` | [PassageGeometry.kt:24](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L24) |
| `fun distance` | [PassageGeometry.kt:44](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L44) |
| `fun atDistance` | [PassageGeometry.kt:45](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L45) |
| `fun passageHash` | [PassageGeometry.kt:46](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L46) |
| `fun union` | [PassageGeometry.kt:47](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L47) |
| `fun around` | [PassageGeometry.kt:48](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L48) |
| `fun norm` | [PassageGeometry.kt:54](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L54) |
| `class FeatureGeometry` | [PassageGeometry.kt:57](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L57) |
| `class PassageWorldPurpose` | [PassageGeometry.kt:58](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L58) |
| `class PassageWorld` | [PassageGeometry.kt:59](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L59) |
| `fun world` | [PassageGeometry.kt:71](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L71) |
| `fun touchesQuery` | [PassageGeometry.kt:94](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L94) |
| `fun hintArea` | [PassageGeometry.kt:95](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L95) |
| `fun coverageGeometry` | [PassageGeometry.kt:148](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L148) |
| `fun blocksSearch` | [PassageGeometry.kt:203](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L203) |
| `fun validateRequest` | [PassageGeometry.kt:265](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L265) |
| `fun analyze` | [PassageGeometry.kt:276](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L276) |
| `fun issue` | [PassageGeometry.kt:279](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L279) |
| `fun along` | [PassageGeometry.kt:301](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L301) |
| `fun search` | [PassageGeometry.kt:376](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L376) |
| `fun clear` | [PassageGeometry.kt:380](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L380) |
| `fun coord` | [PassageGeometry.kt:398](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L398) |
| `fun id` | [PassageGeometry.kt:399](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L399) |
| `class Node` | [PassageGeometry.kt:400](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L400) |
| `fun smooth` | [PassageGeometry.kt:426](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageGeometry.kt#L426) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt

| 声明 | 实现位置 |
| --- | --- |
| `class RasterPassageKind` | [PassageRasterGeometry.kt:12](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L12) |
| `class RasterPassageArea` | [PassageRasterGeometry.kt:13](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L13) |
| `class PassageReferenceArea` | [PassageRasterGeometry.kt:26](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L26) |
| `class RasterPassageGeometry` | [PassageRasterGeometry.kt:27](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L27) |
| `fun rasterPassageGeometry` | [PassageRasterGeometry.kt:30](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L30) |
| `class Key` | [PassageRasterGeometry.kt:35](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L35) |
| `class Rectangle` | [PassageRasterGeometry.kt:36](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L36) |
| `fun classify` | [PassageRasterGeometry.kt:38](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L38) |
| `fun limit` | [PassageRasterGeometry.kt:47](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L47) |
| `fun registered` | [PassageRasterGeometry.kt:72](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L72) |
| `fun gridPoint` | [PassageRasterGeometry.kt:73](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L73) |
| `fun point` | [PassageRasterGeometry.kt:75](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L75) |
| `fun rectangle` | [PassageRasterGeometry.kt:77](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/PassageRasterGeometry.kt#L77) |

## runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/RasterSupercover.kt

| 声明 | 实现位置 |
| --- | --- |
| `object RasterSupercover` | [RasterSupercover.kt:9](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/RasterSupercover.kt#L9) |
| `fun clear` | [RasterSupercover.kt:10](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/RasterSupercover.kt#L10) |
| `fun visit` | [RasterSupercover.kt:23](../../runtime/marine-local/src/main/java/com/yokuli/runtime/marine/planning/RasterSupercover.kt#L23) |

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
