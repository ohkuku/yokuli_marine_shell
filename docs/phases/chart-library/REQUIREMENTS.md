# Yokuli OS · 独立海图库 / Chart Library
## 文件夹引用、零复制读取、海图目录与 Chart 集成实施需求

版本：1.0 · 2026-09-06  
目标：`ohkuku/yokuli_marine_shell`  
本次审阅基线：`codex/shell-map-contract@4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5`  
旧仓参考：`ohkuku/yokuli_nmea_anchor_alarm`，`codex/develop@a845d3d734d3b573a2b53952e66e5f800e944205`  
交付性质：需求与实施合同；不是已实现声明、测试报告或合并授权。

## 0. 决定、优先级与开工边界

**新增独立的 Shell 内应用「海图库 / Chart Library」。用户管理文件夹中的海图原件；Yokuli 维护索引与读取能力。Chart 负责使用这些资源，不再拥有海图导入、资源删除或文件夹管理流程。**

“独立应用”指独立 All Apps 入口、稳定 App ID、宿主、路由、UiState/UiAction、磁贴、搜索贡献和测试边界；仍在同一个 Android APK、同一个 Shell 中，不新建 Android launcher 或第二个 APK。

本合同覆盖旧文档中“海图包只能作为 Chart 内部页面”“海图必须先复制导入”“只有 FULL_TILE_DECODED 才能登记资源”的冲突规定；不覆盖 NMEA_SOURCES 的数据源规则，不重写历史测试报告。更新当前有效产品合同、README 与产品面检查，使新增应用合法，而不是删除约束测试来获得绿色。[R01][R02][R07]

保留现有：WP8 Classic 视觉、竖屏与方屏、串行 Launcher Engine、Back 最多回到应用内桌面、非 Android HOME、用户已有 Start 布局、现有地图地点/路线/轨迹与 NMEA 工作。不得以这次需求为由开启横屏、换地图引擎、重建 Shell 或丢弃正在进行的 NMEA 改动。

开工先记录当前 branch/HEAD/dirty state，核对本文件基线以来的相关 diff。以开发者实际工作树为准，**不自动 checkout、rebase、reset、clean、push、merge 或 release**。旧仓只读。普通路径/命名差异自行映射；破坏性工作树冲突才作为阻塞报告。

### 0.1 必须完成与不在本轮

| 必须完成 | 本轮不做 |
|---|---|
| 独立海图库应用、文件夹和单文件来源 | 新 APK、系统文件管理器、Android 桌面 |
| 本地可随机读取 MBTiles 的原地只读使用 | 云盘同步、自动下载海图、商业授权系统 |
| 多来源、多文件、递归扫描、增量更新 | 修改/移动/重命名/删除外部海图原件 |
| 索引、权限恢复、变更/缺失/损坏诊断 | S57/S63、GeoTIFF、PMTiles、PBF 渲染 |
| 多海图显示候选、优先级、底图/叠加分工 | 从普通栅格图读取数值水深、航行安全判定 |
| 与现有 MapLibre、真实覆盖检查集成 | 迁移旧 Google/LINZ 在线地图体系 |
| 兼容已导入包；显式选择的受管副本 | 后台偷偷复制全部资源来伪装零复制 |

本轮格式是 **raster MBTiles：PNG、JPEG、WebP**，256/512 像素方形瓦片；每项必须有真实解码与 MapLibre 测试。文件内不同图片编码可逐瓦片识别；不能仅因 metadata 的格式声明就信任 blob。不同瓦片尺寸混在同一资源时首版明确拒绝并解释，不硬编码一律 256。工程缩放范围先沿用当前实现的 0–24，不能称为 MBTiles 标准的全部范围。其它格式可以显示“发现但不支持”，不得加入可显示海图集合。

## 1. 已核实的代码事实与迁移原则

| 现有实现 | 本次处理 |
|---|---|
| `ChartPackageCoordinator` 在 Chart feature 内拥有 inspect/install/delete/refresh，并在安装后自动激活 | 资源维护迁往海图库 runtime；Chart 只接受目录快照及显示命令。[R03] |
| `AndroidMbTilesRepository` 把文件复制进 `filesDir/map_packages`，计算完整 SHA，使用 staging/journal/version/lease | 事务、版本恢复和受管存储继续保留；不能拿它的 import 路径充当文件夹扫描。[R04] |
| 当前检查逐个解码所有瓦片；XYZ 会在副本上归一化为 TMS | 发现与深度验证分层；外部原件永不改写，查询时转换行号。[R04] |
| `ChartPackageVersionId` 强制 64 位十六进制，验证枚举只有 `FULL_TILE_DECODED` | 新目录 revision 与可选内容 hash 分开；轻量检查不能假冒全量验证。[R07] |
| `OfflineMarineChartSurface` 是 MapLibre 生产渲染器，当前一个 active package、`mbtiles://` 本地路径 | 保留 renderer/lifecycle/overlay；新增资源读取桥和多资源显示计划。[R05] |
| `AndroidChartCoverageIndex` 只接受 TMS、本地路径，并查询实际 tile keys | 改用共享读取合同；保留“bounds 不是瓦片覆盖证据”。[R06] |
| 现有 `ChartSourceCatalog` 是 USER_IMPORT/NOAA 的发布者与获取渠道目录 | 它不是用户文件夹目录；新 ID 不复用其中枚举，不虚构 NOAA 下载已可用。[R08] |
| `ProductionShellGraph` 由 `InstalledAppBinding` 单点派生入口、视觉和宿主 | 海图库照此注册；保留开工时所有正式应用，不写死只有三项。[R09] |
| 旧 `OfflineMbTiles.kt` 是 `current.mbtiles` 单槽复制；具备只读查询、TMS/XYZ 行变换、WebP 识别 | 只抽取算法/测试场景；不带回单槽、4GB 导入上限、Google TileProvider 或硬编码 Tile(256,256)。[R12] |

本次核对是相关源码定点审阅，没有执行 Android 构建、设备测试或全仓逐行审计。具体路径、证据及建议接入点见 `IMPLEMENTATION_MAP.md`。实现时只需复核相关变更，不重复全仓调查。

