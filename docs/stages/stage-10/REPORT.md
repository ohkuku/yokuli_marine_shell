# Stage 10 — Durable Storage & Recovery

状态：`PENDING_HUMAN_REVIEW`。本提交只覆盖 Stage 10；Stage 11 尚未开始。

## 基线与范围

```text
starting SHA: abca537bea55ac67c33a3383adfadcee6345c45a
branch: codex/launcher-engine-batch-c
```

本阶段以 TDD 建立 Proto DataStore、持久状态迁移、确定性文档修复、启动崩溃循环策略、Safe Mode、Reset Start 和 Android Settings 逃生。

## Red / Green

Stage 10 的 8 项静态合同首次运行得到 `FEFF.E.F`，即 4 failures、2 errors、2 pass；Core Red 同时因 `LauncherPersistedState`、migration、recovery policy 和 restore action 不存在而编译失败。Green 新增 `adapter:shell-storage`，用单一 Proto message 保存整个 Launcher snapshot，而不是把布局拆成散落的 Preference key。

DataStore 的每次 document、theme/accent/language 和 startup-health 更新都在 `updateData` 中读取并写回完整 snapshot，避免并发调用互相覆盖。Proto 同时保留 layout lock、上次 Launcher page 和上次前台 token 的 schema 字段；pointer、分页进度、provisional proposal、transition 和 transient 不落盘。Legacy/future schema 和非法偏好会确定性迁移或回到默认值，损坏 protobuf 由 corruption handler 回退。

Engine 在 durable load 完成前处于 `RESTORING`，拒绝用户 mutation，但 Catalog Flow 仍进入同一串行 action queue。加载后的 StartDocument 再执行 Stage 3 的 profile、entry、duplicate、bounds、overlap 和 size repair；migration、repair 与 persistence failure 都转为 `LauncherIncident`，保留最近 32 条并交给 Android log host。不存在直接 `getValue()` 或未捕获 `first {}` 的恢复崩溃路径。

## Recovery 与自审纠错

启动健康状态以 120 秒窗口累计未完成 launch，第三次进入 Safe Mode；正常 stop 或稳定 10 秒后清零。自审发现并修复启动定时器可能在慢恢复期间提前清零 crash evidence：健康计时现在先等待 startup recording。Safe Mode 只在内存中使用默认布局并降低动效，不再静默覆盖用户布局或主题；用户只有点击 Reset 才清除 snapshot。

第一次 API 34 targeted story 揭示 Activity 重建时多个 DataStore 实例可能争用同一文件，存储已改为 `ShellApplication` scope 单例。原外部 Activity monitor 无法可靠观察系统 Settings，story 改为注入平台 intent boundary，精确验证 `ACTION_HOME_SETTINGS`。若设备没有该 Activity，生产 host 捕获 `ActivityNotFoundException` 并退回 `ACTION_SETTINGS`。

## 平台边界

Android physical HOME 仍由系统保留，普通 Activity 不能可靠截获；HOME flavor 依靠 Stage 9 已验证的 HOME intent、`singleTask/onNewIntent` 和本阶段 Recovery Surface 避免锁死用户，不宣称不存在的平台能力。虚拟 Back/Start/Search、Android Back、可实际投递的键盘/硬件键仍全部进入同一个 `LauncherInput -> LauncherAction -> LauncherEngine.dispatch` 路径；本阶段没有从 `VISUAL_ONLY` 录像编造按键灯效、触感或 latency。

## Gate

```text
Stage 0–10 Python contracts                         PASS (122/122)
Core Engine focused durability/recovery tests       PASS
Proto DataStore debug/release unit tests             PASS
Gradle test + lint + dual Debug/Release + androidTest PASS (1052 tasks)
API 34 real ShellActivity stories                    PASS (23/23)
Standalone/Home Release product-surface audit        PASS
Release metadata / CI / secrets contracts            PASS
WP8 reference semantic validation                    PASS (HUMAN_REVIEWED)
git diff --check                                     PASS
```

两个 Release APK 继续只包含 ShellActivity、Chart 和 Settings，并排除 Shell Lab、Cockpit、Library 与旧 System；只有 Home flavor 增加 HOME/DEFAULT intent category。Stage 10 Gate 已通过，Stage 11 没有在本提交中启动。

## English translation

Stage 10 makes committed launcher state durable, migratable, repairable, and recoverable through one atomic Proto DataStore snapshot. Restore blocks mutations without losing catalog changes, repairs invalid documents deterministically, and records migration, repair, and persistence incidents through the serialized Engine. Three recent incomplete starts activate a non-destructive Safe Mode that temporarily uses the default layout and reduced motion while retaining Chart, Settings, reset, and Android Home-settings escape.

The API 34 gate caught and corrected duplicate DataStore ownership during Activity recreation. Self-review also corrected a startup-health race, missing migration incident propagation, and destructive Safe Mode persistence. Android's physical HOME remains OS-reserved; the HOME intent plus the recovery surface is the truthful platform bridge. All 122 Python contracts, 1052 Gradle tasks, 23 Activity stories, dual Release inspection, and supporting gates pass. Stage 11 has not started.
