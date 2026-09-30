# 离线海图与数据图集

本目录保存 Yokuli 可导入的离线资料目录、来源和离线包：显示海图用 `.yklchart`，航行数据用 `.yklgeodata`。一个包就是一个资料集合；导入后在「图册」中明确选用，文件夹之间只切换，集合内部保留文件顺序。包格式见 [package-format.md](package-format.md)。

## 新西兰 LINZ 水文参考资料

| 项目 | 内容 |
| --- | --- |
| 图集 | 新西兰 LINZ 水文参考资料 · 2026-09-30 下载 |
| 包路径 | `packages/nz-linz-hydro-2026-09-30.yklgeodata`（Git LFS） |
| 类型 | `data`，五个 GeoPackage，一个完整集合 |
| 来源 | Toitū Te Whenua Land Information New Zealand / LINZ Data Service |
| 内容 | 77 个 Hydro 图层，394,629 条来源记录；不同尺度可能重复表示同一对象 |
| 比例尺带 | 1:4k–1:22k、1:22k–1:90k、1:90k–1:350k、1:350k–1:1,500k、1:1.5mil 及更小比例尺 |
| 原始文件顺序 | `01-linz-hydro.gpkg` 至 `05-linz-hydro.gpkg`，细到粗 |
| 许可 | CC BY 4.0，77/77 官方图层元数据已核对；[逐层许可证记录](linz-nz-2026-09-30-licenses.json) |
| 实际文件信息 | 大小、SHA-256、下载地址及发布状态以 [catalogue.json](catalogue.json) 为准 |

这里的“新西兰”指这组 LINZ 图层，而非裁切后的行政范围：保留原图层中的离岛、跨日期线及太平洋／南极延伸范围。下载完整不表示每处水域都有测量或通航资料。没有裁切、简化、填空白或补造垂直基准；WFS 的二维经纬度、标量属性、空值与几何内洞转存为 GeoPackage，原 S-57 拓扑和更新链没有被重建。

