# Yokuli 最终功能收尾 Phase：WP8 三档磁贴与地图 V1

**版本：1.0 · 审阅日期：2026-09-06**  
**目标仓库：`ohkuku/yokuli_marine_shell`**  
**工作分支：`codex/shell-map-contract`**  
**本次实查基线：`9579761a3c7c5cf13735dfecaeb38636b715b695`**  
**状态：施工任务，尚未完成；本文件不是测试通过、真机认可或发布证明。**

> English translation: Complete the existing in-app Marine Shell's classic three-size tiles and offline-first Chart V1. Preserve the Kotlin/Compose shell, installation boundary and existing working interactions. First close the current CI and data-loss defects, then deliver complete map workflows, package lifecycle, GPX, real offline coverage and read-only data degradation. Work continuously through C00–C12 with behavioral evidence, without per-stage owner approval. Do not merge, release, invent map data, restore extended tile shapes, or implement production NMEA/GNSS/navigation runtimes.

---

## 0. 给 Codex 的执行入口

### 0.1 直接执行，不再重新做一套架构方案

本轮要求是：**继续现有分支，将已经搭出的地图骨架变成可独立使用的产品，并把三档磁贴修正真正收尾。**不要再次只交需求分析、Demo、空接口或完成度百分比。

先核对真实 HEAD、工作树、现有任务和 CI，再按 C00–C12 连续执行。本文的 SHA 是审阅锚点，不是 reset 的目标。有新提交则做增量核对；已正确完成的功能只回归，不制造重复实现。保护任何未提交人工修改。

建议将本文件保留原文件名，放入 `docs/phases/chart-wp8-refinement/`，复用该目录已有 `WORK_LOG.md`。只再维护一个精简 `EXECUTION_STATE.json` 和最终 `ACCEPTANCE.md`。不要为每个小字段创建报告，也不要复制一套 Stage 0–11。

配套 `TASK_PLAN.json` 含13个工作包与104个待验收逻辑场景，只是依赖和测试索引；104不是已执行或已通过的测试数量。本文定义产品行为；JSON、探针和日志都不能自证完成。`probes/` 是针对本次基线的诊断工具，修复后应转换为正式“不再发生”的回归测试，而不是长期要求输出 `BUG_REPRODUCED`。

### 0.2 本轮覆盖旧文档的冲突项

| 冲突 | 本轮有效决定 |
|---|---|
| 旧地图主规格或 Shell 文档允许 2×1、2×4、4×4 | **全部作废。正式尺寸仅宽×高 1×1、2×2、4×2。** |
| 旧规格要求 ChartLargeTile、六档缩放 | 改为三档内容和三档循环的行为验证，不恢复错误形状迁就测试。 |
| 旧文本要求所有 App 根页大标题 | 地图根页以地图为主，不常驻大标题；列表和详情页保留 WP 大标题。 |
| 旧文本暗示 Android Home/default launcher | 产品仍是全屏应用内 Shell；Android Home 离开应用，内部 Bridge 回桌面。 |
| 旧切片只要求地图“基础状态/数量” | 本轮要求可进入、操作、保存、重新打开、修改、取消、恢复的完整流程。 |
| 旧报告描述重叠小磁贴控件按最近中心猜点击 | 保留当前已做的独立、不重叠触控区域，不倒退回重叠命中。 |
| 原则性“保留一切旧格式” | 用户允许无历史数据负担的重构；不做庞大历史迁移工程，但任何运行时读取异常不能被当成空库后自动清空资料。 |

在 README、CONTRIBUTING、TDD_PLAYBOOK 和当前有效计划的入口处消除上述指令冲突。历史日志与真实 WP 参考证据只保留追溯，不改写成新实现的成功证明。

### 0.3 不变边界

- 保留 Kotlin＋Compose、现有 Shell Engine、主题、窗口安全区域、`InstalledAppBinding` / `InstalledAppRegistry`。不引入 Unity、不重写第二套桌面、不全仓库 rename。
- 一级生产入口仍为 **地图、设置**。地点、航线、海图包、导入轨迹都在地图内部，不再建独立空 App。
- Shell 核心不认识 Chart、地图 SDK、GPS 或 NMEA。Chart 自己管理工具、资料和会话。
- 地图无网、无定位、无船网仍可浏览已准备图面、选点、测距、保存地点、规划和导出。
- 本轮不实现生产 NMEA 收发、手机定位采集、实际导航推进、到达报警、锚警、航程记录、AIS、测深或自动舵。**只读观测消费接口、NoSource 和故障测试要完成。**
- 不从旧 NMEA App 大段搬运耦合 runtime；不把地图预览变成开始导航。生产不注入测试位置。
- 不擅自合并 main、发布 Release、改生产密钥或强推历史。当前任务可在目标分支连续提交。

---

## 1. 本次审阅结论：什么能留，什么还没闭环

### 1.1 已有且应保留的基础

1. `MarineTileSize` 已只有三档，Chart 已声明三档，Settings 已声明小/中两档；旧尺寸名称已有持久化边界转换。不是还没开始修改尺寸。[E01–E03]
2. App 自己提供磁贴内容、Shell 负责排列/编辑的安装边界已存在。主屏连续拖动、重排、覆盖层控件已有实现和历史回归，不应借地图施工重做。[E03、E04]
3. 已有纯 Kotlin `core:map-domain`、地图 Store、Proto 存储、SAF 海图导入、MapLibre Native 本地栅格路径；**不能笼统把当前地图说成只有假 Canvas。**[E05–E10]
4. 已有选点、保存地点、测量点集、单一路线草稿、路线 undo/redo/反向/保存副本、包启用/删除的基础代码；主要问题是功能未连接成可用流程，以及错误/异步/持久化不完整。
5. 生产没有假 GPS；持久化不把旧位置恢复为实时位置，这个边界要保留。[E05]

### 1.2 当前可交付性

| 用户需要 | 目前实际状态 | 本轮动作 |
|---|---|---|
| 三档磁贴及直接桌面编辑 | 三档生产定义已改；旧六档测试/契约仍失败 | 收尾测试、迁移回归、真实触控回归；不重新造编辑器 |
| 打开真实地图并稳定恢复 | 有 MapLibre 本地路径；也存在按 Google key 分叉的第二生产路径；相机命令/就绪/错误闭环不足 | 一个主渲染路径，真实图面与恢复证据 |
| 地图布局与操作 | 大标题、多个工具标签、固定高度面板叠在地图上；方屏准星只是图形 | 重新整理层级与操作模式 |
| 测距离和方位 | 有点集，面板只显示点数及转路线入口 | 真正的距离、真方位、编辑、撤销、精确放点 |
| 收藏锚地/上岸/补水点 | 能按自动名称写入，地点页仅数量 | 列表、详情、名称/备注/分类、定位、修改、删除/撤销、搜索 |
| 手动路线 | 单草稿、部分几何操作、保存副本；没有完整的保存后重开编辑流程 | 草稿库、正式计划、预览、编辑、保存、复制、反向、计划速度 |
| 海图包管理 | 文件复制/摘要/清单/启用删除初步可用 | 真正验证、取消、版本替换/回退、使用租约、崩溃恢复 |
| GPX / 分段轨迹 | 本次检查的生产链路未发现实现 | 新增受控导入导出与只读轨迹 |
| 沿线离线资料检查 | 有 bounds 等包信息，没有完整覆盖算法/产品流程 | 按真实 tile/index/版本/级别计算，不能 bbox 冒充完整 |
| 无源/断流 | 有粗略 position fresh/stale 模型；不是完整消费端 | 独立质量、单调时钟、历史标记、跟随意图、无输出边界 |
| 地图磁贴 | 小/中图标与标题，宽磁贴为口号和数量 | 真实会话/草稿/计划摘要与低成本历史预览 |
| 完整验证 | 当前 HEAD 的主 CI 红；设备链路被跳过 | 修复并跑同一 SHA 的完整门禁 |

### 1.3 已确认的阻塞与风险

**F01｜交付阻塞：尺寸修正没有同步结束测试。**

- `MarineTileContractTest.tileSupportsExtendedMarineSizes()` 仍断言六个尺寸。
- `test_launcher_stage7_contract.py` 仍查找 `sixSizeResizeCycleIsExactAndEveryStepIsImmediate`。
- `test_shell_app_contract.py` 仍要求已删除的 `ChartLargeTile`。
- `TileEditingRegressionTest.kt` 仍用 `MarineTileSize.entries[random.nextInt(6)]`，三元素枚举会越界。
- `WORK_LOG.md` 为纯英文，触发仓库中文优先文档门禁。

这些分别修复，不删除整组回归、恢复六档、给 CI 加忽略或用空测试占位。[E11]

**F02｜P0：读失败可能变成写空库。**

`DefaultMapStore.restoreBeforeUserActions()` 捕获读取异常后按无快照继续；后续相机变化等事件可以保存默认个人资料集合。另有 `ReplaceFileCorruptionHandler { defaults }` 对包含地点/路线的整份 Proto 做破坏性替换。读失败、数据损坏、新安装必须是三种状态。探针 B02 复现前者；后者为源码确认的恢复策略，不冒称已在 Android 上注入磁盘损坏。[E06、E07]

**F03｜P0：测量转路线覆盖原草稿。**

先创建尚未正式保存的路线，再测量其他点并“转换为路线”，`convertMeasurement()` 直接替换唯一 `routeDraft`，原稿没有归档或确认。探针 B01 复现。[E05]

**F04｜P1：地图输入被保存串行阻塞，过载抛给 UI。**

Map Store 的唯一动作处理器内等待 `persistence.save()`；慢写期间工具切换排队，超过 256 待处理动作直接 `check` 抛异常。不能简单改成无限队列。这里确认的是 **地图动作队列**，不能据此声称整个 Shell 按键一定被同一队列阻塞。探针 B03/B04 复现。[E06]

**F05｜P1：取消不是取消，旧结果会回来。**

包协调器取消只改 UI 状态，没有取消在途任务或淘汰旧请求代次。旧 inspect 返回后重新打开编辑页。安装/新导入也缺少相同的竞争保护。B05 复现 inspect 场景；安装竞争仍需正式测试。[E09]

