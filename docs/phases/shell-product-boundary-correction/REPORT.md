# Shell 产品边界修正报告 / Shell Product Boundary Correction Report

## 中文（主文）

状态：`VERIFIED_LOCAL`，等待 GitHub Actions 对最终证据提交复核。

本次按用户直接产品决定完成三项边界修正：

1. Yokuli OS 继续作为普通 Android 应用运行。Engine、ViewModel、设置页与启动恢复页已彻底删除 Android 桌面设置 action、effect、intent、文案和测试 tag；普通应用信息入口仍保留。
2. Back 的终点固定为应用内 Shell 桌面。临时层、编辑、事务、搜索、最近任务和应用内页面仍按原优先级回退；桌面 Back 返回相同 state 且无 effect，Activity 不结束。
3. 生产 `ShellActivity` 与 debug-only `ShellLabActivity` 均声明 `portrait`。当前产品适配继续覆盖竖屏和方屏，不再把横屏列入运行能力或验收矩阵。通用 Engine 的列数求解保持设备无关。

TDD 证据：

- Red：`fa565696a239d5275104dd986dcdd81be68624f7`
- Green runtime：`53b52a817f90496b68afd38101cb16f4fa0ef17a`
- Verified source HEAD：`eaad0703575528339a5bfc0affb5f977880c2823`
- Python contracts：`224/224 PASS`
- JVM／Lint／Standalone Debug＋Release：`BUILD SUCCESSFUL`，`1236 tasks`
- API 34 targeted Activity stories：`3/3 PASS`
- Release 产品表面与 manifest inspection：`PASS`

自查发现并纠正两项非产品问题：新增公开文档最初缺少标准 English translation 标题；给 Stage 0 主规范添加覆盖提示会破坏已批准哈希。最终版本补齐双语标题，并把哈希锁定文档恢复原字节，覆盖关系仅保存在本报告、需求文档和 README。

## English translation

Status: `VERIFIED_LOCAL`, pending GitHub Actions confirmation for the final evidence commit.

Yokuli OS remains a normal Android app with an in-app Shell. Android Home-settings actions, effects, intents, copy, and UI tags were removed while the ordinary App Info action remains. Back still unwinds every in-app layer but becomes an effect-free no-op at the Shell Desktop, so the host Activity is never finished. Production `ShellActivity` and debug-only `ShellLabActivity` are portrait-only; portrait and square layouts remain supported, while landscape is outside the current product and acceptance contract.

All 224 Python contracts, the full JVM/lint/Debug/Release gate, three targeted API 34 Activity stories, and Release product-surface/manifest inspection passed locally. Self-review also restored the Stage 0 master specification byte-for-byte after detecting that an inline supersession notice would invalidate its approved hash.
