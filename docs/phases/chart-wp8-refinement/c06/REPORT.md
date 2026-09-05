# C06 路线草稿与正式计划报告

> English translation: C06 delivers durable route drafts and saved plans, ordered waypoint editing, WGS84 planning facts, revision-aware save transactions and a complete create/save/find/preview/edit/save UI journey. It does not start navigation or claim GPX, coverage, download, or live-position completion.

## 状态

- package：C06
- baseline：`1c4aa7c6b155c75bd57f826ad8c93396f302059e`
- implementation candidate：`15d0f1a91623cb4121e92cf82bf9dc01952eb982`
- cumulative verified SHA：`15d0f1a91623cb4121e92cf82bf9dc01952eb982`
- status：`VERIFIED_LOCAL`
- hosted CI：留给 C12 的最终同 SHA Gate；本报告不把本地 Gate 冒充托管结果

## 交付

1. `RouteDraft` 与 `RoutePlan` 具有不同生命期；稳定对象/航点 ID、revision、名称、备注、有序坐标快照、地点来源 revision 和编辑基线均可持久恢复。
2. 多个草稿可并存，只有一个活动编辑 ID。新建、测量转换和切换不会覆盖旧稿；正式计划预览不会偷偷创建 dirty draft。
3. 路线点支持追加、精确插入、移动、删除、列表重排、反向、undo/redo 和全线相机请求；相邻同坐标明确提示，非相邻返回和自交不冒充安全校验。
4. 每段展示 WGS84 距离与起始真方位；总距离使用同一事实。计划船速默认为 null，未填仍可保存且用时为破折号；非法输入拒绝，极端输入提示，估算不称实时 ETA。
5. 保存新建或按 base revision 更新同 ID；复制、反向复制和另存才分配新 ID。revision 冲突保留草稿，不执行 last-write-wins。
6. 路线持久提交具有 optimistic transaction：ack 才结束 pending；失败回滚正式计划并恢复精确草稿。连续资料写被 mailbox 合并时，旧/新 ack 与失败仍保持 durability 真值。
7. 同时只允许一个正式路线提交，避免 transaction 被快速第二次保存覆盖；pending 计划的编辑、复制和删除入口暂不可用，并显示事实状态。
8. Room schema v3 通过显式 v2→v3 migration 承接新增字段，无 destructive fallback；生产 renderer 只画当前活动草稿或一条活动正式计划。

## Red、修正与自查

- 初始 Red 覆盖对象分离、nullable speed、完整点编辑、复制/反向、revision 冲突、删除撤销、保存后预览、Room v3 和真实 Compose 旅程。
- 第一轮自查补出路线腿事实、相邻重复可见反馈、保存后 session 选择，以及旧 ack 与较新 pending 写并存的 transaction 完成语义。
- 第二轮异步反证覆盖合并写失败回滚、已确认路线不被后来无关失败误标、快速第二条路线保存不覆盖 transaction，以及 pending 计划不可变更。
- 累计 Gate 纠正 C05 migration 静态匹配和旧 `activeRoute` 字面禁令，使历史 Gate 随 schema/合法路线模型演进但不弱化原风险边界；显式简中资源与默认中文完全一致。

## 聚焦与累计证据

- C06 repository contract：5 passed。
- `core:map-domain`、`adapter:map-storage`、`feature:chart` 聚焦测试与 Android test compilation：passed。
- API 34 C06 route story：画→存→找→预览→编辑→同 ID 再存，1 test passed。
- API 34 Room：完整路线 round-trip、坏记录隔离、durable reopen、v1→v2 与 v2→v3 migration，6 tests passed。
- 当前 SHA 完整 Host Gate：185 个 Python repository contracts；全仓 test、lint、Standalone Debug/Release、benchmark 与产品表面审计通过，共 1207 Gradle tasks。
- JVM XML：294 tests，0 failure/error/skipped。
- 当前 SHA API 34：offline renderer 6、Room 6、app-shell 49，全部通过。
- Debug APK SHA-256：`1c5df1c621993695a502e6a6108e4a05f0381f5d663a3eb846414ff3c205c71f`。
- unsigned Release APK SHA-256：`8ca772e2d3c8052647d514fcc4e98dc5fd134b1ff34c2fc9a86191d9209f8a78`。

## 未宣称完成

- C07 的可取消海图包作业、版本替换、安装 journal 和崩溃恢复尚未完成。
- C08–C10 的 GPX、实际覆盖/来源获取和只读位置观测尚未完成。
- C11/C12 的地图磁贴动态摘要、最终同 SHA 托管 CI、Alpha 产物和三星方屏实体机审核仍未完成。