**F06｜P1：地图相机与恢复状态缺少双向协议。**

本地 MapView 初始化时读取一次 camera，后续主要把 idle 相机回写。没有清晰的恢复/“查看地点”/“全线总览”命令代次与 ack；异步恢复晚于 MapView 初始化时存在相机不跟随恢复、初始 SDK 回调反写默认相机的风险。此项为代码路径分析，尚未在真机复现。Style/MapView 回调也缺少完整代次淘汰。[E08]

**F07｜P1：工具/页面/选择混为一类，返回不经过地图内部。**

`MapTool` 同时有 BROWSE、MEASURE、MANUAL_ROUTE、PLACES、CHARTS；`ChartWorkspace` 和 Shell 的 Back 没有完整的 feature-first 返回协议。选择只保存坐标，不是可操作的对象 ID。持久化丢失 active tool/selection，B06 复现。不能再靠增加更多标签解决。[E04、E05、E10]

**F08｜P1：海图“安装通过”不等于图面可用。**

仓库导入检查只接受 `type='table'`，对 tile 主要做存在性检查。仪器测试里的所谓 raster fixture，其 `tile_data` 实际是单字节 `0x01`，不是 PNG。当前测试证明不了图像解码、投影或实际渲染。标准允许表/视图，不能把合法视图格式当坏文件。缺少详细 zoom 元数据不等于一定不兼容，应从实际索引推导。[E08、E12；S01]

**F09｜P1/P2：路线/测量数学和计划输入不满足产品合同。**

路线汇总使用球面 Haversine，图上原始点直接连线，没有统一的 WGS84 椭球大地线/加密/日期线处理。默认 5 kn 在用户没输入前产生计划用时。B08/B09 复现默认值与数学差异；B09 说明合同不一致，不把球面近似本身夸张成即时航行事故。已有 `GeoBounds` 只接受 west≤east，也不足以描述最小跨日期线范围。[E05]

**F10｜未来消费端 P1：位置质量测试仍不够。**

未来时间戳可因负年龄被判 FRESH，B07 复现的是注入测试观测；当前生产没有真的采集 GPS。position stale 与 fresh 的图形表达、无消息自动过期、Heading/COG 独立性均需完成，不能继续扩大当前简单模型后直接接船网。[E05、E08]

### 1.4 本次实际执行的证据及局限

| 检查 | 结果 | 证据 |
|---|---|---|
| 当前远端分支 HEAD | 9579761… | [E00] |
| 当前主 CI | run 33961941146，completed / failure | [E13] |
| 当前 CI 的设备/性能后续 jobs | skipped | [E13] |
| 下载当前 SHA 的报告 | 地图领域 12 tests / 0 failure；尺寸测试失败 | `evidence/hosted-test-report-index.json` |
| 本地独立编译并运行现有地图领域测试 | 12/12 通过 | `evidence/existing-map-domain-tests.txt` |
| 本地运行现有 MarineTileContractTest | 3 个，1 个失败 | `evidence/existing-marine-tile-tests.txt` |
| 本地执行三个相关 Python 契约方法 | 三个均失败，原因见 F01 | `evidence/targeted-python-contracts.json` |
| 针对当前源码的诊断探针 | B01–B11 均复现各自描述的缺口/异常路径 | `probes/` 与 `evidence/baseline-probes.txt` |

本地使用 Kotlin 1.9.0、JDK 21（`-Xjdk-release=17`）、兼容的本地协程 jar、JUnit 4.13.2。它是隔离验证，**不是仓库 Kotlin 2.1.20/Gradle 的完整 Android 构建**，更不是设备渲染/触控/性能验收。审阅使用与 HEAD 一致的源码快照；其导出规则不包含所有图片/二进制，因此不因快照缺图就断言整个 Git 仓库缺该素材。

CI 中存在 `continue-on-error` 以收集结果，step 的 conclusion 可能显示 success，但真实 outcome/test report 为 failure，最终 gate 正确阻止候选发布。不要误判为“所有测试通过只是最后一步坏了”，也不要一刀删除这种诊断机制。[E13]

---

## 2. 用户工作流：后续实现必须以这些链路为主

### W1｜开地图，不是先面对配置和报错

桌面点地图 → 恢复上次视野和已选资料 → 可以立即浏览。没有 GPS/NMEA 不弹红色故障条，不画假船。没有网络但已准备图面时，功能照常。没有当前区域图面时显示“本区域暂无图面”，提供“导入海图”“查看已安装区域”和真实可用的资料获取入口；不能只留一个永远空白的工作台说完成。

已安装海图可以明确点“查看范围”定位。普通打开、切包、恢复网络不能擅自 fit-all。首次无任何会话时可使用标明为浏览区域的默认相机，但绝不能把默认奥克兰中心当作用户船位。

### W2｜发现一个锚地/上岸点，保存下来还能用

浏览 → 长按空白处选点 → 短摘要显示坐标 → 保存地点 → 名称、分类、备注 → 有持久化确认 → 地点列表可查 → 点详情里的“在地图显示” → 移动/改名/删除/撤销。地点坐标变化不会悄悄修改使用过它的正式路线。

### W3｜迅速知道有多远，方向是什么

从选点或工具菜单进入测量 → 固定 A → 移动地图准星确定 B → 显示距离与 A→B 起始真方位 → 可加入第三点、多段累计 → 选任意点调整/删除/undo/redo → 退出保留本次测量；“清除”才删除。转换为路线生成新草稿，不丢另一条草稿。

### W4｜出发前规划，而不是伪装导航

地图选起点或从已有地点开始 → 添加途经点 → 精确移动/插入/删除/排序 → 输入计划船速（可暂空） → 查看每段及总距离、计划用时 → 保存为命名计划 → Bridge 回桌面 → 再开地图或航线列表 → 预览同一条路线 → 编辑得到草稿 → 保存覆盖该计划或另存副本。预览/反向/点磁贴均不启动航行任务。

### W5｜为出海准备离线资料

导入真实可读图包 → 知道来源/版本/资料限制 → 查看覆盖与图面 → 选个人计划与备用地点 → 检查某一级别及两侧宽度的本地图块 → 明确看见缺失范围 → 对真正支持获取的来源下载/重试；否则导入替换 → 重算。断网、空间不足、更新失败、取消和进程退出不能删掉原来的好包或个人资料。

### W6｜交换、恢复与异常

导入 GPX 先预览对象类型/数量再确认；导出以系统保存成功为准。后台/Bridge/旋转/语言切换不丢已确认编辑。数据源中断仅改变相关观测状态，不清空地图和计划。损坏/读写失败可诊断、重试或导出可读资料，不用静默重置掩盖问题。

### W7｜桌面一眼有用，而不是地图里再造桌面

1×1 是入口图标；2×2 是当前真实任务的一句话摘要；4×2 是上次视图/计划小图＋名称＋一条状态。无内容就稳定图标/文字，不造假地图。缩放与重排仍在主屏完成；磁贴不创建持续 MapView，也不采集定位或下载。

---

## 3. 信息层级与状态所有权（先按这套收敛）

### 3.1 对用户只保留一个地图根页

```text
内部桌面
  地图
    地图根页：浏览 / 测量 / 路线编辑 / 地点位置编辑
    更多
      地点 → 详情 → 在地图显示或编辑
      航线 → 已保存 / 草稿 → 预览或编辑
      海图与离线 → 包 / 来源 / 工作任务 / 沿线检查
      导入 / 导出 → GPX 与文件选择
      已导入轨迹 → 只读详情和地图展示
  设置：Shell 外观、语言、通用偏好
```

“海图图层开关”是地图短面板；“包管理”是资产管理内页。不要混成同一排顶层标签。航线/地点不是额外 App，轨迹不是正在运行的航程。

### 3.2 最小状态拆分，不按组合创建上百个类

| 状态 | 所有者 | 规则 |
|---|---|---|
| Shell surface/task/token | Shell | 只认不透明 ID/token，不读 RoutePlan |
| ChartSession | Chart store | 稳定会话 ID；相机、图层、选择、正在查看的资料 |
| Surface | Chart | Map / Places / Routes / Packages / Details；内部返回来源明确 |
| Tool | Chart | Browse / Measure / RouteEdit / PlacePositionEdit；同一时刻最多一个 |
| Transient | Chart | 菜单、确认、表单、坐标编辑；记录 return target |
| Document | Repository | Place、RoutePlan、RouteDraft、ImportedTrack 各自稳定 ID/revision |
| SaveState | Repository→Chart | 当前对象 revision 的 Pending / Saved / Failed，不是全局“已保存” |
| Camera/gesture visual state | Renderer | 每帧本地反馈；idle/确认边界回写，不逐帧保存全库 |
| Package catalog/jobs | Package repository/coordinator | 一个真源，不在 Map Proto 复制另一个权威清单 |
| Observations | 只读端口 | 位置与 Heading/COG 质量独立；默认 NoSource |

对象选择至少含类型与 ID：Place / DraftPoint / SavedRoute / Track / 临时坐标。Raster 像素没有结构化属性时不能假装查询到航标、水深对象。

### 3.3 返回、Bridge 与输入优先级

Back：**IME → 顶层弹窗 → 取消当前未提交拖动 → 退出当前工具并保留已确认草稿/测量 → 返回内部来源页 → 地图根 Browse 返回桌面**。

Bridge：取消未确认手势、结束输入焦点、保留已确认内存状态并调度保存，立即回内部桌面。**不等待磁盘完成，不清空草稿，不把 Pending 写成 Saved。**恢复地图回到合理的上次任务；不恢复未完成手势或重复弹出文件选择器。

Search：沿用 Shell 搜索入口和原子转场；本地地点/路线结果以只读不透明目标打开。搜索后返回恢复原工具，不能未经确认把结果追加成路线点。按键消费通过现有 host/binding 的窄接口实现，不让 Shell Reducer import Chart。

### 3.4 地图 UI 默认规则

正常竖屏只常驻方向指示、比例尺、图源署名与少量主动作。底部地图工具为“图层、测量、规划、更多”；方屏根据可用尺寸折为“图层、工具”。激活工具后替换本工具栏，不叠加第三排工具。Shell 的 Back/Bridge/Search 不在地图内复制。

