# Yokuli OS 海图来源与导入需求合同

归档状态：`DEFERRED_PRE_LAUNCHER_ENGINE`。Google Maps Adapter 作为冻结基线保留；OpenSeaMap、本地导入和其他海事功能在 Launcher Engine 完成人工验收前不施工。

> English: `DEFERRED_PRE_LAUNCHER_ENGINE`. The Google Maps adapter remains frozen; OpenSeaMap, local import, and other marine work are not active until the Launcher Engine passes human review.

## 1. 本版本结论

Phase 1 只保留三种来源：

1. **Google Maps Android SDK**：联网底图，提供普通地图／卫星图和基础相机交互；
2. **OpenSeaMap seamark overlay**：默认海图叠加层，显示航标等开放海图信息；
3. **本地导入**：用户通过系统文件选择器把自己的地理配准海图导入 Yokuli 私有海图库。

LINZ 不在本版本范围内。旧应用中与 LINZ 有关的 API、URL override、设置项和缓存策略只作为历史审计材料，不进入新运行时，也不为未来预留空 key。

Google Maps 与 OpenSeaMap 的图层必须共享同一个 `MarineChartSurface`、viewport 和手势所有权；导入海图也是这张表面的 source/layer，不得创建第二套地图页面。

## 2. 精确凭据清单

产品运行期凭据只有以下一项：

<!-- phase1-runtime-credentials:start -->
- `GOOGLE_MAPS_ANDROID_API_KEY`
<!-- phase1-runtime-credentials:end -->

本版本不按 dev/prod 拆分 key。一个受限的 Android key 同时允许以下 Android 应用身份：

| 构建 | applicationId | 证书 |
| --- | --- | --- |
| standalone debug | `com.yokuli.marine` | 当前开发机 debug SHA-1 |
| home debug | `com.yokuli.marine.home` | 当前开发机 debug SHA-1 |
| standalone release | `com.yokuli.marine` | Yokuli OS release SHA-1，签名建立后添加 |
| home release | `com.yokuli.marine.home` | Yokuli OS release SHA-1，签名建立后添加 |

如果以后 Play App Signing 使用另一张证书，再给同一包名增加 Play app-signing SHA-1。只有出现独立额度、独立撤销或团队权限边界时，才把 key 拆分；业务代码不得依赖 key 的数量。

Google Cloud 中必须同时设置：

- Application restriction：Android apps，只允许上表的 package/SHA-1 对；
- API restriction：只允许 Maps SDK for Android；
- Billing：项目绑定结算账号；
- Cost control：开启预算通知并按实际可用项设置额度限制。

当前 Google 价目表把原生 `Maps SDK` 基础地图加载列为 unlimited/no charge，但仍要求项目启用 billing；这只是当前计费状态，不是 Yokuli 对永久免费的承诺。Street View、Places、Routes、Map Tiles API 等都不在这把 key 的权限中，也不属于本版本基础地图需求。

Android 客户端 key 会随 APK 交付，不能被当成服务器端机密。加密 vault 用于避免它出现在源码、Git 历史和普通日志中；真正的滥用防线是 package/SHA-1 与 API restriction。曾经公开或进入 Git 历史的旧 key 不得复用，必须在供应商侧撤销。

OpenSeaMap 不需要 API key，但必须显示 OpenSeaMap／OpenStreetMap attribution、遵守数据与 tile 许可，并把来源和更新时间暴露给用户。公共 tile server 没有 Yokuli 可依赖的 SLA；超时、限流或下线都必须退化为底图／本地海图，不能阻断船位、NMEA、Anchor Watch 或其他安全 runtime。

本地导入不需要供应商 API key。Android 发布签名所需的 keystore 与 GitHub Actions secrets 是发布材料，不属于地图供应商凭据，也不因本节的“一把 key”而消失。

## 3. OpenCPN-like 导入的准确范围

“类似 OpenCPN”指用户可以建立自己的海图库、扫描文件、启停图层并查看来源信息，不表示第一版兼容 OpenCPN 的全部格式或插件。

| 格式 | 解释 | Phase 1 状态 |
| --- | --- | --- |
| Raster MBTiles | SQLite 容器中的地理配准栅格瓦片；首个导入与显示合同 | MVP 支持 |
| BSB/KAP | 单图幅、带地理配准与色板的传统栅格海图 | 后续切片 |
| S-57 ENC | 需要独立对象模型和 S-52 portrayal 的矢量 ENC | 未来能力 |
| S-63 ENC | 加密分发、User Permit、许可与更新链路 | 本版本不支持 |
| oeSENC / oeRNC / CM93 | OpenCPN 插件／专有或来源风险格式 | 本版本不支持 |

普通 PNG、JPEG、WebP 或 PDF 没有可验证的地理配准时不得伪装成海图。Vector MBTiles/PBF、GeoTIFF、在线 XYZ URL 和任何可执行 OpenCPN plugin 也不属于 MBTiles MVP；需要时分别建立需求、安全审计、fixture 与 Red/Green 记录。

## 4. Raster MBTiles MVP 合同

导入流程：

`选择文件 → 只读预检 → 临时复制 → 内容与 schema 校验 → 生成摘要 → 用户确认 → 原子激活`

最低要求：

