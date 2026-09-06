# 从这里开始 · Chart Library

这是给 Codex 的执行入口。任务是落实完整需求，不重新写一份设计报告。

## 使用方式

将此目录放入目标仓库的 `docs/phases/chart-library/`。只读旧仓作为算法参考；不要改变其分支或文件。第一轮执行 `tasks/CL00.md`，之后按 `TASK_INDEX.md` 一张一张推进。下载包不包含产品实现，也没有修改你的repo。

## 一次确认、后续引用

行为权威：`REQUIREMENTS.md`；已核实代码：`IMPLEMENTATION_MAP.md`；进度：`EXECUTION_STATE.json`。先用标题/局部范围读取对应节，不把每张工单都当作重新阅读全部项目的请求。

开工检查实际branch/HEAD/dirty state，特别是当前NMEA相关未提交工作；没有授权不checkout/rebase/reset/clean，不push/merge/release。文档内SHA是审阅快照，不是要求强制回退。

每张工单：读取相关节/路径 → 写本范围测试（适用时先Red）→ 最小实现 → 运行目标测试及受影响边界编译/集成 → 更新机器可读进度。不要只看测试文件名就声称测试通过。未知API按当前锁定版本定点查证，不广泛库调研。

## 不可削弱的五条

1. 海图库是独立Shell应用；Chart没有第二套资源维护UI。
2. 支持的本地文件夹原件默认零复制、零写入；不支持直读时提示并让用户明确选择单张复制。
3. SAF到SQLite到MapLibre端到端必须实证，不把content URI当普通路径。
4. 现有map_packages/ID/历史/地图与桌面数据必须迁移兼容；不删除原件。
5. BASIC/可显示/离线就绪/FULL_VERIFIED/航海适用性不能混为一个“正常”。

## 输出约束

只报告修改文件、实际命令/退出码、证据路径和偏离/阻塞；不输出成功构建全日志。事实范围不足写NOT_RUN/BLOCKED，不编造设备结果。工程默认参数可依据测量调整，但不能用调参数掩盖问题。

## 可直接粘贴的首轮指令

```text
继续当前 yokuli_marine_shell 工作树，执行 docs/phases/chart-library/START_HERE.md
以及 tasks/CL00.md。本轮只做CL00。复用IMPLEMENTATION_MAP已有审阅结果，
仅核对实际HEAD差异，不重新做广泛调研。不要切分支、覆盖未提交工作、push或merge。
结束只给基线差异、真实接入点、测试/阻塞和下一张工单。
```

下一轮只需：

```text
按 docs/phases/chart-library/START_HERE.md 执行 tasks/CL01.md。
保留前序已确定接口和约束；仅查本任务相关文件。完成目标测试并更新EXECUTION_STATE。
```

后续替换工单编号即可。文档交付时所有任务为NOT_STARTED，这是模板，不是完成记录。