图根页移除大标题与无用点数说明，列表/详情才用 WP 大标题。短摘要默认收起，完整编辑进入可滚动内页。用现有主题/字重/Accent/图标，不采用一套无关的 Material 卡片设计。栅格图面不盲目反色；夜间 Shell 和海图配色状态分开。

所有操作 hit target 至少 48dp，互不重叠；glyph 可以小。安全区域只消费一次。把地图真实遮挡矩形/padding 提供给 renderer，不能沿用固定 96/150/230dp 估计。320×320、360×360 dp、竖屏、横屏、fontScale 1.0/1.3/1.5 与 IME 都需要布局测试，不因方屏裁掉“确认/取消”。

---

## 4. 施工顺序与批次

一个最终 Phase，**不增加逐个等用户批准的 Stage**。C00–C12 是内部工作包。

| 批次 | 工作包 | 阶段结束时用户真正能做什么 |
|---|---|---|
| A：修正基线与地基 | C00–C03 | 三档尺寸回归通过；不再静默丢资料；真实地图能恢复；层级和按键正常 |
| B：可用的规划工作台 | C04–C08 | 选点、测距、地点、路线、真实海图包与 GPX 操作完整 |
| C：出海准备与交付 | C09–C12 | 实际离线检查、数据断流表达、有用磁贴、同 SHA 自动验证与可测试 APK |

推荐串行 C00→C01→…→C12。允许对不共享写文件的独立任务并行审查，但必须由一个写入协调者串行整合。C07 可在 C02 基础上提前并行研究；C10 不需要等待生产 NMEA。

---

## C00｜结束三档磁贴修正，建立可信的当前基线

**依赖：无。对应旧需求 MAP-R03/R15/R19/R20。**

### 改哪里

`MarineTile.kt`、`MarineTileContractTest.kt`、`TileEditingRegressionTest.kt`、`EditInteractionTest.kt`、`LauncherProtoMapper.kt`、Chart/Settings contribution、相关 Python 契约、当前文档入口。[E01–E04、E11]

### 要做什么

1. 保留现有三档 enum 和别名迁移；Chart renderer keys 必须严格等于 {1×1,2×2,4×2}，Settings 等于 {1×1,2×2}。默认 Chart 宽、Settings 小。
2. 把六档断言改成明确的三档产品规则，并加入拒绝扩展尺寸回归；不要只把字符串改名就算测试了行为。
3. 随机尺寸取值用实际集合大小，参数化覆盖三档与 4/6/8 列，不减样本掩盖越界。
4. 旧 COMPACT→中、TALL/LARGE→宽；验证 tile ID、entry ID、rank、group、spacer 和未受影响条目保留，二次读写幂等。现有桌面不用清空。
5. 修正文档中英文格式；精确替换因产品规则变化失效的文本 gate，保留注册边界、独立内容、直接缩放、无确认勾和触控回归。
6. 获取真实失败日志，不只看 job step conclusion。完整执行 core:shell-engine，即使另一个模块失败也能拿到报告。
7. 在后续功能测试完成前不要把“当前基线已绿”当成地图完成；C00 只是恢复可工作的基线。

### 必测

- 三档精确枚举、Chart/Settings 声明与内容键匹配；单档 App 不显示缩放。
- Chart 小→中→宽→小，每次一次点击即提交，图标不变确认勾；Settings 小↔中。
- 三档混合拖放、预排与 drop 一致、边缘自动滚屏与取消、最小磁贴控件真实点击。
- 旧布局别名迁移保留身份与排序，未知值受控，不清空整个桌面。
- 修复后的现有 Python 契约、JVM 测试和 APK 编译；测试路径变化有替代行为映射。

**完成出口：**实际全仓库 JVM/相关契约绿；无六档生产入口；尺寸迁移和触控保留证据。不能仅以 enum 已改关闭任务。

---

## C01｜先堵丢数据与动作队列故障，再继续加资料功能

**依赖：C00。对应 MAP-R02/R08/R17/R18。优先级：P0/P1。**

### 改哪里

`MapStore.kt`、`MapModel.kt`、`MapReducer.kt`、`adapter:map-storage`、`ShellViewModel.kt` 中地图组装与错误反馈。[E05–E07、E10]

### 产品与存储决定

- 地点、正式路线、草稿、导入轨迹使用事务型用户资料仓储，优先在现有 `adapter:map-storage` 中采用 Room；会话/偏好可以保留 DataStore。不要把全部点集、undo 和包清单塞回每次相机保存的 Proto。
- 允许当前无历史数据的开发格式切换，不做全量旧格式兼容。但“存储损坏/读 I/O 异常”不能当成新安装；不能用 destructive migration 或 ReplaceFileCorruptionHandler 清掉用户资料。发现已有有效数据时先保留/导出，或做明确的一次性导入，不擅自丢弃。
- 只保留一份权威 catalog、一份权威草稿/计划；session 引用对象 ID/revision，不复制整个库。

### 必需行为

1. `NotLoaded / Loading / ReadyEmpty / Ready / ReadFailed / Corrupt` 区分。未成功装载用户库时禁止把默认集合覆盖已有存储；允许用户浏览空图，但写操作明确不可用或暂存于不覆盖原库的隔离草稿。
2. 初始恢复使用独立状态；与地图 ready/包刷新竞争时，不被默认相机事件反向覆盖。
3. 资料稳定 ID 使用注入式 ID 生成器，不能用当前列表 size+1；时间与 ID 可测试。每个资料/草稿有 revision；编辑正式路线带 base revision。
4. UI 对已确认编辑乐观反馈，显示 Pending；写入成功 ack 对应 revision 后才 Saved。旧 ack 不能把更新的一版标为已保存；错误保留编辑并提供重试/导出。
5. 持久化 writer 与交互 actor 解耦。相机重复变化可合并；Save/Delete/Cancel/最终 drop 不能随意丢弃。队列背压可观察、有界，不能直接抛异常给 UI，也不能无限增长。
6. 消费器不会因一次 malformed action/仓储错误/effect logger 错误永久死亡。CancellationException 正常传播；close 后的迟到回调忽略或 typed rejection，不让 UI 崩溃。
7. 草稿切换/测量转路线不能覆盖唯一草稿：先保留原草稿，创建不同 draft ID；写失败仍保留两份内存编辑，正式资料不变。
8. 保存、取消、删除、重复点击按 command ID/revision 去重；Undo 按对象 ID/revision 操作，不把旧快照覆盖后来编辑。
9. Undo/redo 历史有上限，避免每点大量整份 List 复制；长期存的是当前草稿，不把无限历史写入数据库。
10. 默认日志不包含精确位置、备注、文件 URI、token。错误 UI 说明操作及重试方式，而不是只打印 Android Log。

### 必测

- B01/B02/B03/B04 的反向回归；真实仓储读失败、磁盘写满、损坏、未来 schema 与单条坏记录。
- Load 失败后相机/包刷新/切工具都不能改原库；错误恢复后读取原资料。
- 保存 A、继续编辑 B、A 的 ack/失败迟到时，B 的状态正确；快速两次 Save 只一次正式提交。
- 慢保存期间地图交互与内部 Bridge 响应；多次相机变化只保留最新合法值，最终 drop 不丢。
- process kill 在 durable ack 后恢复同 revision；ack 前测试诚实标未保证，不宣称零丢失。
- 删除一项与并发新建不会复用 ID；异常不会杀死下一次动作处理；cancel 不报普通失败。

**完成出口：**无静默清库/覆盖草稿路径；保存状态可以从 UI 和真实仓储核对；与相机无关的资料不在相机事件中整库重写。

---

## C02｜把现有 MapLibre 路径做实，不再增加第二套地图

**依赖：C01。对应 MAP-R01/R02/R04/R10/R11/R17/R19。**

### 改哪里

`OfflineMarineChartSurface.kt`、`ProductionShellGraph.kt`、`MarineChartSurface` 接口、package renderer 绑定与真实 SDK 测试。[E04、E08、E10]

### 主路径决定

继续验证并完善当前 MapLibre Native（基线版本 13.4.1）的本地 raster 路线；**不要因为看到 `mbtiles://` 就认定它是假 API，也不要未经证据再加 PMTiles 转换或 loopback 服务。**官方项目有本地 MBTiles 使用记录，但当前精确版本/栅格/Android 生命周期仍必须在本项目测试证明。[S02]

生产采用一个主交互地图 renderer。删除“有 Google key 就自动走另一套操作路径”的隐式分叉；Google adapter 可以隔离留作历史代码或明确独立能力，但不能继续拥有第二套缺项不同的生产地图流程。非必要不升级地图 SDK。

### 要做什么

1. 用真实 PNG/JPEG 构造小 MBTiles，至少含非对称地物/方向/zxy 标记，导入→验证→图面真正绘出→点线叠加。单字节 fixture 移入“损坏瓦片应被拒绝”的负例。
2. 另验证一个合法真实资料小区域，记录来源、用途限制、处理过程、hash。不能只用自画网格证明用户有可用海图；也不能上传无再分发权的原图到公开 artifact。
3. 首屏不要求 Google key 才有实地图能力。必须有可执行的真实图面导入/获取路径；在线交互底图如有，只能按核实许可接入同一 renderer。空坐标底板仅是无图时的诚实状态，不能作为完整地图交付。
4. 最窄 renderer 协议包含：ready/error、camera command＋revision/ack、camera idle、project/unproject、稳定对象 ID 的 overlay、命中查询、真实遮挡区域、必要时一次性 snapshot。SDK 类不得泄漏进纯 domain。
5. 分开 HostReady（允许转场）、RendererReady（可接受命令）和 Tile/Coverage 状态。不能等网络图块才能退出转场，也不能把 style loaded 当图块齐全。
6. 首次恢复前不向旧 session 反写默认 SDK camera。每条程序化 camera 命令有 ID；相机 echo 不循环保存；旧 MapView/style/package generation 的回调丢弃。设置切换、卸载、快速开关地图后也成立。
7. 支持真实 pan/pinch/rotate、北向复位、比例尺、“查看地点”“查看路线全貌”“查看包范围”。这些动作以 viewport/padding 为依据，不把目标放到面板下面。
8. 错误或丢失包时显示可操作状态并保留用户点线；单块坏图不能杀整个地图。过度放大和无细节可表达，不能无限加载。
9. 一个生产 MapView；隐藏时暂停/释放昂贵资源，保留会话。生命周期 start/resume/pause/stop/destroy/低内存继续正确；转场未允许 heavy content 时不因“无 key”提前挂载原生地图。

