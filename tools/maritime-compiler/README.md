# Yokuli 原生资料编译器

把受支持的 GeoPackage / LINZ 文件夹编译成可在图册导入的 `.yklpkg`：其中的 `.yklgeodata` v3 含规范事实库、基础语义航路区域与三维基础块。原始文件只读，不塞入 APK，不需要 Android 设备。保留原始文件用于重新编辑；面向使用者的原生包不重复携带 GPKG。

```sh
./gradlew :tools:maritime-compiler:installDist

tools/maritime-compiler/build/install/maritime-compiler/bin/maritime-compiler \
  --input /path/to/gpkg-folder \
  --output /path/to/new-version.yklpkg \
  --work-dir /path/to/compiler-work \
  --id my-waters-2026 \
  --name '我的海域' \
  --provider '资料来源' \
  --license '资料许可' \
  --attribution '原资料署名'
```

默认 `--products full`。明确使用 `--products facts` 才输出仅有查询事实的包；不能将它宣传为预制三维/航路包。可用 `--bounds west,south,east,north` 限定准备区域，事实库仍保留源文件全部真实内容；区域以外不声明已经准备。超过单卷区域上限要分卷。

编译工作目录有来源身份和文件 SHA-256 标记；完成的事实与原子导航块可继续使用。未完成的事实库会重建，原件不会修改。默认两个独立只读工作者，可用 `--workers 1` 降低内存占用（允许 1–8，更多工作者需要相应 JVM 堆空间）。输出必须是新文件，只有全部请求工作完成后才原子发布；缺空间、源几何错误和不支持的格式明确失败。`catalog.json` 是原子发布的事实完成检查点，不代表所有模型与导航块都已完成。

Android 与桌面编译**同一份** `GeoPackageChartImporter`、`LinzLdsAdapter`、`GeoPackageGeometryReader`、v8 编码器、`ChartTerrainCompiler`、`PassageGeometry` 和 `compilePassageRegion`；`ChartSql` 的两个实现只适配系统 SQLite/JDBC。无第二份 Python 属性解释器。

三维先发布固定地理网格的基础层，运行时按 sourceKey 读取已准备的祖先块，随后按需补充精细层。海面是共享参数化显示，不复制成无数相同模型；未知高程不伪造。导航使用单独的语义面和受约束三角网，显示用简化模型永远不能作为可航证据。

当前桌面入口接入 GeoPackage / LINZ；S-57、GEBCO 在手机导入仍可用，但桌面入口尚不接受它们。编译和来源几何校验属于制作真实资料的过程，不表示已测得手机的查询耗时或帧率。

包协议见 [package-format](../../chart-library/package-format.md)，生产运行时边界见 [领域契约](../../docs/os/02-DOMAIN-AND-CONTRACTS.md#原生资料生产接入2026-10-03)。
