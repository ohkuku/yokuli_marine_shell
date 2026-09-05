# C08 GPX 1.1 互操作与只读分段轨迹报告

> English translation: C08 adds bounded GPX 1.1 import and truthful export for places, saved route plans, and immutable segmented tracks. Preview is non-mutating, confirmation is one durable transaction, unknown extensions are explicitly not retained, and no track is presented as an active navigation recording.

## 状态

- package：C08
- baseline：`015b4798eb725afbdd55bf8e96e1175aaf51b590`
- implementation candidate / cumulative verified SHA：`d711ac843391622e26a3b2077e124f206c9f90dd`
- status：`VERIFIED_LOCAL`
- hosted CI：最终同 SHA 证据留给 C12；本报告不把本地 Gate 写成托管结果

## 交付

1. 系统文件选择后使用 SAX 流式解析 GPX 1.1；文件 50 MiB、总点 200,000、每条路线 2,000 点、文本 8 KiB、XML 深度 32 均为显式上限，超限失败而非截断成功。
2. DOCTYPE 在字节流入口被拒绝，外部实体、外部参数实体、外部 DTD 与外部解析均关闭或拒绝；解析不会读取外部文件、schema 或 metadata URL。
3. `wpt` 映射为地点、有效 `rte` 映射为正式路线、`trk/trkseg` 映射为只读分段轨迹。单点路线只进入预览警告，不成为可运行计划；空导入批次不能写入孤立 import record。
4. 预览显示数量、范围、警告和逐项选择；同 SHA 重复导入必须明确选择“作为副本”，并生成新的稳定 ID。只有 durable revision ack 后才显示导入成功。
5. Room schema v4 用独立 track、segment、point 和 import-record 表持久化；v3→v4 是显式非破坏迁移，分段、时间、海拔、摘要、revision 和导入时间关闭重开后保持。
6. renderer 每段轨迹生成独立 Feature，不跨远距离缺口补线。大轨迹只在后台生成显示 LOD，原始点不改写；所有 MapLibre source mutation 显式回到 Android 主线程。
7. 地点、路线、轨迹均可保存或分享 GPX；轨迹输出仍为 `trk/trkseg`。取消目标、写入失败、保存成功、准备分享、已打开系统分享页和分享失败是不同状态，“已打开分享页”不声称接收方已保存。
8. Release 不申请全盘存储权限；导入使用受限 URI 读取权限，保存使用 SAF，分享使用只暴露 `shared-gpx/` 的 FileProvider cache 路径。

## Red、自查与纠错

- 初始 Red 固定混合对象、重复摘要、分段身份、Room v4、预览确认、导出类型与真实状态；缺失 API、schema、UI 和 renderer 路线时按预期失败。
- 第一轮反证补了事务碰撞、解析安全、分段渲染和独立 XML parser；Android SAX 对可选安全 feature 的差异促成了强制字节流 DOCTYPE guard，而不是依赖某个 parser 实现。
- 第二轮自查先用失败测试暴露空批次、日期线 bounds 和英文 fallback 名称，再改为拒绝空批次、最短日期线范围和中立 `GPX WPT/RTE/TRK n` 名称。
- 累计设备 Gate 初跑出现分散超时；冷启动最小 smoke 稳定复现 `CalledFromWorkerThreadException`。新增 Red 合同后把后台轨迹计算与主线程 MapLibre mutation 明确隔离，最小 smoke 1/1、完整 Shell 51/51 随后通过。
- 历史 C06 Gate 曾把数据库当前版本锁死为 3；修正为“当前版本不得低于 3 且 v1→v2→v3 迁移链必须保留”，没有放宽迁移或破坏恢复合同。

## 聚焦与累计证据

- C08 repository contract：6 passed。
- GPX domain：11 tests；import coordinator：5；独立 segment renderer：1，全部通过。
- API 34：真实离线 renderer/包 15、Room 7、Shell 51，0 failure/error/skipped；Shell 内含 2 条 GPX UI 故事。
- 当前 SHA 累计：196 个 Python repository contracts；323 个 JVM XML tests；全仓 test、lint、Standalone Debug/Release 共 1207 Gradle tasks，0 failure/error/skipped。
- 产品表面审计：Release 仍仅 Chart + Settings，无 HOME、Shell Lab、位置权限或 Google Maps key 依赖。
- Debug APK SHA-256：`396ac0dc8299974828be79e90401da36a1ca06fab688c5da746777fe94e55886`。
- unsigned Release APK SHA-256：`668d56587bf95a0a46b2ce3995e73c38e57cbac26fe2c06431a902379fea0b3f`。

## 未宣称完成

- 未知 GPX extensions 明确不保留；当前支持字段往返不等于任意厂商 GPX 全字段无损。
- “已打开分享页”不等于外部 App 已接收或保存；Android chooser 没有可靠的接收方完成回执。
- C09 的路线走廊实际图块检查与合法来源获取、C10 的只读位置观测质量、C11 的动态地图磁贴和 C12 的最终同 SHA 托管发布证据尚未完成。
- 三星方屏实体机触控与大 GPX 性能仍属于独立人工审核，不由 API 34 模拟器结果代替。
