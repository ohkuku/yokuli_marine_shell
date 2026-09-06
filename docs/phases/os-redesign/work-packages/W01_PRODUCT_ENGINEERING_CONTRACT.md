# W01 · App Model & Migration Scaffold — Product & Engineering Contract

## Intent

冻结最终五 App 的稳定身份，并让当前 Start 文档能在未来功能真正到达时安全迁移。W01 只铺设迁移能力，
不改变今天用户可见的生产 App。

## Experience

- 今天升级：用户仍看见当前五个已完成 App，磁贴位置/尺寸/分组不变。
- Data 完整交付的那次升级：两个旧数据入口合为一个 Data，最早磁贴原位保留，不产生重复入口。
- Preferences 完整交付的那次升级：Settings 原磁贴原位成为 Preferences。
- 任一目标还没有真实 host 时，旧入口继续工作；不会迁成点不开的磁贴。

## Domain

- 最终 App identities：Chart、Chart Library、Data、Navigation、Preferences。
- launcher schema 与 product-model migration version 分开：前者描述存储形态，后者描述产品身份演进。
- entry alias migration 同时处理 Start placement 与最后前台 token；不碰业务数据库。

## Opportunities

后续 W 可以在目标 App 安装时启用相应 migration step；相同机制可处理安全的 token namespace 演进，
不需要让 Shell Engine 认识 marine App 名称。

## Non-negotiables

- v1 才能先于 v2；目标 entry 未安装时必须停住。
- 幸存 placement 保留 tileId/size/rank/group；spacer 和无关 tile 字节级语义不变。
- migration 幂等；旧动态 token 的 opaque payload 不解码、不丢失。
- 当前 production catalog 不得出现 Data、Navigation、Preferences。

## Forbidden outcomes

- Coming Soon/空 App、双 Data tile、Settings 与 Preferences 同时长期出现。
- 通过清空 Start 文档或回默认布局“解决”迁移。
- Shell Engine import feature 或 marine 类型。

## Compatibility

旧 launcher schema 2 解码为 product model version 0；新 schema 3 持久化独立版本。当前组合根提供迁移计划
与真实 installed entry set，因此 W01 进程不会提前执行 v1。

## Acceptance stories

1. NMEA 与 Data Sources 两磁贴按最早 rank 合一，并保留幸存磁贴全部布局属性。
2. 已有 Data + 旧 alias 仍只留下一个确定性 Data。
3. 目标未安装时 state 完全不变。
4. Data 到达后只执行 v1；Preferences 到达后再执行 v2。
5. 再迁移结果相同，spacer/Chart tile 不变。
6. DataStore 原子提交 schema、product version、placement 与 token。

## Evidence required

`osr_w01_contract`、core migration tests、adapter persistence fixture、app composition-root tests，以及精确 SHA
的统一 CI report。W01 不要求物理设备验收。

## Existing code landmarks

`LauncherPersistence`、launcher proto/mapper、`ProtoDataStoreLauncherPersistence`、`ShellApplication`、
`ProductionShellGraph` 和当前各 App destination 是相关入口；它们只是定位提示，不规定最终文件结构。

## English translation

W01 freezes stable identities and a staged migration contract without installing empty future apps. Product migrations are separate from storage schema migrations, run only when the real target entry is installed, preserve the surviving tile’s layout identity, and are deterministic and idempotent.
