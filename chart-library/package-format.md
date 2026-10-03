# `.yklpkg` / `.yklcharts` / `.yklgeodata` 离线包格式 · 数据版本 3（兼容版本 1、2）

图册只展示可命名的 `.yklpkg` 集合：本机允许先建空包，随后加入显示海图、航行数据或两者；不自动生成海图。显示海图子包使用 `.yklcharts`（`kind=charts`），航行数据子包使用 `.yklgeodata`（`kind=data`）。两者共用同一个可离线导入的 ZIP 容器规范，每包只承载一个文件夹。它们与用于可安装应用的 `.ykl` 分开：没有脚本、运行入口或安装权限。原始 GeoPackage、S-57、GEBCO 栅格或 MBTiles 按字节保存，容器不改变其格式、坐标、原始许可、资料质量或导航资格。

数据导出已使用版本 3 的原生海事 profile：保留规范事实与已完成的三维、导航产物，接收端验证后安装，不重新解析原始 GPKG / ENC。海图仍为 MBTiles，外层 `.yklpkg` 仍是版本 1，旧原始数据包继续可读。版本 3 不能靠改后缀或私自附加 SQLite 冒充；以下固定路径、清单类型及数据库结构必须全部符合。

## ZIP 布局

```text
manifest.json                   必须为第一个 ZIP 成员
files/01-linz-hydro.gpkg
files/02-linz-hydro.gpkg
...
```

支持 STORE / Deflate 及 ZIP64；原生数据库、导航附件和嵌套子包使用 STORE，其他原始载荷使用 Deflate；无加密、无符号链接、无目录成员。归档只含一个清单及清单列出的载荷文件。不得加入未声明的 README、密钥、脚本、任意 JSON 或其他附件。对外来源文档放在仓库目录旁，必要署名和许可同时写入清单。

包内名称是 NFC Unicode、UTF-8 编码的相对路径，使用 `/` 分隔，以 `files/` 开头，完整路径不超过 240 UTF-8 字节。路径段不能为空、`.`、`..` 或以 `.` 开头；拒绝绝对路径、反斜杠、冒号、控制字符和不区分大小写的重名。载荷文件不得用符号链接或特殊设备代替。

## 清单

清单为不超过 1 MiB 的 UTF-8 JSON 对象。以下是只列一个真实载荷的结构示例；新西兰完整包实际列出五个文件。

```json
{
  "format": "yokuli.chart-package",
  "version": 2,
  "id": "nz-linz-hydro-2026-09-30",
  "name": "新西兰 LINZ 水文参考资料 · 2026-09-30 下载",
  "kind": "data",
  "createdAt": "2026-09-30T00:00:00.000Z",
  "provider": "Toitū Te Whenua Land Information New Zealand / LINZ Data Service",
  "license": "CC-BY-4.0; https://creativecommons.org/licenses/by/4.0/",
  "attribution": "Contains data sourced from the LINZ Data Service licensed for reuse under CC BY 4.0.",
  "files": [
    {
      "path": "files/01-linz-hydro.gpkg",
      "bytes": 142458880,
      "sha256": "d4f7450536f82892235e2768877e457e4d1c5aaf2e9006b2410f7d36c4cfcd02",
      "format": "gpkg",
      "priority": 0
    }
  ]
}
```

示例 `createdAt` 只演示语法，实际打包时间由工具产生。字段约定如下。

| 字段 | 约定 |
| --- | --- |
| `format` / `version` | 固定为 `yokuli.chart-package`；原生数据导出整数 `3`，普通原始资料及海图导出 `2`；继续读取版本 `1` |
| `id` | 集合标识；打包工具接受 1–80 个英文字母、数字、`.`、`_`、`-`，首字符为字母或数字 |
| `name` | 展示名称；工具要求非空，最多 512 UTF-8 字节 |
| `kind` | `data` 或 `charts`，一个包只取一种 |
| `createdAt` | 含时区的 ISO 8601 打包时间；工具规范成 UTC `Z`，不是测量或修正时间 |
| `provider` / `license` / `attribution` | 来源、许可、署名字符串；前两项各最多 512 UTF-8 字节，署名最多 8192 字节。版本 1 要求非空，版本 2、3 允许未知时为空，不填造来源 |
| `files` | 1–2000 项，每项声明一个唯一文件 |
| `path` | ZIP 中完全一致的安全相对路径 |
| `bytes` | 解压后的原始大小；整数，1–32,000,000,000 字节 |
| `sha256` | 原始载荷的 SHA-256，64 个小写十六进制字符 |
| `format` | `gpkg`、`s57`、`gebco`、`mbtiles`；数据 v3 另支持下方固定 `native-*` 类型 |
| `priority` | 非负整数，小值先使用；打包工具按相对文件名字典顺序赋 `0…n-1` |
| `files[].rasterProduct` | 版本 2 可选；仅 `gebco` 文件使用 `GEBCO_20xx_Grid` 产品标识，保留泛文件名数值网格的原产品身份。仍经实际产品、栅格与高程校验，不把声明当精度或通航证据 |

