# C03 WP 地图层级、按键与方屏交互报告

> English translation: C03 makes the chart root map-first, separates feature surface/tool/transient state, routes Back through the mounted app before the Shell, and binds crosshair selection to the real renderer viewport. It establishes gesture identity and cancellation but does not claim C04 measurement mathematics or full point-editing tools.

## 状态

- package：C03
- baseline：`dcb694614e9110d765fdb3ef8ff1d464e0d7102f`
- implementation candidate：`7d1a1df593f8a8cafc5c83e72e1849d53a8bd673`
- status：`IMPLEMENTED_UNVERIFIED`
- hosted CI：留给 C12 的最终同 SHA Gate；本报告不伪造远端结果

## 交付

1. `MapSurface`、`MapTool` 与 `MapTransient` 成为互不冒充的状态平面。Chart 根页以真实 renderer 为主体；地点、路线和图包是页面，不再伪装成互斥底图工具。
2. 浏览点按对象、空白长按、准星确认与重叠对象候选统一进入 reducer。测量/路线空白点按只产生临时候选，只有“添加点”提交一次；地图相机移动不会加点。
3. Android Back、Compose Back 与虚拟 Back 共用 feature-first 路由。一次 Back 只关闭 transient、未确认手势、工具或子页中的最上层；都不存在时才交还 Shell。
4. 真机测试发现快速“选工具→Back”时 Compose 快照会滞后一帧，使 Shell 先退桌面。生产 handler 已改为从 MapStore 的实时 `StateFlow` 判定，避免重组时序决定导航结果。
5. Bridge 保留已确认的地图工具和临时点，Search 从 Shell 直接进入目标；Android Home 仍是平台生命周期语义，不与应用内 Bridge 混同。
6. 准星位置从实际 viewport 四边遮挡区计算，再通过 renderer `unproject` 得到经纬度并执行 hit query。Shell 的圆角/安全带被映射进 Chart；左右边距、顶部状态、底部命令区各扣一次。
7. 编辑预览带 `MapGestureId`；旧 gesture frame 无效，最终 drop 坐标直接提交一次。视口、旋转、IME 重排和多指开始会取消未确认预览，不改变先前确认坐标。
8. 根命令目标至少 48dp；页面表单可滚动并使用 `imePadding`。320dp 方形容器和 1.5 字体比例下，候选确认/取消和根命令仍可达。

## 聚焦证据

- C03 repository contract：6 passed。
- `core:map-domain`、`feature:chart` JVM tests：passed。
- `adapter:map-offline` 与 `app-shell` Android test 编译：passed。
- API 34 `:app-shell:connectedStandaloneDebugAndroidTest`：43 passed，0 failed。
- 定向回归证明工具 Back 后仍停留 Chart；准星调用 renderer `unproject`，点击覆盖工具不会落入地图。

## 累计 Gate

尚未运行。进入 C04 前必须从本报告提交重新执行 C00–C03 Python/CI 合同、全仓 JVM、lint、Debug/Release 构建以及 API 34 renderer/Shell/Room 设备测试；任一失败即停止。

## 未宣称完成

- C04 的 WGS84 椭球距离、坐标输入、测量数值、任意点移动/插入/删除、undo/redo 尚未完成。
- C05–C10 的地点、路线、GPX、位置观测、离线检查与设置闭环尚未完成。
- 三星方屏实体机和所有者手感审核仍是 C12 外部审核项。
