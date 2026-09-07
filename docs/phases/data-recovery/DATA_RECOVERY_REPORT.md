# Yokuli OS Data Recovery — Cumulative Report

状态：`DATA-R00–R09 CODE COMPLETE — HOSTED CI AND HUMAN ACCEPTANCE PENDING`

## 合同与边界

- 来源：`Yokuli_OS_Data_NMEA_Marine_Nervous_System_Recovery_Contract.md`
- SHA-256：`70fa6c56ec9a1124f8509bfd2b7f3f7d0857fb7c883740af96641a215ebcbfd2`
- 本轮完成 DATA-R00–R09。DATA-R10 必须由真实 APK 人工验收；DATA-R11 只能在人验通过后把最终用户旅程重新锁进 CI，本轮没有提前执行。
- 旧五段式 Data UI、具体 tab/page/string/composable 形状不再具有产品权威性。NMEA 协议、runtime、持久化、选源、并发与安全测试仍为硬门禁。

## 已完成产品结果

### R00–R02：产品真相与 Boat

- 一级区域严格收敛为 `Boat / Flow / Connections`；Source、Consumer、Connection Detail、Diagnostics 都是 drill-down。
- Boat 以可交互的 2.5D 船体场景为主视觉，Position、Heading、Depth、Wind 四个感官热点和卡片读取同一个 resolved-data snapshot。
- LIVE / HELD / STALE / UNAVAILABLE 与 attention 都来自既有 marine-data freshness/source truth；选中来源失效时不会借备用来源伪装成功。
- 高频值更新不会重播整页入场；低动效和 2D fallback 保留同样的信息与可访问动作。

### R03–R04：To-C 连接体验

- Add Source 首屏以 Boat Gateway、UDP Broadcast、Phone Location 等用户目的开始，技术 endpoint 收在 Advanced。
- Connection card 用人类状态、能提供的语义感官和 Fix/Retest 呈现；socket/bind 成功不再等于“数据可用”。
- 新的 probe 使用真实 TCP/UDP transport、P1 framer/parser/checksum 语义，只有检测到 Position/Heading/Depth/Wind 等语义能力才算检测成功。
- Probe 是一次性、可取消且有超时的临时会话，不写 connection repository、不启动正式 runtime/FGS、不污染 sentence/observation catalog。
- 测试成功只进入检测结果摘要；用户必须再次选择“使用此来源”，才保存并启动正式连接。失败和超时不会留下 durable connection。
- Phone 保持系统内建能力，不伪装成 NMEA socket。

### R05–R08：信任、Flow 与诊断

- Source trust 融入每个语义感官详情；当前 trusted source、候选、standby、unavailable 和 selected-source loss 可解释且可操作。
- 选源仍通过既有原子 source-selection port 提交，没有 UI 私有偏好，也没有 silent failover。
- Flow 是一张共享 truth 驱动的拓扑：`输入来源 → 语义感官 → Yokuli OS 采用值 → 当前消费者`。
- Simple 模式使用用户语言；Expert/Diagnostics 才展开 sentence、raw evidence、checksum/drop/parse metrics。
- Flow 的 source、sensor 和 consumer 节点都可进入相应详情；selected、standby、unavailable 路径有不同视觉权重。
- Chart、Navigation、Start Tile 的 required/optional 感官需求由 consumer registry 投影；注册与当前 active 是两个独立事实。

### R09：OS 投影

- Data Live Tile 优先显示真实 marine hero value；没有可用值时显示 overall health，不再把 connection count 当产品状态。
- Active Navigation 缺失 required Position 时，Shell 状态条从同一 Data consumer truth 显示只读 `NAV DATA LOST / 导航数据丢失` 警示。
- Chart/Navigation/Shell 没有重新计算 source truth；它们只消费 Data 投影。

## TDD 与自查纠错

- Probe Red 首次准确发现“检测到能力后立即自动保存”的旧顺序；`93cb854` 将保存移到明确的 `UseTestedConnection` 动作后。
- Flow Green 首次 compilation gate 发现 semantic group 到 vessel sense 的类型映射错误；`c3e4cde` 完成唯一映射并恢复编译。
- Live Tile Red 发现 fixture 同时声明 resolved USER 数据和空 source decision 的内部矛盾；`a37a687` 修正 fixture，而没有弱化生产 invariant。
- 保留 legacy token/section 解析只用于 migration 与旧 deep link 兼容；production Data host 不再渲染旧五段式 presentation。

## 本地定向证据

| Gate | 结果 |
| --- | --- |
| Product Recovery policy | `6/6 PASS` |
| `AndroidNmeaConnectionProbeTest` | `4/4 PASS` |
| Data consumer/connection coordinator + projection | `7/7 PASS` |
| Marine projection + Data live tile | `5/5 PASS` |
| `:app-shell:compileStandaloneDebugKotlin` | `PASS` |
| `git diff --check` | `PASS` |

遵循本轮效率约定，本地没有为每个小提交重复执行全仓 test/lint/assemble/device；完整质量门禁和可下载 APK 由 push 后 hosted CI 负责。

## 明确未验证

- DATA-R10 真实 APK 的 3 秒可理解性、触控可发现性、视觉层级、低动效和真实设备手感：`PENDING_HUMAN_ACCEPTANCE`。
- 锁屏、Doze、OEM 后台、真实 GNSS/船网、功耗与三星方屏：`UNVERIFIED_PHYSICAL_DEVICE`。
- DATA-R11 行为测试重新上锁：`NOT_STARTED_BY_CONTRACT`。
- 以上未验证项不得由 JVM、Compose story、模拟器或 compilation 结果冒充通过。

## Design decisions

- 复用已验证的 socket、parser、catalog、selection runtime，没有建立第二套 NMEA 或 source truth。
- 将临时探测与持久连接拆成两个明确事务，避免“测试一下”产生不可见配置或后台工作。
- 用产品语义投影包住底层 DataKey，而不是删除底层精确性；普通用户看到感官与影响，专家仍能追到 sentence/raw evidence。
- 复用同一 consumer registry 同时驱动 Data、Live Tile 和 Shell warning，避免 App 各自推断健康。
- 没有在人工接受前用新的布局快照或具体按钮位置把仍可能调整的 presentation 再次锁死。

## English summary

DATA-R00 through R09 are code complete. The product now exposes Boat, Flow and Connections over one resolved marine-data truth; uses a disposable semantic TCP/UDP probe before explicit save-and-start; preserves semantic source trust without silent fallback; renders one navigable source-to-consumer topology; and projects real Data health to the live tile and Shell warning. Focused local gates pass. Hosted CI, real-APK DATA-R10 acceptance, physical-device evidence and post-acceptance DATA-R11 behavior locking remain explicitly pending.
