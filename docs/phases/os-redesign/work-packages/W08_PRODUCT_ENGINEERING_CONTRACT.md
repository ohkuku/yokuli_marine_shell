# W08 Product & Engineering Contract — Connected Base + Owned Chart Layers

版本：1 · 2026-09-06  
状态：施工候选

## Intent / 产品愿景

Chart 应该是一张连续的航海态势面：有网络和可用 Google 配置时，卫星影像给用户提供熟悉的岸线与环境
语境；用户授权给 Yokuli 的海图同时作为自己拥有、自己控制的图层叠在其上。二者是独立能力，不是谁为谁
“补洞”。在线底图失败时，本地海图仍应继续工作；本地来源失败时，也不能把在线影像说成离线海图。

## Experience / 用户体验

- 配置有效路径上的 Chart 默认使用 Google Satellite，而不是道路地图。
- 在海图库选择的 base/overlay layer 保持确定顺序、可见性与透明度，并出现在同一相机和手势空间。
- 来源 revision 改变后旧 tile cache 不再继续显示；新来源尚未打开时明确处于加载状态。
- 在线底图不可用与本地图层退化分别表达。用户可以只靠仍可读取的本地图层继续浏览。
- 地图、标记、测量、路线草稿和船位更新不会为了刷新业务 overlay 而销毁 MBTiles overlay。

## Domain / 领域边界

- Google SDK 只拥有在线底图、相机、触摸和 native overlay handle；Chart Library 仍拥有授权、目录与读取。
- 一个 display plan 是 revision-bound 的只读输入。renderer 不修改外部文件，不改变 catalog，不自行选源。
- `BASE_*` 与 `OVERLAY_*` 是正交事实；renderer generation 隔离迟到 callback。
- MBTiles XYZ/TMS 差异只在读取边界转换，Google SDK 始终请求 XYZ 坐标。

## Opportunities / 可发展空间

后续可以在同一合同上增加更细的加载观测、分图层故障解释、快速透明度控制和快照生成；不要求采用某个
固定 controller/repository 结构，也不要求把 MapLibre 删除。无 Google key 或 SDK 不可用时，现有本地
renderer 仍是重要降级路径。

## Non-negotiables / 不可协商边界

- 不抓取、逆向或自行缓存 Google tile URL；在线内容只通过官方 SDK。
- Google key “已注入”不等于 API 授权、账单、package/SHA restriction、网络或图块加载已验证。
- 用户 MBTiles 只读、revision-pinned、有界打开；plan 替换和页面销毁必须释放 session/native overlay。
- 图层 order、opacity 和缺失状态必须来自当前 display plan；失败不得静默丢掉仍可读的其它 layer。
- 业务 overlay 高频变化不能清空或重建所有 raster layer。

## Forbidden outcomes / 禁止结果

- 把 Google 影像下载进 Yokuli cache，或用于填充本地 coverage 结论。
- 因 Google base 未加载而隐藏已打开的本地 overlay。
- 继续用 `GoogleMap.clear()` 处理标记刷新，连带删除 raster overlay。
- 用 MapView 创建成功或 secret 非空冒充 `BASE_READY`。
- 把用户文件 URI、key、坐标或 provider token写进日志/CI artifact。

## Compatibility / 兼容策略

W05–W07 所需目录、SAF runtime 和独立 Chart Library App 已由 CL01–CL12 提供并继续复用。未配置 Google
key 的构建继续使用现有 MapLibre 本地显示路径；旧 managed import 和 display plan schema 均不迁移、不清库。

## Acceptance stories

1. 有运行可用的 Google SDK 时，Chart 使用 Satellite；选择两个本地图层后，两者按 plan 顺序和透明度叠加。
2. 图层 revision 更新触发旧 overlay cache 清理、旧 session 关闭，新 plan 不能被迟到旧结果覆盖。
3. 一个图层打开失败时，其它可读图层保留，状态为 `OVERLAY_DEGRADED`。
4. 在线 base 尚未加载时，本地 overlay 可以达到 `OVERLAY_READY`，两项状态互不覆盖。
5. 更新船位/标记/测量不会调用全图 clear 或重新打开 MBTiles。
6. 无 key 构建仍能显示已选择的本地图，不宣称 Google 已就绪。

## Evidence required

- JVM：坐标/row scheme、revision session、部分失败、释放和 generation reducer 合同。
- 编译/静态：Satellite、TileOverlay、透明度、z-index、cache clear、生命周期和禁用私有 Google URL。
- API 34：本地 fixture 与 Google adapter overlay wiring；外部 tile 内容不作为 emulator 必过条件。
- 人工：真实 key、账单/restriction、网络和图块视觉证据单独标记，不由 CI 代替。

## Existing code landmarks

优先审阅 `GoogleMarineChartSurface`、Chart display plan、Chart Library read port、现有 MapLibre 多图层路径与
composition root。它们是入口提示，不规定最终文件或 class 结构。

## Implementation freedom / 实现自由

实现可选择 native TileProvider、现有安全 loopback gateway 或其它仓库内已验证方式，只要满足上面的只读、
有界、释放、坐标、缓存和真实性合同。不得仅为对应文档名增加无行为的架构层。

## English translation

Chart combines an official Google Satellite connected base with revision-pinned, user-owned local chart layers in one camera space. Base and overlay readiness are independent. Local layers remain truthful and usable when the connected base is unavailable, while Google never fills local coverage or becomes an offline claim. The contract constrains ownership, lifecycle, cache invalidation, ordering, opacity, data safety, and evidence without prescribing an internal class graph.
