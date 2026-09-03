# Yokuli OS — TDD 开发规范

## 目标

Yokuli OS 使用严格、可审计的 Test-Driven Development。每个功能增量必须先定义行为，再提交失败测试，最后实现最小代码并重构。安全关键行为不得用截图或手工点击代替自动合同。

## Red → Green → Refactor → Record

### 1. Red：先描述用户行为

每个工作项先写一个短合同：

```text
Given  初始状态与数据来源
When   用户动作或系统事件
Then   可观察结果
And    不允许发生的副作用
```

然后创建最小失败测试并运行，确认失败原因正是缺失行为，而不是测试环境、编译错误或错误断言。

### 2. Green：只实现当前行为

实现使当前测试通过的最小生产代码。不提前迁移无关旧代码，不为了未来假设增加兼容层，也不弱化断言来换取绿色。

### 3. Refactor：在全绿状态整理设计

允许提取模型、接口和 fake，消除重复并改善命名。重构前后必须运行受影响模块测试；涉及 Shell、Runtime 或 Safety 边界时运行完整 gate。

### 4. Record：留下可追踪证据

每个完成增量必须：

- 更新 `CHANGELOG.md` 的 `Unreleased`；
- 更新行为合同或 ADR（若边界改变）；
- 在提交/PR 中记录 Red 测试名、Green 实现与实际执行命令；
- 明确 `UNVERIFIED_DEVICE` 或 `UNVERIFIED_HARDWARE`，不能把未执行验证写成通过。

## 测试金字塔

### Level 1 — 纯 JVM 单元测试

覆盖无 Android 依赖的模型和算法，运行最快，是主要 TDD 回路：

- App Registry 唯一性与 target resolution
- StartGridLayoutEngine 占位、碰撞、resize、恢复
- Shell Navigator 的 Home/Back/Recents 语义
- Live Tile 的无副作用状态投影
- freshness、来源仲裁与冲突策略
- Anchor、Trip、Navigation、Sonar 状态机
- 几何、NMEA parsing 和数据转换

命名采用行为而不是方法，例如：

```text
home_returns_to_desktop_without_stopping_anchor_runtime
stale_depth_is_never_projected_as_live
resize_reflows_tiles_without_overlap
```

### Level 2 — 模块集成测试

使用 fake clock、fake runtime gateway、内存数据库或测试 dispatcher，验证模块协作：

- ShellNavigator + RuntimeTaskManager
- DataBus + LiveTileProjection
- ResourceArbiter + 多 runtime owner
- repository + Room v1
- process-state serialization + restore

集成测试必须验证副作用，例如磁贴订阅不能启动 GNSS 或 NMEA。

### Level 3 — Android instrumented / Compose 故事测试

通过真实 `ShellActivity` 和导航链验证用户故事，不优先测试孤立 composable：

- 320×320、360×360、手机竖屏与宽屏布局
- Start ↔ All Apps 手势与字母跳转
- 长按、拖动、resize、pin/unpin
- App → Back/Home → Desktop
- Recents 恢复 UI task，不停止 domain task
- 全局告警覆盖 Desktop、App 与 Recents
- permission request 只由显式能力动作触发
- rotation、process recreation 与状态恢复

### Level 4 — 设备与实船门禁

自动化不能证明以下项目：真实 GNSS、脆弱单客户端 NMEA 网关、音量/振动、锁屏/省电、Wi-Fi 切换、方屏硬件性能和实船告警。它们使用人工 checklist，结果分别记录为 `VERIFIED_DEVICE` 或 `VERIFIED_VESSEL`。

## 每个功能的 TDD 切片

一个切片必须可以在一次短迭代内闭环。以 Desktop 为例：

1. Registry 返回唯一 App ID。
2. 默认布局只引用 Registry 中存在的目标。
3. 4 列网格可以放置 Small/Medium/Wide tile。
4. 重叠布局被拒绝。
5. 点击 tile 打开正确 target。
6. Home 返回桌面但 runtime 不变。
7. 长按进入 edit mode。
8. Resize 重排且不重叠。
9. 保存后重建进程仍恢复布局。

每一步单独经历 Red、Green、Refactor，避免一次写完整桌面后再补测试。

## 测试隔离规则

- 时间必须来自注入的 monotonic clock；测试不得依赖真实等待。
- 随机 ID 和采样数据使用固定 seed。
- Runtime 测试不得直接访问 Compose 状态。
- UI 测试不得重新实现领域算法作为 expected value。
- 网络、GNSS、传感器和文件选择默认使用 fake；真实硬件属于明确的额外 gate。
- 测试禁止依赖执行顺序和前一个测试遗留状态。
- flaky test 不能简单 retry 后当作通过；需记录并修复根因。

## CI 门禁

每个 PR 至少执行：

```text
compile
unit tests
lint/static analysis
instrumented integration tests
API compatibility launch smoke
debug artifact upload
```

合并条件：所有必需 gate 成功。失败时上传测试报告、logcat、截图和可复现信息；失败构建生成的 APK 必须标记 `UNVERIFIED`。

## PR 证据模板

```markdown
### Contract
Given ... When ... Then ...

### Red
- Test: `...`
- Expected failure: `...`
- Command: `...`

### Green
- Minimal implementation: `...`
- Command/result: `...`

### Refactor
- Boundary/name changes: `...`

### Verification
- Unit: PASS/FAIL/NOT RUN
- Integration: PASS/FAIL/NOT RUN
- Device: VERIFIED_DEVICE/UNVERIFIED_DEVICE
- Vessel: VERIFIED_VESSEL/UNVERIFIED_HARDWARE

### Documentation
- CHANGELOG updated: YES/NO
- Contract/ADR updated: YES/NO/N/A
```

## 首个 TDD 里程碑

Chart-first Shell Foundation 的第一批测试顺序：

1. `registry_contains_unique_app_ids`
2. `every_default_tile_target_resolves`
3. `layout_rejects_overlap`
4. `resize_reflows_tiles_without_overlap`
5. `all_apps_pins_a_typed_shortcut`
6. `anchor_tile_opens_chart_anchor_mode`
7. `square_start_keeps_system_reachable`
8. `home_does_not_stop_runtime_task`
9. `tile_projection_has_no_resource_side_effects`
10. `critical_alarm_outranks_every_other_overlay`

这些测试先红后，才创建对应生产实现。
