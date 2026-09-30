# 离线海图与数据图集

本目录保存 Yokuli 可导入的离线资料目录、来源和离线包：图册统一使用 `.yklpkg` 资料包。包内显示海图用 `.yklcharts`，航行数据用 `.yklgeodata`；可以只有一种内容，不会自动生成海图。选中资料包后，地图、准星查询与规划共用这套内容；只在包内排序，不在资料包之间叠加优先级。包格式见 [package-format.md](package-format.md)。

## 新西兰 LINZ 水文参考资料

| 项目 | 内容 |
| --- | --- |
| 图集 | 新西兰 LINZ 水文参考资料 · 2026-09-30 下载 |
| 包路径 | `packages/nz-linz-hydro-2026-09-30.yklpkg`（Git LFS） |
| 类型 | `atlas`，包含一个 `data` 子包、五个 GeoPackage；不含预制海图 |
| 来源 | Toitū Te Whenua Land Information New Zealand / LINZ Data Service |
| 内容 | 77 个 Hydro 来源图层；新西兰范围内保留 286,035 条来源记录及 1 处保守未知区 |
| 比例尺带 | 1:4k–1:22k、1:22k–1:90k、1:90k–1:350k、1:350k–1:1,500k、1:1.5mil 及更小比例尺 |
| 原始文件顺序 | `01-linz-hydro.gpkg` 至 `05-linz-hydro.gpkg`，细到粗 |
| 许可 | CC BY 4.0，77/77 官方图层元数据已核对；[逐层许可证记录](linz-nz-2026-09-30-licenses.json) |
| 实际文件信息 | 大小、SHA-256、下载地址及发布状态以 [catalogue.json](catalogue.json) 为准 |

当前修订按 LINZ 官方 50842 图层的 EEZ 外边界闭合面裁切，保留新西兰本土、离岛、领海和跨日期线水域，剔除南极及其他范围外资料；边界中的两处公海孔洞保留。处理范围不是新的法律边界声明。原下载共 394,629 条记录，移除 108,594 条范围外记录；不简化、不填空白或补造垂直基准。一处日界线原始面有歧义，裁切后作为明确未知区保留，不能成为水深或规划证据。原 S-57 拓扑和更新链没有被重建。边界与转换来源见 [范围文件](boundaries/nz-linz-eez-2026-09-30.geojson)；可重复执行 [区域裁切工具](../scripts/clip_linz_region.py)（Shapely 2.1）。