## 2. 产品职责：维护资源与显示资源分开

| 所有者 | 拥有的状态/操作 | 不拥有 |
|---|---|---|
| 海图库应用 | 来源列表、资源列表、扫描、权限修复、资源启停/分类/排序、受管副本、验证与诊断 | 地图相机、活动路线、船位源 |
| Chart 应用 | 地图显示选择、当前显示图层、透明度、地图相机、进入海图库的深链 | SAF 授权、目录遍历、资源 CRUD、复制任务 |
| ChartLibraryRuntime | 持久目录、扫描任务、读取会话、版本代际、健康与可用性 | 页面视觉、任意全局事件总线 |
| MapLibre adapter | 按显示计划加载 raster source/layer，报告真实渲染状态 | 扫描整个资源库、修改原件、替用户挑数据源 |
| Shell 组合根 | 安装、类型化导航、系统 picker 宿主与生命周期接线 | 海图领域逻辑 |

必须区分三件事：**资源登记/可用**、**允许参与显示**、**当前地图正在显示**。关闭某图层不删除文件；禁用某来源不撤销目录记录；移除来源不等于擦除原件。

海图文件夹来源、海图出版来源、NMEA 数据来源是三个不同概念。UI 不把海图库放入 NMEA 的「数据来源」应用，也不共享那个来源选择器。

## 3. 用户主流程与默认值

主流程：

```text
All Apps → 海图库 → 添加文件夹 → 系统目录授权
→ 列出发现的 MBTiles 与检查状态 → 选择用于显示的来源/海图
→ 在 Chart 打开 → 根据视野使用这些资源

以后：文件管理器加入/替换海图 → 海图库刷新 → 目录更新
```

首次空态只有“添加文件夹”“添加单个文件”“说明”。不显示演示图、不自动下载、不开启手机定位或 NMEA。

添加文件夹设置：显示名称（默认系统返回的目录名）、是否包含子文件夹（默认开）、默认资源角色（未分类/海图底图/影像底图/叠加图）。未分类资源可以查看详情，但需明确角色后才加入自动组合。文件夹保存即登记来源并启动一次扫描，**不自动把扫描到的每个文件切成当前底图**。

新发现资源默认允许使用；但只有用户已在 Chart 选择的来源集合，才会把其中新资源纳入候选。即使该集合新增文件，也不重置相机、不切换影像/海图主题，不覆盖用户手动固定的单张图。

单文件使用系统文件选择器，默认原地引用，与文件夹项遵循同一读取、权限、版本和错误语义。不能因为是单文件就隐式复制。

首次启用外部引用显示一次说明：Yokuli 不修改原文件；原文件移动/删除、存储离线或授权失效会影响可用性；完整海图更新宜在停止使用该资源后进行。不把说明包装成航海适用性认证。

## 4. 海图库页面与操作

### 4.1 首页：来源优先

```text
海图库                              ＋
3 个来源 · 47 张资源 · 2 项需处理

MarineCharts
文件夹 · 包含子文件夹
42 张 · 40 可用 · 2 待处理
上次完整扫描：刚刚

单独添加的海图
3 张 · 原地引用

Yokuli 本地副本
2 张 · 应用内存储
```

这是布局示意，不是生产默认数据。总大小分别展示“原文件引用大小”“Yokuli 实际占用”；未知大小明确标未知，重复来源不重复累计同一已知物理文档。支持来源筛选、名称/相对路径搜索、待处理筛选和批量启停。

来源详情提供：显示名称、来源类型、系统可解释路径/提供方、包含子目录、启用、重新扫描、扫描进度与取消、重新授权、移除来源、该来源的资源列表。仅当 provider 有可用系统打开方式时提供“在文件管理器中打开”，不为不支持的 provider 放死按钮。

### 4.2 资源详情

展示真实名称与文件名、来源及相对目录、类型/编码/瓦片尺寸/行号方案、大小、zoom、声明范围、来源/授权/署名/版本信息、读取方式、验证层级、最近检查时间、异常和修复动作。缺少的字段保持 Unknown；文件修改时间不是海图出版日期，最大 zoom 不代表更准确。

操作：启用/禁用、角色、同角色优先级、重新检查、完整验证、在 Chart 查看、解除单文件登记、明确选择“保存应用内副本”。用户自定义标题/备注/分类只存目录 DB，不回写 MBTiles metadata。嵌入事实与用户填写事实分别保留 provenance。

预览使用有界静态缩略图或范围描述，不给每个列表项创建 MapView。点击“在 Chart 查看”通过 Shell 命令打开该资源/范围；没有可靠 bounds 时不捏造定位到某处。返回保持海图库筛选/滚动状态。

### 4.3 存储与诊断

分开显示：原件引用、受管副本、目录/缩略图/短期缓存。清缓存不得删除受管副本或外部原件。诊断可展示 provider、可否随机读取、上次 scan generation、读取错误、验证进度；导出诊断默认移除完整 URI、精确位置、网络凭据和进程 token。

## 5. 原地读取的技术合同

### 5.1 不能把目录授权误当成数据库读取能力

SAF 支持用户选择目录树和持久授权，但返回的是文档 URI。`openFileDescriptor(...,"r")` 可能给出流式 pipe，不保证随机读取。SQLite 的普通 URI 机制使用 `file:`；现有 MapLibre 的 `mbtiles://` 文件读取也不能因此自动读取 `content://`。[P01][P02][P03]

产品目标是**对通过能力验证的本地目录实现真正原地读取**，不是承诺所有系统文件提供方均零复制。

| 能力验证结果 | 行为 |
|---|---|
| 完整、可随机读取的本地文档；读取器支持 | 只读原件，不生成整库副本 |
| 有 fd 但不支持 seek / 是虚拟文档或流 | 列出资源，显示“此位置不能直接读取”；提供改选本地目录/显式复制单张 |
| provider 可能需要联网 | 显示离线能力未验证；不标“已离线就绪”，不默默批量下载 |
| 权限丢失或设备未挂载 | 保留目录，显示重新授权/来源离线，不当成所有文件已删除 |
| 静态数据库不完整或依赖未提供的 journal/WAL | 阻止发布为可读；要求完整导出，不能偷偷修复原件 |

