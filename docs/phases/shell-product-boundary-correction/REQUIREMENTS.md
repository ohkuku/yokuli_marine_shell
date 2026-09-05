# Shell 产品边界修正 / Shell Product Boundary Correction

## 中文（主文）

Yokuli OS 是一个拥有应用内 WP 风格 Shell 的普通 Android 航海应用，不是 Android 桌面替代品。以下三条是当前产品合同，覆盖旧 Stage 报告和导入规范中的冲突表述，但不改写那些历史证据。

1. 设置、恢复页和 Engine 不得提供“打开 Android 桌面设置”入口或通用 `ACTION_SETTINGS` 逃生动作。正常的 Android“应用信息”上下文动作仍可保留，它不属于桌面设置。
2. Back 依次关闭临时层、取消编辑／事务、回退应用内页面并返回 Shell 桌面。已经位于 Shell 桌面时，Back 是无副作用的幂等动作，不结束 Activity、不退出进程，也不跳到 Android 桌面。
3. `ShellActivity` 与 debug-only `ShellLabActivity` 默认只允许竖屏。当前适配范围是竖屏与方屏；不把横屏作为产品能力、验收矩阵或运行时状态变化。Engine 的通用列数算法可以保留，但不代表横屏支持。

这三条不能削弱沉浸式全屏、虚拟 Back／Start／Search、可投递硬件键统一进入串行 Engine 的既有合同。

## English translation

Yokuli OS is a regular Android marine app with an in-app WP-style shell, not an Android Home replacement. The active product contract is:

1. Settings, recovery UI, and the Engine expose no Android Home/Desktop settings escape and no generic `ACTION_SETTINGS` action. The ordinary Android App Info context action remains valid.
2. Back dismisses transient UI, cancels edit/transactions, unwinds in-app routes, and returns to the Shell Desktop. At the Shell Desktop it is an idempotent no-op: it must not finish the Activity, exit the process, or reveal Android Home.
3. `ShellActivity` and debug-only `ShellLabActivity` are portrait-only by default. Portrait and square devices remain in scope; landscape is not a product capability or acceptance target. Generic Engine column-count support does not claim landscape support.

These corrections preserve immersive full-screen operation and the single serialized Engine path shared by virtual and deliverable hardware inputs.
