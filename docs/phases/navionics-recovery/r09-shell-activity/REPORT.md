# NAVREF-R09 — Shell Activity / Future Consumer 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- `MarineOngoingActivityPort` 提供唯一的进程级 consumer contract，直接携带 Active Navigation 与 Track Recorder 的原始 snapshot，并把命令转发给真正拥有状态的 runtime。
- Shell 在原有 27dp display-only 状态条内显示航行活动，不新增页面高度，也不把状态条变成隐藏按钮。
- 导航中显示目标航点序号与 runtime 已计算的 DTW；暂停、完成、等待 position 分别有真实文案。系统航迹显示 Recording、Paused 或 Save Pending。
- Navigation 和 Track 状态优先出现在有限状态条槽位；Chart/Navigation 页面、Start Tile 与未来 Cockpit 可以观察同一个 process truth。
- 中文为默认资源，英文资源完整对应；公制/海里显示继续服从同一全局单位偏好。

## Design decisions

- Consumer snapshot 保留 `ActiveNavigationSnapshot` 和 `TrackRecorderSnapshot` 本身，不复制 route library、active leg、DTW/BTW/XTE 或 recorder 状态，从结构上阻止 future Cockpit 建第二套计算。
- Consumer port 只做 flow composition 和 typed command delegation；它不拥有 session，不持久化，也不增加新的生命周期。
- Shell presentation 只格式化 runtime 已提供的事实。缺少 solution 时只显示目标航点，不猜测距离或方位。
- 沿用现有 `WpStatusStripItem` 的无点击语义和固定高度，满足状态可见性但不牺牲 App 内容区。

## Preserved invariants

- Active Navigation、Track Recorder 各自仍只有一个 process owner。
- Shell / Start / future consumer 不读取 route library 自己算导航数据。
- Shell 状态条仍为 display-only，不能打开 Preferences。
- Close 某个 App UI session 不停止全局航行或航迹记录。
- 状态文案不会把等待位置数据表达成有效 DTW/BTW。

## TDD evidence

- Red：首轮共享 consumer flow 测试暴露出测试 scheduler 尚未启动 eager collector；这是测试调度问题，不是生产并发问题。
- Correction：测试显式驱动同一 scheduler，验证后续 snapshot 仍按原对象传播，且 Navigation / Track command 精确转发给 owning runtime。
- Green：`MarineOngoingActivityPortTest` 1/1 PASS。
- `:feature:navigation:compileDebugKotlin` PASS。
- `:app-shell:compileStandaloneDebugKotlin` PASS。
- 按 Product Recovery Window，不在人工接受前用 presentation/string 断言重新锁死状态条造型。
- 全量 machine gate 留给 push CI。

## Human acceptance pending

- Start、Chart、Navigation 之间切换时，NAV / TRACK 状态连续且不重置。
- Track Recording 时返回 Yokuli Desktop，顶部显示记录状态且记录继续；Pause/Stop 后状态立即真实更新。
- Active Navigation 时顶部显示当前目标和 DTW；缺位置时不得残留旧距离。
- 状态条没有点击入口，且没有比原布局多占一行 App 内容。
- 中文/英文与全局单位切换后，状态条在同一渲染周期内一致更新。

## English summary

NAVREF-R09 exposes one process-owned ongoing-marine-activity contract for Shell and future Cockpit consumers. It forwards the exact Active Navigation and Track Recorder snapshots and commands, adds compact truthful activity to the existing display-only status strip, and creates no parallel route math, persistence, or lifecycle. CI and physical acceptance remain pending.
