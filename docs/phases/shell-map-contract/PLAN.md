# Shell–App 磁贴合同与离线地图实施计划

状态：`IN_PROGRESS`  
基线：`a144de02657b5dd778d329f26ee5f2443370af01`  
工作分支：`codex/shell-map-contract`

## 1. 本轮目标 / Objective

本轮先修正 Launcher 的编辑模型，再让地图作为一个独立 App 通过同一安装合同进入 Shell。完成标准不是“看起来像磁贴”，而是：

1. App 声明自己的入口、可用磁贴尺寸以及每个尺寸的独立内容；
2. Shell 统一负责排列、编辑手势、拖放、持久化、撤销、主题容器和无障碍；
3. App 不依赖 Shell 的桌面实现，Shell 不通过 `chart`、`settings` 等字符串分支认识 App；
4. 地图离线时仍可浏览、选点、管理本地资料、测量和规划，不伪造 GPS、NMEA、在线授权或导航状态。

English: first correct the Launcher edit and installation contracts, then install an offline-first Map app through that same boundary. The app owns supported sizes and size-specific tile content; the Shell owns layout, interaction, persistence, theme chrome, and accessibility.

## 2. 证据边界 / Evidence boundary

Stage 2.5 的 owner-supplied WP8.1 模拟器视频已批准几何、页面切换、应用打开/返回和 Live Tile 可见变化，但明确将 edit、long-press drag、resize、pin/unpin 标为 `NOT_OBSERVED`。因此：

- 24 px 外边距、12 px 缝隙和 99/210/432 px 磁贴几何继续使用已批准测量；
- 编辑按钮尺寸、长按阈值、拖动迟滞和回弹只标为 `DERIVED_UNVERIFIED`，等待真机人工审查；
- 不把 Android 上的产品修正反写成“WP8 实测值”。

官方历史资料只用于锁定职责边界：Windows Phone 8 有 small/medium/wide 三种尺寸以及 Standard/Flip/Cycle/Iconic 等 App 提供的 Tile 数据；不同尺寸可以使用不同图像或文本；用户控制固定、取消固定、缩放和排列。参考：

- <https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/windows-8-building-apps-for-windows-8-and-windows-phone-8>
- <https://learn.microsoft.com/en-us/previous-versions/windows/apps/jj208800(v=vs.105)>
- <https://news.microsoft.com/speeches/steve-ballmer-and-stephen-elop-microsoft-and-nokia-press-conference/>

## 3. Shell–App 合同 / Contract

### 3.1 App 拥有

- `LauncherCatalogContribution`：App/Entry/LaunchToken、默认尺寸、有序的支持尺寸；
- `LauncherEntryVisualContribution`：应用列表标题与图标、状态语义、**每个支持尺寸一份明确 renderer**；
- `InternalAppHost`：把属于自己的 LaunchToken 映射到 App 内 destination；
- App 自己的数据状态。Tile renderer 只能读取适合 glance 的快照，不能因被绘制而启动 GPS、NMEA、网络或下载。

### 3.2 Shell 拥有

- 单一 `InstalledAppBinding` 注册表以及重复/缺失校验；
- Start Document、packing、drag preview、drop commit、undo 和持久化；
- 长按/拖动/点击仲裁、编辑 chrome、统一主题色、最小触控区和无障碍动作；
- Catalog/visual/host/token registry 的派生。Shell 不提供按尺寸放大同一内容的 fallback。

### 3.3 强制不变量

- `defaultSize in supportedSizes`，`supportedSizes` 非空且无重复；
- renderer keys **严格等于** `supportedSizes`；缺一项、多一项或未知 Entry 都在 composition root fail fast；
- 一个 Entry 的 LaunchToken 只注册一次，一个 App 只拥有一个 Host；
- 新增第三个 App 只增加一个 binding，不修改 Desktop 的 Entry-ID 分支；
- Shell Engine 保持无 Android/Compose/App 依赖。

## 4. 磁贴编辑行为 / Edit behavior

### E1 — 一次点击缩放

- 长按磁贴进入编辑；右下 resize affordance 一次点击即切换到 App 声明序列中的下一尺寸、提交、持久化并保持编辑选中；
- 不出现 resize preview、取消叉或确认对号；无第二次确认；
- 只有一个支持尺寸时不显示 resize affordance；
- 1×1 的可见圆盘必须清楚，触控区至少 48 dp，并与 unpin 目标不重叠。

### E2 — 同一次长按拖动

- 长按后不抬手即可拖动；仅长按后松手则进入编辑但不改变位置；
- 被拖磁贴跟手，邻居在落手前显示拟议重排，插入标记可见；
- 抬手原子提交并持久化；pointer cancel、窗口变化、Back 或 Catalog 变化恢复最后已提交文档；
- 普通纵向滚动不能被短触或轻微移动误判为拖动；编辑态禁用页面横滑。

### E3 — 可验证性

- Reducer 单测覆盖 App 限定尺寸、一步提交、无额外确认、拖放/取消/持久化/撤销；
- Compose/Activity 测试覆盖 1×1 控件视觉与 hit target、一次点击尺寸变化、单次长按连续拖动、邻居预排和重启恢复；
- 真实 Activity 使用 merged semantics；只有必须检查独立子节点时才使用 unmerged tree。

## 5. 地图产品分层 / Map product slices

Shell–App Gate 全绿后才开始以下切片：

### M1 — 离线浏览基础

- 地图相机、图层、选点、工具和最后会话状态；
- 无定位也能浏览；位置状态独立表达 `unavailable / stale / fresh`，绝不把旧位置画成实时；
- 空白处长按创建临时选择，方屏提供十字准星精确落点；
- 地点与测量是独立领域对象，可保存、编辑和恢复。

### M2 — 海图与离线包

- 先打通一个许可清楚的 raster MBTiles 本地导入链路；
- 包含来源、许可、完整性、覆盖范围、版本和安装状态；安装/更新原子化；
- 图层可见性与已安装包分开；删除包不删除地点、航线或测量；
- 不从标准 OSM tile server 批量抓取，不宣称 ENC/S-63 能力。

### M3 — 手工航线规划

- 航点增删改、插入/移动、undo/redo、草稿、复制、反向、计划航速与估算时长；
- 明确标为“手工航线”，不冒充自动寻路或实时导航；
- GPX 导入/导出经过独立 parser/validator；数据恢复不抢相机、不丢草稿。

### M4 — Map Tile

- Map Entry 通过同一 binding 安装；每个支持尺寸拥有独立 renderer；
- Tile 只显示已持久化的本地事实（如最后区域、离线包状态、草稿摘要），点击恢复上次会话；
- Live/装饰动画在 Shell 编辑态冻结；Tile 后台不得启动位置、船网或下载。

## 6. 提交与 Gate / Delivery

1. `test(contract): lock app-owned tile presentation`（Red，再 Green）
2. `fix(tiles): make resize immediate and controls usable`
3. `fix(tiles): make long-press drag continuous`
4. `refactor(shell): install apps through one validated binding`
5. `feat(map): add offline map workbench foundation`
6. `feat(map): add chart packages and manual route planning`

每个切片先提交并 push，再运行对应昂贵本地 Gate，让 hosted CI 与本地测试并行。任一 Gate 失败即停在该切片，不进入后续地图切片。报告会记录 Red 证据、Green 证据、自审发现、修正和仍需人工判断的 UI/真机项。

