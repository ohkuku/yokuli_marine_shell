# NMEA_SOURCES P1 字段与证据映射 / Field and Evidence Mapping

状态：`P1_CANDIDATE`。本表冻结 P1 纯数据层实际支持的语义；它不是 NMEA 标准全文，也不代表任何船端设备、网络或后续选源已经可用。

## 1. 共同规则

- 严格 checksum 是默认策略；`*` 后必须恰好是两个 ASCII 十六进制字符 `[0-9A-Fa-f]{2}`，`+1`、`-1` 等数字解析器可接受但协议不允许的形式必须拒绝。checksum 错误只形成有界诊断，绝不产生观测或刷新 freshness。兼容模式只允许“缺少 checksum”，并把每个观测明确标为 `UNVERIFIED_ALLOWED`；它不能与 `VERIFIED` 混淆。
- `receivedAtMonotonic` 只用于当前进程内排序和 `<3s LIVE / >=3s HELD / >=10s STALE`；来源 UTC 另存，绝不改 Android 系统时钟。
- 空字段表示“本句没有这项证据”，不写 `0`、不刷新旧值。协议明确的无效状态产生 value-less invalidation。
- 一条已解析 frame 的全部字段共享唯一 `ObservationGroupId`；后续 OS 选择不得把不同 group、不同来源的成对值拼成一帧事实。
- TCP frame 最大 1024 bytes。Sentence inventory 最大 256 keys；Observation catalog 最大 256 candidates，每个 candidate 最多 32 个 talker/formatter evidence streams；raw preview 同时最多 200 条且不超过 256 KiB。达到上限必须增加 eviction/rejection/high-water 可观察计数。
- `MarineObservation` 不只是字段名上 typed：构造时就拒绝 `DataKey` 与 value kind、unit 或范围不匹配的组合，例如 Position + knots、负的 count、`360°` course 或 negative depth。parser 的单位换算溢出必须成为 typed field error，不得抛异常。
- NMEA 数字词法只接受 ASCII digits 和允许位置的小数点；只有 altitude/offset 等明确有符号语义的字段可用 `-`。拒绝 leading `+`、科学计数法和 Unicode digits。Position 额外要求 latitude 为 `ddmm[.fraction]`、longitude 为 `dddmm[.fraction]`，不得把 decimal degrees 错解为 packed degrees/minutes。
- `GP`、`GN`、`II` 等 talker 只属于 sentence provenance，不是物理设备身份。

## 2. 句型字段

| 句型 | 产生的 DataKey | 单位／参考系 | 有效与空字段规则 |
|---|---|---|---|
| RMC | Position；SOG；COG；SourceTime；MagneticVariation | 经纬度同一值；SOG knots；COG true degrees；UTC epoch；E 为正、W 为负的 degrees | status `A` 且 mode 为空或受支持才发布；`V`／mode `N` 立即使 Position、SOG、COG invalid；非法非空 mode 不发布观测；任一字段空只跳过该字段；磁差值/方向必须成对 |
| GGA | Position；FixQuality；Satellites；HorizontalDilution；Altitude | 经纬度；count；dimensionless；meters | quality `1..8` 才允许 position/altitude；`0` 使 Position 与 Altitude invalid；其他值为字段错误且不发布 fix-dependent 值；卫星数与 HDOP 仍是独立诊断字段；高度只接受 `M` |
| GLL | Position | 经纬度同一值 | status `A` 且 mode 为空或受支持；`V`／`N` 立即 invalid；非法非空 mode 不发布观测；坐标四字段必须完整 |
| VTG | COG；SOG | true course degrees；knots | true-course 指示符必须是 `T`；速度优先接受 knots `N`，否则把 km/h `K` 换成 knots；mode `N` invalid，非法非空 mode 不发布；本阶段不把 magnetic track 冒充 COG |
| ZDA | SourceTime | UTC epoch millis | UTC `hhmmss[.fraction]`、两位日、两位月、四位年必须共同形成有效日期时间，且均只允许 ASCII digits；本地区偏移字段不修改系统时间；不完整、符号化或越界不发布 |
| HDG | Heading(MAGNETIC)；MagneticVariation | degrees；E 正/W 负 degrees | heading 必须在 `[0,360)`；磁差值与 E/W 必须成对；deviation 本阶段不解析，不据此合成 true heading |
| HDM | Heading(MAGNETIC) | degrees | 指示符必须为 `M`，否则只有字段错误且没有 heading |
| HDT | Heading(TRUE) | degrees | 指示符必须为 `T`，否则只有字段错误且没有 heading |
| DPT | Depth(BELOW_TRANSDUCER)；按 offset 另给 BELOW_SURFACE 或 BELOW_KEEL | meters | 原始 depth 非负；正 offset 才生成 surface depth，负 offset 才生成 keel depth，零 offset 不伪造第二参考面；调整后负值拒绝 |
| DBT | Depth(BELOW_TRANSDUCER) | 统一 meters | 首选 meters `M`，否则 feet `f` 或 fathoms `F` 换算；不从 DBT 猜 surface/keel |
| MWD | WindAngle(TRUE_NORTH)；WindAngle(MAGNETIC_NORTH)；WindSpeed(TRUE) | degrees；knots | true/magnetic 指示符分别必须为 `T`/`M`；速度优先 knots，否则 m/s 换算；方向与速度是不同 key 但保持同一 frame group |
| MWV | WindAngle(APPARENT 或 TRUE_RELATIVE)；WindSpeed(APPARENT 或 TRUE) | degrees；knots | `R` 与 `T` 是不同语义实例；speed 接受 `N/M/K`；status `V` 同时 invalid 同帧 angle/speed，`A` 才发布；未知 reference/status 不发布错误字段 |