- 通过 Android Storage Access Framework 选择单个文件，不索取整个存储权限；
- 扩展名不能代替内容识别；只把通过 SQLite header 和 MBTiles schema 校验的文件送入 reader；
- 导入前检查声明大小、实际读取量、剩余空间和可配置的大小上限；
- 以只读／query-only 方式检查 `metadata`、`tiles`、zoom 范围、tile bounds 和 image payload；禁止加载 SQLite extension；
- MVP 只接受明确声明且解码成功的 PNG、JPEG 或 WebP raster tile；
- 明确记录 TMS/XYZ row scheme；缺失或含糊时拒绝导入，不通过猜测静默翻转；
- 忽略或拒绝要求再次联网、执行脚本、加载 native library 或动态插件的 metadata；
- 计算文件 hash，保存来源、文件名、导入时间、bounds、zoom、格式和用户备注；
- 校验失败时删除 staging 副本并保留原文件；只有完整成功后才替换活动索引；
- 删除海图必须是可确认操作，不能连带删除 Place、Route、Trip、Anchor 或 Survey 领域数据。

首个实现切片必须先准备最小有效、缺表、损坏 SQLite、伪扩展名、超限、空 tile、错误图片类型、TMS/XYZ 和导入中断 fixtures，再编写 parser/reader。

## 5. UI 与安全语义

Chart 的 layer/source 面板至少区分：

- `地图`：Google 普通／卫星底图；
- `海图`：OpenSeaMap 航标叠加；
- `我的海图`：已导入的 MBTiles 与状态；
- `航海数据`：未来的路线、锚地、轨迹、测深等领域 overlay。

用户必须看见当前 source、在线／离线状态、attribution、导入文件名和最后验证时间。海图缺失、陈旧、覆盖不足或未验证不能显示成“安全”；导入成功也不代表内容权威或适航。OpenSeaMap 和用户生成的 MBTiles 不能替代官方海图，最终导航责任与数据许可责任仍由用户承担。

切换、缩放或导入任何地图都不得隐式 Start/Stop Anchor、Navigation、Trip、Survey、NMEA 或输出任务。地图失败只改变地图可用性状态；全局安全 overlay 和数值 runtime 继续运行。

## 6. 实现顺序

1. 先完成 source/layer selector 的纯 UI state/action 与中英资源；
2. **已完成基础接入**：隔离 Google Maps Android SDK adapter，以环境变量注入受限 key，并对无 key 使用明确 fixture fallback；真实 key、拒绝和离线设备验收仍需补齐；
3. 接入 OpenSeaMap overlay、署名、来源与故障退化；
4. 用 fixtures TDD 实现 raster MBTiles 预检、导入、索引和显示；
5. 真实设备验证触控、内存、存储中断、后台 runtime 隔离和离线恢复；
6. 只有新需求成立后，分别评估 BSB/KAP、S-57 等格式。

## 7. 参考

- [Google Maps Android 获取 key](https://developers.google.com/maps/documentation/android-sdk/get-api-key)
- [Google Maps API 安全最佳实践](https://developers.google.com/maps/api-security-best-practices)
- [Google Maps Android 用量与结算](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing)
- [Google Maps 价目表](https://developers.google.com/maps/billing-and-pricing/pricing)
- [OpenSeaMap tile 接入](https://wiki.openseamap.org/wiki/h%3AEn%3AOpenSeaMap_in_Website)
- [OpenSeaMap FAQ 与许可](https://openseamap.org/index.php?L=1&id=faq)
- [OpenSeaMap 法律与航海警告](https://www.openseamap.org/index.php?L=1&id=imprint)
- [OpenCPN chart formats](https://opencpn.org/wiki/dokuwiki/doku.php?id=opencpn%3Amanual_basic%3Aset_options%3Acharts)
- [OpenCPN MBTiles/KAP 说明](https://opencpn.org/wiki/dokuwiki/doku.php?id=opencpn%3Amanual_advanced%3Acharts%3Ambtiles)

## English translation

Phase 1 has exactly three chart sources: Google Maps Android SDK as the connected base map, the keyless OpenSeaMap seamark overlay as the default nautical overlay, and user-imported local charts. LINZ and its credential/URL configuration are explicitly out of scope. Both Android application IDs and their debug/release signing certificates may share one Android-restricted `GOOGLE_MAPS_ANDROID_API_KEY`; the key is restricted to Maps SDK for Android and is not split by dev/prod until quota, revocation, or ownership boundaries justify that split.

The isolated Google Maps adapter and environment-to-manifest injection are now implemented. A missing key selects an explicit fixture surface. Real-key device acceptance plus denied/offline behavior remain pending; OpenSeaMap and local import are not implemented by this slice.

Google currently lists native Maps SDK base-map loads as unlimited/no charge, but a billing-enabled project is still required and this document does not promise permanent free pricing. OpenSeaMap requires attribution and provides neither an official-chart substitute nor an application SLA. Local imports require no provider key.

OpenCPN-like means a user-managed chart library and source/layer workflow, not blanket format compatibility. Raster MBTiles is the first MVP. BSB/KAP is a later raster slice, S-57 requires a future vector portrayal system, and encrypted/proprietary formats such as S-63, oeSENC, oeRNC, and CM93 are not supported in this version. Imports are staged, bounded, content-validated, opened read-only, indexed atomically, and never allowed to execute plugins or stop independent safety runtimes.
