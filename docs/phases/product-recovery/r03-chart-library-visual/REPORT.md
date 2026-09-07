# R03 — Chart Library Visual App 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- 海图库一级产品表面现在明确分为 `覆盖 / 图层 / 视图 / 来源`，而不是把 Source、Asset、Stack 混成一个资源管理首页。
- Coverage 主体是由 catalog 中 logical Layer 的真实 bounds 绘制的空间画布；多文件 Layer 先聚合再显示，重叠通过叠加区域可见，未知/不可用内容保留在 Layer 检视列表而不被假装删除。
- 点击 coverage 区域或 Layer 行会选择对应 logical Layer；选中轮廓、合并范围、可用资源数和 zoom span 同时可见。
- Layer 卡片表达用户地图对象：名称、coverage thumbnail、来源/资源数量、容量、健康、显隐、不透明度与顺序。用户不需要理解 Asset priority。
- View 是 catalog 中的真实持久对象，支持创建、重命名、复制、删除非活动 View、激活、底图选择和逐 Layer 组合。
- 活动 View 的 Composite Preview 调用与 Chart 相同的 Google/离线 renderer、同一 DisplayPlan/stack/opacity；Preview 使用独立 MapState 和独立 snapshot sink，不会移动 Chart 相机或污染桌面 live tile。
- 文件路径、provider、授权、scan、Basic/Full、受管副本等技术操作继续只在 Sources/detail 中出现。

## Replaced presentation

- 删除 composition root 中旧的 Asset/SourceSet display bridge；Chart Library 不再借 Chart 的旧 Asset selection 假装 Layer stack。
- 旧 `STACK` workspace 被真实 `LAYERS` 与 `VIEWS` 分开。
- 旧按钮、固定 Composable 和字符串断言继续列在 `docs/SUPERSEDED_PRODUCT_TESTS.md`，没有恢复为 gate。

## Preserved runtime/domain

- 继续使用 R01 SAF/MBTiles read truth、Basic/Full validation、scan generation、managed copy 和安全删除。
- 继续使用 R02 单 catalog、Layer/View/Active View 事务与 Room v5；没有新增 UI repository 或第二份地图配置。
- Composite Preview 只增加 feature-local transient camera/renderer state；持久地图内容仍只有 catalog 一份真相。

## Design decisions

- Coverage Canvas 只画实际 embedded/derived tile footprint；没有 metadata 时依赖 R01 derived extent，不用文件存在状态冒充 coverage。
- 一个 Layer 卡片聚合一个或多个底层 MBTiles，Sources 才展开文件 inventory。
- Preview 通过 composition slot 由 app-shell 注入生产 renderer，使 Chart Library 保持不依赖 Chart feature 或 Android adapter。
- View 的 Layer membership 直接修改 View 组合；全局 Layer 可见性与某个 View 的组合保持可解释的不同语义。

## Known gaps

- 真机 pan/zoom、Google key 注入、离线 overlay 与已知良好 MBTiles 的像素结果等待 GitHub API 34 artifact 和人工验收。
- Coverage 第一版是矩形/coarse footprint，不宣称复杂 polygon union 或逐 tile 无缝覆盖。
- 人工尚未确认小屏 portrait 下四工作区、Layer 卡片和真实 renderer preview 的触控手感。

## Human stories

待验收：`CL-01`–`CL-09`、`CL-12`、`CL-15`–`CL-19`、`CL-24`–`CL-30`。特别需要实际文件验证：缺 metadata 的 known-good MBTiles 能形成 coverage、Basic 可用、Preview/Chart 同图同透明度、失败 rescan 不抹掉旧 catalog。

## Tests

- 新 projector 行为测试证明 logical Layer 与 Active View 是 UI 的首要地图内容，Source/Asset 仍只用于技术明细。
- 本地 `:feature:chart-library:testDebugUnitTest`：19/19 PASS。
- 本地 app-shell compile：PASS。
- 旧 `ChartDisplayWorkspaceStoryTest` 已删除，因为它锁定被人工否决的 Asset Quick Layers presentation；新 UI instrumentation 要在人工产品验收后按最终旅程重建。

## Git

- R03 product surface：`2a386646ae4262373f76ab56ac61315a39b54b4c`、`7353a36`、`54516d0`
- 真实 composite preview：`aff99c6e936fc9270167fb38b6a584c057fd9483`
- CI report/artifact：本批 push 后填写。

## English summary

R03 replaces the asset-first Chart Library with four product workspaces: spatial Coverage, logical Layers, durable Views and technical Sources. The active View preview uses the exact production Chart renderer and DisplayPlan with an independent preview camera. CI and human APK acceptance remain pending.
