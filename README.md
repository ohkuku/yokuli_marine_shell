# Yokuli OS

以 Windows Phone 8 的排版、磁贴和滑动体验，重新做一个简单好用的航海应用平台。

当前是 **0.2.0 experience** 手动体验版，基于 `yokuli_marine_shell` 重建。先验证实际使用，再恢复自动测试、性能门禁和发布工程化。历史实现和文档仍保留，默认构建只编译 `app-shell/src/rebuild` 中的新实现。

## 这一版可以做什么

- **开始屏幕**：全屏运行，左右滑动切换磁贴与应用列表；长按磁贴拖动、切换三种尺寸、取消固定；虚拟返回、开始、搜索，长按返回查看最近应用。
- **海图**：直接平移缩放、准星选点、一键保存标记、拖动 A/B 图钉测距、在图上添加和拖动航点、保存/编辑/反向复制航线、逐点直线引导。船位跟随可被手动拖图打断。
- **海图库**：连接用户文件夹或导入离线副本；栅格 PNG/JPEG/WebP MBTiles，256/512 图块，TMS/XYZ，支持 `tiles` 表和视图；缺少中间缩放级别时使用正确父图块区域。文件范围不能代替实际图块覆盖。
- **我的航行**：标记名称与备注、航线、GPX 导入导出。未保存航线草稿在应用保存时一并保留。
- **船舶数据**：手机定位与 NMEA 船位由用户明确选源；每个仪表值显示来源和年龄，缺失显示空缺、过期明确降级。
- **NMEA**：TCP 输入、UDP 接收、TCP 单句发送、沿当前 TCP 连接输出新鲜手机船位、本机独立 TCP 共享服务。服务由进程持有，切换壳内应用不会断开；共享不把过期数据当成新数据发送。
- **个性化**：中英文即时切换，八种主题色，深浅背景、常亮和减少动画。

## 安装和构建

GitHub Actions 的 **Build downloadable human-test APK** 继续提供可安装 APK，不等待旧测试矩阵。下载 `YOKULI-OS-DEBUG-<commit>` 制品。

```bash
./gradlew assembleStandaloneDebug
# app-shell/build/outputs/apk/standalone/debug/app-shell-standalone-debug.apk
```

需要 Java 17 和 Android SDK 36。包名仍为 `com.yokuli.marine`，使用原有 Gradle wrapper、版本环境变量、签名环境变量和 `standalone` 构建名称。旧应用数据不删除；新体验的数据使用独立文件，旧数据尚未自动迁移。

### API Key 原样继承

`scripts/secrets/yokuli-secrets.sh`、`secrets/identity.age`、`recipient.txt`、`vault.json.age` 原样保留。GitHub 继续通过 `GOOGLE_MAPS_ANDROID_API_KEY` Repository Secret 注入 Manifest。Key 不写入源码、日志或字符串 BuildConfig。

```bash
./scripts/secrets/yokuli-secrets.sh run -- ./gradlew assembleStandaloneDebug
```

详见 [现有密钥管理说明](docs/SECRETS_MANAGEMENT.md)。无需解锁 vault 即可构建离线海图；没有 Google Key 时普通地图使用 OpenStreetMap，卫星模式不可用。Key 已配置不等于服务授权成功。

## 第一轮手动体验

1. 左右滑动桌面；长按磁贴并拖动，再改变尺寸。打开设置，试中英文和浅色背景。
2. 海图库连接真实文件夹，查看海图；缩放跨越原文件的多个级别，并在无覆盖处检查提示。
3. 拖图选点，标记，再打开收藏修改备注。测距时分别拖 A/B，地图应保持不动。
4. 新建三点航线，拖动中间航点、保存，退出海图再打开；使用、下一点、结束，然后导出 GPX。
5. NMEA 输入真实船载地址，查看原始语句及来源；切回桌面再打开，连接应继续。断开服务器，观察数据过期。
6. 开启共享，用另一台设备连接显示的 IP/端口；单独开启手机 GPS，也可提供独立船位。关闭数据来源后应停止输出过期数据。

## 当前边界

这是实际体验候选，不宣称完成旧版所有业务。锚警报、声呐测绘、完整航迹、AIS、固件和外部应用包安装尚未迁入；没有用占位页面代替。航线提供直线距离与方位，不做避险自动规划或自动舵控制。

海图库目前不读取 S57/S63、PBF、GeoTIFF 或 PMTiles。部分 Android 文件提供器无法被 SQLite 原地读取，此时会创建本机兼容副本并占用空间；外部原件保持不变。支持最多 12 个同时显示的来源。重新扫描后反映目录增减和提供器报告的文件版本变化。

本地已做 APK 构建和模拟器关键流程验证；真实海图文件、船上网络、GNSS、OEM 后台行为和操作手感仍需你的手测。[实现与验证记录](docs/experience/NOTES.md)

## English

Yokuli OS is a WP8-inspired marine shell rebuilt around direct chart interaction. This experience build includes Start/app-list swipes, editable tiles, chart marks and draggable rulers, on-chart route editing, a folder-based MBTiles library, explicit GPS/NMEA sources, bidirectional TCP and standalone NMEA sharing. Chinese and English ship together.

Build with `./gradlew assembleStandaloneDebug`. The existing encrypted secrets vault, `GOOGLE_MAPS_ANDROID_API_KEY`, application ID, signing variables and GitHub human-test APK workflow are retained. Engineering gates are deferred while the owner reviews the experience. The historical implementation is retained for reference, outside the default build.