seekable 不等于 offline。离线就绪的验证范围必须包含真实无外网读测试/已知本地 provider 证据，不依据文件扩展名判断。provider 自身是否缓存/下载需如实说明，Yokuli 不宣称能控制第三方内部存储行为。

### 5.2 选定的实现路线

默认路线：**SAF → 只读随机访问文件句柄 → SQLite 只读 reader → raster tile bytes → MapLibre raster source**。

原地 SQLite reader 使用经过测试的 FD 适配层；本轮预定实现是 bundled SQLite + 小范围 JNI/VFS 适配，不另写 SQLite 文件格式解析器。VFS 是 SQLite 的文件 I/O 接口机制；这不等于 Android 系统 SQLite 已提供现成 SAF 支持。[P04]

CL01 先建立可复用的真实读取纵向切片。如果项目实际已有具备同等合同的 FD reader，可复用并记录；不进行广泛库选型。禁止用 URI 猜真实路径、要求 root/全盘管理权限，或把未经跨设备验证的 `/proc/self/fd/...` 字符串技巧当成通用方案。

最低 reader 合同：64 位文件大小/offset、确定 fd 所有权、正确处理短读/关闭/取消/损坏/错误传播；读取支持 table/view 形式的 tiles；没有 WRITE/CREATE/DELETE/ATTACH 任意外部路径能力；只执行应用定义的参数化查询。采用 SQLite 限额、查询中断与有界 page cache。读取原件期间不创建源目录 sidecar，不执行 VACUUM、CREATE INDEX、坐标归一化或 journal 修复。

优先支持完整 regular-file descriptor。AssetFileDescriptor 子区间必须严格按 offset/length 封装，并单测越界；没有实现则明确拒绝这种能力，不把底层整个文件误认为 MBTiles。只申请 `r`，不为了获得 seekable 文件而申请 `rw`。[P02]

新 JNI 库须锁定来源/版本/hash、许可证与依赖记录，覆盖当前支持 ABI，至少真机 arm64 与测试 x86_64，并核验 Android 16KB page-size 构建/加载兼容；不顺手升级整个 Android/MapLibre 技术栈。[P08]

### 5.3 一致性与读取期间外部修改

本轮支持**完整静态 MBTiles 归档**，不支持另一个应用一边写 SQLite/WAL 一边提供事务一致读取。不能因为自己只读，就将外部可修改文件声明 `immutable=1`；该选项会跳过锁与变更检测。[P03]

VFS 的锁/sidecar/变更语义必须审查并测试；不可将 lock 回调全部空实现后宣称安全。不能满足静态完整数据库条件或必要锁合同的 provider，应明确归入不能直读，而不是读取可能不一致的结果。

每个读取会话绑定 assetId + contentRevision + source generation。打开前后检查可获得的身份/大小/修改信息及数据库状态；明确发现替换/截断/变更时立即失效该代会话、瓦片/覆盖缓存，等待重新检查；不得把旧内容配上新版本元数据。正在验证的文件变更后，验证结果不能算新版本通过。

provider 不保证通知、版本号或可靠修改时间时，同大小同时间替换无法保证即时检测；UI 提供“强制重新检查/重新打开”，文档明确该限制。不得以抽样 fingerprint 冒充内容全等证明。

## 6. MapLibre 集成：必须把瓦片真正送进去

当前生产适配器把 app-private `mbtiles://` 地址直接交给 MapLibre。[R05] 新架构不能只写 SAF scanner，然后仍让这个路径读取器消费 URI。

默认桥接方案为**仅本进程使用的 loopback raster tile gateway**：SQLite reader 提供 bytes，MapLibre 通过 TileSet/瓦片 URL 获取。MapLibre 的 RasterSource 提供 TileSet 构造方式。[P05][P06]

```text
MapLibre RasterSource / TileSet
           ↓ GET（仅本机）
http://127.0.0.1:<临时端口>/<进程随机token>/<assetId>/<revision>/{z}/{x}/{y}
           ↓
ChartTileReadPort → FD/path reader → MBTiles 原件
```

该 gateway 是 renderer adapter 的内部实现，不是海图共享服务器，更不是 NMEA 转发功能。只绑定 loopback，使用随机端口与至少 128-bit 随机 token；没有任意文件路径、SQL、目录枚举、远程 URL 代理接口；只有已授权 asset/revision 的只读请求。未知 token/ID 拒绝，参数长度/整数范围/并发/队列/单瓦片大小均限制。token 不写日志，不接受通配 LAN 绑定或跨域开放。

Android 网络配置只允许必要 loopback cleartext，不全局打开任意 HTTP；核验当前 13.4.1 的请求后端和网络安全设置。缺片、版本失效和读错误区分，不能一概返回成功的透明瓦片掩盖问题。设置合适的禁止持久缓存响应，并核验 MapLibre volatile/磁盘缓存实际行为；不能每次扫描都清全应用缓存。

CL02 必须验证实际版本、release 构建、无外网与相机移动请求。若已存在经过证据验证、可注入 raster bytes 的原生桥，可等效替代 loopback，并保留所有访问/缓存/取消合同；不能将“后续验证”作为本轮结束状态。

gateway 生命周期由单个 runtime owner 管理，开始显示前完成启动；没有活跃地图/检查消费者时释放不必要资源。离开海图库页面不停止 Chart 使用中的 reader。进程重启创建新 token/session，不从持久化恢复旧端口或旧 fd。

## 7. 数据模型与持久化

下面是责任与字段合同，具体类名可按现有架构调整；不存在的符号不当作仓库事实。

| 实体 | 必须表达的内容 |
|---|---|
| `ChartLibrarySource` | UUID sourceId；TREE/SINGLE_DOCUMENT/MANAGED；opaque locator；名称；启用；递归；默认角色；grant 状态；scan generation/结果 |
| `ChartAsset` | stable assetId；文档身份；来源 membership；展示路径；格式；大小；可选 bounds/zoom/count；metadata 与 provenance；角色/优先级/启用；validation/access 状态；revision |
| `ContentRevision` | 本目录使用的版本标识与检查证据；与源观察到的 identity/size/mtime/revision hint 关联；可选 SHA256 |
| `ChartReadSession` | assetId/revision/source generation；read-only handle；引用计数；能力；close/取消/失效 |
| `CatalogSnapshot` | catalogRevision；可查询的来源/资源视图；聚合问题数；发布事务结果 |
| `ChartDisplayPlan` | 地图已选择的来源/单图、角色、可见资源次序、透明度、revision；不是另一个库 |

