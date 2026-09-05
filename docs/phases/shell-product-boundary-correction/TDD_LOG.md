# TDD 日志 / TDD Log

## RED — 2026-09-06

- Baseline: `0bd086fc4923730207e75e8047655b6695b08c5c`.
- 新增当前产品边界静态合同，并把历史 Stage 9/10 测试改为接受最新直接需求。
- Reducer 测试要求桌面 Back 保持当前状态且不产生 effect。
- Activity stories 要求 Shell 桌面 Back 不结束宿主、恢复页不再出现 Android 设置出口、resolved Activity 固定为竖屏。
- `python3 .github/scripts/test_shell_product_boundary_correction.py` 按预期失败：生产代码仍含 Android 设置 action/effect 与 UI，Desktop Back 仍请求退出宿主，两个 Activity 仍为 `fullSensor`。
- 首次 JVM 定向命令被受限环境拒绝访问 Gradle wrapper lock；这是执行环境失败而非产品断言，Red 行为已由静态合同确定，提交后在获准的 Gradle 环境重跑。

## GREEN — pending implementation

实现和完整门禁结果将在 Green commit 后补录。
