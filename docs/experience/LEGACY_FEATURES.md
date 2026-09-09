# 旧版能力迁入 / Marine capability migration

旧版 `yokuli_nmea_anchor_alarm` 的业务代码、数据库及迁移、资源和服务已整体纳入
`legacy-marine`，并由 Yokuli OS 同一个 Activity、同一套数据服务运行。下表按界面调用与命令链核对；
“已接入”表示有真实实现和入口，不表示已经通过实船验收。

The complete old production module is embedded in Yokuli OS. This table audits
reachable screens and their commands, rather than counting copied files.
Device and boat acceptance is still required.

| 能力 / Capability | 新入口 / Entry | 条件与边界 / Conditions |
| --- | --- | --- |
| 已知锚点、拖锚半径、运行中调半径 / Known anchor and adjustable radius | 锚警报 → 当前 / Anchor watch → watch | 选择定位源；启动会检查有效定位和通知权限 / Explicit source and start checks |
| 自动锚中心估算、候选接受/拒绝、重新估算 / Centre estimation and review | 锚警报 → 设置锚点、当前估算、历史 / Anchor setup, estimate and history | 真实轨迹与几何证据；不足时保持“学习中” / Evidence required; no invented estimate |
| 暂停、继续、起锚、24 小时轨迹 / Pause, resume, lift and trail | 锚警报 → 当前 / Anchor watch → watch | 暂停保留会话，起锚结束并保存 / Paused session remains intact |
| 循环响铃、通知、确认/延后提醒、自定义声音 / Alarm, notifications, snooze and sound | 全 Shell 警报浮层；设置 → 船舶设置 → 报警与通知 / Global overlay; boat settings → alarm | 需要设备上的声音、通知及后台权限验收 / Physical device acceptance |
| 浅水/深水、风速/风向变化警戒 / Depth and wind guards | 锚警报 → 下锚设置或当前警戒配置 / Anchor setup and live guard controls | 对应的新鲜 NMEA 数据；数据缺失不能当成安全 / Fresh qualified measurements |
| 预检查、监控健康、事件日志、诊断包 / Preflight, health, incidents and support | 锚警报；船舶设置 → 存储与支持 / Anchor watch; storage & support | 诊断包默认不包含精确位置和原始 NMEA / Privacy-preserving default |
| TCP/UDP 输入、原始语句、校验、连接健康 / NMEA RX and diagnostics | NMEA → 连接、原始数据 / NMEA → connection, raw data | 输入连接独立于是否采用 NMEA 船位 / Transport is independent of source selection |
| 手机数据反向输出、同一全双工连接、独立 TCP/UDP / Phone → boat publishing | NMEA → 输出 → 向船载网络发送 / NMEA → output → boat network | 显式开始；仅发布本机或 App 自有测量，防止回声 / Explicit start and loop prevention |
| 独立本机 NMEA TCP 服务 / Standalone NMEA server | NMEA → 输出 → 在本机提供服务 / NMEA → output → phone service | 自己的端口、启停和客户端；不要求船载输入 / Independent listener and lease |
| 手机 GNSS、船首向、ROT、姿态、气压、推算风发布 / Local measurement publication | NMEA 输出 → 手机到船网的流选择 / Output stream selection | 每个字段分别选择，等待有效来源；姿态需要确认安装 / Per-stream readiness and mounting |
| Android 定位代理 / Android mock GPS proxy | 数据 → 来源详情 → NMEA → Android GPS 代理 / Data → source details → GPS proxy | 用户在 Android 开发者选项指定本 App；不能伪装独立手机 GPS / Explicit Android provider setup |
| 字段来源、更新年龄、冲突、船首向选择 / Provenance, age, conflict and heading | 数据 → 来源详情 → 点击读数 / Data → source details → reading | 船位统一服从手机/NMEA/关闭选择；不会提供不起作用的另一个 AUTO 开关 / One explicit position choice |
| 航程开始、暂停、继续、结束、航点 / Trip recording and waypoints | 海图记录按钮；航行记录 / Chart recording; trip recorder | 同一真实记录引擎；锚警与航程不能同时活动 / Same recorder; watch/trip exclusivity |
| NAV/航行/运动/天气、命名仪表、自定义字段绑定与记录 / Instruments and custom fields | 仪表或航行记录 → 仪表与编辑 / Instruments / trip recorder | 缺失保持空缺，自定义记录最高 2 Hz / No fake zeroes; up to 2 Hz custom recording |
| 手机横倾/纵倾安装、船首向对齐、独立气压 / Phone mounting, heading and pressure | 船舶设置 → 手机船舶传感器；航程启动 / Boat sensors; trip start | 对齐确认不等于把已倾斜船体“归零” / Preserve actual heel |
| 航程报告、最快 500 m、帆航/运动/水深/气压分析 / Trip reports and analytics | 航行日志 → 已完成航程 / Logbook → completed voyage | 按已记录字段显示；吃水余量需要兼容的水深参考 / Recorded evidence and compatible depth reference |
| 指标着色回放、跳转事件、航点 / Coloured replay and event navigation | 航行日志 → 航程 → 地图/回放 / Logbook → voyage → map/replay | 读取已保存航程，不会把所有航线常驻在海图上 / Explicit saved voyage selection |
| CSV、GPX、KML/KMZ、图片、AI 源 ZIP / Exports | 航行日志或锚警历史 → 分享 / Voyage or anchor history → share | 使用 Android 文件/分享流程；AI ZIP 需精确位置确认，无自动上传 / Reviewed local exports |
| 收藏锚地、区域、集合、笔记、照片、防护、到访记录 / Anchorage library | 锚地 / Anchorages | 本地数据库管理；导入/恢复不等于自动迁移另一个 App 的私有沙盒 / Local storage with explicit import |
| 双 QR 分享卡、扫码/相册导入 / QR sharing and reviewed import | 锚地 → 地点详情 → 分享；扫码按钮 / Anchorage → share or scan | 富信息导入经用户查看，摄像头需要授权 / Reviewed import and camera permission |
| 接近锚地、距离/方位、船向/手机方向 / Anchorage approach | 锚地 → 点位 → 接近 / Anchorage spot → approach | 直线参考；不是障碍规避或自动安全航线 / Direct approach reference |
| 个人水深记录、同服务器匹配、调查管理 / Personal sonar surveys | 个人水深图 / Personal sonar | 真实定位和水深必须来自同一个 NMEA 服务器 / Same-server position/depth pairing |
| 手动/LINZ 潮汐改正、离线基准面历史 / Tide correction and history | 个人水深图 → 新调查的潮汐选项、历史图层 / Sonar → tide options and history | 自动改正依赖可用站点/年度预测，缺失明确显示 / Real provider data required |
| 共用文件夹海图与区域 LINZ 图层 / Shared folder charts and LINZ overlays | 海图库；锚警地图 → 图层 / Chart Library; anchor layers | 所有 9 个旧地图入口共用同一图层与文件优先级；不再单独导入单个文件 / All nine map surfaces share the folder compositor |
| 完整备份/恢复、缓存清理、隐私、反馈 / Backup, restore and support | 船舶设置 → 数据与备份、存储与支持、关于 / Boat settings | 恢复有明确替换确认；不会自动上传 / Explicit replacement and sharing |
| 显式演示轨迹与声呐 / Explicit demo | 数据 → 来源详情 → 开发者与演示 / Source details → demo | 使用真实 GNSS 起点；演示状态与真实记录分开 / Real origin, labelled simulation |

本轮还修复了旧界面在嵌入后会失效的“打开 NMEA/数据”“调整警戒半径”等导航命令，
并让虚拟返回先退出子页面。共用 Shell 语言和外观，嵌入界面不再出现另一套底部导航或重复大标题。

The Shell now owns language, appearance, app navigation and virtual Back. Legacy
navigation commands emit transient requests; nested settings, replay, approach
and anchorage pages consume Back before the application closes.