**不得拿现有 `ChartSourceId.USER_IMPORT/NOAA_NCDS` 当文件夹 ID。不得要求扫描 GB 文件才能生成稳定 ID。** 外部 asset 用持久 UUID + 文档身份映射；managed 旧 ID 继续可解析。revision 不必是 SHA；只有真的读完整内容计算后才填写 contentHash。[R07][R08]

身份先用 provider authority/documentId 等可靠标识，目录路径只作展示。不同授权 URI 指向同一可确认文档时作为 membership 别名去重；移除一个来源，不撤销另一个仍依赖的授权。两个名字/大小一样的文件不得直接合并。无法稳定确认移动/更换的身份时要求重新关联，并保留用户可理解的差异。

元数据目录采用 Room/SQLite 事务索引，海图 blob 不进入目录 DB。默认在新 Android adapter 内建立专用版本化目录库；现有 Map 的地点/路线/轨迹库保持。MapState 只持久化展示选择及必要迁移映射，不再是资源登记的权威库。

状态分层：

```text
偏好：enabled / disabled；role；priority
访问：UNCHECKED / READABLE / PERMISSION_LOST / SOURCE_OFFLINE /
      MISSING / CHANGED / DIRECT_READ_UNSUPPORTED
验证：DISCOVERED / BASIC_READABLE / FULL_VERIFIED /
      INVALID / UNSUPPORTED_FORMAT / CANCELLED_OR_INTERRUPTED
```

组合状态投影成人类文案；不建一个 `available:Boolean` 丢失含义。是否可显示由当前能力、基础验证、启用、显示选择共同决定。`FULL_VERIFIED` 只代表某个 revision 的测试范围，不能代表海图更新及时或航线安全。

## 8. 文件夹扫描与更新策略

### 8.1 扫描触发

保存来源后扫描；用户可手动刷新；应用恢复/打开海图库时按有界时间策略检查需要刷新的来源。系统变更通知只作提示，不依赖所有 SAF provider 有可靠 watcher。[P01]

不能每次 pan/zoom、重组 Compose、进入磁贴或收到 NMEA 就扫描文件夹。后台扫描不由页面订阅数决定；首次页面立即展示持久目录及“待检查”，不等待整个目录完成才画 UI。

### 8.2 流水线

1. **列举**：后台按目录 query，只请求需要的列。收集候选、可用大小/修改信息与稳定文档 ID；按 MIME 与扩展名结合识别，不能仅依赖 provider 的通用 MIME。
2. **差异比较**：未变化项复用已有元数据；新项/变化项进入有界验证队列。对无可靠变更字段的 provider 标记证据不足并做必要重开，而非承诺增量识别完美。
3. **基础检查**：按第 9 节随机读取，不复制、不全量解码。可读项逐批显示。
4. **完成事务**：只有相关目录范围成功完整枚举，才把本次未见的旧项判为 MISSING。部分失败/取消/权限失效不把未扫描部分全部判 missing。

每次扫描有 operationId/generation，取消或来源移除后旧回调不能发布。`scanComplete`、`scanPartial`、`scanFailed` 分开，保持 lastSuccessfulScan。一个文件坏了不使整库失败。

递归只在授权树内。设置最大递归深度、枚举数、待处理任务数和时间预算；到限额展示“扫描未完成/达到限制”，提供收窄目录或继续，不静默截断然后报全部完成。路径循环/别名需要已访问集合；分页只在 provider 实际支持时使用，不能假定 LIMIT 会生效。

未完成文件（常见 .tmp/.part/零字节/大小仍变化）不作为可用海图。具有 .mbtiles 名称但失败的项可见并说明“文件未就绪/数据库无效”，不能一律忽略使用户找不到它。

### 8.3 替换、移动与缺失

改名称不改变可靠 documentId 时保留偏好；身份变化时不凭文件名无条件继承。资源内容变化先发布 CHANGED，重新验证后产生新 revision，切换 renderer/coverage 缓存；原图不可读时明确缺口，不偷偷拿不相关来源补成“正常”。

重新授权同一来源后校验目录身份、重建可读文档 URI、再扫描；不要创建一整套重复资产。用户选了不同文件夹来替换时先显示影响并确认映射。

## 9. 验证分层与 MBTiles 语义

### 9.1 自动基础检查

读取 SQLite header、metadata/tiles 的 table/view 与必要字段，bounded metadata，实际样本瓦片的坐标、payload 长度、编码/解码尺寸。执行真实索引查询确认可读；SQL 时间、VM steps、读取字节和返回数据要受预算限制。

metadata 足够时不默认执行全库 COUNT(*)/GROUP BY/全量 hash/每瓦片 decode。缺少 zoom/bounds 时可使用有界、可中断索引查询；不能快速得到就标未知/待深入检查。未知 bounds 不得显示成整个世界。可供手动查看的资源不一定已具备自动空间选择资格。

抽样不是全库健康保证。每个实际请求仍检查 blob 长度、坐标和解码安全。基础可读的资源可以显示，但页面明确未完整验证。

### 9.2 用户主动完整验证

保留当前对坐标、重复项、图像内容等的完整验证思路；异步、可取消、可见进度，可选完整 SHA/精确统计。原件只读，不生成新完整包；验证运行中不占满 renderer 的 IO/内存预算。

只有全部所需检查结束且源 revision 未变，才能记录 FULL_VERIFIED。进程终止/取消/错误均不是通过；重启可重新开始或从可证明有效的 checkpoint 恢复，不伪造续跑。

### 9.3 坐标与编码规则

MBTiles 标准行号为 TMS。为兼容现有用户资源，本轮也接受明确 `scheme=xyz` 的约定；metadata 缺省按 TMS，未知 scheme 报错，不猜测。[P07]

