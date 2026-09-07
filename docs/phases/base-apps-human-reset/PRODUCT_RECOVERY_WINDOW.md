# Yokuli OS 基础应用 Product Recovery Window

## 决定

从基线 `b30d12fa8be04a5b41edb34abde8d7df479298c7` 起进入 Product Recovery Window。

人工验收已否决 Chart、Chart Library、Navigation 的现有 presentation/interaction structure，并否决 Data Flow 的文字列表式可视化。此前绿色 CI 只能证明旧合同，没有产品批准效力。

当前人工合同来源为仓库所有者提供的 `Yokuli_OS_Base_Apps_Human_Acceptance_Reset.md`，SHA-256：

```text
c0443aea42619e56ca3aebbc8379949a411414e94025a1ddbb90de98d560d3d5
```

## 保持硬门禁

- domain model 与数学，包括 geodesic、navigation math、tile coordinate；
- NMEA parser、framing、checksum、freshness、source selection；
- persistence、schema migration、process restore；
- MBTiles/SAF 数据读取与资源身份；
- runtime lifecycle、线程、并发、有界资源与安全边界；
- Shell Engine 的串行 reducer、二维 Start Document、drag/resize、session、Back 和 durable restore；
- Kotlin/JVM tests、lint、Debug/Release compile、release surface、API compatibility 与 CI/report machinery。

这里的 Kotlin/JVM 与 instrumentation 门禁采用显式清单，不等于执行所有模块中的旧测试。Chart/Chart Library/Data/Navigation Feature presentation suites 在恢复期不进入硬门禁；core 与 adapter 测试继续运行。

## 暂时退出产品 Gate

以下测试作为历史证据保留，但在对应 App 通过人工验收前不参与最终质量判定：

- 断言旧 Chart page/tab/button/composable 或旧 journey 编号的 presentation contracts；
- 断言 Chart Library 必须以 Sources/Assets/Facts 为主结构的 contracts；
- 断言 Navigation 必须包含 `RouteSketch`、列表上下按钮或旧 CRUD editor 的 contracts；
- 断言 Data 必须保持旧五页文字列表 UI 的 contracts；
- W15/W16 及上一轮 Chart/Shell UX 静态 Gate 中绑定已否决 UI 形状的部分。

CI 禁止再用 `unittest discover test_*.py` 或根工程 `./gradlew test` 偷偷重新执行这些退出的静态脚本/Feature presentation tests。退出 Gate 不删除历史文件、不删除底层 behavior tests，也不把未验收 App 标成通过。

## 新测试进入时机

正确 UX 落地后，才为真实用户旅程补 behavior/instrumentation：

- Chart A/B 直接拖动、Quick Mark、地图 route planning 与 active navigation；
- Chart Library Coverage、logical layer、Stack、opacity/order 与 composite preview；
- Data instruments、真实 topology、node drill-down 与稳定 live update；
- Navigation 真实 planning/active map；
- Recents Close、二维 tile、语言和跨 App truth。

这些测试验证用户操作与可见结果，不规定类名、Composable 名、页面层级或内部实现算法。

## 发布语义

Product Recovery Window 内即使所有自动化通过，APK 也只能叫 `PRODUCT-RECOVERY` / `HUMAN-ACCEPTANCE-PENDING`。禁止发布名为 `VERIFIED` 或声称产品已验收的 artifact。

语义 tag 的签名发布在恢复期暂停。性能工作流只保留与被否决 UI 结构无关的冷/热启动信号；原 Stage 11 页面旅程保留为历史测试但不再阻塞恢复包。

关闭 Recovery Window 必须由仓库所有者对某个 App 明确给出人工批准；随后才为该 App 恢复新的 Human Acceptance Gate。

## English translation

During the Product Recovery Window, core domain, math, protocol, persistence, migration, resource-reading, lifecycle, concurrency and safety tests remain hard gates. Static tests that prescribe the rejected Chart, Chart Library, Navigation or Data presentation are retained only as history and are not authoritative. New UI automation is added after the real user journey exists. Automated green evidence produces a human-acceptance-pending recovery APK, never a product-approved or fully verified artifact.