**本图集仅为离线水文参考资料。** LINZ 明确说明 LDS 数据不用于导航，且 LDS Chart Vector Data 和海图 GeoTIFF 自 2024 年 5 月起暂停更新。`2026-09-30` 是下载日期；包的 `createdAt` 是打包时间，都不是海图出版或修正日期。官方 API 的 `publishedAt` 也只作为图层元数据保存，不能推断为每个对象的测量时间或海图改正时间。[LINZ 水文数据说明](https://www.linz.govt.nz/products-services/data/types-linz-data/hydrographic-data)

### 下载并导入

**[下载新西兰 LINZ 离线包（约 248 MiB）](https://github.com/ohkuku/yokuli_marine_shell/raw/refs/heads/codex/yokuli-os-rom/chart-library/packages/nz-linz-hydro-2026-09-30.yklpkg)**

1. 从本目录的 `catalogue.json` 使用已发布的下载地址，取得完整 `.yklpkg` 文件，并核对大小与 SHA-256。未发布条目不能当作已有下载。
2. 将包放到手机，在 Yokuli「图册 → 导入资料包」选择它。
3. 导入期间显示后台任务；完整校验、解包和索引成功后，资料包才进入目录供选择。五个比例尺文件属于同一集合，保持默认细到粗顺序，需要时只调整集合内部优先级。进度分别显示文件次序与当前文件各表累计对象量，不把单个文件的进度当作整个包的百分比。

旧版包包含南极延伸范围，必须重新下载本修订；此次数据和 SHA-256 已改变，不能只改文件后缀。升级会保留既有资料并迁入资料包，用户确认新版后可移除旧包。新的下载是可直接导入的 `.yklpkg`；其中数据可在手机单独导出成 `.yklgeodata`，不再重复分发一份相同的大文件。

本包不含 MBTiles。选用后仍显示内置底图，准星读取实际深度与设施，规划读取同一数据。需要独立海图时，在包内选择「生成离线海图」，指定当前地图中心周边的范围、细节和输出目录；完成后实际 MBTiles 加入原资料包，再在海图中切换「包内海图」。生成是用户操作，不在导入时或拖动地图时发生。

GitHub 网页保存的文件或下载的仓库源码压缩包可能只有 Git LFS 指针。若文件仅有几行，以 `version https://git-lfs.github.com/spec/v1` 开头，它还不是图集，不能导入。在已配置 Git LFS 的本地 checkout 中，可获取本条目的实体文件：

```sh
git lfs pull --include="chart-library/packages/nz-linz-hydro-2026-09-30.yklpkg" --exclude=""
```

GitHub 普通 Git 单文件上限为 100 MiB；本图集用 Git LFS 管理。Git LFS 在 Git 中保存指针，实体数据另存；获取实体仍受仓库访问权限、LFS 可用性和配额约束。GitHub 自动生成的源码压缩包是否包含实体取决于仓库设置。[GitHub 大文件规则](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)、[Git LFS 说明](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-git-large-file-storage)

### 许可和来源

Contains data sourced from the LINZ Data Service licensed for reuse under CC BY 4.0.

许可证：[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。原始来源和官方图层 API 地址保存在 [逐层许可证记录](linz-nz-2026-09-30-licenses.json) 中；此次于 2026-09-30 无凭据读取 77 个公开图层元数据，全部返回许可 ID 171、`type=cc-by`、`version=4.0`。这些字段核实了许可声明，不构成 LINZ 对 Yokuli 或转换结果的认证、背书或适航保证。再次分发应保留来源署名、许可链接和上述转换说明。[LINZ 复用规则](https://www.linz.govt.nz/products-services/data/licensing-and-using-data)、[官方署名要求](https://www.linz.govt.nz/products-services/data/licensing-and-using-data/attributing-linz-data)

原下载记录曾写明未独立核验逐层许可；本目录新增的许可证记录补充了这一步。下载程序的数量、来源 ID 和文件哈希检查仍不构成服务端跨请求的原子快照，不能据此声称源资料具有统一修订版本。

本包来源是 LDS 公开 GIS 图层。LINZ 另行提供的 S-57 分发合作需遵循其专门许可，NZ ENC 服务也有独立条款，不能把本图集的 CC BY 4.0 结论套用到从其他渠道取得的 ENC。[LINZ 图表数据获取与 S-57 许可](https://www.linz.govt.nz/products-services/charts/where-find-charts)

## 在手机整理和导出

图册只有资料包列表。可以新建空包，加入 MBTiles / `.yklcharts` 海图，以及 S-57、GeoPackage、GEBCO 或 `.yklgeodata` 数据。每包维护一个数据目录；新来源是明确替换，完整就绪后才切换。若需组合多份原始数据，将它们放入同一文件夹导入；包内的文件优先级作用于查询、规划与生成，资料包之间没有优先级。

包的「内容」进入各文件、对象与元数据管理；「管理」可以编辑包名称和说明，导出整个 `.yklpkg`，或单独导出 `.yklcharts` / `.yklgeodata`。文件的原始内容、各自 metadata 与内部顺序保留；包级说明不会覆写文件资料。空包没有可导出的实际内容。

普通原生数据目录优先关联原件并保存查询索引；文件提供方无法随机读取或没有可靠版本时间时才缓存。压缩包必须完整校验解包，不能对 ZIP 内的数据库直接随机读取。原件变更或权限失效时要求重新连接，不能用旧索引假装数据未变。

海图目录与包内单个原生 MBTiles 优先以只读文件描述符读取原件；无法直接读取时缓存。显式「生成离线海图」只读取当前真实数据并保存有界区域 MBTiles（单次最多 4096 图块），空缺区域透明；它是本机参考图，不是完整 IHO S-52 认证制图产品。生成结果与原数据各自维护，查询/规划从不读取渲染图像来推断水深。

导入、生成、导出离开页面后继续，任务有真实进度及取消；事务成功前保持旧资料。失败不会把空目录当作已导入包。移除包仅清理它拥有的本机内容；SAF 原件、借用的旧目录不被删除。源文件缺失时拒绝导出残缺包。
## 打包其他资料集合

使用仓库内的 [打包工具](../scripts/package_charts.py)，Python 3.9 或以上，无第三方依赖：

```sh
python3 scripts/package_charts.py \
  --source ../offline-data/LINZ-NZ-Waters-2026-09-30 \
  --output /path/children/nz-linz-hydro-2026-09-30.yklgeodata \
  --id nz-linz-hydro-2026-09-30 \
  --name '新西兰 LINZ 水文参考资料 · 2026-09-30 下载' \
  --kind data \
  --provider 'Toitū Te Whenua Land Information New Zealand / LINZ Data Service' \
  --license 'CC-BY-4.0; https://creativecommons.org/licenses/by/4.0/' \
  --attribution 'Contains data sourced from the LINZ Data Service licensed for reuse under CC BY 4.0.'
```

然后将子包目录打成资料包：

```sh
python3 scripts/package_charts.py --kind atlas --source /path/children \
  --output /path/new-zealand.yklpkg --id nz-linz-hydro-2026-09-30 \
  --name 新西兰海域 --provider LINZ --license CC-BY-4.0 \
  --attribution 'Contains data sourced from LINZ Data Service, CC BY 4.0.'
```

输出是实际包路径、压缩大小、整体 SHA-256、文件数和未压缩总量；把这些实际值写入目录后再发布。`--created-at` 可显式提供含时区的 ISO 8601 时间以便重复构建；否则记录执行时的 UTC。重新打包会原子替换指定输出，源文件不变。

工具不读取私有密钥，不联网获取源数据，不替资料供应方授予再分发许可。新增供应方时先记录其真实许可、覆盖、版本与转换方式；有导航资格的原始资料和仅供参考的资料都由原导入器按各自规则检查，不能靠包名、`provider` 或 `license` 字符串取得资格。