```text
外部请求统一 XYZ(z,x,y)
TMS 存储：row = (1L << z) - 1 - y
XYZ 存储：row = y
```

转换在同一个查询 helper 中，renderer 与 coverage 使用它。禁止修改外部 tile_row。范围检查在移位/计算前进行，文件 offset 与大小始终 Long/64-bit。超过旧导入 4GB 上限不能据此拒绝支持的只读原件。[R12]

允许 tiles 为兼容 view；不要要求所有数据库只有某种物理表布局。索引不足导致查询过慢时记录性能问题/中断，不往原件写索引。metadata 与样本不一致时显示问题，不能虚构缺失值。恶意/异常 schema、超大 blob、解码炸弹、路径注入都需测试。

## 10. 多海图显示、优先级与真实覆盖

### 10.1 显示选择

Chart 的“图层/海图”界面只控制当前显示：选择单张图或一个/多个已登记来源集合、底图类型、叠加显隐和透明度；“管理海图库”深链到独立应用。资源分类/永久优先级修改在海图库进行，不在 Chart 复制维护页面。

兼容两种明确模式：

- **固定单张**：只用该资源；新增图不抢占，失效显示原因，用户主动改选。
- **来源集合**：在用户选择的同角色集合中按视野/zoom 查候选，按显式优先级及稳定 ID 决定图层顺序；不按到达顺序/文件名字/最大 zoom 随机切换。

默认新来源不加入当前显示选择。集合内新文件基础验证通过后可参与，但不改变相机、固定单图或主题。重叠区域排序可解释，图层列表告诉用户用的是谁；系统不把优先级称为航海可靠性排名。

### 10.2 性能与图层计划

用目录空间/zoom 索引筛选视野候选，再打开实际需要的资源。不能每个瓦片请求扫一遍文件夹或打开所有数据库。不同 tileSize 用匹配的 raster source 参数，不能把 512 的内容冒充 256。

每个计划携带 catalogRevision 与 asset revisions；异步旧请求不能污染新图层。保留现有 renderer generation、MapView 生命周期和 overlay 数据；更新海图层不能丢掉地点、路线、轨迹或位置绘制。[R05]

设置活跃来源/连接/图层上限；候选过多时按确定优先级选择并显示“部分资源未显示”及调整入口，不能静默漏图还声称所有覆盖。没有瓦片的区域露出明确无覆盖状态，不能自动联网找 Google 底图填洞。叠加图永远不被当作完整底图。

经度环绕与跨日期变更线范围必须正确处理；可拆成两个经度区间。错误/未知 bounds 不排除用户明确固定的资源，也不拿它证明整片区域已覆盖。

### 10.3 航线离线覆盖检查

保留现有按 required tile keys 查询的 `LocalChartTileIndex` 思路，底层更换为同一个只读 reader/session。[R06]

检查针对确定的显示集合、revision、目标 zoom。集合覆盖可对合格底图的真实 key 做并集；overlay-only 不填补底图缺口。metadata bounds 只是候选筛选，不是已经存在瓦片的证据。

分别表达：未检查、检查中、完整存在、部分缺失、来源无法读取、结果因资源更新而失效。检查中断不能变成通过。低 zoom 放大显示不是目标 zoom 的完整原生覆盖；应提示 overzoom/细节不足。结果只说明已检查的本地数据，不证明海图适用或航线安全。

## 11. 原件、副本与删除合同

| 操作 | 外部原件 | 目录记录 | 应用内文件 |
|---|---|---|---|
| 禁用来源/资源 | 不动 | 保留偏好 | 不动 |
| 移除文件夹来源 | 不动 | 解除来源 membership；其它引用保留 | 不自动删副本 |
| 移除单文件登记 | 不动 | 移除/保留可解释的引用历史 | 不动 |
| 清缩略图/临时缓存 | 不动 | 保留 | 仅清可重建缓存 |
| 删除受管副本 | 不动 | 事务更新受管记录 | 明确确认、无活动 lease 才删 |

**V1 不提供“删除外部原件”。** 用户用文件管理器维护原件即可；不为管理海图变成一个高风险文件管理器。

“保存应用内副本”是用户主动操作，不是不可见兼容缓存。事前说明需要复制整个文件、预计字节/未知大小、可用空间、用途和原件仍保留；可取消。未知大小采用实际写入计数与动态剩余空间检查，不无限写盘。

保留 staging → 校验 → 原子发布 → 目录提交 → 激活索引的恢复能力，失败保留旧可用版本。副本完成不自动切换当前地图；显式“使用此副本”才改变显示选择。副本与外部项关联，避免误以为这是两张独立出版的海图。

缓存/版本整理必须尊重所有活动 reader、renderer 和 coverage leases。用户删除当前受管图，先解释地图影响，切换到明确无图或用户选中的替代，再关闭会话并删除；不可删除仍由 native renderer 读取的文件。

## 12. 授权、离线与后台生命周期

使用系统 ACTION_OPEN_DOCUMENT_TREE/ACTION_OPEN_DOCUMENT，仅保存系统实际授予的 persistable read 权限。拒绝/取消 picker 不留下半个来源；provider 无持久 grant 时显示限制，不承诺重启可用。不能假设 `/Download` 根目录或存储根可授权；指导创建专用子文件夹。[P01]

grant 由统一组件管理并引用计数。取消固定磁贴/退出页面不 release grant。移除一个来源时先检查其它单文件/重叠树是否仍需要该授权；有序关闭会话再释放不再使用的 grant。

runtime 由 ShellApplication 所有，Feature 不直接启动 thread/socket/原件 reader。离开页面不取消用户已启动的扫描；用户主动“取消扫描”才取消。进程死亡后持久化任务恢复为“中断/待检查”，不复活 fd 和 PASS 状态。

普通目录扫描不新建常驻 FGS。需要较长完整验证/复制时提供可见进度、取消和明确恢复方式，按当前 Android 平台约束选择调度方式；不为了海图库借用 NMEA 的长期前台服务，不承诺强制停止后还能继续。[R10]

