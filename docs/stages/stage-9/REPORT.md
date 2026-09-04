# Stage 9 — Navigation / Motion / Immersive / Virtual Keys

状态：`PENDING_HUMAN_REVIEW`。本提交只覆盖 Stage 9；Stage 10 尚未开始。

## 基线与 TDD

```text
starting SHA: 8aa88557e16ca3c2848df94ed8faf076e5678ea8
branch: codex/launcher-engine-batch-c
reference hash: af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
```

Stage 9 静态合同首次得到 `4 failures / 3 errors`；Engine 与 Android key adapter Red 因 Search、Recents、内部 token 返回栈、host-exit effect、`LauncherInput` 和按键 adapter 尚不存在而编译失败。后续又以独立 Red 锁定 Search/Recents 在离开 Start 前必须取消 provisional layout。

## 统一输入与真实平台边界

Back、Start、Search、Recents 现在是 `core:shell-contract` 的统一 `LauncherInput`，虚拟键、Android Back callback 和 Activity 能收到的 `KEYCODE_BACK / ESCAPE / HOME / SEARCH` 都映射到同一 `LauncherAction`，经过 Stage 4 的单一串行队列。Start/Home 回到 Start、保留内部任务并取消 provisional 操作；Search 是对 runtime Catalog 的真实可筛选 overlay；长按 Back 打开真实内部 task Recents，而不是静态 mock。

Android reserves the physical HOME key before a normal application can intercept it. 因此生产系统 Home 的正确路径是 `home` flavor 的 HOME intent 与 `singleTask/onNewIntent`，而不是声称 `dispatchKeyEvent` 一定能收到实体 HOME。外接键盘或测试设备若实际投递 `KEYCODE_HOME`，它会走同一个 Start action。普通 App 也不能永久禁止边缘手势显露系统栏；Yokuli 会在创建和重新获得 window focus 时重申 immersive sticky 行为，但不冒充 kiosk。

虚拟键 glyph 家族来自 Stage 2.5 录像的 `VISUAL_ONLY` 证据。录像没有按键激活、灯光、触觉或区域高度证据，因此 54dp 屏内区域和 Android 平台 `VIRTUAL_KEY/LONG_PRESS` 触感明确为 `DERIVED_UNVERIFIED` 产品适配；没有实现伪造的 WP8 灯效、按压时序或力度。

## 导航与动效

内部 App Task 使用 opaque LaunchToken back stack，Settings 子页不再由 Activity 的 Compose local state 决定。Back 顺序为 transient、provisional edit、EditIdle、内部 route、内部根/All Apps 回 Start、standalone host exit；Home 总是回 Start 且不销毁 tasks。Recents 可恢复现有 Chart/Settings task。

Turnstile/slide 使用人工批准 profile 的 700ms page settle、1000ms app-open 可见区间和 750ms Back return 区间。Android “移除动画”设置切换到短 fade。所有非安全动画由 Compose transition/Animatable 驱动，target 改变会取消旧协程并从当前视觉状态接管。

Chart 的 Google `MapView` 在 incoming transition settle 前不会构造；期间只渲染轻量、无虚构海事事实的 transition plane。离场开始后 MapView 立即从 3D plane 中卸下，因此它不参与 shared 3D transform。

## Gate 与自审

首次 22 条 API 34 Activity story 全部通过。自审随后发现 Search/Recents 可在 provisional layout 上方打开，以及 catalog 移除任务时 Recents return surface 可能悬空；实现已在后续累计 Gate 前取消 provisional state 并修复 return surface。

提交前累计 Gate 又发现 Stage 2 Compose-host 与 Stage 4 ViewModel 所有权的静态合同表达发生漂移；Stage 9 在未进入 Stage 10 的前提下恢复了既定 host resolution 与 Activity ViewModelStore 获取路径，并从头重跑。最终结果：114 条 Python 合同测试通过；Gradle `test`、`lintStandaloneDebug`、双 flavor Debug/Release 和 instrumentation APK 共 954 个 task 通过；API 34 上 22/22 Activity stories 通过；双 Release APK 均保持 Chart + Settings 且仅 Home flavor 增加 HOME/DEFAULT；发布元数据、Secrets workflow、CI contract、WP8 reference hash 与 `git diff --check` 全部通过。

## English translation

Stage 9 unifies virtual keys, Android Back, and deliverable keyboard/hardware key events as typed LauncherInput values processed by the single serialized Engine. Search filters the real runtime catalog; long Back opens a real internal-task switcher; HOME intents reuse the single Activity and preserve tasks. Android's physical HOME key is normally reserved by the OS, so the HOME launcher intent is the truthful production bridge. Virtual-key size and platform haptics are explicitly derived and unverified because the reviewed recording proves only the visible glyph family. Reviewed motion intervals drive navigation, reduced motion uses a short fade, and Google MapView mounts only after the lightweight transition plane settles.