### 必测

- 真 renderer 断网冷启动，清除 incidental online cache，只留导入包；验证可区分图块内容，而不是只断言 View 存在。
- TMS/XYZ、经纬度顺序、透明边、边界瓦片、不同 tile 尺寸、缩放/旋转、纬度范围、日期线。
- 恢复延迟→SDK 先 ready；SDK 延迟→恢复先完成；两种顺序最终相机相同，无默认相机覆盖。
- A/B 快速切 style/包，A 回调晚到不会污染 B；dispose 后回调无副作用。
- 50 次地图↔Bridge↔Search，native 实例计数不持续增长；空包/无包时按键可用。

**完成出口：**用户能真实浏览一个合法准备好的图面，文件/图面/投影/会话三者一致；写一页 renderer/source 决策与证据，不开展新的 GIS 平台项目。

---

## C03｜收敛 WP 地图层级、按键与方屏交互

**依赖：C01、C02。对应 MAP-R02/R03/R17。**

### 改哪里

`ChartWorkspace.kt`、Chart 内部页面/工具状态、`ChartUiContract.kt` 旧字段、`ProductionShellGraph.kt`、`ShellActivity.kt` / host 输入协议。不要动纯 Shell 引擎的 App 无关性。[E04、E10]

### 要做什么

- 实现第 3 节的 Surface/Tool/Transient 分离；清理旧 `DEMO / GOOGLE_MAPS`、`mapConfigured` 作为地图就绪的错误替代，但不要为了命名整仓库重写。
- 地图根页去大标题、去“地点/海图作为互斥工具标签”；默认图面最大化，有选择才出现短摘要。
- 接入 feature-first Back；Bridge/Android Home 区分保持；Search 直接打开目标页不闪回上页。
- 新增准星精确操作于所有屏幕，方屏默认优先使用。准星选点来自实际 viewport 的 project/unproject，不只是画两个线段。
- 浏览中点已有对象→选中；长按空白→临时选点；重叠多个对象→短候选列表。工具按钮不穿透地图。
- 测量/路线编辑中轻点空白只设置候选位置，点“添加点”才写入；平移地图不能添加点。已有 handle 拖动是快捷方式，精确准星/坐标输入必须也可完成。
- 拖动/最终确认/取消带 gesture ID；多指、视口变化、旋转或键盘重排时取消未确认位移，之前的编辑保留。最后一帧坐标不能因重组/节流丢掉。
- 48dp 不重叠 hit targets、主题与字体尺度、圆角安全带、短屏可滚动表单、焦点与键盘恢复。

### 必测

- 根页、地点详情、路线草稿、图层、包详情、弹窗、IME 各自 Back/Bridge/Search 的返回目标；一次 Back 不跳过两个层级。
- 地图 pan/pinch 不被 Shell Pager 消费；点工具不落地图点；长按与 add 不重复创建。
- 方屏不遮住确认/取消，不靠缩小字号掩盖布局；1.5 字体/长中文名仍可操作。
- 暂离/返回恢复工具及选中对象；不存在的对象显示可关闭错误，不崩溃。

**完成出口：**即使资料功能尚未全加齐，整套地图导航与输入优先级已经唯一、稳定。后续功能按该模型填入，不再继续堆标签页。

---

## C04｜坐标、选点和测量成为真正可用的工具

**依赖：C03。对应 MAP-R04/R05/R06。**

### 改哪里

`core:map-domain` 中地理计算/解析、测量模型和 reducer；Chart 的选点摘要、测量 UI；renderer 点线生成。[E05、E08、E10]

### 统一数学合同

持久化坐标为 WGS84 显式 latitude/longitude Double，拒绝 NaN/Infinity/越界，经度规范化且保留日期线语义；renderer 可显示纬度限制与用户原始坐标分开。不要为了显示把合法的高纬导入坐标悄悄改掉。

距离与路线段采用 **WGS84 椭球大地线**；优先使用经版本/许可证确认的 GeographicLib Java 类库。内部距离米、速度米/秒、时间秒，显示 NM/m、kn、`°T`。没有可靠磁差时不显示 `°M`。计算与绘制使用同一种曲线；按误差预算加密后投影，跨日期线拆线，fit bounds 用最小合理经度包络。

相同点距离 0、方向未定义显示“—”；近对跖点算法不能 NaN，方向多解情形不得伪装唯一操舵建议。路线段显示的是**起始真方位**，不是整段恒定航向或驾驶指令。计算从 renderer 拿经纬度，不从屏幕像素推海里。

### 选点

临时选点摘要给出：坐标、所属可用图源信息、保存地点、从此测量、从此规划、复制坐标。没有活动导航 runtime 不显示“导航到这里”。支持十进制度与度分小数输入，明确格式/半球，拒绝分≥60、符号与半球矛盾、非法范围；输入错误在字段旁说明，不悄悄修成另一个位置。

### 测量

- 两点模式显示 A→B 距离及起始真方位；多点模式显示本段与总长。
- 0 点说明如何放起点；1 点说明如何放终点；2 点以上始终有实际数字，不再只报数量。
- 能选中、移动、插入、删除任意测量点；精确准星与坐标输入可替代手势。确认一次编辑进入 undo；连续拖动预览不写每帧历史。
- Undo/redo、清除、全段总览；退出工具保留本次测量，清除是独立命令。V1 不建“测量收藏库”，需长期保存时转路线。
- 转路线复制坐标到独立新草稿，原测量不变；C01 确保另一草稿不丢。两者不共享可变列表。

### 必测

- 独立数学参考值：赤道 1° WGS84 弧长约 111319.490793m；新西兰短段、长段、同点、极区、日期线、近对跖样本。精度阈值由算法与数据精度给出，不用当前函数输出当 golden。
- 坐标格式合法/冲突/负零/边界/高精度往返；GeoJSON lon/lat 转换不颠倒。
- 三点测量任意点修改、撤销重做、清除后撤销规则明确；末次 drop 后总长与最终位置一致。
- 地图旋转/缩放只改变图形，不改变地理距离；日期线不横穿全世界。
- 测量与地点/路线相互独立，转路线不覆盖先前草稿。

**完成出口：**用户真的可以得到距离与方向并修正测量，不再把“存了两个点”当测量完成。

---

## C05｜地点库闭环：收藏、再找、修改、删除、恢复

**依赖：C04。对应 MAP-R06/R08。**

### 最小模型与页面

Place：稳定 ID、position、name、notes、category/tags、createdAt/updatedAt、revision。分类先保持锚地/码头/上岸/补水/个人标记，不引入公共评分、百科、账户或云同步。

地点列表为真正的数据列表，能搜索/排序并进入详情；空态提供从地图选点/输入坐标入口。保存时可输入名称与备注，空名称可用本地化自动名，但不是只能自动命名。详情提供地图显示、编辑资料、显式移动、删除/撤销、导出。

移动地点：进入专门位置编辑 → 保留原点与候选位置 → 确认一次写入或取消；普通地图平移、地图按压与选择不可改变地点。删除给明确对象名及影响，不误删路线。撤销仅在 revision 仍兼容时恢复原 ID，不覆盖后来的记录。

正式路线使用坐标快照，可带 sourcePlaceId/revision；原地点移动/删除不自动改路线。详情可以提示来源地点变化，但修改路线要显式确认。

本地搜索查名称、备注、分类/标签和明确坐标，中文/英文均可；无结果不是自动联网找全球地名。地图上多个地点重叠时有候选选择，不按列表偶然顺序选第一个。

### 必测

- 保存→持久 ack→列表→详情→地图显示→改名/备注/分类→实际进程重启恢复。
- 位置编辑取消不改原坐标；确认/撤销稳定；拒绝坐标格式错误。
- 删除被路线引用的地点后，路线坐标/顺序/计划速度不变；删除后新建不复用 ID。
- Undo 与随后编辑/导入冲突时不会覆盖新数据；失败不显示已保存。
- 空库、重复名称、超长中文、1.5 字体、搜索无结果、返回焦点/滚动位置。

**完成出口：**任何保存的地点都能从 UI 再找到和管理，不留“库里有数据、界面只有数量”的状态。

---

## C06｜手动路线：草稿、正式计划、重新编辑与预览

**依赖：C04、C05。对应 MAP-R07/R08/R16。**

### 对象语义

`RoutePlan` 为正式计划，`RouteDraft` 为独立编辑草稿。至少保存：稳定 ID、名称/备注、点 ID 与顺序、坐标快照、revision；编辑现有计划时保存 basePlanId/baseRevision。打开预览不创建 dirty draft。

允许多个未完成草稿，UI 同时只有一个活动编辑会话。切另一条/测量转换/新建时保留旧草稿；Routes 列表的草稿分组能重新打开。不为此创建新 App 或复杂多窗口。

### 必需操作

1. 任意起点；从地图/地点开始；逐点添加、线段插点、任意点移动/删除、点列表重排、Undo/Redo、全线总览。
2. 名称与备注可编辑；每段距离和起始真方位、总距离可查。重复相邻点提示处理；非相邻返回同一点或自交路线允许，不假装做安全验证。
3. 计划船速初始为 **未设置/null**。不再自动 5 kn；没填时仍可保存路线，仅用时为“— / 填写计划船速后估算”。0/负数/NaN/Infinity 拒绝，极端值提示。计划用时来自计划距离/计划船速，不取旧 SOG、不叫实时 ETA，不假设潮流/天气。
4. “保存”新建或更新当前正式计划并结束对应草稿；“另存为副本”才新 ID；重复点 Save 不能造两条。版本冲突保留草稿，提供另存或重新载入，不静默 last-write-wins。
5. 复制创建新计划；反向生成新副本或明确在当前草稿反向，不能默改正在预览的正式计划。按钮文案精确说明哪个对象会改变。
6. 删除/撤销；丢弃草稿只丢该次编辑，原正式计划仍在；写失败保留未保存状态。
7. 计划预览在地图显示已保存的点线，而不只是当前 routeDraft；切换计划选择有明确 active ID，不把所有保存路线突然堆在地图上。
8. 详情可导出 GPX、做离线资料检查。首版不显示“开始导航”假入口。固定到桌面是 C11 条件增强，不能为此重构 Shell。

