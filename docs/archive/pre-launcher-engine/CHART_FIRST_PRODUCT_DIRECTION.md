# Yokuli OS — WP8 Shell + Chart-first 产品方向

归档状态：`SUPERSEDED_PRE_LAUNCHER_ENGINE`。仅保留历史研究；当前唯一施工主文档是 [`LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md`](../../requirements/LAUNCHER_SHELL_ENGINE_MASTER_SPEC.md)。

> English: `SUPERSEDED_PRE_LAUNCHER_ENGINE`. This is historical research only; the linked Launcher Shell Engine Master Spec is the sole current construction contract.

## 1. 为什么重新开始

上一版的问题不是采用 WP8，而是把功能清单直接铺成了一墙缺乏层次的磁贴。Map、Anchor、Navigation、Sonar、Trips、Data Sources 等都被做成平级入口，既不像成熟海图产品，也没有还原真正的 WP8 Live Tile 体验。这会产生三个问题：

1. 用户启动后不能立即看到最重要的海图与船位；
2. Anchor、Navigation、Survey 容易各自发展出重复地图、camera state 和工具；
3. 大量系统入口抢占首屏，航海任务反而被埋没。

Yokuli OS 继续使用完整的 WP8 Shell。成熟海图产品用于决定磁贴后面的功能组织，不用于替换 WP8 桌面。

## 2. 市场产品中采用的共同模式

- B&G Zeus S 使用 Anchoring、Cruising、Racing 等场景模式，只显示当前情境所需信息。
- Simrad NSX 将 charting 作为核心体验，并使用 activities bar 保持常用工作入口可达。
- Raymarine LightHouse 把 Anchor 作为 Chart App 的模式，而不是另一套地图应用。
- Navionics Boating 将图层、离线海图、路线、标记、自动导航、天气潮汐和 SonarChart Live 围绕同一海图组织。

Yokuli 不复制任何品牌资产或具体页面，但采用这些经过市场验证的信息架构。

参考：

- https://www.bandg.com/en-gb/zeus-s/
- https://www.simrad-yachting.com/nsx/
- https://support.garmin.com/en-US/?faq=E4Seu3gusY9iS3YktWkfyA

## 3. 最终组合方式

应用启动后进入真正的 WP8 Start Screen：

```text
WP8 Start Screen
├── CHART    → Chart.Browse
├── ANCHOR   → Chart.Anchor
├── COCKPIT  → Cockpit.Overview
├── LIBRARY  → Library
└── SYSTEM   → System
```

默认桌面只固定这五个入口。Navigation、Survey、Trips、Anchorages、NMEA 和 Data Sources 留在 All Apps，也允许用户自行 Pin，但不再默认铺满首屏。

进入 Chart 后，它保持全屏并承载四个工作模式：

```text
BROWSE    浏览与查看
NAVIGATE  目标、航点和路线
ANCHOR    下锚、监控和恢复
SURVEY    测深调查与覆盖
```

四种模式共享同一个：

- `MarineChartSurface`
- viewport / camera state
- chart source 与离线区域
- boat position presentation
- selection model
- object model
- layer renderer

任何功能不得创建第二套完整交互地图。

## 4. WP8 Start Screen 结构

```text
┌────────────────────────────────────┐
│ 23:21              GPS · NMEA · 72%│
├────────────────────────────────────┤
│ CHART · HERO 4×2                   │
│ 轻量海图、船位、当前活动模式        │
├──────────────────┬─────────────────┤
│ ANCHOR · 2×2     │ COCKPIT · 2×2  │
│ SAFE · 32/60 m   │ 6.2 kn · 184°  │
├──────────────────┼─────────────────┤
│ LIBRARY · 2×1    │ SYSTEM · 2×1   │
└──────────────────┴─────────────────┘
                         左滑 → Apps
```

这不是静态彩色按钮墙。每个磁贴有清楚的职责：

