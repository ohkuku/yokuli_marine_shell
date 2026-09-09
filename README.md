# Yokuli OS

基于原版 Windows Phone 8 Shell 交互的 Android 航海应用平台。当前代码版本 **0.4.0-domains.1**，按九个 App 和一套系统会话重新组织，供手动体验。

产品约定见[领域模型](docs/product/OS_DOMAIN_MODEL.md)，累积反馈见[整改清单](docs/product/OS_REWORK_BACKLOG.md)。0.3 的旧工作区组织已经撤下，历史截图不代表这一版的验收结果。

## 九个应用

| App | 主要内容 |
| --- | --- |
| 海图 | 直接操作地图、准星标记、双图钉测距、路线规划与导航、系统记录按钮、实测水深。 |
| 海图库 | 授权文件夹、创建命名图层、扫描与错误处理、文件优先级和覆盖范围。 |
| 航行日志 | 当前记录、历史、时刻与备注、共享地图回放、报告和导出。 |
| 锚警 | 新地图上的准备与值守、未知锚位学习、候选采用、半径与条件警戒、历史与收藏。 |
| 我的航行 | 坐标、航线、锚地、集合、照片和到访；引用原资料，GPX 交换与归档恢复。 |
| 仪表 | 真实读数、趋势、可配置仪表、船体 3D 姿态和安装校准。 |
| NMEA 输入及输出 | 多条命名连接、每连接状态、收发路由、来源和原始语句。 |
| 本机 NMEA 客户端 | 当前呈现已有的本机发布服务，显示实际客户端及发送状态；最终客户端/服务端角色仍待确认。 |
| 设置 | 船名、船体与传感器参考、语言、单位、坐标格式、显示、声音、来源、权限、磁贴偏好和资料备份。 |

## 系统行为

- Shell 沿用 `codex/shell-map-contract` 的 Start、磁贴、横滑、转场、搜索、最近任务、虚拟键与原存储。九 App 注册迁移保留现有磁贴身份与布局。
- 海图、锚警、坐标选点和日志回放共用地图组件。来源直接单选在线、卫星或某个命名图层，显示当前名称；地图内容不会改变原生视口高度。
- 记录只有一套控制面板。导航、记录和锚警各自拥有会话，允许途中锚泊继续记录。关闭界面不停止后台任务。
- 保存路线不自动导航或铺图；正在导航使用冻结的路线快照，编辑收藏不会移动当前目标。
- 船位统一为关闭、手机或 NMEA。手机选择负责权限与系统定位流程；活动航程不覆盖它。NMEA 连接和字段采用分开，失效不偷偷切手机。
- 旧技术模块保留解析、Room 数据、估计、告警、采样与导出；新 App 不再嵌入旧全业务工作区。

## 安装和构建

```bash
./gradlew :app-shell:assembleStandaloneDebug
# app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
```

需要 Java 17、Android SDK 36；运行要求 Android 9 或以上，包名仍为 `com.yokuli.marine`。GitHub Actions 的 **Build downloadable human-test APK** 通过原构建生成 `YOKULI-OS-DEBUG-<commit>` 制品。本轮不恢复旧测试矩阵，以编译、定向运行检查和用户手动体验推进。

## API Key

原 `scripts/secrets/yokuli-secrets.sh`、加密 vault、版本/签名变量、Gradle wrapper 及 `standalone` 构建继续使用。GitHub 使用 Repository Secret `GOOGLE_MAPS_ANDROID_API_KEY`，共享地图读取同一 Manifest Key；LINZ 可通过原环境提供 `LINZ_API_KEY` / `YOKULI_LINZ_API_KEY`。

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew :app-shell:assembleStandaloneDebug
```

见[密钥管理说明](docs/SECRETS_MANAGEMENT.md)。无 Google Key 可使用本地海图与 OpenStreetMap；卫星地图需要有效授权和网络。旧独立 App 的私有数据通过其导出/备份导入，不越过 Android 应用隔离自动读取。

## 手动体验重点

1. 保留旧安装数据升级，检查九 App、磁贴和子页返回；切换中英文、单位与磁贴内容。
2. 连接包含重叠 MBTiles 的文件夹，命名图层、调整文件优先级；在海图、锚警和回放直接切来源。
3. 拖动、显示准星、移动测距图钉和连续切源，检查相机、分辨率与响应。
4. 保存、选择、导航分别操作；记录途中下锚，结束记录后值守继续，编辑收藏不改变活动会话。
5. 两条输入、两条输出并行，停掉一条后检查其他连接；断开选定船位源时明确失效。手机与 NMEA 都关闭后应无隐藏定位请求。
6. 收藏锚位、补充照片和到访，重新使用该坐标；回放、报告和导出对应真实记录。

## 当前边界

- 文件夹图层支持栅格 PNG/JPEG/WebP MBTiles（256/512，TMS/XYZ，tiles 表或视图），按文件优先级透明合成；S57/S63、PBF、GeoTIFF、PMTiles 尚未支持。部分文件提供器需要兼容副本并占用本机空间。
- 导航为人工规划与几何引导；没有自动避险航路、商业海图授权或自动舵控制，也不生成缺失水深、潮汐、仪表数据。
- 设置里的备份覆盖原航行/锚地/照片/船舶资料与已有输出设置；尚不是包含 Shell 布局、全部新连接配置和海图库授权的一键整机备份。新坐标和路线通过 GPX 导出。
- 本机节点目前保留实际的手机数据发布服务。多输入系统观测汇总发布和用户所指“客户端”的最终职责不能视为已完成。
- 真实船网、GNSS、声音、厂商后台行为和用户重叠海图仍需真机验收。编译或模拟器启动不代替这些验证。

## English

**0.4.0-domains.1** reorganizes Yokuli OS into nine apps using the original marine_shell WP8 launcher, build and key management. Chart, anchor watch, coordinate picking and replay share one map implementation and current source. Recording has one controller; navigation, recording and anchor watch have independent sessions. Saved route edits do not mutate active guidance.

The apps now use the retained business/data engines directly instead of nesting the previous Boat Watch workspace. Settings restore global units, vessel information, display, sound, permissions and tile preferences. NMEA sessions are partitioned by connection. My Sailing presents coordinates, routes and existing anchorage records with their spots, visits and photos.

This is a manual-review build. Real charts, marine connections, device positioning and alarms still need owner acceptance. The local NMEA app currently exposes the existing publisher service; its final client role and aggregate publication remain open. Existing sailing/vessel backup is explicitly scoped and is not a full OS backup.
