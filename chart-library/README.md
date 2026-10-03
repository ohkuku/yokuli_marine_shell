# 官方海图资料库

本目录保存 Yokuli 可导入的离线资料目录、来源和离线包：海图册统一使用 `.yklpkg` 资料包。包内显示海图用 `.yklcharts`，航行数据用 `.yklgeodata`；可以只有一种内容，不会自动生成海图。选中资料包后，地图、准星查询与规划共用这套内容；只在包内排序，不在资料包之间叠加优先级。包格式见 [package-format.md](package-format.md)。

## 网页与应用中的「海图下载」

[官方资料库网站](https://ohkuku.github.io/yokuli_marine_shell/charts/) 与 Yokuli OS 内的「海图下载」读取同一份目录；网站同时链接 [开发者文档](https://ohkuku.github.io/yokuli_marine_shell/developers/)。网页只托管页面和小清单，资料包实体继续走 Git LFS。在线目录地址为 `https://ohkuku.github.io/yokuli_marine_shell/charts/catalogue.json`。

应用默认在用户 Documents 中的 `Yokuli OS Documents/Chart Packages/<大洲>/<国家>/<collectionId>/` 保存资料包，当前新西兰目录为 `Chart Packages/Oceania/New Zealand/nz-linz-native/`。文件名使用目录的 `fileName`，包含地区、资料类型与版本。网页下载的保存位置由浏览器决定，不冒称能操作手机应用目录。下载与导入分开；取得完整包后仍由用户选择导入海图册和是否选用，不覆盖活动资料。

发布状态以目录中的 `status` 为准：只有 `published` 能下载；`uploading` 可以展示真实内容和体积，但不提供下载按钮；`draft` 和 `withdrawn` 不出现在消费者列表。消费者下载只展示当前标准原生资料；旧 GeoPackage 来源包已从下载目录撤下，实体仅保留给制作与维护。网站与应用读取同一状态。

### 维护全球资料目录

**唯一手工维护源是 [catalogue.json](catalogue.json)。** 不直接修改网页或 APK 内的 JSON 副本；运行以下生产内容生成命令同步：

```sh
python3 scripts/publish_chart_catalogue.py
```

它校验发布元数据与路径、确定性生成 `docs/charts/catalogue.json` 和 `runtime/marine-local/src/main/assets/chart-store/catalogue.json`，并从 `design/brand` 同步网站品牌母版。不会上传实体，不查询网络，也不会把上传中的条目自动改成已发布。应用可以用内置快照展示目录；刷新成功后使用服务器目录。新增数据包不需要为了网站改版或重新发 APK。

| 字段 | 维护规则 |
| --- | --- |
| `id` | 一个不可变发行文件的身份；同 ID 的已发布字节、大小与摘要不改变 |
| `collectionId` | 跨版本稳定的系列，如 `nz-linz-native`；新的地区、不同内容形态各有自己的系列 |
| `releaseVersion` | 真实发行版本，如 `2026.10.03-native.1`；修订必须新版本、新发行 ID 与新文件名 |
| `location` | 稳定的大洲/国家/海域代码，加对应中英文名称；不预先创建没有资料的全球地区 |
| `name` / `nameEn` | 用户能理解的海域名；不把数据格式缩写堆在标题里 |
| `description` / `descriptionEn` | 包里实际提供什么，不宣称不存在的功能或认证 |
| `contentTypes` | `charts` 二维海图、`data` 深度/设施等数据、`navigation` 已制备离线航路、`terrain` 已制备三维、`sources` 来源原件；没有 MBTiles 等实际海图就不能写 `charts` |
| `profile` | 真实载荷形态；当前 `native-maritime-v3` 或 `source-geopackage` |
| `recommended` | 同海域优先展示的日常使用资料；不能替代发布状态或数据资格 |
| `fileName` / `downloadSubdirectory` | 安全、稳定的 `.yklpkg` 文件名和相对保存目录；不得包含绝对路径、`..` 或反斜线 |
| `downloadUrl` / `bytes` / `sha256` | 实体下载地址、真实完整文件长度与摘要；不使用 Git LFS 指针文件的大小/摘要 |
| `status` | `draft` → `uploading` → `published`；撤回使用 `withdrawn`，保留发行身份以便已下载记录追溯 |
| `provider` / `license` / `attribution` | 实际来源、许可和署名；与 `sourceUrl`、`licenseUrl`、`licenceEvidence` 一并保留 |
| `sourceNotice` / `sourceNoticeEn` | 数据时效与实际内容限制；下载日期、构建日期、测量日期不能混用 |

发布顺序：在本地完成包与摘要 → 写入新发行记录且状态为 `uploading` → 生成目录 → 通过命令行推送 Git LFS 实体 → 实体上传成功且真实下载可取后，将本次发行改为 `published` → 再次生成目录并提交推送。失效包先 `withdrawn`，不要复用已发布 ID 偷换内容。不同版本可共存；消费者目录只发布标准原生资料，同系列旧版本可折叠；来源原件不进入消费者选择，保留在维护文档与仓库中。

GitHub Pages 使用专门的 `gh-pages` 分支、`/(root)` 发布源。生产源码仍在当前工作分支的 `docs/`，根页包含资料库与开发者入口；发布脚本只复制 `index.html`、`.nojekyll`、`charts/` 与 `developers/`，保留 Pages 分支上其他已有内容。大数据、仓库历史和私有配置不会被复制。无需新增 CI 工作流。

```sh
# 先生成实际要发布的目录及开发者资源
python3 scripts/publish_chart_catalogue.py
python3 sdk/tools/package_extensions.py
# 只准备临时 gh-pages checkout，输出其路径，不提交、不推送
python3 scripts/publish_docs_pages.py
# 明确发布：有变化才提交，以普通 CLI push 更新 gh-pages
python3 scripts/publish_docs_pages.py --push
```

脚本从当前工作树读取已生成的内容，允许发布尚未提交的生产资产；维护者仍应把相应源码与目录一起提交。它先读取远程现有 `gh-pages`，首次没有分支时才建立孤儿分支；并发更新时普通 push 会拒绝覆盖，不强推。只生成临时目录与网站提交，不修改 Git 配置、登录状态、CI 或 Pages 设置。首次由仓库管理员将 Pages 发布源设为 `gh-pages / (root)`；仓库文件已提交、分支已推送与 Pages 已上线是不同状态。

## 新西兰原生离线资料（2026-10-03 编译）

**[下载原生预编译资料包（约 1.13 GiB）](https://github.com/ohkuku/yokuli_marine_shell/raw/refs/heads/codex/yokuli-os-rom/chart-library/packages/nz-linz-native-2026-10-03.yklpkg)**

使用包含 `504ad6ba` 或后续修订的应用，在「海图册 → 导入资料包」选择这个 `.yklpkg`。安装完成后选中「新西兰海域」，准星、航线规划和海图/AIS 三维共用它。导入仍有一次完整性校验和解压；不会在手机上重新解析全国 GPKG、重建这些导航区域与三维基础块。

| 内容 | 实际产物 |
| --- | --- |
| 数据来源 | 2026-09-30 下载并裁到新西兰 EEZ 的同一套 LINZ 资料，下载日期不是海图出版日期 |
| 规范事实 | v8，全精度坐标；286,035 条来源记录和 1 处明确未知区 |
| 离线规划 | 31,640 个基础语义航路区域，nav v3；船型约束仍在实际规划时应用 |
| 三维基础层 | 2,150 个概览区域，复杂区域细分后共 3,827 个完整块，规则 `terrain-7` |
| 原生载荷 | 约 1.93 GiB，包含事实、导航和三维；不再同时携带五个 GPKG 原件 |
| 文件摘要 | `0259dc0b71842e5615456aa35947b6745f40d9170c19fa944b407e8128c8d576` |
| 制作入口 | [共用手机生产实现的桌面编译器](../tools/maritime-compiler/README.md) |

这份资料保留全部既有来源限制与未知区。基础三维已经制作，近距离详细层仍可按需准备；没有高程的设施或陆地不补造真实高度。包内不含 MBTiles，需要二维离线海图时仍由用户明确选择「生成离线海图」。体积、各载荷摘要和来源记录见 [catalogue.json](catalogue.json)。手机上的实际查询耗时和帧率不能由资料包完成状态推断。

导入不会覆盖用户现有资料；选用标准包后可在海图册移除不再需要的旧包。海图册负责标准包的导入、内容管理与按需准备。下方原件归档仅供维护者编辑来源或重新编译，不再作为应用与网页中的另一个下载选项。许可与 LINZ 停更说明同样适用于预编译版。

## 制作维护：新西兰 LINZ 原件归档

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

### 维护原件

原始文件与许可证据继续保存在本仓库，方便后续重新编译和制作新标准包；不会删除，也不会出现在应用或网站消费者下载列表中。

[维护者获取来源归档（约 248 MiB）](https://github.com/ohkuku/yokuli_marine_shell/raw/refs/heads/codex/yokuli-os-rom/chart-library/packages/nz-linz-hydro-2026-09-30.yklpkg)。它包含原始 GeoPackage；日常使用请下载本页上方的标准原生资料包。重新制包走 [maritime-compiler](../tools/maritime-compiler/README.md)，完成后作为新的不可变发行版本发布。

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

海图册只有资料包列表。可以新建空包，加入 MBTiles / `.yklcharts` 海图，以及 S-57、GeoPackage、GEBCO 或 `.yklgeodata` 数据。每包维护一个数据目录；新来源是明确替换，完整就绪后才切换。若需组合多份原始数据，将它们放入同一文件夹导入；包内的文件优先级作用于查询、规划与生成，资料包之间没有优先级。

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