所有载荷的解压后总量不得超过 **64,000,000,000 字节**；上表的单文件和总量限制均为十进制，不是 GiB。文件排列按 `priority` 升序，清单顺序、ZIP 载荷顺序保持一致。文件优先级只影响本集合，不建立跨集合叠加关系；用户导入后仍可调整同一集合内的顺序。

| 集合种类 | 载荷 `format` | 文件后缀与意义 |
| --- | --- | --- |
| `data` | `gpkg` | `.gpkg`，原有 GeoPackage profile 或受支持的 LINZ 来源 |
| `data` | `s57` | `.000`–`.999`，未加密 S-57 基础单元和更新；不含 `CATALOG.031` |
| `data` | `gebco` | `.tif`、`.tiff`、`.asc`、`.ascii`，受支持的 GEBCO 数值高程；不接收 NetCDF、TID 或彩色展示图作为高程证据 |
| `charts` | `mbtiles` | `.mbtiles`，离线显示海图 |

扩展名不区分大小写；导入、导出和命令行打包均要求 `.yklcharts` 对应 `kind=charts`、`.yklgeodata` 对应 `kind=data`，扩展名与经校验的清单必须一致。旧 `.yklchart` / `.yklcharts` 数据后缀不再兼容；本轮新版新西兰资料已经移除南极内容并重新打包，必须重新下载当前 `.yklpkg`；旧文件不能仅改名冒充新版，摘要以目录清单为准。普通 `.zip` 仅用于原始交换集，不接受改名的 Yokuli 包绕过类别校验。选择错误入口时明确引导到“海图”或“数据”。不能在 `charts` 包放入数据文件，也不能在 `data` 包加入 MBTiles。打包工具只检查文件头与扩展名的一致性、尺寸和归档完整性；完整格式语义、S-57 更新链、GeoPackage profile 和 GEBCO 产品身份仍由生产导入器验证。例如 GEBCO 现有导入器最多接受 256 个栅格，S-57 单文件另有 512 MB 限制，容器的较大上限不覆盖这些约束。两种离线包均应携带完整集合；S-57 单独增量继续走原有单文件更新流程。

## 文件夹与文件 metadata

版本 2 在清单顶层与每个 `files[]` 条目分别支持可选 `metadata` 对象，键和值均为字符串。顶层描述整个文件夹，每个文件只保存该文件自身的资料，不将顶层提供方、日期或许可批量覆盖到文件。每个 map 最多 64 项，字段名非空、不重复、最多 80 个字符，单个值最多 8192 个字符，键和值的 UTF-8 总量最多 128 KiB；整个清单仍限 1 MiB。拒绝控制字符，值仅额外允许换行、回车和制表。

常用键为 `description`、`provider`、`license`、`attribution`，同时支持用户自定义字段。没有 metadata 的原文件也可正常导入。原文件中的完整 metadata 随文件字节保留，自动提取内容无需重复灌入清单；显式包内逐文件注释独立保留。移动端只提取有界的展示字段，不重写数据库或把打包日期当测量日期。版本 1 不含 metadata 对象，但仍可读取；新版新西兰资料需从当前目录重新下载 `.yklpkg`；内部数据子包仍使用 `.yklgeodata`。容器版本兼容不意味着允许错误扩展名。

图册“管理 → 文件夹资料”编辑顶层字段；“导出整个文件夹”导出原始文件、各文件 metadata 和当前内部顺序。单个文件只属于一个原始资料包；原生格式内的多个表仍随该文件完整导出。没有跨文件夹优先级。

### 应用内导出生命周期

新数据导入只显示任务，完整成功才进入目录；失败或取消不留空条目，更新已有资料则在成功前保留旧可读版本。同一规范来源 URI 复用原身份。迁移前已存在的真实部分资料保留，可重扫，但不可导出为完整集合。数据导出由 Core `ChartDataService` 接受独立 `requestId` 并持有版本租约；完整打包到私有临时文件后，再写到系统文件选择器授予的目标。界面消费 `ChartExportJob` 的准备、打包、复制、完成、取消、失败和中断状态。离开图册不中断已接受作业；取消及失败尝试删除本次不完整输出、释放租约和临时文件。Android 文档提供方不保证原子覆盖，复制时进程死亡可能留下不完整目标，重启显示中断并要求重新导出，不能宣称目标已成功。