### 必测

- 新路线→保存→列表→重开预览→编辑→保存同 ID/revision；另存才新 ID。
- 草稿 A→草稿 B→A 恢复；测量转新草稿时 A/B 都仍在；写失败不丢当前编辑。
- 点插入/删除/重排/移动/反向的 undo/redo；跨日期线正确，末次输入不丢。
- 未填速度没有时长且可保存；输入后计算正确，修改速度只影响该计划。
- 快速重复 Save、旧 ack、并发版本冲突、删除已被其他入口引用的计划。
- 图层切换/包更新/位置恢复不修改路线点；全流程没有 NMEA/自动舵输出。

**完成出口：**“画→存→找→看→改→再存→恢复”全部能从真实 UI 操作，而不是只有 SaveRouteCopy。

---

## C07｜海图包：真实验证、可取消任务、版本与崩溃恢复

**依赖：C01、C02；可与 C05/C06 的独立工作并行审阅。对应 MAP-R10/R11/R12/R17/R18。**

### 改哪里

`AndroidMbTilesRepository.kt`、`MbTilesMetadata.kt`、`ChartPackageRepository.kt`、`ChartPackageCoordinator.kt`、Chart 包内页、相关 instrumentation tests。[E08、E09、E12]

### 支持范围

V1 必须实际支持 Web Mercator raster MBTiles 的 PNG/JPEG，规范兼容的 metadata/tiles 表或视图。WebP 只有验证具体 SDK/设备解码路径后才声明支持。pbf vector、加密 ENC/S-63、zip、原始 TIFF 未实现时给明确不支持原因，不反复 spinner。**扩展名不是格式证明。**[S01]

metadata 的可选/推荐 bounds、minzoom、maxzoom 缺失时从真实 tile 索引计算可用值，不强迫用户猜填；存在却非法/不一致则标明。来源、许可、发布日期、版本未知可为 Unknown；用户手动填写是“用户声明”，不是“已核验官方来源”。不能为了安装让用户随便编一个 CC-BY 和版本号。

### 导入与验证

选择单文件 → 受控 staging 复制/摘要/进度 → 检查 SQLite/schema/query → 索引/编码/解码验证 → 用户看清范围、真实格式、署名和限制 → 安装。无网依然可以本地导入。

- 在后台流式工作；复制/SQL/扫描都有取消与预算。空间不足途中失败也正确处理，不能仅复制前检查一次。
- 固定只读查询，不执行 metadata 中脚本或 URL，不 ATTACH 任意路径、不加载扩展。视图允许但查询受限，异常/过复杂查询可取消和失败。
- 检查 z/x/y 有限整数范围、TMS 行翻转、重复 key、空包、索引与 metadata 矛盾、tile 数据编码、图像像素/字节尺寸上限。初检抽样与“全部图块解码通过”是不同等级，不混写。
- 一个有效坐标 key 有坏图片仍是坏图；不接受 0x01 为合格 PNG。使用时遇坏块也要隔离/说明，不能把整条输入主循环杀掉。
- 大包不 readBytes，日志不输出绝对路径或整份 metadata；拒绝路径穿越、无边界尺寸与未知 URI 滥用。

### 版本和持久化

逻辑包 ID 与不可变版本 ID 分开；sha256 是字节身份，不是资料逻辑身份或官方更新日期。同文件可去重；同名不同包不能互相覆盖。更新需要绑定同一逻辑包，清楚展示旧→新版本。

流程：staging→完整检查→同卷版本目录原子发布→仓储事务切 active pointer→延后清理。文件和数据库不是同一个 ACID 事务；用安装 journal/reconciler 处理“复制一半/文件已移动未入库/已激活未清旧版”的崩溃点。旧好版本在新版本成功前继续可用。

renderer、snapshot 和 coverage job 对版本持使用租约。删除/替换先撤下使用或等待释放，再清文件；用户删除确认说明影响，个人地点/路线/GPX 不删。保留明确回退能力；包损坏不是在 listInstalled 中静默消失，需可诊断、移除或修复。

### 任务协调

每次 inspect/install/download/delete 有 operation ID、generation 和单一 Job owner。取消必须取消在途工作并淘汰旧结果，当前文件选择取消不显示失败。用户取消后旧结果不得打开 Editing 或改 active package。单个包版本同一时刻只有一个安装任务；并发不同任务有上限。

界面区分 Copying/Inspecting/ReadyToInstall/Installing/Failed/Cancelled；有已完成字节而未知总量时，不伪造百分比。失败可重试；Back 离开任务页不自动丢 staging，取消与离开是不同动作。需要长任务后台继续时采用经过目标 Android 版本核对的任务机制；不能依赖 ViewModel 永远存活。

### 必测

- 表 schema 与视图 schema；缺推荐 metadata 但有效索引；非法 zoom（B10）、坐标、重复 key、坏 tile、透明 tile、不同 tile 尺寸、空包、超大 metadata。
- SAF 撤权/断开、途中空间不足、取消复制/inspect/install、旧任务晚回、两个文件竞争、同包重复安装。
- 每个安装边界进程中止，重启后至少保留旧有效版本；不会指向半文件；staging/孤儿处理可重入。
- 活跃 renderer/coverage 正在读时删除/替换；camera 与用户点线不变；删除后旧 callback 不复活图源。
- 旧版本回退、损坏清单可见、清缓存不删准备好的离线包，海图包不进入不适合的大体积系统备份。

**完成出口：**包的“已安装”是经过验证、可读、可恢复的事实，不是复制出文件就算完成。

---

## C08｜GPX 1.1 与只读分段轨迹

**依赖：C05、C06。对应 MAP-R06/R08/R09/R18。**

### 用户流程

系统选文件 → 受限流式解析 → 预览地点/路线/轨迹数量、范围与警告 → 用户选中并确认 → 事务性写入。相同文件重复导入按 digest/import record 提醒，允许明确“作为副本”；不自动按地理距离把不同地点合并。

GPX 1.1 的 wpt→Place、rte→RoutePlan、trk/trkseg→只读 ImportedTrack。轨迹分段之间不连线，原始时间/可选元数据按支持合同保留。导出轨迹仍是 trk/trkseg，不偷偷转成航线；不新建“航行记录中”的状态。[S04]

单点/空路线在预览说明，不能存成可运行计划；可选择另存地点。未知 extensions 可保留原文件受控备份或明确列不支持字段，不能承诺全字段无损。中文/空可选字段不使整文件失败。

### 安全与资源

禁 DTD、外部实体、外部 schema 和 metadata URL 自动请求；XML 深度、字符串、点数、文件大小有产品上限。初始预算可沿用文件 50 MiB、总点 200,000、路线点 2,000/条、备注 8 KiB/项，基于实测可调整但必须解释，不静默截断后成功。禁止整个大文件 readText/readBytes。

导出走 SAF/FileProvider，区分生成完成、用户选目标取消、写目标成功、分享给外部 App。取消保存对话框不 Toast 成功，写失败不丢原资料。使用受限 URI 权限，不申请全盘权限。

大轨迹可显示简化，但原始导入数据不被 LOD 修改；按视口查询/抽稀避免主线程绘制 200k 点。选中对象与分段身份保持。

### 必测

- GPX wpt/rte/trk 多对象混合；至少两段轨迹且中间远距离缺口，渲染不补线。
- 中文/英文、可选字段空值、时区/缺时间、顺序与坐标精度、支持字段往返。
- 重复导入提醒、显式副本、新稳定 ID；解析完成后取消不入库；批处理故障不半批隐式成功。
- XXE/DTD/XML bomb、极深嵌套、超长文本、NaN/范围错、超点数预算；证明不读外部文件/网络。
- 外部目标撤权、空间不足、分享取消；导出结果可由独立 GPX parser/schema 工具读取。

**完成出口：**用户可以把真实地点/计划带进来和带出去，轨迹类型与分段不被破坏。

---

## C09｜沿线离线资料检查与真实来源获取

**依赖：C06、C07。对应 MAP-R10/R11/R12/R13。**

### 检查的含义

叫“离线资料检查/为航线准备资料”，不叫“安全航线检查”。结果证明指定范围、图源版本和显示级别的图块在本机；不证明深度、障碍、海况或图面改正足够。

输入至少固定：route ID+revision、选用图源/包版本集合、目标级别、两侧准备宽度、备用地点及半径。默认两侧各 2NM 可作为可改的资料准备宽度，明确不是航行安全走廊，也不擅自用用户吃水计算。

### 实现

1. 用与路线绘制一致的大地线及可验证误差界生成所需检查区；处理端点圆角/缓冲、转角、日期线和极区。可保守多检查，不能漏检查后声称完整。
2. 遍历与区域相交的 tile keys/spans，在实际有效本地索引检查。禁止只看 package bounds，禁止只采样几个航点，禁止把低级图上采样当作目标级别数据。
3. 把结果至少拆为：`tileAvailability`、`contentFootprint`、`navigationSuitability=NOT_ASSESSED`。图块全存在但透明/图幅裁边未知时显示“图块已在本机，有效图面未核验”，不是一个覆盖所有不确定性的绿色 Complete。
4. 内容/图层切换、路线修订、包更新/删包、目标级别/宽度改变立即让结果失效。结果绑定完整 input fingerprint；旧 job 不能把新状态标完整。
5. 缺失在地图/列表可定位；有合法获取 capability 才显示“获取缺失”；ImportOnly 显示导入入口和缺失范围。获取后重新检查，不因下载 job 成功就直接 Complete。
6. 工作量有预算，初始所需 keys 上限 200k 或更小经实测决定。超限显示“范围过大，请缩小”，不静默截断、不在 UI 每帧扫 SQL。

