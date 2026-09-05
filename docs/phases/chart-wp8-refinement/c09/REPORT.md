# C09 沿线离线资料检查与真实来源报告

> English translation: C09 checks exact local MBTiles keys for a saved route corridor at one target zoom. Tile availability, effective chart content, and navigation suitability remain separate facts. NOAA NCDS is documented as an external import source; no unimplemented in-app downloader is exposed.

## 状态

- package：C09
- baseline：`438c31752ecdd939d204c3867b6e190371025b6e`
- implementation candidate / cumulative verified SHA：`968e1b804d293ba5d25c573c1ee66b52e6f319b9`
- status：`VERIFIED_LOCAL`
- hosted CI：最终同 SHA 证据留给 C12；本报告不把本地 Gate 写成托管结果
- source delivery：`BLOCKED_EXTERNAL`（真实来源和许可已核对；生产只提供外部下载后的系统文档导入，不伪造下载能力）

## 交付

1. `OfflineCoverageRequest` 固定 route ID/revision、坐标、不可变 package version IDs、目标 zoom、每侧宽度、备用区域和 200,000-key 硬上限；SHA-256 fingerprint 覆盖所有这些输入。
2. WGS84 大地线按目标级别/纬度的保守间距取样，端点和转角使用重叠圆盘范围；日期线 X 坐标回绕。允许多检查，超预算明确失败，绝不截断后报告完整。
3. Android 索引只查询指定 zoom 的精确 `(tile_column,tile_row)`，把 MBTiles TMS Y 转为 renderer 使用的 XYZ Y；不会用 package bounds、低级图或几个航点代替实际 tile row。
4. 检查只保留一个 generation/job。新任务、取消、路线 revision、图包版本/删包变化会使旧结果失效；非协作迟到结果不能覆盖新状态。
5. UI 从已保存路线进入，允许指定目标级别和默认每侧 2 NM 的资料准备宽度；显示规划、检查、取消、超限、索引失败、失效和完成状态。不同路线不会继承彼此的失败或取消结果。
6. 结果分开显示 `tileAvailability`、`contentFootprint=NOT_VERIFIED`、`navigationSuitability=NOT_ASSESSED`。缺失 tile key 可在列表定位；“全在本机”仍同时显示“有效图面未核验”和“适航性未评估”。
7. NOAA NCDS 以 `IMPORT_ONLY / BLOCKED_EXTERNAL` 登记；发布 UI 只复用现有 SAF 导入入口，没有“获取缺失”或自动更新按钮，也没有批量抓取 OSM 公共瓦片服务。

## 来源核对

- NOAA NCDS 下载页：`https://distribution.charts.noaa.gov/ncds/index.html`。官方提供由最新 NOAA ENC 派生并按周更新的 MBTiles 下载，适合离线应用。
- NOAA GIS Data and Services：`https://www.nauticalcharts.noaa.gov/data/gis-data-and-services.html`。官方同时说明 MBTiles 可用于离线应用；相关 GIS 服务不满足 USCG chart-carriage 要求。
- NOAA data licensing：`https://www.nauticalcharts.noaa.gov/data/data-licensing.html`。Office of Coast Survey 数据按公共领域/CC0 策略发布。
- 仓库已有 `ncds_21.mbtiles` 四块未重编码追溯样本及原包/逐 tile SHA-256。C02 已在 API 34 完成真实导入和像素渲染；C09 新增测试确认四个真实 TMS row 能按 XYZ key 命中，同时同一声明范围内无 row 的 key 仍为缺失。

## Red、自查与纠错

- Red 先固定路线段穿洞、精确目标级别、日期线、输入 fingerprint、200k 上限、事实维度拆分、唯一 job/取消/迟到结果和来源 capability。
- 初版 UI/组合根完成后，自查发现长计算在第一个 plan 完成前没有可取消的可见状态；增加独立 `Planning` 状态，随后才进入带精确 key 数量的 `Checking`。
- 自查发现全局 coordinator 的 `TooLarge/Cancelled/Failed` 若无 route identity，打开另一条路线可能看到上一条路线的结果；所有非 Idle 状态现均绑定 route ID，Renderer 只消费当前路线状态。
- 初版 SQLite 实现按 zoom 扫描全部 row。虽然结果正确，但超大包的成本不受 route key 数量约束；修正为每批最多 400 个 exact pair 的参数化查询，并在批次和 row 级检查取消。
- 封口前发现精确索引测试仅使用合成 SQLite；新增带来源哈希的 NOAA fixture 测试，防止“真实渲染”和“精确覆盖”仅靠说明文字连接。

## 聚焦与累计证据

- C09 repository contract：5 passed；coverage domain：7 tests；coordinator：4 tests，全部通过。
- API 34 聚焦：精确索引 2、离线覆盖 UI 1，全部通过。
- API 34 累计：真实离线 package/renderer/index 17、Shell 52、Room 7，0 failure/error/skipped。
- 当前代码候选的累计 Host Gate：201 个 Python repository contracts；338 个 JVM XML tests；全仓 test、lint、Standalone Debug/Release 共 1207 Gradle tasks，0 failure/error/skipped。
- 产品表面审计：Release 仍仅 Chart + Settings，无 HOME、Shell Lab、位置权限或 Google Maps key 依赖。
- Debug APK SHA-256：`6e3f2fd052693d27958769a0328c4983fde1dda679a45a10e6f42a802d1053ca`。
- unsigned Release APK SHA-256：`52f7f83997ffb6ba4afafdf83082b37c3b71e23068e22f6f29a961cb17b72326`。

## 未宣称完成

- `tileAvailability=AVAILABLE` 不证明 tile 非透明、图幅有效、资料足够新或航线安全；content footprint 和 navigation suitability 仍明确未核验/未评估。
- NOAA 资料没有内置到 Release，也没有应用内下载、暂停/恢复或自动更新。本机检查和 ImportOnly 路径已完成；来源获取仍是独立 `BLOCKED_EXTERNAL`，不能与机器能力混写。
- C10 的只读位置观测质量、C11 的动态 Shell 地图磁贴以及 C12 的最终同 SHA 托管发布证据尚未完成。
- 三星方屏实体机触控与真实船用判断仍属于独立人工审核，不由模拟器或资料存在性替代。