海图目录从保留的 MBTiles 文件导出，包括仅在本机隐藏或移除显示的条目；完整源文件缺失时不静默跳过。数据目录的新版本保留导入原件与 S-57 完整基础/更新链。旧安装若只有索引、没有原件，明确提示重新扫描/导入，不生成只含索引的假图包。

## 打包与失败处理

命令行 [package_charts.py](../scripts/package_charts.py) 继续生成兼容的版本 1 包；应用内共享 writer 的海图为版本 2，原生数据为版本 3；CLI 合包可以校验并封装 v3 数据子包。命令行工具只使用 Python 标准库，以块流式读取：先计算原文件大小与 SHA-256，再写清单和 Deflate 载荷；写入时再次计算 SHA-256，并核对文件身份、大小和修改时间，发现并发修改立即失败。原文件内容不被转换。ZIP 固定使用 1980-01-01 成员时间；实际包时间记录在清单。

目标必须在源目录外；`--kind charts` 要求扩展名 `.yklcharts`，`--kind data` 要求 `.yklgeodata`。输出先写同目录下唯一的 `.part`，关闭 ZIP、刷新并同步内容后，以原子替换写入目标。普通失败或中断会删除本次临时文件，已有目标保持到成功替换时。进程被强制杀死或断电可能留下 `.part`，它不是可发布包。

源目录中的隐藏文件和隐藏子目录均不进入归档，工具不进入 `.private`。非隐藏符号链接与特殊文件被拒绝。少数已知伴随文件（`manifest.json`、`catalogue.json`、`README`/`.md`/`.txt`、`使用说明.md`、`LICENSE`/`LICENCE` 及其 `.md`/`.txt`、`attribution.md`/`.txt`）按名称忽略，内容不读取。其他未知或不支持的文件导致失败，需先移出资料集合，不能借图集包传递任意附件。

GeoPackage 和 MBTiles 若仍存在 `-wal`、`-shm` 或 `-journal` 事务伴随文件，会被拒绝。请使用创建数据库的软件正常提交、关闭并 checkpoint 后再打包；工具不会修改源数据库，也不会只复制主文件而遗漏事务内容。

整体包 SHA-256 与压缩大小写在仓库目录清单中；包内每项 SHA-256 检查载荷完整性。哈希不是发行者数字签名，不证明资料时效、出版授权或任何导航资格。可用数据仍受 [海图交互契约](../docs/product/CHART_INTERACTION_CONTRACT.md) 和 [GeoPackage profile](../docs/GEOPACKAGE_CHART_PROFILE.md) 约束。


## 图册容器 `.yklpkg`

外层清单 `format=yokuli.atlas-package`、`version=1`、`kind=atlas`。其余名称、来源、UTC 日期、metadata、大小与摘要字段沿用上述严格规范，但 `files` 只允许 1–2 项、每类最多一项：

| format | 文件后缀 | 子包清单 kind |
| --- | --- | --- |
| `charts` | `.yklcharts` | `charts` |
| `geodata` | `.yklgeodata` | `data` |

空包只在本机保存；没有真实文件不能导出。纯数据包可直接包含原有 `.yklgeodata`，无需制造空海图或自动渲染 MBTiles。外层先完整校验 SHA-256、大小及子清单，再由原 ChartLibrary / ChartDataService 验证全部真实格式并落盘。旧显示包仅需将 `.yklchart` 改名 `.yklcharts`，内容不改；数据包必须使用 `.yklgeodata`。

```text
manifest.json
files/charts.yklcharts       可选，与下项至少存在一个
files/data.yklgeodata        可选
```

命令行合包使用同一个生产工具。源目录只放需组合的子包，原件不被改写；工具会流式核对每个子包的所有声明载荷及摘要，不只检查扩展名。

```sh
python3 scripts/package_charts.py --kind atlas --source /path/children \
  --output /path/collection.yklpkg --id my-collection --name "我的海域" \
  --provider "来源名称" --license "原许可" --attribution "原署名"
```

