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

`PassageRegionRouter` 先读取同一来源的 nav-v3 产品。`PassageTopologyStore` 只解析已有 CRC 头里的连通分量及门户，跳过深度证据、约束和栅格数组；粗层探索不解压 WKB、不检查整份几何、不运行局部网格寻路。基础 policy 仍为 `base-water-semantics-v2`，不改变已有包的格式、来源或键。

跨区采用分层、延迟边验证：`PassageLazySearch` 先在门户图上产生候选通道，只有候选上的连接进入完整产品和精细网格检查。失败的连接在本次搜索中排除，成功连接复用；选中通道全部验证后即交付，不证明全局最短。连通分量和门户不会按显示分辨率合并，窄水道不因缩小地图而被多数像元抹掉。路径简化仍逐线段检查真实水域及当前条件。

设置吃水时，矢量和纯数值栅格分支均按吃水加最小余深筛选已知数值；船宽/走廊、明确不足的净空和用户避让区仍参与局部检查。缺少数值深度的区域不能声称满足该条件；有数值但缺少垂直基准的参考资料可以筛选，候选仍显示 `INSUFFICIENT`，不伪造 datum 或潮高。未设置吃水时只做明确标注的连通性草稿。所有自动候选继续 `draftOnly`，不隐式开始或替换活动导航，完整分析与导航资格校验保持独立。

基础面路径已经符合条件时，不另建船型网格；确实受浅水或其他条件阻挡时，仅为选中的区域准备独立条件产品。`Query` 没有编译入口，缺失产品以准备需求退出累计搜索期限，由 Core 在 `LOADING` 阶段准备并原子发布，再复用已解码块和已验证连接继续。区域每航段累计协作式搜索预算为 2 秒，单次准备预算 120 秒；预算耗尽是中断，不是“没有水路”。这不是手机一秒性能保证：首次缺块、条件产品准备及不可立即中断的第三方几何调用仍可能耗时。生产日志 `YokuliPassage` 区分 total/search/prepare 毫秒，并记录头读取、完整块读取、局部搜索、查线、展开与细化数量。

实现依据：[HPA* 增强及延迟边权](https://ojs.aaai.org/index.php/AIIDE/article/view/18791)、[LazySP：昂贵边计算的延迟验证](https://ojs.aaai.org/index.php/ICAPS/article/view/13788)、[Theta* 任意角度寻路](https://idm-lab.org/bib/abstracts/Koen10r.html)、[Detour 路径走廊与直线化接口](https://recastnav.com/classdtNavMeshQuery.html)。这里是适配现有精确水域产品的有界草稿算法，不是 Garmin 内部算法，也不宣称实现这些论文的所有最优性保证。

当前桌面入口接入 GeoPackage / LINZ；S-57、GEBCO 在手机导入仍可用，但桌面入口尚不接受它们。编译和来源几何校验属于制作真实资料的过程，不表示已测得手机的查询耗时或帧率。

包协议见 [package-format](../../chart-library/package-format.md)，生产运行时边界见 [领域契约](../../docs/os/02-DOMAIN-AND-CONTRACTS.md#原生资料生产接入2026-10-03)。
