# TDD 矩阵 / TDD Matrix

| 合同 | Red 证据 | Green 证据 |
|---|---|---|
| 不存在 Android 桌面设置出口 | 静态合同拒绝 action/effect、UI tag、资源文案和 `ACTION_SETTINGS` | Python 合同 + Release 源码扫描 |
| Back 终点是 Shell 桌面 | Reducer 测试要求桌面 Back 无 effect；Activity story 要求宿主不 finishing | JVM reducer + API 34 Activity story |
| 默认仅竖屏 | Manifest 合同要求两个 Activity 为 `portrait`；设备故事读取 resolved ActivityInfo | Python 合同 + API 34 Activity story |
| 既有壳输入语义不退化 | 保留模块 Back、临时层、编辑取消、虚拟／硬件输入故事 | Stage 0–11、C00–C12 与完整单元／设备门禁 |

## English translation

Static, JVM, and device tests jointly guard the absence of Android Home settings, the non-exiting Desktop Back boundary, and portrait-only Activities while retaining all existing in-app navigation semantics.