### 图源和下载

一个实际来源至少登记发布者、URL、许可/核对时间、用途限制、署名、原始日期/版本或 Unknown、offline/display/redistribution/download capabilities。格式可读、字节一致、来源可信、适航信息是四件不同事情。至少完成一个合法真实小区域图面的端到端验证。

对具有实际授权下载端点的来源实现 unique job、暂停/恢复/重试/更新。先验证端点/许可再写生产目录，不能假造可下载套餐。不具备来源条件的任务明确 `BLOCKED_EXTERNAL`，仍完成 ImportOnly 的全部本地功能；不能把“下载尚未可用”隐藏在完成报告中。

下载基础要求：HTTPS、受控提供方/重定向、payload 与并发上限、有限重试/Retry-After、校验成功后才进入 C07 安装。用户暂停后恢复网络不擅自继续；断网自动等待与用户暂停分开。Range resume 校验 ETag/Last-Modified/总长度，处理 200/206/416；版本变了重来，不拼接两份文件。未知总长度显示已下载字节。

**不能从 `tile.openstreetmap.org` 批量预取或提供离线区域下载。**开放 OSM 数据与公共瓦片服务权限不同；交互浏览许可不等于离线下载许可。其他来源逐一核实。新西兰资料优先考察 LINZ 正式发布说明，但不因“官方 TIFF/ENC 可下载”就声称当前 raster App 可替代官方航海资料或支持 S-63 解密。[S03；S06 为待实施复核链接]

### 必测

- bbox 内洞、路线段穿洞但航点不在洞、目标级别缺图但低级有图、透明边框、转角/窄缝、跨日期线与终点缓冲。
- 图块全部存在但图面有效性未知时，两个维度不混写；没有安全适航结论。
- 计算中改路线/宽度/级别/包版本，旧结果被拒绝；删包立刻失效。
- 超预算、取消、后台返回、索引损坏；任何 Unknown 不等于 Available。
- 下载取消、断网恢复、用户暂停、ETag 变化、错误长度、206/200/416、限流、校验失败、空间不足、进程重启、旧好版本仍可用。
- Provider 许可与现实能力决定按钮；不存在的自动更新端点不显示假操作。

**完成出口：**用户能解释为什么哪里资料缺失、如何补齐；来源获取受阻必须单独报告，不与本地机器完成状态混写。

---

## C10｜把无源和断流消费端做对，生产数据接入继续后置

**依赖：C01–C03。对应 MAP-R01/R14/R16/R18。**

### 改哪里

位置模型/质量策略与纯 Kotlin tests、只读观测端口、Chart 投影与 renderer overlay。不要新建 NMEA writer、Socket 输出、GPS 权限采集或导航服务。[E05、E08]

### 合同

- 生产默认 `NoSource`。测试专用 Fake/Replay 不进入生产目录、发布 flavor 或正常设置页。
- 样本带 source ID、source session/epoch、可用的 sequence/observation ID、可选采样 UTC 与其可信度、接收单调时钟、validity、可选 accuracy。重连或 UI 重组不能刷新缓存样本年龄。
- 同坐标的新样本可以新鲜；同样本重复收到不能冒充新样本。同 ID 但新 source epoch 不能因旧去重表永久丢弃。只有 arrival time 时明确其局限，不声称可识别所有上游回放。
- 负年龄/未来 timestamp、乱序、源切换、设备重启分别受控；不同 boot 的 elapsed time 不互比。无新消息也要自动转过期，使用可注入 clock/policy；NoSource 空闲不跑高频 timer。
- Position、Heading、COG/SOG 的质量独立。HDG 有 TRUE/MAGNETIC 参考；无磁差不要当真航向用。COG 不叫船首向，低速/低质量 COG 不画成确定的船首方向。
- 无有效位置不画船；有效位置无朝向画中性点；有有效真 HDG 才表示船首；COG 向量另有名称/线型。accuracy 未知不画假精度圈。
- position 过期改历史标记和年龄，停止实时向量推进；HDG 过期只降级 HDG，不清 position。链路断开与样本仍暂有效可以同时存在；不清空最后已知位置，也不把它继续画成亮的实时船。
- `Browse` 与 `FollowPosition` 是用户意图，不由数据到达自动决定。用户 pan 后离开跟随；恢复源不抢相机。此前跟随且无用户浏览动作时，可按明示规则在有效恢复后继续。无 provider 时不露出不能用的“回船位”。
- 历史快照不能恢复为 fresh。NoSource 不阻塞测量、路线、GPX、包工作。默认不做航位推算或未来位置预测。

### 必测

同坐标新样本；重复缓存；source epoch 重启；源切换；未来/乱序时间戳（B07）；UTC 时钟改变与 monotonic 过期；无消息过期；不同 boot 历史恢复；position fresh+heading stale；仅 COG；精度未知；断开但样本尚未超时；恢复后不抢 Browse 相机；Follow 的用户选择正确。

检查 renderer 真的根据质量改变样式，而非只改状态文字。测试 spy/依赖边界证明保存、预览、磁贴、观测恢复都没有船网/自动舵输出调用；地图合法瓦片网络访问与船网输出是不同事项，不把所有网络权限全部禁掉充数。

**完成出口：**当前无源地图正常，未来数据模块可通过只读端口接入；此次不提前实现数据采集和航行任务。

---

## C11｜三档地图磁贴与 Shell 会话集成收尾

**依赖：C03、C05、C06、C07；显示离线检查结论时还依赖 C09。对应 MAP-R02/R03/R15/R16。**

### 改哪里

`ChartLauncherPresentation.kt`、`ChartShellContribution.kt`、Chart 的摘要/快照投影；必要的 binding/search 扩展。Shell 仍只负责外壳、编辑、动画与布局。[E03、E04、E10]

### 内容规则

| 尺寸 | 内容 | 不得出现 |
|---|---|---|
| 1×1 | 地图 glyph；必要、可解释的简短 badge；完整无障碍名称 | 地图缩略图、小到无法读的多项仪表 |
| 2×2 | 图标/标题＋当前草稿或选定计划名＋一条真实状态；无任务则上次浏览区域/图面状态 | 只放一个放大图标就宣称摘要完成；虚构实时值 |
| 4×2 | 低频上次图面快照或纯路线小图＋名称＋一条状态；图像清楚标“上次视图/计划” | 持续 MapView、假新西兰海岸线、无证据“安全/资料齐全” |

状态优先：未保存/写入失败 → 正在编辑的草稿 → 选中计划/离线资料状态 → 上次区域 → 普通入口。不要把开发术语、SourceId、长列表计数或“offline-first”口号当主内容；数量可作为附属事实，不替代实际任务信息。

预览只从已有前台地图机会性产生，或由用户路线纯几何绘出。无快照则图标/文字 fallback，不为截图偷偷启动第二 MapView、网络、定位或船网。缓存有界，key 包含 camera/source/style/route revision 与尺寸桶；迟到 callback 不覆盖当前快照。资料署名/快照再用权不足时只显示用户路线几何，不绕过限制。

编辑主屏时冻结装饰性轮播/预览切换，不冻结关键的“未保存/故障”语义；状态真实性优先，切换不要导致触控目标跳动。不可见/后台停装饰动画。

点击普通地图磁贴恢复已有 ChartSession；明确路线 deep link 打开预览，不启动导航，不重复压栈。非法/删除 ID 给可操作的“不存在”。Shell Search 的地点/路线结果可通过窄贡献接口加入，不给 Shell 塞地图模型。

**路线固定到桌面是条件增强：**只有现有 secondary entry 合同可直接安全扩展才做；没有则记录 `NOT_APPLICABLE_CURRENT_SHELL`，不显示假“固定”按钮、不为它重做 Shell。普通地图磁贴、搜索与会话恢复不是条件项。

### 必测

- 所有支持尺寸各有对应内容 renderer，三档连续缩放与重排不回归；小磁贴可操作。
- 长中文/英文、无资料、未保存、已有计划、过期覆盖报告、无快照等内容；各尺寸可读、无裁掉重要状态。
- 快照 A 晚于 B 返回不覆盖；删包/修改路线后旧摘要不再说资料齐全；无隐形网络或 MapView。
- 普通磁贴恢复原会话，Search→地点/路线原子转场；删除对象的旧入口不崩溃。
- 条件路线 pin 若实现，重复固定/删除目标/用户取消固定均有回归，不自动新增到桌面。

**完成出口：**三档各自有用且低成本，普通主屏足以完成自定义，不再需要 Demo 页面。

---

## C12｜同一候选完整回归、纠错与交付

**依赖：C00–C11；外部来源阻塞按独立状态报告。对应 MAP-R19/R20。**

### 12.1 先核对测试真正进入了 CI

基线 `.github/scripts/run_device_tests.sh` 默认只调用 `:app-shell:connectedStandaloneDebugAndroidTest`，不是所有 adapter instrumentation。必须把真实 MBTiles repository 与真实 renderer 的 device tests 显式纳入工作流；只有存在 test 文件而 CI 不运行不算覆盖。[E12、E13]

复用现有 CI，不复制整套流水线。增添明确的 Chart 设备/离线/恢复验证 job 或步骤，结果进入最终必要 gate。测试矩阵不能只包含 Shell 大标题/磁贴打开这一条旧 smoke。旧大标题测试要按新产品行为替换断言，但仍验证一级入口与主题一致性。`test_ui_architecture.py` 中禁止源码出现 `vesselPosition/activeRoute` 等字面断言，如与合法的只读观测或计划模型冲突，应以“无源不造船位、预览不启动导航、无生产假数据”的行为测试替代；不能为了名字扫描而拆出隐藏假值。

### 12.2 必跑层级

