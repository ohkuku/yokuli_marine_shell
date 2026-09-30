# `.yklchart` 图集包格式 · 版本 1

`.yklchart` 是可离线导入的 ZIP 容器，承载一个海图集合或一个数据集合。它与用于可安装应用的 `.ykl` 分开：没有脚本、运行入口或安装权限。原始 GeoPackage、S-57、GEBCO 栅格或 MBTiles 按字节保存，容器不改变其格式、坐标、原始许可、资料质量或导航资格。

## ZIP 布局

```text
manifest.json                   必须为第一个 ZIP 成员
files/01-linz-hydro.gpkg
files/02-linz-hydro.gpkg
...
```

使用 Deflate，支持 ZIP64；无加密、无符号链接、无目录成员。归档只含一个清单及清单列出的载荷文件。不得加入未声明的 README、密钥、脚本、任意 JSON 或其他附件。对外来源文档放在仓库目录旁，必要署名和许可同时写入清单。

包内名称是 NFC Unicode、UTF-8 编码的相对路径，使用 `/` 分隔，以 `files/` 开头，完整路径不超过 240 UTF-8 字节。路径段不能为空、`.`、`..` 或以 `.` 开头；拒绝绝对路径、反斜杠、冒号、控制字符和不区分大小写的重名。载荷文件不得用符号链接或特殊设备代替。

## 清单

清单为不超过 1 MiB 的 UTF-8 JSON 对象。以下是只列一个真实载荷的结构示例；新西兰完整包实际列出五个文件。

```json
{
  "format": "yokuli.chart-package",
  "version": 1,
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
| `format` / `version` | 固定为 `yokuli.chart-package` / 整数 `1` |
| `id` | 集合标识；打包工具接受 1–80 个英文字母、数字、`.`、`_`、`-`，首字符为字母或数字 |
| `name` | 展示名称；工具要求非空，最多 512 UTF-8 字节 |
| `kind` | `data` 或 `charts`，一个包只取一种 |
| `createdAt` | 含时区的 ISO 8601 打包时间；工具规范成 UTC `Z`，不是测量或修正时间 |
| `provider` / `license` / `attribution` | 来源、许可、署名字符串；工具要求非空，前两项各最多 512 UTF-8 字节，署名最多 8192 字节 |
| `files` | 1–2000 项，每项声明一个唯一文件 |
| `path` | ZIP 中完全一致的安全相对路径 |
| `bytes` | 解压后的原始大小；整数，1–32,000,000,000 字节 |
| `sha256` | 原始载荷的 SHA-256，64 个小写十六进制字符 |
| `format` | `gpkg`、`s57`、`gebco` 或 `mbtiles` |
| `priority` | 非负整数，小值先使用；打包工具按相对文件名字典顺序赋 `0…n-1` |

所有载荷的解压后总量不得超过 **64,000,000,000 字节**；上表的单文件和总量限制均为十进制，不是 GiB。文件排列按 `priority` 升序，清单顺序、ZIP 载荷顺序保持一致。文件优先级只影响本集合，不建立跨集合叠加关系；用户导入后仍可调整同一集合内的顺序。

| 集合种类 | 载荷 `format` | 文件后缀与意义 |
| --- | --- | --- |
| `data` | `gpkg` | `.gpkg`，原有 GeoPackage profile 或受支持的 LINZ 来源 |
| `data` | `s57` | `.000`–`.999`，未加密 S-57 基础单元和更新；不含 `CATALOG.031` |
| `data` | `gebco` | `.tif`、`.tiff`、`.asc`、`.ascii`，受支持的 GEBCO 数值高程；不接收 NetCDF、TID 或彩色展示图作为高程证据 |
| `charts` | `mbtiles` | `.mbtiles`，离线显示海图 |

扩展名不区分大小写。不能在 `charts` 包放入数据文件，也不能在 `data` 包加入 MBTiles。打包工具只检查文件头与扩展名的一致性、尺寸和归档完整性；完整格式语义、S-57 更新链、GeoPackage profile 和 GEBCO 产品身份仍由生产导入器验证。例如 GEBCO 现有导入器最多接受 256 个栅格，S-57 单文件另有 512 MB 限制，容器的较大上限不覆盖这些约束。`.yklchart` 应携带完整集合；S-57 单独增量继续走原有单文件更新流程。

## 打包与失败处理

[package_charts.py](../scripts/package_charts.py) 只使用 Python 标准库，以块流式读取：先计算原文件大小与 SHA-256，再写清单和 Deflate 载荷；写入时再次计算 SHA-256，并核对文件身份、大小和修改时间，发现并发修改立即失败。原文件内容不被转换。ZIP 固定使用 1980-01-01 成员时间；实际包时间记录在清单。

目标必须在源目录外，扩展名为 `.yklchart`。输出先写同目录下唯一的 `.part`，关闭 ZIP、刷新并同步内容后，以原子替换写入目标。普通失败或中断会删除本次临时文件，已有目标保持到成功替换时。进程被强制杀死或断电可能留下 `.part`，它不是可发布包。

源目录中的隐藏文件和隐藏子目录均不进入归档，工具不进入 `.private`。非隐藏符号链接与特殊文件被拒绝。少数已知伴随文件（`manifest.json`、`catalogue.json`、`README`/`.md`/`.txt`、`使用说明.md`、`LICENSE`/`LICENCE` 及其 `.md`/`.txt`、`attribution.md`/`.txt`）按名称忽略，内容不读取。其他未知或不支持的文件导致失败，需先移出资料集合，不能借图集包传递任意附件。

GeoPackage 和 MBTiles 若仍存在 `-wal`、`-shm` 或 `-journal` 事务伴随文件，会被拒绝。请使用创建数据库的软件正常提交、关闭并 checkpoint 后再打包；工具不会修改源数据库，也不会只复制主文件而遗漏事务内容。

整体包 SHA-256 与压缩大小写在仓库目录清单中；包内每项 SHA-256 检查载荷完整性。哈希不是发行者数字签名，不证明资料时效、出版授权或任何导航资格。可用数据仍受 [海图交互契约](../docs/product/CHART_INTERACTION_CONTRACT.md) 和 [GeoPackage profile](../docs/GEOPACKAGE_CHART_PROFILE.md) 约束。