- Chart Hero 是一张轻量、可解释的动态海图缩略图；
- Anchor 永远优先显示安全状态，不轮播隐藏 ALARM；
- Cockpit 显示最关键的航行数据，不塞满仪表；
- Library 显示最近内容或数量摘要；
- System 只显示连接健康和告警计数。

磁贴之间以黑色间隙形成 WP8 节奏，不使用卡片阴影、圆角容器、Material Ripple 或 Dashboard 分组标题。

### All Apps

Start 向左滑进入 WP8 字母列表。列表同时包含四个核心 App 和深链接 Shortcut：Anchor、Navigation、Survey、Trips、Anchorages、Data Sources、NMEA、Diagnostics、Settings。长按可以 Pin/Unpin。

## 5. 进入 Chart 后的成熟海图结构

海图上只保留当前必要工具：

- zoom
- recenter/follow
- layers
- search
- measure
- mode switcher

进入 Anchor、Navigate 或 Survey 后，用当前模式的操作替换非必要工具，避免同时堆满按钮。

### Context Inspector

点击海图对象、航点、锚地、AIS/未来目标后，从底部或右侧打开 Inspector：

- 对象名称与类别
- 方位和距离
- 深度/风险摘要
- Navigate、Save、Details 等上下文动作

手机使用 bottom sheet；宽屏使用右侧 mini inspector。它不改变海图 camera owner。

## 6. 视觉语言

外层 Shell 继续尽可能还原 WP8 的视觉、手势和 UX：

- 纵向 Start Screen、Live Tile、向左滑 Apps、字母 jump list；
- 长按移动、Resize、Unpin 与离散网格重排；
- 平面、高对比、无阴影；
- 大号轻字重与清晰数字；
- 明确的按压反馈和短转场；
- 黑色夜间基底；
- 少量强调色；
- 状态绝不只依赖颜色；
- 避免 Material 大圆角卡片和装饰性容器。

主题从一开始支持：

- `DAY`：强光可读；
- `DUSK`：低亮度冷色；
- `RED_NIGHT`：后续实测后启用。

海图颜色服从航海信息层级，不服从 Launcher accent。浅水、危险物、航道、选中路线和活动安全边界必须拥有稳定、不可混淆的语义色。

## 7. 核心工作流

### Browse

启动 → 立即看到最后有效视区或船位 → 浏览/跟随 → 选择对象 → Inspector → Navigate/Save/Details。

### Navigate

选择目标或 Route → 检查路线 → Activate → 海图突出 active leg → 显示 BRG/DTW/XTE/ETA → Pause/Resume/Stop。

### Anchor

进入 Anchor mode → Set anchor → Preflight → 设置中心/半径/条件 → Arm → 海图显示锚点、安全圈和摆动轨迹 → Alarm/Recover → Pause/Resume/Adjust → Lift。

### Survey

进入 Survey mode → 检查 depth 与 position 来源 → 选择 tide correction → Start → 海图显示实测覆盖、插值和无数据区域 → Pause/Resume → Stop/Save。

### Cockpit

显示 HDG、COG、SOG、STW、Wind、Depth、Trip 和活动导航数据。可以打开轻量 chart preview，但点击后回到同一个 Chart surface。

### Library

统一管理 Places、Routes、Trips、Anchor sessions、Surveys。详情中的地图是静态 preview；需要交互时跳回 Chart 并选择对应对象。

### System

统一管理 Connections、Data Sources、Devices、Display、Safety、Storage 与 Diagnostics。

## 8. 运行时边界

页面不是任务。以下任务由独立 Runtime 管理：

- Anchor Watch
- Active Navigation
- Trip Recording
- Bathymetry Survey
- NMEA Input
- Boat Output
- Phone NMEA Service

切换到 Cockpit、Library 或 System 不停止任务。System strip 和全局安全 overlay 在所有页面之上持续呈现运行状态。

## 9. 首个可开发垂直切片

第一轮不接真实地图 SDK、GPS 或 NMEA。先验证产品结构：

