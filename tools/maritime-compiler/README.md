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

三维先发布固定地理网格的基础层，超出单块预算的复杂区域继续细分，只有完整子块发布，不伪造 READY 父块。运行时按 sourceKey 读取已准备的祖先块或细分子块，随后按需补充精细层。地形规则 `terrain-7` 使用共享稳健几何叠加，去掉裁块边缘形成的假海底竖墙；旧显示产物失效不改变资料事实。海面是共享参数化显示，不复制成无数相同模型；未知高程不伪造。导航使用单独的语义面和受约束三角网，显示用简化模型永远不能作为可航证据。

### 运行时航路产品的使用

`PassageRegionRouter` 先按相同来源、规则和 policy 读取包内或之前准备好的 `.nav`，不在读取缓存前执行一次 `geometry.world()`。没有用户避让区时，连通性草稿直接使用 `base-water-semantics-v2` 产品；不为中性船型再压缩、写入一份相同网格。基础产品键和文件格式未变，已有可用产品继续复用。

同一区域连通分量内的路径优先在局部网格求解并完整复核后交付；跨区域搜索优先交付首条可连接目的地的草稿，不为证明全局最短继续扩展。门户候选由固定起终点确定，已知代价的下界先剪枝，再运行局部网格搜索。路径简化先检查终点捷径，失败后仅向前尝试最多 32 个候选点；每条捷径仍检查精确水域，没有降低几何精度。

真正缺失的区域仍由 Core 在持有快照的作业中准备并原子缓存，进度明确显示“准备缺失导航块”；这条首次准备路径仍可能耗时，不能把仅有事实、缺少所需预制块的包宣传为秒出航线。连通性草稿仍是 `draftOnly`，不证明满足吃水或净空；完整航线检查的证据要求不变。上述读方优化不是已验证的手机一秒性能指标。

当前桌面入口接入 GeoPackage / LINZ；S-57、GEBCO 在手机导入仍可用，但桌面入口尚不接受它们。编译和来源几何校验属于制作真实资料的过程，不表示已测得手机的查询耗时或帧率。

包协议见 [package-format](../../chart-library/package-format.md)，生产运行时边界见 [领域契约](../../docs/os/02-DOMAIN-AND-CONTRACTS.md#原生资料生产接入2026-10-03)。
