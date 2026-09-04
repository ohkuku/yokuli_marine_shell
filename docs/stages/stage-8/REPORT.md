# Stage 8 — Pin / Unpin / Context Menu

状态：`PENDING_HUMAN_REVIEW`。这是 Batch B 的最后一个独立 Stage；没有进入 Stage 9。

## 基线与范围

```text
Stage 7 commit / starting SHA: f5d59ba81267f7c3ce9f2120bda20c09a0386a14
branch: codex/launcher-engine-batch-b
```

All Apps 长按现在只向串行 Engine 派发 `OpenEntryContextMenu`，不再以 Compose `remember` 保存菜单或静默修改桌面。菜单只对 `PINNABLE` Entry 显示适用的 Pin/Unpin；App info 保持独立。

Pin 使用 `APPEND_AFTER_LAST_OCCUPIED_ROW` 语义创建并提交确定性 transaction，随后回到 Start、发出 `ScrollStartToReveal`，并在 Engine state 中保留可恢复的 reveal request。Renderer 滚到目标、用默认 spring 显示临时轮廓和轻微放大，收敛后确认 request。这个高亮幅度明确标为 `DERIVED_UNVERIFIED`；Stage 2.5 没有观察 Pin 动画，因此不声明 WP8 时序。

Unpin 只移除 Start placement，不改变 runtime Catalog、应用任务或 Feature 数据。Pin/Unpin 都显示双语、无固定超时的 Undo/结果反馈；Undo 从同一 transaction 恢复精确 before document。重复 Pin、不可固定和布局失败不会静默，显示明确 Notice。

Catalog 新 Entry 只进入 All Apps，不自动 Pin；移除 Entry 只删除相应 placement 并保留无关坐标。Catalog revision 变化会清除指向旧目录的 provisional transaction、Undo/transient 和无效 reveal，避免旧 Entry 被 Undo 恢复。

## TDD 与 Gate

初始静态合同：`FAILED (failures=5, errors=2)`。初始 JVM Red 因 `OpenEntryContextMenu`、`PinEntry`、`UnpinTile`、reveal 和 typed transient 尚不存在而编译失败。Green stories 覆盖 context-first、Pin 返回与定位、重复 Pin、Unpin 安装语义、双向 Undo 和 Catalog 增删。

最终累计 Gate：107 条 Python 合同测试通过；954 个 Gradle task 通过；17 条 API 34 Activity story 通过；Standalone/Home 双 Release 二进制审计通过；WP8 reference 人工批准哈希与语义验证通过；`git diff --check` 通过。

## 自审与纠错

自审在 Green 后发现并修复三项问题：`CancelTileOperation` 会把普通 Idle 错置为 EditIdle；小磁贴上原 48dp 编辑控件互相遮挡；HorizontalPager 预组合 Start 与 All Apps 时会产生两个 Undo 语义节点。最终实现分别恢复精确交互状态、采用标注为 `DERIVED_UNVERIFIED` 的紧凑小磁贴控件并保留 TalkBack 自定义操作、只在当前 Surface 渲染 transient feedback。对应 JVM 或 Activity 回归测试均已通过。

## English translation

Stage 8 moves the All Apps context menu and every Pin/Unpin mutation into the serialized Engine. Pin commits an append-after-last-row transaction, returns to Start, requests reveal, and offers Undo. Unpin changes only the Start document and never removes the installed entry. Duplicate or unavailable operations produce explicit localized feedback. Catalog additions do not auto-pin, while removals preserve unrelated coordinates and invalidate stale transactions. Pin timing was not observed in Stage 2.5, so the spring highlight is explicitly derived product feedback rather than claimed WP8 evidence.
