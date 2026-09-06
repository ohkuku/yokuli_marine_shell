# W11 Product & Engineering Contract — Active Navigation Is a Real Session

版本：1 · 2026-09-06  
状态：施工候选

## Intent / 产品愿景

Active Navigation 是一次明确开始、可以暂停、能够恢复并由用户结束的航行会话。它把保存路线与 OS 已选择的实时
位置事实结合，回答“下一个点在哪里、离它多远、我偏离了多少、这一段和全程进行到哪里”；它不是 Chart 上的一组
装饰数字，也不是自动驾驶控制器。

## Experience / 用户体验

- 用户从一条已保存且 revision 精确匹配的路线开始导航，随后在 Chart 底部看到下一点、DTW、BTW true、XTE、ETA
  和全程进度。
- 暂无新鲜位置时，会话仍存在但不显示推算出来的假 solution；暂停和进程恢复也不会沿用上一个进程的 live fix。
- 上一航段、下一航段、暂停/继续和停止都是明确操作；自动前进只有用户选择 arrival-radius policy 后才发生。
- 进程终止后，原 ACTIVE 会话恢复为 PAUSED，用户确认继续后仍须等待当前进程的新鲜 OS 数据。
- Chart 只呈现会话与提供开始入口；会话生命周期由进程 runtime 持有，不由页面是否打开决定。

## Domain / 领域关系

- Navigation Library 持有不可变 RoutePlan；Active Navigation 持有 route ID + exact revision、active leg、策略和状态。
- marine-data 仍是 source selection 的唯一真相。Navigation 只读 resolved Position/SOG/COG，不选择输入、不申请权限、
  不启动 socket，也不静默切到 Phone。
- DTW/BTW/XTE/progress 是 WGS84 route + selected fix 的派生事实。COG 不是 heading，route bearing 也不是传感器 heading。
- 轻量 session 独立持久化；实时 fix、solution、精确位置和数据源 endpoint 不落盘。

## Opportunities / 可发展空间

未来可以增加不同 arrival policy、off-course 提醒、航段备注、route revision recovery choice 与更丰富的 progress visual。
这些能力必须保持可解释、可停止，并与自动舵/NMEA 输出形成显式的新合同；W11 不为它们预建控制接口。

## Non-negotiables / 不可协商边界

- Start 必须绑定保存路线的 exact revision；路线不存在、过短或已变化时原子拒绝。
- ACTIVE、PAUSED、COMPLETE 与无 session 的 STOPPED 语义不混用；命令由 runtime 串行处理。
- 只有 LIVE/HELD 且带当前进程 monotonic provenance 的 position 才产生 solution；STALE/INVALID/UNAVAILABLE 不产生。
- ETA 只有有效正 SOG 才存在；BTW 明确为 true bearing；XTE 符号定义固定为航段右/右舷为正。
- session persistence future/corrupt/IO failure 必须可见，不能安装清空 handler。
- 不持久化实时 fix、solution、坐标、SOG/COG 或 provider/endpoint token。

## Forbidden outcomes / 禁止结果

- 增加 autopilot、steering、NMEA transmit/output 或网络控制端口。
- 把旧 `navigationActive` boolean 恢复为真实 session，或凭存在一条 route 自动开始。
- 页面关闭时停止 runtime；进程恢复后自动 ACTIVE 并复用旧 live solution。
- 无 SOG 时按 planned speed 伪造实时 ETA，或用 COG 冒充 heading。
- 为 active session 新建第二份 route library，或修改/复制旧 Room route 数据。

## Compatibility / 兼容策略

W10 的现有 Room library 仍是路线唯一持久化真相；新 session store 只记录会话引用与策略。旧 map session 的
`navigationActive` 不迁移。Chart 旧 Routes UI 在 W12 完成前继续存在，并新增显式 start handoff；没有 session 时 W09
预留的 strip seam 保持完全空白。

## Acceptance stories

1. 用户从两点以上已保存路线开始，Chart 回到地图并显示真实 next/DTW/BTW/XTE/progress；停止后 strip 消失。
2. Start 携带旧 revision 时被拒绝，当前会话与路线数据不变。
3. NMEA/Phone 的 OS resolved position 变为 STALE 或 INVALID 时会话保留，但 solution 消失且不静默换源。
4. 有正 SOG 时显示 ETA；无 SOG 或 0 节时 ETA 明确不可用。
5. arrival-radius policy 到达中间 waypoint 只前进一段；manual policy 永不自动前进。
6. ACTIVE 会话在新进程恢复为 PAUSED；路线缺失/revision 变化时拒绝恢复并清除无效引用。
7. 用户离开 Chart 后输入仍可更新 runtime；回到 Chart 时看见同一会话而非页面私有副本。

## Evidence required

- JVM：WGS84 solution、freshness/ETA、串行 controls、arrival policy、revision mismatch 与 process restore。
- Adapter：session proto round-trip、future/corrupt failure 不清空、marine-data resolved projection 不泄露 endpoint。
- API 34：真实 session strip 展示和 typed controls；W12 再覆盖完整 App journey。
- 静态：无 output/autopilot API、runtime 进程 ownership、Chart seam 仅在 session 存在时注入。
- W16 人工：航行输入、真机进程恢复、方屏可读性与物理 Back → Start。

## Existing code landmarks

优先审阅 W10 Navigation Library port、`MarineSourcePositionPort` 的 source/provenance 规则、W09 Chart strip seam、现有
Room adapter、process-owned `ShellApplication` 和 Map route preview。它们是当前事实，不强制最终 class 或文件结构。

## Implementation freedom / 实现自由

可以选择 actor、mutex/reducer、独立 adapter 或复用现有 store，只要命令串行、输入单一、恢复明确、数据有界且满足故事。
不得为匹配文档标题创建无行为层，也不得把业务 runtime 放进 Compose。

## English translation

Active Navigation is a deliberate, persisted passage session over an exact saved-route revision and the OS-resolved marine-data inputs. It derives WGS84 DTW, true BTW, signed XTE, progress, and ETA only when valid SOG exists. A restored ACTIVE session becomes PAUSED and waits for fresh process input. UI never owns the runtime, stale inputs produce no solution, and W11 adds no autopilot, steering, NMEA output, or second route store.
