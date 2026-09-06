# W08 报告 / Google Satellite and owned chart overlays

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`01fd23ac77cc91b06f448d2aec8aa6f61f1900c8`

## 产品结果

- 配置了非占位 key 的 Chart 使用 Google Satellite，并在同一个 Google Map 上叠加当前 Chart Library plan。
- 每个本地图层保留 plan 顺序、opacity、tile scheme、zoom 与 revision；部分打开失败不会丢掉其它图层。
- base 和 overlay 状态拆开，旧 renderer generation callback 无法覆盖当前状态。
- 业务 marker/polyline 更新只移除自己的 native handle，不再使用会删除 raster overlay 的全图 `clear()`。
- 未配置 key 时保留现有 MapLibre 本地显示路径；没有扩大任何 Google tile 获取或缓存范围。

## DESIGN DECISIONS

- 复用 CL01–CL12 的 display plan 与只读 `ChartResourceAccessPort`，没有再造第二套目录或文件拥有者。
- Google TileProvider 直接消费 revision-pinned read session；用串行 session 读取保护底层 SQLite handle，避免再开一条文件服务器协议。
- plan 更新直接释放旧 session、清理 native cache 并重建最多八个 overlay；高频业务 overlay 则独立更新。
- `BASE_READY` 只由 Google 的 map-loaded callback 产生，不由 key 存在或 MapView 构造成功产生。
- 保留 MapLibre 作为无 key/本地-only 降级路径，而不是为了统一实现删除已验证能力。

## 真实性边界

- GitHub 能验证 key 是否注入、SDK wiring、编译与本地 fixture；不能证明真实 Google API 授权、账单、网络或 tile 视觉成功。
- Android/Google native overlay 的最终像素与真实外部 provider 仍需流水线/物理证据，未回传前不写 PASS。

## 测试源交付给 Pipeline

- core：base/overlay 正交推进、旧 generation 拒绝、detach 清理事实。
- adapter：plan 顺序/opacity、XYZ/TMS 读取、非法坐标、部分失败、session close。
- W08 static：Satellite、TileOverlay、cache clear、z-index、no Google URL、CI/report/lock。

## English translation

W08 makes Google Satellite the configured connected base and renders the current revision-pinned Chart Library plan through native tile overlays. Online-base and local-overlay state are independent, business overlays no longer clear raster layers, and the no-key MapLibre path remains available. Hosted and real-key visual evidence is still pending.
