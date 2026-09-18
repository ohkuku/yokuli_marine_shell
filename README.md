# Yokuli OS

以 Windows Phone 8/10 的排版、横滑、磁贴和虚拟键组织的 Android 航海应用平台。当前版本 **0.5.0-experience.5**（versionCode 9）。本轮恢复中间 Home，将右侧搜索键改为通知；新增独立数据中心统一手机与船上设备的数据来源，并修复跨应用返回与页面恢复。

完整结构见 [26 项反馈、全部应用接口与数据拓扑](docs/product/OS_INTERFACE_TOPOLOGY.md)，声明位置见 [API 索引](docs/product/API_INDEX.md)。数据所有权见 [数据中心契约](docs/product/DATA_CENTER_CONTRACT.md)，页面访问与返回见 [应用导航契约](docs/product/APP_NAVIGATION_CONTRACT.md)。实际验证结果与边界见 [本轮交付记录](docs/experience/EXPERIENCE_5_DELIVERY.md)。

## 应用

| 应用 | 主要操作 |
| --- | --- |
| 海图 | 准星选点、先预览坐标再进详情、双图钉测距、规划/预览/使用路线、全局记录。 |
| 图册 | 文件夹、命名图层、扫描、改名、文件启停与优先级、移除/恢复、解除关联。 |
| 航海日志 | 当前航程、时刻与笔记、暂停/继续/保存、历史回放/报告/导出、在海图预览。 |
| 守锚 | 下锚、选点和半径、值守、近期渐隐轨迹、累计停留区域、起锚与收藏。 |
| 我的航行 | 坐标、航线、收藏锚地、集合、GPX、预览与导航。 |
| 驾驶台 | 真实罗盘、风向、姿态、量表与趋势；自选仪表、拖动和上下排序。 |
| 数据中心 | 逐项查看已采用读数、手机与 NMEA 候选、来源和更新时间；选择来源、启停手机定位、固定手机与校准。 |
| 船联网 | 多连接、报文与流量、发送目的地、按能力发送系统读数或转发指定输入。 |
| 数据共享 | 本机监听服务、实际连接客户端、选择发布内容与转发输入；采用数据中心选定的读数。 |
| 设置 | 船名与船舶资料、语言、单位、坐标格式、主题色、文字大小、常亮、声音、权限、资料备份。 |

磁贴工坊是 Shell 工具：先选择应用，再横滑预览样式和大小。同一应用只保留一块磁贴，切换样式保留位置；实时读数与应用使用同一来源。海图图像快照显示捕获时间，船位和速度独立实时更新。

## 一套系统

- 普通应用入口打开新的首页访问；最近任务恢复原页面实例。明确的跨应用对象操作使用调用关系，Back 恢复调用者的原页、选择和草稿；工具和内部子页优先返回。任务卡使用真实窗口截图，冷启动才显示应用启动画面。中间 Home 回桌面并结束当前跨应用调用链。
- 坐标、测距、航线、锚圈在地图引擎内原生绘制，与海图同帧缩放。准星坐标紧挨底部命令，比例尺使用整数刻度并避开操作区。
- 海图、锚警、坐标预览和日志回放共享图源与地图组件，各自保留视野。选择在线、卫星或任一自定义文件夹图层。
- 自定义海图内置离线全球陆地/海岸参考底图，不依赖首次下载。它不包含水深或助航资料，不能替代用户海图。[数据说明](docs/product/OFFLINE_WORLD_BASEMAP.md)。
- 海图、日志、系统栏和磁贴读取同一个航行会话。记录、导航、锚警彼此独立，关闭页面不停止后台工作。
- 数据中心统一船位与逐项读数来源，展示手机、NMEA 和派生候选及采纳结果；手机定位启停与安装校准也在这里。继续使用原 `VesselSettingsRepository.metricSourcePins` 与船位 `GpsDataSource / POSITION_CONNECTION`，不创建另一套来源配置。选中来源失效不会偷偷切换；GPS 短时未更新只显示时效，不反复遮挡操作。
- 本机分享与主动发送共用发布规则；按能力筛选实际报文，保留来源与连接代次，禁止给相同 IP 回送其输入数据。
- 轻提示先出现在系统栏，底部右键（原搜索键）打开通知中心，中间 Home 始终返回开始屏幕；通知中心保留应用、正文、时间和详情；顶部下滑完全交还 Android 系统。阅读通知不解除业务警报。
- 统一字体、圆形单选、矩形输入框、横滑和细节动效。全局单位、坐标格式、文字大小在应用和磁贴同步。全屏控件按圆角横向避让。
- 声纳测深调查的界面、采样与自动恢复已移除；既有数据库保留，真实 NMEA 水深仍可作为仪表和条件警戒输入。

## 构建和密钥

```bash
./gradlew :app-shell:assembleStandaloneDebug
# app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
```

Java 17、Android SDK 36；Android 9 及以上；包名 `com.yokuli.marine`。原 Gradle wrapper、签名参数和 CI 下载制品继续使用。

Google 在线/卫星底图的 `GOOGLE_MAPS_ANDROID_API_KEY` 仍由原仓库的密钥管理注入。GitHub 使用同名 Repository Secret；本地使用：

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew :app-shell:assembleStandaloneDebug
```

密钥不写入源码，见 [密钥管理](docs/SECRETS_MANAGEMENT.md)。无 Google Key 时可使用用户本地海图、内置参考底图和联网 OpenStreetMap。

## 体验重点与边界

按海图选点→标记预览→详情→我的航行首页、下锚→值守→起锚、记录→时刻→暂停→保存→地图回放走完整故事；再检查多连接停止隔离、输出能力选择和全局偏好。

当前支持栅格 PNG/JPEG/WebP MBTiles；S57/S63、PBF、GeoTIFF、PMTiles 未接入。导航为用户手动规划与几何引导。锚泊色块表示实际记录到的停留范围，不是声纳或安全水域推断。

任务截图与趋势有各自的进程内保留周期。资料备份覆盖原航行、锚地、照片和船舶资料；不宣称是包含所有 Shell/连接/文件夹授权的一键整机备份。新坐标和路线可 GPX 导出。

真实船网、GNSS、报警声、厂商后台和曲面屏仍需真机体验；构建和模拟器检查不替代这些条件。字体使用 Microsoft 开源 Selawik（许可证随包附带），中文采用平台 CJK 回退，不宣称完全等同于 Segoe WP。

## English

Version **0.5.0-experience.5** (versionCode 9) restores the centre Home key and puts notifications on the former right-hand Search key. Data Center owns phone and NMEA source selection, phone location and mounting calibration. Boat Network owns connections and outgoing traffic; Data Sharing owns the local publishing service. All three use the existing shared source policy. Linked app operations return to the original caller page; ordinary launches open the app home, while Recents resumes its existing page. Chart annotations render natively with the map camera; anchor watch emphasizes the map and real swing history. Instruments use meaningful live graphics and a reorderable personal layout. Tile Studio presents one app at a time, with one configurable tile per app. Local NMEA and external outputs share actual capability filtering and source-aware IP echo prevention. Sonar survey UI, acquisition and automatic restoration are removed while historical records are retained.

See the linked interface topology, contracts and delivery record for implementation and validation boundaries. The original build, Android identity and API-key management remain in use.