1. 纯 domain：几何、状态/保存 revision、取消/去重、位置质量、覆盖算法。
2. 真实存储：用户资料数据库、实际 MBTiles SQL/图片、GPX 文件、安装 journal/crash matrix。
3. Compose：控件命中、准星、表单、方屏/IME、转场中间帧，不能只有 semantics 调命令。
4. 真实 SDK：Native 地图、本地图面、网络隔离、坐标与overlay、相机/样式竞争、资源释放。
5. 完整 App：W1–W7 与下面 J01–J06，Back/Bridge/Search 都走正常生产入口。
6. 性能/资源：release-like 构建，复用 benchmark；模拟器是趋势，三星真机标待验收。

### 12.3 不允许遗漏的整链旅程

| ID | 操作链 | 必须观察到的结果 |
|---|---|---|
| J01 | 安装合法小图包→禁外网冷启动→地图→选锚地→填写备注保存→Bridge→地点详情 | 真图面仍在；原坐标/文本/revision可核；无 GPS 故障墙 |
| J02 | 测两点→看距离/°T→加第三点→改中间点→undo/redo→转新路线→保存 | 每步数值与图形一致，原有其他草稿不丢，没有默认5kn时长 |
| J03 | 计划A编辑→切计划B→Search开地点→Back→Bridge→返回→进程终止重启 | 两份草稿、选择、相机与已 ack 内容按规则恢复，未确认拖动不复活 |
| J04 | GPX导入预览→取消→再次确认→分段轨迹展示→导出→独立parser读取 | 取消无入库；类型/分段正确；没有跨缺口连线或假活动导航 |
| J05 | 包V1正使用→装V2→在关键边界失败/杀进程→重启→回退→覆盖检查 | 至少V1可用；无半安装active；个人资料不变；旧覆盖结果失效 |
| J06 | 三档地图磁贴缩放/拖放→打开草稿→数据源测试断流/恢复→回桌面 | 各尺寸摘要正确；历史不冒充实时；恢复不抢相机；无船网输出 |

场景中的实际航海区域只用于用户自己规划/测试，不生成一条未经评估却声称可航行的推荐航线。

### 12.4 性能和布局检查

- 320×320 / 360×360 dp、普通竖屏/横屏、圆角/挖孔、IME、字体 1.0/1.3/1.5；最小尺寸仍有完整操作入口。
- 地图 pan/zoom/rotate、选点、端点拖动、写入中 Bridge、导入中地图操作、Start 小磁贴 controls/drag/auto-scroll。
- 每个操作尽快有下一帧可见反馈；不能直到文件检查/数据库保存完成才响应。
- 空闲 NoSource 不高频轮询；后台无持续装饰渲染。连续50次进出地图，MapView/native资源数不单调增加。
- 初始应用侧 tile/preview 缓存预算可取64MiB并实测调整，不能把它当 SDK 全进程内存保证。大GPX/大图包应流式处理、预算拒绝，不OOM。
- 60Hz真机以低jank、P95 frame overrun≤0等为测量目标，不在没有真机时写达标。模拟器比同环境基线，记录P95/P99和连续长帧；截图只证明布局，不证明跟手。
- 至少一个外部测试驱动终止并重新启动进程。`ActivityScenario.recreate()` 只证明Activity重建，不得替代进程死亡。明确 force-stop 与系统回收模拟的区别。

### 12.5 构建、清理与安全边界

- 保持 Release 仅实际入口，无Lab/Fake/Replay/测试海图冒充生产资料，无HOME默认桌面intent。
- 检查最终 merged manifest；地图SDK带来的定位权限不因本轮误重新进入生产。当前不采集GPS，不申请位置权限。
- 清理 `.github/workflows/refinement-source-snapshot.yml`、`refinement-jvm.yml`、`refinement-patch.yml` 及 `.github/refinement/apply.py` 等临时施工工具前，核对没有在途/依赖任务；普通 Codex checkout 不需要这些旁路。
- **不要再次运行已经部分应用过的文本替换 apply.py。**先审查真实代码差异，避免把旧补丁当幂等迁移器。
- 不为了绿灯绕过必要 job、不把失败标 optional、不重试直到偶然绿然后忽略稳定复现。
- 同一冻结候选执行两轮针对性反证审阅：先数据/异步/资源，再用户旅程/布局。发现P0/P1继续修，重跑受影响及累计回归。

### 完成出口

C00–C11 的必要功能有生产入口、真实实现、成功/失败/取消路径、持久化语义、实际执行测试；同一SHA的完整必要CI通过。来源交付与真机状态独立。没有尚未解决的P0/P1才能称 `CORE_MACHINE_READY`。

只有人工能填 `HUMAN_ACCEPTED`。Stage 2.5/11 已有参考材料和真机手感不是机器工作无限等待的理由，也不是Codex有权自批的部分。本轮不重复启动它们，不篡改其测量/认可记录。

---

## 5. 具体测试目录与命令

### 5.1 优先使用的现有位置

```text
core/map-domain/src/test/kotlin/com/yokuli/marine/map/domain/
core/shell-contract/src/test/kotlin/com/yokuli/shell/contract/
core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/
feature/chart/src/test/java/com/yokuli/marine/feature/chart/
adapter/map-storage/src/test/java/com/yokuli/marine/map/storage/
adapter/map-offline/src/test/java/com/yokuli/marine/map/offline/
adapter/map-offline/src/androidTest/java/com/yokuli/marine/map/offline/
app-shell/src/androidTest/java/com/yokuli/marine/shell/
.github/scripts/
```

可在相同模块增加数据库/GPX/完整旅程测试文件；新类名是实现选择，不需要为了与文档文字一字不差建空类。需要新增 androidTest 依赖时核对模块构建配置和任务是否实际生成。

### 5.2 基线与持续检查命令

```bash
# 只核对，不自动清工作树或reset旧SHA。
git status --short
git branch --show-current
git fetch origin
git rev-parse HEAD
git rev-parse origin/codex/shell-map-contract

# 先执行受影响的纯模块；--continue 用于收集所有模块真实结果，非忽略失败。
./gradlew --no-daemon \
  :core:shell-contract:test :core:shell-engine:test :core:map-domain:test \
  --continue --stacktrace

# 当前分支必须继续通过的累计检查。
python3 -m unittest discover .github/scripts 'test_*.py'
./gradlew --no-daemon test --continue --stacktrace
./gradlew --no-daemon :app-shell:lintStandaloneDebug --stacktrace
./gradlew --no-daemon \
  :app-shell:assembleStandaloneDebug :app-shell:assembleStandaloneRelease \
  --stacktrace

# 启动已配置的模拟器后；确认为真实执行，不是 NO-SOURCE / SKIPPED。
./gradlew --no-daemon \
  :adapter:map-offline:connectedDebugAndroidTest \
  :app-shell:connectedStandaloneDebugAndroidTest \
  --continue --stacktrace

# 既有性能任务，使用合适的release-like配置和固定环境。
bash .github/scripts/run_device_tests.sh performance
```

上述命令基于本次模块/任务命名；修改模块测试配置后必须核对实际任务。Python全量门禁在完整checkout运行，不在缺图片/二进制的审阅快照里把“文件缺失”误判为产品bug。

GitHub CLI 可用时：

```bash
gh run list --repo ohkuku/yokuli_marine_shell \
  --branch codex/shell-map-contract --commit "$(git rev-parse HEAD)"
gh run view RUN_ID --repo ohkuku/yokuli_marine_shell --log-failed
```

`RUN_ID` 替换成实际返回值，不能固定复用本文旧run。APK、log、test report和performance证据都标同一SHA/构建配置/digest。未通过必要门禁的诊断APK只可标 `UNVERIFIED`。

### 5.3 固定测试资料（现在就做，不最后补）

- 合成非对称PNG/JPEG raster：多个zoom、zxy文字/方向、透明边、缺洞、日期线、合法视图schema。
- 坏输入：0x01假PNG、损坏SQLite、空包、重复key、非法zoom/坐标、超大metadata/图片、SAF失效。
- 包V1/V2：同逻辑资料不同不可变版本，含缺洞与图面改变，用于激活/回退/覆盖失效。
- GPX：中文与空可选字段，多route，多trkseg且缺口明显；安全恶意fixture仅在测试里构造。
- 一个来源许可核验过的真实小区域：地理位置、来源hash、转换流程可复现，不能用画出来的假海岸替代。

fixture 的 expected 不从被测算法当场计算；实际地图截图断言应能区分TMS倒置、lat/lon颠倒、错误级别和空背景。UI fixture 不混入正常用户库，发布产物不伪装成真实资料。

---

## 6. Codex 持续自查与推进协议

### 6.1 每个工作包的闭环

```text
读取HEAD/dirty state/任务与上次失败
→ 明确用户入口、完成/取消/失败/返回结果
→ 先重现关键反例或补红测试
→ 最小实现
→ 只读独立审阅或新的反证审阅轮次
→ 窄测试＋本包gate＋受影响累计回归
→ 原子commit并核对diff
→ push后检查同SHA CI
→ 更新唯一执行状态并自动进入下一依赖已满足的任务
```

没有子代理就自己切换到审阅轮次，不声称“另一位代理已批准”。不是每写一行都要完整模拟器跑一遍，但每个对外候选必须累计验证；等待CI期间可以做独立审阅或无依赖工作，不掩盖未通过的依赖。

### 6.2 每轮至少审问以下问题

1. 真实UI从哪里进、怎么做完、怎么取消、怎样回来？有没有数据已写入却找不到的对象？
2. 没图、没网、没位置、读失败、写失败、损坏各是什么状态？是否混成一个“需要配置GPS”？
3. 哪些状态只是用户声明/metadata/旧缓存，却被说成“验证通过/已保存/实时/完整”？
4. 最后一次pointer输入、取消、drop、旧callback、source恢复是否能覆盖新结果？
5. 是否有两份独立权威数据：SDK与camera、Proto与catalog、草稿与正式计划、进度与实际job？
6. 测试走的是生产路径还是直接调reducer/semantics跳过了出问题的触控/renderer？
7. 日志/导出/预览有无泄露私人坐标、token、原始海图许可受限内容？
8. 有没有重新引入六档、默认Launcher、Demo入口、假导航、未来模块灰色入口或未要求的服务？

### 6.3 失败处理与停点

