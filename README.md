# Yokuli OS

以原版 Windows Phone 8 Shell 交互为基础的 Android 航海应用平台。当前为 **0.3.0-experience.2**，优先手动体验与功能探索。

## 本轮改动

- **Shell 回归原版**：直接使用 `codex/shell-map-contract` 的桌面、应用列表、磁贴拖动与编辑、转场、最近应用、搜索和虚拟实体键。保留原来的 Start 文档与布局规则。
- **海图稳定交互**：准星和信息条浮在地图上，不再改变地图高度；切换视图会替换整个原生地图视图。标记、可拖动双图钉、航线编辑仍直接在海图操作。
- **文件夹图层**：连接用户文件夹，为文件夹创建并命名图层；在文件夹内排序文件优先级。重叠图块按优先级合成，透明区域由下方文件补齐；图层也可排序、开关、重命名。扫描只读外部原件。
- **航线与航迹分开**：保存航线只保存，明确查看或使用后才显示；海图恢复开始记录按钮，记录可暂停、继续、标记和结束。活跃航迹直接显示在海图。
- **船位开关**：手机 GPS、NMEA 船位都可以关闭。开启手机 GPS 时申请权限并启动定位；NMEA 连接后才可选择船位。选中一项时另一项禁用，数据失联不自动换源。
- **旧应用业务接回**：锚警报、自动锚心估算、风与水深值守、航行记录/报告/回放/导出、锚地收藏与二维码、个人声呐与潮汐修正、手机传感器与自定义仪表、完整 NMEA 输入/输出/独立服务/全局 GPS 代理、备份与诊断。见 [功能入口清单](docs/experience/LEGACY_FEATURES.md)。

主界面保持简单：海图处理地图操作；记录处理当前航行；日志处理历史；高级数据能力放在各自应用中。没有生成虚假的船位、航速、海图覆盖或在线服务状态。

## 安装和构建

```bash
./gradlew :app-shell:assembleStandaloneDebug
# app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
```

需要 Java 17、Android SDK 36，运行要求 Android 9 或以上。包名仍为 `com.yokuli.marine`。GitHub Actions 的 **Build downloadable human-test APK** 提供 `YOKULI-OS-DEBUG-<commit>` 制品；本轮不运行旧测试矩阵。

原有版本变量、签名变量、Gradle wrapper 和 `standalone` 构建名称继续使用。Shell 原源码保留，当前将原版 Shell 模块与重制应用组合构建；旧 Boat Watch 业务以 `legacy-marine` 模块接入同一进程。只有一套船舶后台数据服务，切换壳内应用不会另开 NMEA 连接。

## API Key

原有 `scripts/secrets/yokuli-secrets.sh` 与加密 vault 原样保留，GitHub 继续使用 Repository Secret `GOOGLE_MAPS_ANDROID_API_KEY`。两个地图入口共用同一 Manifest Key。LINZ 可通过同一运行环境提供 `LINZ_API_KEY` / `YOKULI_LINZ_API_KEY`。

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew :app-shell:assembleStandaloneDebug
```

详见 [密钥管理说明](docs/SECRETS_MANAGEMENT.md)。没有 Google Key 仍可使用本地海图与 OpenStreetMap；Google 卫星地图需要有效授权和网络。旧应用中的数据可通过原有备份/恢复界面迁入，不会越过 Android 应用隔离自动读取另一个包的数据。

## 手动体验重点

1. 对照原 Shell 操作桌面、编辑磁贴、滑动、返回和最近应用。
2. 海图库连接一个包含重叠 MBTiles 的文件夹，命名图层，调整文件优先级；回图检查覆盖变化。
3. 拖动海图，检查准星出现时地图尺寸与分辨率；连续切换普通/海图/卫星视图。
4. 保存航线后应从图上消失；明确查看或使用后才出现。开始航行记录，切换应用，再返回检查是否继续。
5. 船位全部关闭 → 开手机 GPS → 关手机 GPS → 接 NMEA → 开 NMEA 船位 → 关闭船位。输入仍可继续提供风、水深与其他仪表。
6. 逐项体验锚警报、记录与回放、声呐、NMEA 独立服务以及导出。报警声音、真实 GNSS、真实船网和各厂商后台行为仍需真机确认。

## 数据范围

文件夹海图库目前读取栅格 PNG/JPEG/WebP MBTiles（256/512，TMS/XYZ，tiles 表或视图）。缺少中间级别时使用正确父图块区域。S57/S63、PBF、GeoTIFF、PMTiles 暂不支持。部分 Android 文件提供器需要本机兼容副本，连接大型目录可能占用相应空间。

航线仍为人工规划与直线引导，不提供避险自动航路或自动舵控制。无内置商业海图授权，也不生成缺失的海图、潮汐或仪表数据。

产品流程参考 [Garmin Navionics 功能说明](https://www.garmin.com/en-CA/garmin-technology/marine-technology/charts-and-maps/navionics-boating-app/) 与 [官方用户指南](https://support.garmin.com/et-EE/?faq=qglPSVEgoH4Hl6GIbHoHx9)：保留标记、航线、轨迹的不同操作语义，将地图操作留在地图上。具体 UI 继续遵循本项目的 WP8 Shell。

## English

Yokuli OS combines the original marine_shell WP8 launcher and interaction engine with direct chart operation and the existing Boat Watch business engine. Folder-based named chart layers support per-file priority and transparent overlap. Routes appear only when selected; recording is separate, with a live track and pause/resume/finish controls. Phone GPS and NMEA position can both be off.

Anchor watch, voyages and replay, exports, anchorages, sonar/tide correction, phone instruments, complete NMEA input/output/server/proxy and backup are available through dedicated Shell apps. Build and key management remain based on marine_shell. This is a manual experience candidate; real marine data and device behavior require owner review.