初始目录读取和原件 I/O 在后台线程，图片解码有界。UI、磁贴、状态条都是只读投影；不允许某个磁贴开启了几十个 reader。

## 13. 最小架构变更与端口

优先复用现有模块，不制造十个只有接口的空模块：

```text
core:map-domain
  新增中立 chartlibrary 模型/命令/查询/reader 合同与纯策略
       ↑                         ↑
adapter:chart-library-android   feature:chart-library
  SAF/索引DB/扫描/FD reader       独立管理UI/投影
  受管副本/任务/lease
       ↑（由组合根注入）
adapter:map-offline ← core 合同 → feature:chart
  MapLibre tile bridge/coverage   只读目录与显示选择

app-shell = runtime 所有权 + InstalledAppBinding + 系统picker + 跨app路由
```

新增两个主要 Gradle 模块：`:feature:chart-library`、`:adapter:chart-library-android`。FD native 代码优先放后者；纯合同先在现有 `:core:map-domain` 的中立包内，禁止依赖 Android/Feature。以后有真实复用需要再拆 core，不以应用独立性为由迁移整个地图 domain。

推荐端口责任：

| 端口 | 责任 |
|---|---|
| `ChartCatalogReadPort` | 分页/筛选查询与目录 revision、健康摘要 |
| `ChartLibraryCommandPort` | 添加/移除来源、启停、排序、关联、验证/复制命令 |
| `ChartLibraryJobPort` | 任务当前进度、取消、完成/失败/中断 |
| `ChartResourceAccessPort` | 取得绑定 revision 的只读会话、能力与 lease |
| `ChartTileReadPort` | 同会话读取 tile bytes、存在性、必要 metadata |
| `ChartDisplayResolver` | 从已选择来源和目录生成确定显示计划；纯策略 |

系统 picker 是类型化一次性 effect，返回到发起操作的 operationId；不是把 Activity 或 ContentResolver 传进 ViewModel/domain。跨应用操作通过 Shell 路由端口，不互相 import ChartWorkspace/LibraryViewModel。

目录发布基于持久事务；状态、版本、错误一起更新。Chart 的 MapState 允许保留只读投影，不保留第二份可独立写入的海图资产登记库。没有 global EventBus、service locator 或 Feature 间共享 MutableStateFlow。

## 14. 既有安装迁移

迁移范围是**当前 Yokuli Shell 已有用户数据**，不是越权读取旧 Anchor Watch 应用私有目录。

1. 注册特殊 MANAGED 来源接纳现有 `filesDir/map_packages`。读取现有 active index、manifest、version/history/journal；不复制、不重新 import、不强制全量 hash/验证。
2. 保留旧 ChartPackageId/logicalId/versionId 的映射；现有活动单图迁为“固定单张”模式，署名和验证来源保留。新索引需要的未知字段如实标记，不默认升为新验证等级。
3. 迁移是幂等事务，有 schema/migration version；崩溃重试不产生重复资产。先保证新目录可恢复，再切换读取权威；不同时双写两套真相。
4. 地点、路线、轨迹、地图相机和 Start Document 保持不变；旧的 Chart 海图包页面路由恢复时，转到库/相应资源或提供清晰过渡，不能崩溃或启动两个维护入口。
5. 外部库备份只包含索引/用户偏好/关联信息，不含原件、fd、loopback token。跨设备/清数据恢复仍需重新授权，不把备份 URI 当有效 grant。
6. 原有受管版本事务需保留兼容恢复/回退能力。删除或迁移代码前用真实旧格式 fixture 验证。目录损坏只隔离相关项/库，不能通过重置整个 Map/Shell DB 解决。

旧 Anchor repo 仅作为源码参考。需要以后跨应用导入时另做用户授权流程，本轮不暗读其私有 `current.mbtiles`。[R12]

## 15. Shell 入口、磁贴、状态与导航

建议稳定 App ID `chart_library`，主 LaunchToken `chart_library.browse`；资源与来源 token 只带 opaque ID，不带原始路径或任意 URI。遵循已有 token 长度/解析/未知对象处理合同。[R11]

正式入口由一个 `InstalledAppBinding` 派生 All Apps、搜索、visual、host。包含英文/中文名称。Chart 和 Settings 中相关维护动作改为深链，不能保留第二套编辑器。

| 磁贴 | 内容 |
|---|---|
| 1×1 | 海图库图标；必要时异常角标，不塞路径/容量 |
| 2×2 | 海图库；“42 张可用”或“2 项需处理”；次级为扫描状态 |
| 4×2 | 来源与资源摘要；最多两条重点问题/扫描进度 |

磁贴大小严格使用 1×1/2×2/4×2。正常资源存在不亮成“航海安全”。标题/错误/长路径在大字体与窄屏可读，复用方形布局、accent、现有字号、App Bar 与导航；不新造 Material 圆角卡片/底部 Tab 风格。

状态条只汇总有意义的扫描/当前所用资源不可用事件，不让后台未用的一张坏图变成全系统故障。点击问题进入海图库已筛选页。遵循现有 neutral status contribution，不让 desktop 依赖 SQLite/海图存储类型。

升级仅在 All Apps 可发现，不自动固定、不重新排列桌面；全新安装默认布局也保持当前设置，除非另有明确产品要求。Back 从详情到库，从跨 app 查看返回既有 caller 上下文；Start 返回桌面；Android Back 仍通过原 Engine，不能退出 Yokuli。[R01][R09]

## 16. 性能、资源预算和隐私

以下为本轮**初始工程预算**，不是标准或未经测试的性能承诺；集中配置并用实测调整，变更记录理由。

| 项目 | 初始约束 |
|---|---|
| 扫描并发 | 同来源 1 个 generation；全局最多 2 个 metadata/基础验证 worker |
| 打开数据库 | 全局硬上限，初始 12；渲染/coverage/验证共享预算，不能各自另开 12 |
| 页面目录 | DB 分页；单页初始 100；不把全部 metadata/blob 放进每次 StateFlow |
| 自动检查 | 有界样本、SQL VM/耗时/字节预算；超时显示待深入检查，而非重试全库 |
| 瓦片内存 | 独立有界 LRU，初始 32 MiB 编码缓存；解码/MapLibre/native 另计总预算 |
| 活跃 raster 资源 | 初始最多 8，超限可见；严禁假报所有资源都已显示 |
| 源数据写入 | 原地路径的外部原件写入、整库复制字节必须为 0 |
| 复制空间 | 64-bit 计数，持续核验余量；预留量集中配置并显示不足原因 |