同一失败签名连续最多三次有依据修复；仍失败就换诊断方法，记录根因假设与反例。不要盲增sleep/timeout，除非已证明是环境速度约束且保留状态断言。基础设施故障有有限重试；缺设备、许可、账号或付费授权只阻塞相关任务，继续独立任务。

需要停下询问的只有：人工未提交修改冲突、不可逆用户数据选择、外部账号/付费/许可授权、当前规范出现安全关键冲突。普通UI细节和已规定行为不逐项问用户。真实来源外部阻塞不能通过伪造合法URL/版本或使用临时随机样本绕过。

### 6.4 执行状态最小格式

在 `docs/phases/chart-wp8-refinement/EXECUTION_STATE.json` 维护以下信息即可。此示例为模板，不是已经开始/完成的声明：

```json
{
  "schemaVersion": 1,
  "phase": "WP8_CHART_COMPLETION",
  "branch": "codex/shell-map-contract",
  "auditedSha": "9579761a3c7c5cf13735dfecaeb38636b715b695",
  "workingBaseSha": null,
  "currentTask": "C00",
  "taskStatus": {},
  "lastVerifiedSha": null,
  "ciRun": null,
  "openFindings": [],
  "sourceDelivery": "NOT_VERIFIED",
  "deviceReview": "PENDING_OWNER",
  "nextAction": "核对工作树与真实CI；执行C00"
}
```

任务状态：`TODO / IN_PROGRESS / IMPLEMENTED_UNVERIFIED / VERIFIED / BLOCKED_EXTERNAL / BLOCKED_CONFLICT`。条件路线固定增强可单独 `NOT_APPLICABLE_CURRENT_SHELL`；不能据此把普通地图磁贴也跳过。

每个 VERIFIED 记录真实 test路径/命令、SHA、结果与证据定位；同一测试可覆盖多个场景，不重复造函数凑数。只保留简短findings、worklog和最终acceptance，不搞文档生产线。

---

## 7. 需求和工作包对照

延续此前地图V1的需求ID，只用于追溯，不要求把之前的空壳重新建一次。

| 需求 | 本轮归属 |
|---|---|
| R01 无网/无源独立工作 | C02、C03、C10、C12 |
| R02 会话与恢复 | C01、C02、C03、C11、C12 |
| R03 WP/方屏/输入 | C00、C03、C11、C12 |
| R04 地理数学 | C02、C04、C06、C09 |
| R05 测量 | C04 |
| R06 对象身份/地点/轨迹 | C01、C05、C06、C08 |
| R07 手动计划非导航 | C06 |
| R08 草稿/持久化确认 | C01、C05、C06、C08 |
| R09 GPX类型保真/安全 | C08 |
| R10 包验证/版本 | C02、C07、C09 |
| R11 真实来源/许可 | C02、C07、C09 |
| R12 获取/取消/恢复 | C07、C09 |
| R13 真实离线覆盖 | C09 |
| R14 观测质量 | C10 |
| R15 三档只读磁贴 | C00、C11 |
| R16 不输出/不启动导航 | C06、C10、C11、C12 |
| R17 异步/取消/资源边界 | C01、C02、C03、C07、C09 |
| R18 隐私/功耗/存储边界 | C01、C07、C08、C10、C12 |
| R19 执行真实测试 | 所有工作包、C12累计 |
| R20 证据与持续纠错 | C00、C12、第6节 |

---

## 8. 最终交付物与不能冒认的状态

Codex 最终向用户提供一份 `ACCEPTANCE.md`、明确SHA的候选APK及digest、实际CI链接、关键旅程录像/截图、真实来源记录和已知限制。不是再发一份“后续可以做”的计划。

报告必须分开：

```text
当前branch / baseline SHA / ending SHA
已完成的用户流程：W1–W7分别说明
自动验证：实际通过 / 失败 / 未运行；同SHA CI run
核心状态：CORE_MACHINE_READY 或 NOT_READY（列阻塞）
真实资料来源：VERIFIED / BLOCKED_EXTERNAL（原因）
在线获取：实际capabilities，不存在的功能不写完成
路线secondary pin：实现且验证 / 条件不适用（理由）
APK：配置 / SHA / digest / 是否UNVERIFIED
三星真机：PENDING_OWNER 或真实记录，不能用模拟器替代
未解决P0/P1：机器ready前必须为空
生产NMEA/GNSS采集/活动导航/船网输出：本轮未实现
下一步：用户真机走一条完整工作流与关键手感验收
```

用户不需要替Codex发现“保存后根本打不开”“测量没有距离”“点击取消还会安装”这类基础缺项。真机验收重点是方屏、圆角、跟手、海图实际观感及真实环境稳定性，不是替代机器应完成的功能闭环。

---

## 9. 审阅证据索引

所有仓库代码锚定本次基线 `9579761a3c7c5cf13735dfecaeb38636b715b695`。链接为追溯来源，不代表该实现通过验收。未提供精确源码行号的以文件及上文函数名定位，避免版本变化后虚构行数。

- **E00 — 当前分支与SHA**：https://github.com/ohkuku/yokuli_marine_shell/tree/codex/shell-map-contract ；不可变提交：https://github.com/ohkuku/yokuli_marine_shell/commit/9579761a3c7c5cf13735dfecaeb38636b715b695
- **E01 — 三档定义**：`core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/core/shell-contract/src/main/kotlin/com/yokuli/shell/contract/MarineTile.kt
- **E02 — 旧尺寸边界转换**：`adapter/shell-storage/src/main/java/com/yokuli/shell/storage/LauncherProtoMapper.kt`。实查Mapper使用 `MarineTileSize.fromPersistedName` 读取placements/spacers，不重置桌面。
- **E03 — 功能贡献**：`feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartShellContribution.kt`、`ChartLauncherPresentation.kt`，以及 `feature/settings` 对应贡献文件。
- **E04 — 安装和主路径分叉**：`app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/app-shell/src/main/java/com/yokuli/marine/shell/ProductionShellGraph.kt
- **E05 — 地图领域和缺口**：`core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapModel.kt`、`MapReducer.kt`、`ChartPackageRepository.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapReducer.kt
- **E06 — 输入、恢复与保存**：`core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapStore.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/core/map-domain/src/main/kotlin/com/yokuli/marine/map/domain/MapStore.kt
- **E07 — 用户资料持久化**：`adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/ProtoDataStoreMapPersistence.kt`、`MapProtoMapper.kt`、`MapStateSerializer.kt`、`src/main/proto/map_state.proto`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/adapter/map-storage/src/main/java/com/yokuli/marine/map/storage/ProtoDataStoreMapPersistence.kt
- **E08 — Native渲染与包检查**：`adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt`、`AndroidMbTilesRepository.kt`、`MbTilesMetadata.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/adapter/map-offline/src/main/java/com/yokuli/marine/map/offline/OfflineMarineChartSurface.kt
- **E09 — 包任务竞争**：`feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartPackageCoordinator.kt
- **E10 — 地图真实UI与输入接线**：`feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt`、`ChartUiContract.kt`，`app-shell/src/main/java/com/yokuli/marine/shell/ShellActivity.kt`、`ShellViewModel.kt`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/feature/chart/src/main/java/com/yokuli/marine/feature/chart/ChartWorkspace.kt
- **E11 — 尺寸与文档旧断言**：`core/shell-contract/src/test/kotlin/com/yokuli/shell/contract/MarineTileContractTest.kt`、`core/shell-engine/src/test/kotlin/com/yokuli/shell/engine/TileEditingRegressionTest.kt`、`.github/scripts/test_launcher_stage7_contract.py`、`test_shell_app_contract.py`、`test_ui_architecture.py`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/core/shell-contract/src/test/kotlin/com/yokuli/shell/contract/MarineTileContractTest.kt
- **E12 — 原海图fixture与测试入口**：`adapter/map-offline/src/androidTest/java/com/yokuli/marine/map/offline/AndroidMbTilesRepositoryTest.kt`、`.github/scripts/run_device_tests.sh`。https://github.com/ohkuku/yokuli_marine_shell/blob/9579761a3c7c5cf13735dfecaeb38636b715b695/adapter/map-offline/src/androidTest/java/com/yokuli/marine/map/offline/AndroidMbTilesRepositoryTest.kt
- **E13 — 当前真实CI**：https://github.com/ohkuku/yokuli_marine_shell/actions/runs/33961941146 ；build job `101295216231`。本次下载报告artifact `9968269841` 与失败bundle `9968274762`；未验证APK artifact `9968269270`。

### 外部技术约束

外部来源只解释格式/服务边界，不替代本项目行为与实测。实施时核对固定依赖版本和适用许可。

- **S01 MBTiles 1.3 官方规范**：支持规范表/视图；name/format为必需，bounds/zoom为推荐元数据；容器可放不同编码，不能后缀通吃。https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md （本次已复核）
- **S02 MapLibre Native官方项目MBTiles记录**：本地文件协议有实现/使用记录，不证明当前项目的每种图包和版本自动通过。https://github.com/maplibre/maplibre-native/issues/3559 ；https://github.com/maplibre/maplibre-native/issues/17 （本次已复核）
- **S03 OSM标准瓦片服务政策**：公共服务不允许批量预取/离线包下载；其他提供方分别核实。https://operations.osmfoundation.org/policies/tiles/ （本次已复核）
- **S04 GPX 1.1官方schema**：WGS84/metric、waypoint/route/track及分段结构。https://www.topografix.com/gpx/1/1/ （本次已复核）
- **S05 GeographicLib Java参考**：https://geographiclib.sourceforge.io/html/java/net/sf/geographiclib/Geodesic.html （本次直连超时；Codex实施时核对官方库固定版本、许可证与API，不据链接直接声称已验证）
- **S06 LINZ资料获取说明**：https://www.linz.govt.nz/products-services/charts/where-find-charts （本次页面直读失败；只作为实施时来源核查入口。本文件不授权任何具体数据再分发，也不声称已得到新西兰可用官方海图包）

**结束原则：不重新造壳，不继续堆入口；把真实地图、用户资料、规划、离线准备和恢复串成完整工作流，用实际执行的反例与证据结束这一轮。**
