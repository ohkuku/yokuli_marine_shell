# TDD 日志 / TDD Log

## RED — 2026-09-06

- Baseline: `0bd086fc4923730207e75e8047655b6695b08c5c`.
- 新增当前产品边界静态合同，并把历史 Stage 9/10 测试改为接受最新直接需求。
- Reducer 测试要求桌面 Back 保持当前状态且不产生 effect。
- Activity stories 要求 Shell 桌面 Back 不结束宿主、恢复页不再出现 Android 设置出口、resolved Activity 固定为竖屏。
- `python3 .github/scripts/test_shell_product_boundary_correction.py` 按预期失败：生产代码仍含 Android 设置 action/effect 与 UI，Desktop Back 仍请求退出宿主，两个 Activity 仍为 `fullSensor`。
- 首次 JVM 定向命令被受限环境拒绝访问 Gradle wrapper lock；这是执行环境失败而非产品断言，Red 行为已由静态合同确定，提交后在获准的 Gradle 环境重跑。

## GREEN — 2026-09-06

- Red commit：`fa565696a239d5275104dd986dcdd81be68624f7`。
- 运行时实现 commit：`53b52a817f90496b68afd38101cb16f4fa0ef17a`。
- 自查文档修正 commit：`29373656723377da0ee99a14d6712ac46b907ac0`；补齐公开文档的 `English translation` 标题。
- 证据链修正 commit：`eaad0703575528339a5bfc0affb5f977880c2823`；恢复哈希锁定的 Stage 0 主规范原字节，最新覆盖关系只写入独立修正文档和 README。
- 新合同、Stage 9、Stage 10、Final Correction 定向 Python 合同全部通过。
- 临时隔离 Python 环境安装锁定的 `jsonschema==4.25.1` 后，全仓 Python 合同 `224/224` 通过。
- `./gradlew test lint assembleStandaloneDebug assembleStandaloneRelease --console=plain` 成功，`1236` 个 Gradle tasks 完成或命中缓存。
- API 34 模拟器执行三个新增 Activity stories，`3/3` 通过：桌面 Back 不结束宿主、恢复页只留应用内出口、Activity resolved orientation 为 portrait。
- CI／Release 产品表面脚本通过；Release APK manifest 中 `ShellActivity screenOrientation=1`，且没有 Android `HOME`／`DEFAULT`。

## English translation

The Red contract captured the obsolete Android settings escape, host-exiting Desktop Back effect, and sensor-based orientation. Green removed those paths, passed all 224 Python contracts, the full JVM/lint/build gate, three targeted API 34 Activity stories, and Release APK surface inspection. Stage 0's hash-locked source document was restored byte-for-byte after self-review found that adding a supersession note would invalidate its evidence chain.