遍历 1000 个文件不代表必须逐字节读完 1000 个 DB。验证报告记录扫描打开数、源读取字节、生成副本字节、内存峰值、page cache、fd、MapView、gateway request/拒绝/取消数与耗时，而非仅展示一个截图。

数据库/schema/metadata 均是不可信输入。限制长度、rows、blob、解码尺寸，拒绝路径逃逸/SQL 注入/任意脚本；归属/署名安全文本展示，不能加载嵌入的追踪 URL。[P05] 原始文件路径、坐标与文档权限默认不上传；不得记录或导出 provider token。

## 17. 验收矩阵

| ID | 场景 | 必须结果 |
|---|---|---|
| A01 | 全新安装无资源 | 海图库有真实独立入口；空态，无假海图 |
| A02 | 添加含子目录的本地树 | 真实枚举，显示相对目录，原件不复制 |
| A03 | 单文件添加 | 使用同一目录/reader，不走隐式 copy |
| A04 | 首次授权拒绝或取消 | 无半成品来源、无崩溃 |
| A05 | App/设备重启 | 持久授权与目录恢复；健康先复核，不恢复 fd |
| A06 | 权限撤销/USB离线 | 保留目录并区分状态；不把整库判删除 |
| A07 | 同树重新授权 | 恢复使用且不重复登记 |
| A08 | 双重树/单文件引用同文档 | 可确认身份时去重；移除一个引用不伤另一个 |
| A09 | 外部加入/移走文件后完整刷新 | 正确发现/标缺失，原件不动 |
| A10 | 扫描半途取消/子目录失败 | 未完成区域不批量 missing；旧回调被拒 |
| A11 | 同名不同内容/不同文件 | 不误合并；必要时用户确认关联 |
| A12 | provider 只提供 pipe | DIRECT_READ_UNSUPPORTED；没有偷偷复制 |
| A13 | 无外网读取本地图 | 原地 reader 到 MapLibre 真正出图 |
| A14 | 大于4GB资源、偏移超过4GB的有效瓦片 | Long/64-bit 读取正确；明确 synthetic/实文件证据 |
| A15 | TMS与XYZ相同图案 | 相同地理位置显示；原文件hash不变 |
| A16 | tiles为view | 支持正确查询；不要求特定物理表布局 |
| A17 | PNG/JPEG/WebP、256/512 | 正确显示；不同尺寸/不支持内容诚实拒绝 |
| A18 | metadata未知/矛盾/超长 | Unknown/异常，不虚构范围版本，不崩溃 |
| A19 | 损坏DB、超大blob、恶意schema | 有界失败，一项失败不拖垮其它来源 |
| A20 | 默认扫描大库 | 无全量copy/hash/decode；BASIC不标FULL |
| A21 | 完整验证取消/文件中途改变 | 不能发布FULL_VERIFIED |
| A22 | Chart关闭，海图库扫描 | 独立可用，不创建隐藏MapView |
| A23 | 未打开海图库，Chart读已登记图 | runtime可用，不要求管理页保持活跃 |
| A24 | 多区域文件、移动视野 | 只打开需要的候选，无按瓦片全库扫描 |
| A25 | 固定单图时新增另一张 | 不抢占、不改变相机 |
| A26 | 重叠图排序/底图与overlay | 确定顺序、可解释归属；overlay不等于底图覆盖 |
| A27 | bounds覆盖但内部有缺瓦片 | 真实coverage显示缺口，不报全覆盖 |
| A28 | 日期变更线、zoom边界、overzoom | 正确环绕；缺细节不冒充原生覆盖 |
| A29 | 渲染中撤权/删除/替换原件 | 失效旧会话与缓存、状态一致，无混版本假正常 |
| A30 | 外部同size/mtime且provider无通知 | 明示检测限制；强制重新检查可恢复 |
| A31 | 清缓存/移除来源/取消固定 | 外部原件及其它来源不受损 |
| A32 | 受管副本复制失败/取消/空间不足 | 旧图可用；staging清理/恢复，原件保留 |
| A33 | 删除正在使用的受管副本 | 有影响确认，先释放leases，不崩溃 |
| A34 | 现有map_packages升级+中途进程死亡 | ID/活动单图/历史恢复，幂等，不重复copy |
| A35 | 地点/路线/轨迹/桌面原有数据 | 迁移前后保持，NMEA任务不被覆盖 |
| A36 | token/路径注入、LAN访问gateway | 拒绝；只读loopback、无任意文件读取 |
| A37 | 反复打开/关闭Chart和Library | fd/任务/MapView有界；无多个gateway泄漏 |
| A38 | 中英/深浅/大字体/窄屏/方屏/三尺寸 | 关键文案可读，错误不只靠颜色 |
| A39 | 从Chart/Settings深链、Back/Start | 唯一维护入口；caller状态保持；Back不退出 |
| A40 | 当前产品安装门禁 | 包含海图库及其它已完成应用，无debug假入口 |
| A41 | native ABI/API26与36/16KB兼容 | 真实构建/加载结果；不可用设备记未执行 |
| A42 | 1000资源目录与30分钟操作/故障负载 | 记录真实预算数据，无无界增长，无自动整库副本 |

零复制专门验收：测试前后比较测试原件内容 hash；记录 App 存储新增文件清单和大小；随机访问读取字节计数；禁止新增与源大小相当的 .mbtiles/.db/临时归档。**此测试可以计算 fixture 的完整 hash，不能因此要求每次生产扫描也全量 hash。** 合成稀疏大文件只能证明对应 offset/计数能力；不能冒充真实多GB海图长期渲染证据。

## 18. 施工顺序与节省上下文