**本图集仅为离线水文参考资料。** LINZ 明确说明 LDS 数据不用于导航，且 LDS Chart Vector Data 和海图 GeoTIFF 自 2024 年 5 月起暂停更新。`2026-09-30` 是下载日期；包的 `createdAt` 是打包时间，都不是海图出版或修正日期。官方 API 的 `publishedAt` 也只作为图层元数据保存，不能推断为每个对象的测量时间或海图改正时间。[LINZ 水文数据说明](https://www.linz.govt.nz/products-services/data/types-linz-data/hydrographic-data)

### 下载并导入

**[下载新西兰 LINZ 离线包（约 319 MiB）](https://github.com/ohkuku/yokuli_marine_shell/raw/refs/heads/codex/yokuli-os-rom/chart-library/packages/nz-linz-hydro-2026-09-30.yklgeodata)**

1. 从本目录的 `catalogue.json` 使用已发布的下载地址，取得完整 `.yklgeodata` 文件，并核对大小与 SHA-256。未发布条目不能当作已有下载。
2. 将包复制到手机，在 Yokuli「图册 → 数据」中选择导入数据包。
3. 文件夹登记后即可明确选用，准备在后台继续。此包校验解包后按文件建立索引，先准备好的文件可先浏览与查询；五个比例尺文件属于同一集合，保持默认细到粗顺序，需要时只调整集合内部优先级。

已下载的旧 `.yklchart` 数据包继续按清单的 `kind=data` 识别，无须重新下载或改名。新发布及数据导出使用 `.yklgeodata`，海图导出只使用 `.yklchart`。

GitHub 网页保存的文件或下载的仓库源码压缩包可能只有 Git LFS 指针。若文件仅有几行，以 `version https://git-lfs.github.com/spec/v1` 开头，它还不是图集，不能导入。在已配置 Git LFS 的本地 checkout 中，可获取本条目的实体文件：

```sh
git lfs pull --include="chart-library/packages/nz-linz-hydro-2026-09-30.yklgeodata" --exclude=""
```

GitHub 普通 Git 单文件上限为 100 MiB；本图集用 Git LFS 管理。Git LFS 在 Git 中保存指针，实体数据另存；获取实体仍受仓库访问权限、LFS 可用性和配额约束。GitHub 自动生成的源码压缩包是否包含实体取决于仓库设置。[GitHub 大文件规则](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)、[Git LFS 说明](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-git-large-file-storage)

### 许可和来源

Contains data sourced from the LINZ Data Service licensed for reuse under CC BY 4.0.

许可证：[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。原始来源和官方图层 API 地址保存在 [逐层许可证记录](linz-nz-2026-09-30-licenses.json) 中；此次于 2026-09-30 无凭据读取 77 个公开图层元数据，全部返回许可 ID 171、`type=cc-by`、`version=4.0`。这些字段核实了许可声明，不构成 LINZ 对 Yokuli 或转换结果的认证、背书或适航保证。再次分发应保留来源署名、许可链接和上述转换说明。[LINZ 复用规则](https://www.linz.govt.nz/products-services/data/licensing-and-using-data)、[官方署名要求](https://www.linz.govt.nz/products-services/data/licensing-and-using-data/attributing-linz-data)

原下载记录曾写明未独立核验逐层许可；本目录新增的许可证记录补充了这一步。下载程序的数量、来源 ID 和文件哈希检查仍不构成服务端跨请求的原子快照，不能据此声称源资料具有统一修订版本。

本包来源是 LDS 公开 GIS 图层。LINZ 另行提供的 S-57 分发合作需遵循其专门许可，NZ ENC 服务也有独立条款，不能把本图集的 CC BY 4.0 结论套用到从其他渠道取得的 ENC。[LINZ 图表数据获取与 S-57 许可](https://www.linz.govt.nz/products-services/charts/where-find-charts)

## 在手机整理和导出

“图册 → 海图／数据”分别管理两类文件夹。导入不再要求选择参考或分析用途；自动保留各文件自身的 metadata，规划仍核对实际深度、覆盖与来源限制。选用与打开管理分开，文件夹之间只切换，内部文件可排序。

进入某个文件夹的管理页，可编辑“文件夹资料”（说明、来源、许可及自定义字段），再选“导出整个文件夹”，将海图保存为 `.yklchart`、数据保存为 `.yklgeodata` 到自己选择的位置。导出保留原文件、文件自身的 metadata、文件夹 metadata 与内部次序；文件夹说明不会覆盖文件说明。包可复制到其他设备离线导入，旧 `.yklchart` / `.yklcharts` 数据包保留导入兼容。

普通数据文件夹先登记、再按文件读取并建立私有离线索引，不先复制完整目录。首次纯 GeoPackage 文件夹可分批使用已就绪部分；S-57 更新链、混合格式与栅格首次仍需完成完整索引。压缩包始终先做完整校验和解包。更新可读文件夹时保留旧版本，失败或取消不删除已就绪内容；只有整个集合准备完成后才可完整导出。数据目录保留原始文件，旧安装只有索引时会要求重新扫描或导入后再导出。海图导出包含文件夹中所有仍保留的原文件，包括仅在本机隐藏/移除显示的条目；文件丢失时提示重新连接，不能只导出一部分却称为完整包。

## 打包其他资料集合

使用仓库内的 [打包工具](../scripts/package_charts.py)，Python 3.9 或以上，无第三方依赖：

```sh
python3 scripts/package_charts.py \
  --source ../offline-data/LINZ-New-Zealand-2026-09-30 \
  --output chart-library/packages/nz-linz-hydro-2026-09-30.yklgeodata \
  --id nz-linz-hydro-2026-09-30 \
  --name '新西兰 LINZ 水文参考资料 · 2026-09-30 下载' \
  --kind data \
  --provider 'Toitū Te Whenua Land Information New Zealand / LINZ Data Service' \
  --license 'CC-BY-4.0; https://creativecommons.org/licenses/by/4.0/' \
  --attribution 'Contains data sourced from the LINZ Data Service licensed for reuse under CC BY 4.0.'
```

输出是实际包路径、压缩大小、整体 SHA-256、文件数和未压缩总量；把这些实际值写入目录后再发布。`--created-at` 可显式提供含时区的 ISO 8601 时间以便重复构建；否则记录执行时的 UTC。重新打包会原子替换指定输出，源文件不变。

工具不读取私有密钥，不联网获取源数据，不替资料供应方授予再分发许可。新增供应方时先记录其真实许可、覆盖、版本与转换方式；有导航资格的原始资料和仅供参考的资料都由原导入器按各自规则检查，不能靠包名、`provider` 或 `license` 字符串取得资格。