其他 envelope、identifier 和 checksum 合法但未支持的 formatter 进入 `UNSUPPORTED` sentence inventory，显示为“尚未解析／仅查看”，不生成假值。

## 3. 来源、会话与目录身份

- `ConnectionId` 是用户配置通道的稳定 UUID；显示名与 endpoint 不是 ID。
- TCP 候选使用 connection origin。UDP 的 observed sender endpoint 是 provenance，不直接等于持久候选身份。配置必须明确采用 `HOST_ADDRESS` 或 `HOST_AND_PORT` origin policy；默认的 host-address policy 允许临时 source port 变化而不制造新候选，endpoint policy 则明确选择区分端口。
- session generation 属于整个 `ConnectionId`，不是单个 UDP sender。Runtime 必须在接收任何数据前显式 `beginSession`；会话 admission 与 catalog commit 在同一个线性化操作中完成，旧 generation 不能在新 generation 开始后再落盘。active sessions 最多 32；为拒绝延迟复活而保留的 active + inactive known/tombstone records 最多 256，两种容量分别计数且分别记录 high-water。inactive tombstone 不占 active slot，删除已保存连接时由明确 `forgetConnection` 释放。
- Sentence key 使用 stable source + talker + formatter + 必要语义 discriminator；因此 GP/GN 不合并，MWV R/T 不合并。Sentence 的 last-state 用 `(receivedAtMillis, ObservationGroupId.frameSequence)` 排序，同一毫秒迟到的低 sequence 不能覆盖新帧。Observation candidate 则按 stable source + semantic DataKey 聚合互补句型，因此同一来源的 RMC/GGA/GLL 不伪装成三台设备。
- UDP stable identity 与 observed endpoint provenance 始终分开保存。即使使用 `HOST_ADDRESS` 策略使 source port 不参与持久身份，Sentence inventory 和 Raw Preview 仍保留每条实际 sender host/port；缺失或与 stable source 矛盾的 UDP provenance 在边界直接拒绝。
- 同候选仲裁顺序固定为：更新的显式 invalid 优先；否则 LIVE 优于 HELD、HELD 优于 STALE；相同 freshness 层才使用文档化 formatter rank。rank 只是确定性工程规则，不宣称某句型天然更精确。

## 4. P1 与后续阶段的边界

P1 只有纯 Kotlin frame/parser/catalog/freshness 与不可变快照；没有 socket、Android、权限、服务、持久化、全局采用来源、Feature UI 或生产 fixture。TCP/UDP runtime 属于 P2；3 秒首次候选窗口、持久选择、ResolvedData 与手机系统定位属于 P3。

## English translation

P1 defines strict, bounded and provenance-preserving parsing for the twelve listed NMEA 0183 sentence families. Blank fields never become zero, explicit invalidity cannot masquerade as a live value, checksum trust and frame grouping reach every observation, and wind/depth/heading references remain distinct. Connection sessions are admitted explicitly and bounded; UDP endpoint observations are separated from a configured stable identity policy. Sentence inventory identity remains more detailed than observation-candidate identity. No socket, Android lifecycle, source-selection transaction, UI or production fixture is implemented in this phase.
