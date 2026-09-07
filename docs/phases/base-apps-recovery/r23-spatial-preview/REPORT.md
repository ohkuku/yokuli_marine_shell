# R23 — Real Coverage and Selected View Preview

状态：`IMPLEMENTATION + FOCUSED MACHINE GATE COMPLETE — HOSTED CI PENDING`

## Baseline

- 基线 HEAD：`cc87810ffad5fa1435de2a23330e3b04cc3b39ae`
- 实现代码 HEAD：`6f9cc2bfbf22f58b85336fb18b61a75c0c3f7c8d`
- Execution directive SHA-256：`36f520ea6232b152554dd1d23d224e18b08009af89157ca046b1381a9dd68ac5`

## 产品结果

- Coverage 的主要空间表面不再是经纬格网方框；它使用 Chart 已有的生产地图 surface、真实 basemap 和 Basic-readable MBTiles plan。
- 选择 logical Layer 时，Coverage 生成只包含该 Layer 的 transient display plan，并按真实 bounds 调整预览相机；不写 catalog。
- Views 预览由当前 selected View 构建，而不是偷用 globally active View。
- 预览 inactive View B 不会激活 B，Chart 仍继续消费 active View A。
- Coverage 与 View Preview 共用同一个 production surface factory、tile gateway 和 resource access；没有新增第二个 renderer。

## Red / Green

首轮测试因 fixture 仍处于 `NEVER_SCANNED` 而失败，planner 正确拒绝把它当成可渲染内容。夹具纠正为 `scan COMPLETE + generation 1` 后：

| 定向 Gate | 结果 |
| --- | --- |
| inactive B preview without activation | PASS |
| selected Layer real render plan | PASS |
| feature coordinator suite | PASS (`10/10`) |
| app-shell production composition compile | PASS |
| `git diff --check` | PASS |

完整质量门禁交给 hosted CI。

## 过渡与未完成

- Layer coverage thumbnail 和数值 bounds 仍可作为 secondary metadata；旧 schematic world canvas 已从 primary Coverage surface 删除。
- R24 尚需把 Chart 的 durable waypoint/route/GPX 写入统一到 Navigation。
- 地图视觉、缩放手感和真实设备触控仍为最终人工验收项。

## CI

- 报告前缀：`CODEX-CI-REPORT-`
- Hosted run：`PENDING_PUSH`

## English summary

R23 replaces the schematic primary Coverage canvas with the same real map surface used by Chart. Coverage creates an ephemeral logical-Layer plan, while Views preview the selected View even when another View remains active. Preview never mutates the durable active View, and no second renderer was introduced. Focused behavior and production composition gates pass; hosted CI and human map usability remain pending.