分成 `CL00–CL12`，详见 `TASK_INDEX.md` 和 `tasks/`。每次只执行指定工单；读相关需求节、已核实路径与直接依赖，不重新调研整个项目。实现地图与海图库共享代码时执行相关集成/编译，不为了省 token 跳过必要测试。

先完成 CL01/CL02：**真实 SAF 原件 → SQLite → MapLibre** 的能力闭环，再扩展完整 UI。没有这条链路，不能把一个漂亮目录页交付成“文件夹海图库已经完成”。

同一工单内先写本范围失败测试，再实现，再运行目标测试/直接消费者编译；边界变动运行相应合同。最终 CL12 才集中做完整现有质量门禁和跨应用耐久验证；必要阶段已有完整门禁不得取消，仅不在每个微小修改后重复执行。

工单结束仅报告：实际 HEAD、修改文件、执行命令/退出码、证据路径、偏离/阻塞、下一工单。成功日志写文件，不把几千行 Gradle stdout 粘回上下文；失败只贴 relevant section。测试未执行与失败分开。

项目当前真实构建目标是 standalone；不要恢复不存在的 Home flavor。最终按当时任务清单执行，例如：

```bash
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-ci-contract.sh
bash .github/scripts/test-release-product-surface.sh
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleStandaloneRelease
# 另跑本轮新增reader/provider/renderer/UI/migration instrumentation；名称以真实Gradle任务为准。
```

签名/设备/SDK/依赖缺失，记 `BLOCKED` 或 `NOT_RUN`；不造签名、不把编译成功等同全部通过。更新目前运行的产品面断言保留真实性要求，历史Stage报告保持原样。

## 19. 完成定义

用户能从 All Apps 独立打开海图库，授权一个本地海图文件夹，看到真实资源并修复可解释的异常；不复制受支持原件即可在 Chart 使用；文件增删替换后可安全刷新；多图选择和覆盖检查一致；原有导入图与地图/桌面数据不丢失；退出管理页不影响地图读取；没有重复维护 UI。

最终交付必须包括实际支持的 provider/文件形态、零复制证据、完整验收矩阵状态、真实命令与报告、未执行的设备项、迁移结果与限制。目录登记成功、基础检查通过、可显示、离线就绪、全量验证通过是不同事实，任何一项都不得代替另一项。

---

## 附录：来源与核实范围

仓库来源固定到本次审阅 commit，链接/精确路径见 `IMPLEMENTATION_MAP.md`。文中所有新类型、模块安排、预算、UI默认值和施工次序均为本轮设计决定，不宣称仓库已有实现。

- [R01] Shell README：当前阶段、过期边界、WP8/导航/构建合同。
- [R02] Shell `docs/implementation/NMEA_SOURCES_P0_BASELINE.md`：当前分支、用户选择的旧仓develop、真实安装接入点、SDK与standalone目标。
- [R03] `ChartPackageCoordinator.kt`：资源工作流所有权、安装后选图。
- [R04] `AndroidMbTilesRepository.kt`：全复制/全解码/XYZ归一化、受管事务与leases。
- [R05] `OfflineMarineChartSurface.kt`：MapLibre、本地单包地址、renderer生命周期。
- [R06] `AndroidChartCoverageIndex.kt`：真实tile key覆盖检查。
- [R07] `ChartPackageRepository.kt`：SHA版本约束与唯一全解码验证级。
- [R08] `ChartSourceCatalog.kt`：发布者/获取渠道，非文件夹索引。
- [R09] `ProductionShellGraph.kt`：InstalledAppBinding、现有Chart import接线、默认桌面。
- [R10] `ShellApplication.kt`：进程作用域、现有仓储与NMEA runtime。
- [R11] `ChartShellContribution.kt`：token与磁贴尺寸合同。
- [R12] 旧仓develop `OfflineMbTiles.kt`：单槽导入、TMS/XYZ和raster读取。
- [R13] 旧仓develop `map/nautical/NauticalSourceResolver.kt`：底图偏好与overlay分工参考。
- [R14] Shell `gradle/libs.versions.toml`：本次MapLibre锁定版本13.4.1。

[P01] Android SAF / documents and files：
https://developer.android.com/training/data-storage/shared/documents-files

[P02] ContentResolver，特别是 openFileDescriptor/openAssetFileDescriptor：
https://developer.android.com/reference/android/content/ContentResolver

[P03] SQLite URI（file:、immutable和锁语义）：
https://www.sqlite.org/uri.html

[P04] SQLite VFS 与文件I/O回调合同：
https://www.sqlite.org/vfs.html
https://www.sqlite.org/c3ref/io_methods.html

[P05] MapLibre Android TileSet：
https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-tile-set/index.html

[P06] MapLibre RasterSource与raster style：
https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.sources/-raster-source/index.html
https://maplibre.org/maplibre-style-spec/sources/

[P07] MBTiles 1.3 格式合同：
https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md

[P08] Android native库16KB page-size兼容：
https://developer.android.com/guide/practices/page-sizes

公开文档核实日期：2026-09-06；实现须核对项目锁定依赖的实际API，不因最新文档列出某接口就盲用。

### 固定版本仓库链接

- [R01] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/README.md
- [R02] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/docs/implementation/NMEA_SOURCES_P0_BASELINE.md
- [R03] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt
- [R04] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidMbTilesRepository.kt
- [R05] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt
- [R06] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/AndroidChartCoverageIndex.kt
- [R07] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartPackageRepository.kt
- [R08] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/ChartSourceCatalog.kt
- [R09] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt
- [R10] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/app-shell/src/main/java/com/yokuli/marine/shell/ShellApplication.kt
- [R11] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt
- [R12] https://github.com/ohkuku/yokuli_nmea_anchor_alarm/blob/a845d3d734d3b573a2b53952e66e5f800e944205/app/src/main/java/com/yokuli/anchorwatch/map/OfflineMbTiles.kt
- [R13] https://github.com/ohkuku/yokuli_nmea_anchor_alarm/blob/a845d3d734d3b573a2b53952e66e5f800e944205/app/src/main/java/com/yokuli/anchorwatch/map/nautical/NauticalSourceResolver.kt
- [R14] https://github.com/ohkuku/yokuli_marine_shell/blob/4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5/gradle/libs.versions.toml