1. 应用进入 WP8 Start Screen；
2. 默认布局严格只有 Chart、Anchor、Cockpit、Library、System 五块磁贴；
3. 左滑进入 All Apps，右滑返回，字母 jump list 可用；
4. Anchor 是 `Chart.Anchor` shortcut，不生成第五个核心 App；
5. Chart 支持 Browse/Navigate/Anchor/Survey 四个 typed modes；
6. 320×320、手机竖屏、船载横屏均无裁切；
7. 长按、Resize、Unpin 和网格重排具有真正 WP8 编辑语义；
8. 全局 Safety Overlay 可以覆盖任意页面（后续 Runtime 切片）；
9. 任何 Tile 都不启动 runtime resource。

## 10. TDD 的第一组合同

```text
app_launches_into_wp8_start_screen
default_start_contains_exactly_five_tiles
anchor_tile_targets_chart_anchor_mode
chart_modes_share_one_viewport_state
anchor_mode_does_not_create_an_anchor_app_task
navigation_mode_does_not_create_a_second_chart
survey_mode_does_not_create_a_second_chart
all_apps_contains_core_apps_and_shortcuts
swipe_left_opens_all_apps
long_press_enters_wp8_edit_mode
closing_chart_ui_does_not_stop_anchor_runtime
critical_alarm_overlays_every_workspace
square_screen_keeps_chart_and_primary_controls_reachable
desktop_stub_does_not_request_location_or_start_nmea
```

首轮已经完成 Registry、导航、桌面编辑和真实 Activity 故事测试的 Red/Green；Runtime 与 Safety Overlay 合同留在后续切片。

## 11. 海图来源基线

Phase 1 已选择 Google Maps Android SDK 联网底图、无需 key 的 OpenSeaMap seamark 默认海图叠加，以及用户本地导入的 raster MBTiles。它们共享同一个 Chart surface 与 viewport，不按供应商拆页面。

本版本明确不接 LINZ，也不保留 LINZ key 或 URL override。OpenCPN-like 指海图库、扫描、启停和来源管理工作流；首版不声称兼容 OpenCPN 的全部格式。精确凭据、格式矩阵、导入安全和 TDD fixtures 见 [`requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md`](requirements/CHART_SOURCE_IMPORT_REQUIREMENTS.md)。

## 12. 暂停实现的内容

在产品结构通过之前，不做：

- 让用户日常选择 HOME 默认项（当前只提供可单独安装的 `home` 构建变体）；
- Room schema；
- 真实 NMEA/GNSS；
- Anchor/Trip/Survey 算法迁移；
- OpenSeaMap provider 与本地导入的真实读取／网络绑定；
- 自动航线规划。

这些工作不会消失，但不应抢在核心海图交互验证之前。

## English translation

Yokuli OS keeps the WP8 Shell but replaces the flat “one tile per feature” prototype with a chart-first marine information architecture. Start contains only five default entry points: Chart, Anchor, Cockpit, Library, and System. Browse, Navigate, Anchor, and Survey are typed modes of one shared chart viewport, while shortcuts in All Apps deep-link into those owners. Phase 1 selects Google Maps as the connected base, keyless OpenSeaMap seamarks as the default nautical overlay, and user-imported raster MBTiles; LINZ is explicitly excluded.

The first milestone validates Shell structure and UI behavior with explicit fixture data. An isolated Google Maps base-map adapter is now wired, with environment-to-manifest key injection and an explicit fixture fallback; real-key device acceptance remains separate. It does not claim working OpenSeaMap, local-chart import, GNSS, NMEA, anchoring, trip recording, sonar, firmware, or automatic routing. Those runtimes enter one at a time only after their state/action and safety contracts are tested. Closing or navigating away from UI must not implicitly stop an independently owned safety runtime.

The Chinese sections above are the normative detailed product rationale, market-feature synthesis, module map, screen grammar, typed destinations, safety invariants, TDD story list, and deferred-scope record.
