# Yokuli Marine Shell 产品模型纠错报告

```text
machine:          MACHINE_VERIFIED
human fidelity:   HUMAN_FIDELITY_PENDING
physical devices: PHYSICAL_DEVICE_PENDING
branch:           codex/marine-shell-final-correction
machine parent:   23413c7f3297ca1937a67f32e019885eef189ee4
```

本轮从锁定基线完成十个独立提交 Slice：基线审计、普通沉浸式应用身份、原子视觉 Surface、Marine Bridge、安全窗口 Chrome、六尺寸 Marine Tile、自适应 Packer、桌面直接编辑、Settings 视觉重做、Motion/Search 与最终质量 Gate。历史 Stage 合同没有被静默改写；本报告只封口产品模型纠错，不启动 GPS、NMEA、Anchor、Trip、Navigation 或 Survey。

## 产品结果

- Yokuli 是沉浸式全屏 Android 航海应用，WP 风格 Shell 位于应用内部；Manifest 不再声明 Android `HOME` / `DEFAULT`，系统 Home 正常离开 Yokuli。
- 冷启动进入 Desktop；恢复已有 task 时保持当前内部 Surface。底部罗经花是 Yokuli Desktop/Bridge，不冒充 Windows 标志或系统 Home。
- Desktop、Module List、Search、Recents 与 Module 是一等 Surface。Search → Chart、Bridge、Back 和重复 Launch 使用串行 Engine action 与原子 transition，Release 中间帧不会暴露错误 Surface。
- Release Catalog 仍严格只有 Chart 与 Settings；地图只表达已配置／仅浏览，不声称定位、网络、授权、账单或图块已验证。
- Window Chrome 使用 safe drawing、display cutout、rounded corner、IME 与 system-gesture insets；不会把模拟方屏冒充真实三星硬件结论。
- Tile 支持 1×1、2×1、2×2、4×2、2×4、4×4。文档持久化 rank/size/group，自适应 Packer 生成 viewport placement；显式 Spacer 才保留空位。
- Desktop 直接长按、拖拽、动态 reflow、Resize 预览／提交／取消；pointer-frame offset 留在 Renderer，Engine 只接收串行语义动作，小 Tile 编辑目标至少 44dp。
- Settings 使用中英文、黑白高对比排版与 4×N 紧凑色样；Accent 仅承担选择、焦点和必要强调。

## TDD 与机器证据

最终质量 Red 首先准确暴露 3 个缺口：新增性能旅程、当前 Profile 和最终 Gate/报告。Green 后，自审没有接受“测试进程成功”作为性能证据：第一次 11/11 运行中 `settingsScroll` 的 `gfxFrameTotalCount=0`，因此新增零帧拒绝合同并调整测量窗口。定向复测 5/5 通过，且每轮观察到 2 个目标帧。

```text
Final correction Python contract                 15/15 PASS
Macrobenchmark correction + retained journeys    11/11 PASS, 5 iterations each
Settings zero-frame regression rerun               1/1 PASS, 5 iterations
API 34 ShellActivity stories                     29/29 PASS at motion slice
Baseline Profile                                  1984 current rules
Startup Profile                                   1605 current rules
Release production registry                       Chart + Settings only
```

API 34 模拟器趋势中，cold/warm Start TTID 中位数分别为约 `675.67/280.39 ms`；Desktop ↔ Module List P50/P90 为 `29/46 ms`；Search → Chart 为 `42/89 ms`；30 个混合 Tile 拖拽为 `38/101 ms`；2×2 → 4×4 Resize 为 `57/81 ms`；320dp 圆角视口为 `31/48 ms`；Settings 修正后观察到 2 帧/轮，P50/P90 为 `42/42 ms`。这些值只用于同一模拟器环境的相对趋势，不是硬性能门限，也不能证明“输入后一帧反馈”或“无明显掉帧”已经在真机通过。

可执行封口脚本为 `.github/scripts/run_marine_shell_final_gate.sh`：host 模式运行所有 Python/helper 合同、WP8/Golden 语义验证、Gradle unit/lint/Debug/Release/benchmark 构建与 Release APK 产品表面审计；device 模式再运行完整 Activity stories、全部性能旅程和要求 11 条完整且非零交互帧的汇总器。GitHub CI 与本地 Gate 可以在提交后并行；任何失败必须以独立纠错提交处理，不能篡改本报告中的未验证边界。

## 仍需人工关闭

以下内容明确没有被 Codex 写成通过：

```text
Golden candidates:        CANDIDATE_PENDING_HUMAN_REVIEW
subjective WP fidelity:   HUMAN_FIDELITY_PENDING
Samsung square hardware:  PHYSICAL_DEVICE_PENDING
ordinary physical phone:  PHYSICAL_DEVICE_PENDING
60 / 90 / 120 Hz:         PHYSICAL_DEVICE_PENDING
```

本轮不等待、触发或管理旧 Release；新分支 CI 只作为当前提交的自动反馈。最终 Alpha 发布仍由 owner 在人工视觉和真机证据可接受后决定。

首个 Hosted run `33934748331` 的 build、API 34 stories 与 API 36 smoke 通过，但 Macrobenchmark 暴露 benchmark task 复用没有显式 Desktop reset。后续纠错只为 `benchmark` build 增加 intent handshake，并经串行 Engine queue 恢复默认测试起点；普通 Release 的 task resume 继续保留当前 Surface。最终接受以纠错提交的新 run 为准。

纠错 run `33936328836` 随后暴露 Light Theme story 只等待 Compose idle、没有等待异步持久化后的 window `SideEffect`。测试改为等待真实 status/navigation bar 变白后再断言；生产主题、动画和导航代码未为测试放宽。最终接受继续以后续提交的新 run 为准。

## English translation

This correction is `MACHINE_VERIFIED`, while subjective fidelity and physical devices remain explicitly pending. Yokuli is now a normal immersive Android marine application with an internal WP-inspired shell, not an Android HOME provider. System Home leaves Yokuli; the in-app compass Bridge navigates within Yokuli. Release remains limited to Chart and Settings and does not claim unimplemented marine capabilities or operational map readiness.

Desktop, Module List, Search, Recents, and Module are atomic visual surfaces driven by serialized Engine actions. Search launches Chart without an intermediate launcher frame. Safe chrome derives from real platform inset inputs. The six-size marine tile document stores rank, size, and group and is packed adaptively for each viewport. Direct edit/drag/resize keeps pointer-frame offsets in the renderer. Settings uses restrained bilingual typography and compact accent swatches.

The final TDD audit rejected a zero-frame Settings benchmark false positive, regenerated current Baseline/Startup Profiles, and covers eleven five-iteration emulator journeys. Emulator values are trend evidence only, not calibrated frame gates. Golden acceptance, subjective WP feel, Samsung-square hardware, ordinary phones, and physical 60/90/120 Hz verification remain `PENDING`. The old release pipeline is outside this task; owner approval is still required before an Alpha is declared final.

Hosted run `33934748331` passed build, API 34 stories, and API 36 smoke, but exposed that reused benchmark tasks had no explicit Desktop reset. The correction adds a build-type-guarded benchmark intent handshake that restores the deterministic start point through the serialized Engine queue. Normal Release task resume still preserves its current surface. Final hosted acceptance belongs to the correction commit's new run.

Correction run `33936328836` then exposed that the Light Theme story waited only for Compose idleness, not the window `SideEffect` following asynchronous preference persistence. The test now waits for the real status/navigation colors before asserting; production theme, motion, and navigation behavior were not relaxed. Final hosted acceptance remains assigned to the next correction run.
