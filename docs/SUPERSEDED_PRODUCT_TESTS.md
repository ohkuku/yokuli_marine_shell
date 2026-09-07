# 已退出产品权威的测试 / Superseded Product Tests

状态：`PRODUCT_RECOVERY_WINDOW — ACTIVE`

本清单不会删除历史测试，也不会降低 domain、runtime、persistence、migration、MBTiles 或安全门禁。它只记录哪些旧测试绑定了已经被人工否决的产品形状。对应 App 未经人工验收前，这些测试不得重新成为 blocking gate。

| Test / workflow id | 旧行为 | 退出原因 | Replacement human story | Future automation target |
|---|---|---|---|---|
| `launcher_stage1_contract` | 固定旧生产入口与文案 | 精确 UI 字符串不是产品正确性 | SHELL-01–06 | 人工通过后的 Start/索引可见行为 |
| `launcher_stage5_contract` | 固定 pager 实现形状 | 实现细节不应成为产品合同 | Shell 手势人工旅程 | 跟手分页与 reduced-motion 行为 |
| `launcher_stage6_contract` | 同时检查空间行为和具体 Composable | 二维布局 domain 保留，旧 renderer 形状不保留 | SHELL-01–02 | 任意 1×1 横/竖摆放与恢复 |
| `launcher_stage7_contract`, `launcher_stage8_contract` | 固定旧编辑、pin/unpin 控件与文案 | 编辑 presentation 被重新设计 | SHELL-01–02 | 长按、拖动、resize、pin 的真实手势旅程 |
| `launcher_stage9_contract` | 固定旧虚拟键 UI 结构 | 只保留 Back/Home/session 安全语义 | SHELL-03–05 | Recents Close 与 Shell-root Back |
| `launcher_stage10_contract` | 混合持久化与旧 UI 断言 | durable restore 继续硬测，presentation 退出 | SHELL-02、PREF-02 | process-death 后用户可见恢复 |
| `launcher_stage11_contract` | 旧 fidelity candidate 页面与截图 | 未经人工产品批准 | R09 Human Acceptance | 批准后的 golden/gesture/performance 证据 |
| `shell_app_contract` | 固定 tile renderer 名称和尺寸组合 | App 与 Shell 合同保留，旧视觉实现不保留 | SHELL-01–06 | 二维布局、live tile 与 app session 行为 |
| `chart_c12_contract` | 固定旧 Chart 页面、按钮、字符串与 journey | Chart 已被人工否决并转为 map-first | CH-01–17 | Target、Quick Mark、A/B、route/nav 地图旅程 |
| `chart_library_cl07_contract` | Sources/Assets-first 首页 | Chart Library 必须转为地图内容工作台 | CL-10–15、CL-30–34 | Coverage/Layers/Views/Sources 用户旅程 |
| `chart_library_cl08_contract` | Asset/SourceSet 直接充当显示产品模型 | 裸 Asset 不再是用户 Layer | CL-20–25 | Active View 与 logical Layer 消费 |
| `chart_library_cl09_contract` | 混合 managed-copy 迁移与旧 workspace | 迁移继续硬测，旧 workspace 不保留 | CL-01–06、CL-10–25 | 非破坏迁移及新的 Layer/View UI |
| `chart_library_cl10_contract` | 固定旧 linked navigation presentation | 跨 App truth 保留，旧页面跳转不保留 | CL-24–25、CH-05–07 | Chart Library→Chart active View 旅程 |
| `chart_library_cl12_contract` | 以旧页面存在宣称产品完成 | CI 不得代替人工验收 | R09 Human Acceptance | 用户批准后的完整 CL acceptance pack |
| `nmea_sources_p2_contract` | 混合真实 runtime 与旧输入 UI 结构 | runtime 单元/集成测试保留，旧 presentation 退出 | DATA-03–04 | Data 内 TCP/UDP 与 raw 用户旅程 |
| `nmea_sources_p4_contract`–`nmea_sources_p7_contract` | standalone sources App、旧列表/交付形状 | Data 必须整合 inputs/sources/topology | DATA-01–09 | instruments、selection、topology 与生命周期 |
| `osr_w02_contract` | 固定 live Composable/test tags | 性能语义保留，具体 UI 未获批准 | DATA-01–02 | 稳定 live instrument 更新 |
| `osr_w03_contract` | 固定 Overview/Inputs/Sources/Flow/Diagnostics 五段与旧 Data 形状 | 新 Data 合同明确只保留 Boat/Flow/Connections 一级心智，Sources 融入语义详情，Diagnostics 下沉 | DATA-R00–R10 | 人工批准后的 Boat、Flow、Connections 行为旅程 |
| `osr_w04_contract` | 固定五段 Data 文字页面 | topology 不能是文字箭头 | DATA-01–09 | 节点、边、active path 与 node detail |
| `osr_w09_contract` | 固定旧 Chart/Quick Layers presentation | Chart 必须消费 logical Layer/View | CH-01–17、CL-24–25 | map-first Chart 与 View/Layer picker |
| `osr_w12_contract` | `RouteSketch`、point list、上下移动按钮 | Navigation 必须用真实地图直接编辑 | NAV-01–12 | 点、拖、leg insert、save/start/stop |
| `osr_w13_contract`, `osr_w14_contract` | 固定 Preferences 分区与 live tile 视觉 | 只保留 locale/units/tile runtime truth | PREF-01–05、SHELL-01–06 | 人工批准后的 Preferences/live tile 行为 |
| `osr_w15_contract`, `osr_w16_contract`, `osr_w16_build_evidence` | 固定旧产品面并宣称最终完成 | Recovery Window 内禁止最终产品声明 | R09–R10 | 批准后的新累计验收台账 |
| `chart_shell_ux_correction` | 固定一次被否决的中间 UI 修正 | 中间形状不具有兼容权 | CH/SHELL P0 stories | 最终用户旅程，不检查类名/字符串 |

仍然有效的相关底层证据包括：Launcher Engine reducer 与 Start Document、NMEA parser/runtime/source policy、navigation math、Room/DataStore migration、MBTiles SQLite/raster/TMS/XYZ/SAF、并发资源上限、release surface 和构建兼容性。

## English summary

These historical tests remain in the repository for traceability but do not define the product during recovery. Their replacement automation will be written around owner-approved user journeys, not class names, exact strings, page counts, tabs, or a rejected presentation structure. Correctness and safety tests remain blocking throughout.