`ChartBundleStore` 只维护集合绑定和持久事务，海图仍由 ChartLibrary 管理，数据仍由 Core 管理。全包更新使用独立代，所需子项全部成功后才切换绑定；失败清理本事务拥有的子项，原已启用集合保持可用。恢复按 requestId 对账。仅导入一种内容也可使用；单独添加海图会按真实 URI 去重并保留现有顺序，添加数据是明确替换完整数据来源，不悄悄跨目录混合。

旧独立目录只迁移一次：此前明确选定的海图与数据可归入同一包，其余各成一包；不删除旧数据。用户解绑或删除集合只清理该集合真正拥有的私有子项，SAF 原文件及借用的旧目录不被删除。LINZ 连接也属于集合的数据来源，未缓存时保持待下载状态。

导出支持完整 `.yklpkg` 或单类子包。Core 数据导出与 ChartLibrary 海图导出各冻结实际来源；仅在两者成功生成后流式写出外层包。内部导出通过不对外开放的 FileProvider，只暴露 `cache/chart-bundle-export/`，不放宽 Core 的目标 URI 限制。导出事务留存 requestId、目标与来源身份；恢复时来源已变则明确失败，不能用不同子项覆盖同一回执。文档提供方不保证原子覆盖，失败尝试清理此次部分输出；不能删除时明确提示保留了部分文件。


## 数据 v3：原生事实与准备产物

`kind=data`，`format=yokuli.chart-package`，`version=3`。必须各含一个 `native-catalog`、`native-facts`；可选产物各最多一个，仍可包含原始格式文件。清单内每项仍有独立 bytes / SHA-256，原生格式只接受以下完全一致的路径：

| 路径 | format | 生产含义 |
|---|---|---|
| `files/runtime/catalog.json` | `native-catalog` | `format=yokuli.native-maritime`、schema=1；ChartDataset、原始文件来源及其完整性。最多 32 MiB，不携带本机 linked URI |
| `files/runtime/features.sqlite` | `native-facts` | SQLite user_version=8；唯一规范属性、全精度坐标分块、RTree / 可移植候选索引、稳定内容身份 |
| `files/runtime/terrain-products.sqlite` | `native-terrain` | READY 三维块；无排队作业、无设备路径，每块自带 schema、来源、尺寸和 SHA-256 |
| `files/runtime/navigation.bin` | `native-navigation` | YNA3/version1 导航附件；仅基础水域拓扑，文件名、大小、CRC 与规则版本逐项核验 |

v8 中 `features.payload` 只保存属性及几何类型；`geometry_part` 保存部件、孔洞与范围，`geometry_span` 将原精度坐标保存为至多 256 条边的独立无损压缩块，邻块只共享必要端点。纬度索引使冷点查直接读取穿过射线的块。`geometry_fact` 保存几何摘要，`native_content` 保存版本身份及内容链；`native_identity` 只在导入设备上绑定数据 ID，不改变来源事实身份。属性、部件及全几何详情仍来自同一事实库，不能把简化显示网格用于水深或航线检查。

安装先在私有 stage 校验归档摘要、facts 版本与结构、SQLite quick_check、对象计数、几何块结构及产物载荷，再重绑定本机 ID并原子发布。三维块和导航图仅在来源身份、规则、优先级与区域相符时复用。失败保留旧版本；来源不符的产物不能绕过重新准备。

图册“储存占用”按需读取当前版本文件大小，分别显示本地原件、事实与索引、三维、导航和其他文件；关联外部原件单列，不计入本机私有占用。准备中可手动刷新，不把 ZIP 压缩大小当作安装占用。

当前手机导出携带已保存原件及规范事实、已经完成的准备产物，不宣称整个数据覆盖都已准备三维或导航。原生事实型包若明确没有原件，可以再导出原生 profile；不能还原未携带的 GPKG/ENC。GEBCO 栅格目前仍须携带受支持的原始数值文件，安装重建随机读取描述，ASCII 首次需转换。旧安装只有索引且没有完整原件时仍不冒充完整原始交换集。

`准备离线区域` 明确请求海图当前中心周围的 2 / 5 / 10 km 区域；Core 保存真实完成块，图册和通知中心展示准备状态。显示所需的其他区域按需补充。取消保留完成块；导航准备被进程死亡中断后需明确继续，三维未完成块可恢复排队。外层包不会额外再复制一套产物。

原生成员使用 STORE 是为后续随机读取保留布局；当前生产导入仍进行一次有界流式校验与安装，不是 ZIP 内零复制 SQLite。CLI 仍负责原件封装和组合子包，尚没有独立桌面事实/地形编译器。仓库原有新西兰下载包没有因本次代码变更自动成为预编译 v3 包。
