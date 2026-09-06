# NMEA_SOURCES P4 报告 / P4 Report

状态：`PASS`

起点提交：`e2fd6160a16efec50171a5e7e39068093277ec5c`

## 范围与结果

P4 交付独立的 Data Sources Feature。页面只投影 P1/P3 的真实 `SentenceCatalog`、完整候选 `SourceCatalog`、原子 `SourceDecision + ResolvedDataSnapshot` 与手机定位状态，不保存第二份业务真相，也不持有 socket。

已实现的用户闭环：

- “数据 / 句型”双视图、全文搜索、需处理／多来源／手机／指定连接筛选。
- 任意真实 `DataKey` 自动出现在目录；没有位置但只有风或水深时仍可完整工作。
- 候选详情保留未采用来源；选择和停用均调用唯一 OS 事务端口，反馈来自实际提交结果。
- 合法但未支持的 NMEA 句型、历史会话、明确无效状态和最多 20 条关联原始证据可见。
- 手机系统定位只有显式启用操作；普通拒绝触发权限请求，永久拒绝与系统位置关闭分别给出可恢复操作。NMEA 不受手机授权失败影响。
- NMEA Input 与 Data Sources 之间只传递有界、验证后的不透明 token；不传 host、显示名、原始句子或测量值。
- 页面内部 Back 先回总览；总览 Back 交还 Shell，因此 Feature 自身不会退出应用。

本阶段只建立 Feature 和 composition-root coordinator，不提前把两个新应用注册进生产 Catalog、默认 Start 或状态条；正式安装、Tile/Status projector 与跨 Feature effect 执行属于 P5。这也保证升级不会在现有桌面上偷偷增加磁贴。

## TDD 与自查纠错

Red 提交：`c05d3e21acb74aef5c496b1e7ab50362733ebea0`。

- P4 静态合同首跑：`7 tests; 4 errors / 3 pass`；Feature JVM 首跑因 Data Sources 类型全部不存在而在 `compileDebugUnitTestKotlin` 失败。
- 第一轮 Green 的 core 门禁发现超长外部 token 在解析前由值对象抛异常。`dc4b388` 把安全拒绝放回解析边界，错误 token 现在返回 `null` 而不崩溃。
- 首次设备故事编译发现测试未显式提供 `WpThemeSpec`；`e29838c` 修复测试装配，没有改变产品 UI 或弱化断言。
- 自审追加 Red `aee5d96`，覆盖 phone-only、单一已选来源、20 条 raw 上限、运行时新增句型，以及永久拒绝／系统位置关闭的恢复入口。该 Red 精确因缺少 `ResolvePhoneLocation` 与 `OpenAppPermissionSettings` 而编译失败；`cc0e7f1` 完成对应 Green。
- 搜索先对筛选后的候选集合匹配，避免连接筛选后被另一个来源的文本误命中。

## 最小 P4 Gate

```text
python3 .github/scripts/test_nmea_sources_p4_contract.py                PASS 8/8
./gradlew :core:marine-data:test                                        PASS 117/117
./gradlew :feature:data-sources:testDebugUnitTest                       PASS 16/16
./gradlew :feature:nmea-input:testDebugUnitTest                         PASS 17/17
./gradlew :app-shell:compileStandaloneDebugKotlin                       PASS
./gradlew :feature:data-sources:connectedDebugAndroidTest               PASS 3/3 (API 34 AVD)
git diff --check                                                        PASS
```

按用户要求，P4 没有重复执行全仓 `test`、Lint、APK assemble 或全部设备套件；它们统一留到 P7。真实权限对话、系统位置设置返回、真机 GNSS 与后台行为仍为 `UNVERIFIED_PHYSICAL_DEVICE`，不会以纯 Compose story 冒充。

## English translation

P4 passes its scoped gate. The independent Data Sources Feature projects the one real sentence, candidate, decision, resolved-data, and phone-location truth; it owns neither sockets nor parallel preferences. Data/sentence views, search and filters, candidate selection/disable transactions, unknown and historical sentences, bounded raw evidence, phone permission recovery, safe Back behavior, and bounded opaque cross-feature links are implemented and tested. Production installation, tiles, status-strip projection, and effect routing intentionally remain P5. Full repository gates remain deferred to P7, and physical permission/GNSS/background behavior remains explicitly unverified.
