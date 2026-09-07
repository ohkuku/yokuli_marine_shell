# DATA-R10 — Real APK Human Acceptance

状态：`PENDING`

此清单只供仓库所有者在 hosted CI 产出的真实 APK 上验收。机器测试不得代签。

## Boat

- [ ] 打开 Data 后 3 秒内能知道 Position、Heading、Depth、Wind 的健康状态。
- [ ] 船体场景是主视觉；mast/keel/position/heading 热点可发现且可进入对应详情。
- [ ] 高频数值更新不会重播整页或船体入场动画。
- [ ] No-data 空态没有工程 counter 或 raw sentence。

## Connections

- [ ] Add Source 第一屏不是 TCP/UDP 表单。
- [ ] Boat Gateway / UDP / Phone 的用户心智清楚，endpoint 只在 Advanced。
- [ ] Test Connection 能说明实际检测到哪些语义数据，不能只说 connected。
- [ ] Test 失败或退出不新增 connection；Test 成功后仍需点“使用此来源”才保存。
- [ ] Connection card 能说明提供什么、当前问题和 Fix/Retest。

## Source trust

- [ ] Position 等感官详情能看见当前 trusted source 和其他候选。
- [ ] 按 semantic group 切换 source 后生效。
- [ ] selected source 丢失时明确 degraded/unavailable，不暗中切备用源。
- [ ] Phone 看起来是系统能力，不是 NMEA socket。

## Flow and diagnostics

- [ ] Flow 是一张完整 topology，不是逐行 rectangle + arrow 报表。
- [ ] selected、standby、unavailable 路径可以区分。
- [ ] consumer nodes 可见、可点，并说明 source loss 对当前 App 的影响。
- [ ] Simple 模式不以 NMEA sentence name 为主要语言。
- [ ] Diagnostics 不在一级首页，但 raw/checksum/drop/parse evidence 仍可到达。

## OS projection

- [ ] Data tile 显示有意义的 marine value/health，不是 connection count。
- [ ] Active Navigation 缺 required Position 时，Shell 出现只读导航数据警告。
- [ ] 该警告不引入第二套 source/failover 逻辑。

## 决定

```text
reviewed APK SHA:
reviewed CI run:
decision: PENDING
notes:
```

只有此决定为 `APPROVED` 后，才允许进入 DATA-R11，将接受的行为旅程重新写成 blocking CI；不得锁具体像素布局、文案或 composable 名称。
