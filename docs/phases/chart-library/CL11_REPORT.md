# CL11 报告 / 故障、数据安全与有界资源

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`4bf4e536c71fd399bbb29ee7f19322cd5eb7786b`

## 产品结果

- 海图来源枚举现在在 provider cursor 层就受 10,000 项、32 层和 30 秒预算约束，不会先把任意大的
  cursor 全部装进内存。主动取消与时间预算用不同结果表达：前者是 `CANCELLED`，后者保留已发现内容并
  报 `PARTIAL / TIME_BUDGET_REACHED`，不会把慢 provider 伪装成用户取消。
- 同一外部文档可以由多个授权来源共同引用。一个来源刷新后不再包含该文档时，只移除该来源 membership；
  一个授权失效也不会把仍可由另一授权读取的共享资源全局标成不可用。最后一个有效来源消失时才进入缺失
  或权限失效状态。
- 大目录扫描的 catalog 发布被拆成每批至多 1,000 个 mutation，再单独发布来源终态。任何一批失败都会
  尝试把仍为 `RUNNING` 的 scan 收敛为 `PARTIAL`，避免用户看到永久“解析中”；旧 catalog 真相不会因
  未完成扫描被静默清空。
- Reader 同时最多打开 12 个 session、最多等待 12 个请求；额外请求得到 typed
  `RESOURCE_LIMIT`。BASIC 验证、完整验证、复制任务、source/asset coordination、gateway registration、
  gateway worker 与请求队列均有冻结上限，不再由 source/asset 数量生成无界锁表或线程队列。
- 进程内 tile gateway 仍只监听 `127.0.0.1`，只接受不可猜测 token 注册的只读 tile 请求；registration
  最多 8 个、worker 最多 8 个、排队最多 16 个。过载被拒绝并计数，不会为追求“看起来能用”放开路径
  或 LAN 访问。

## 故障与数据正确性

- 扫描、验证和读取仍使用 generation/revision 检查；旧回调不能覆盖新目录或新资源。
- 资源删除/撤权/替换时，旧 read session 会失效；关闭 session 后才释放并聚合读取证据。
- 新增 runtime 指标只记录计数与字节，不记录 URI、路径、tile 内容或密钥。CI 只提取三类 allowlist
  证据：`zero-copy-read`、`runtime-bounds`、`catalog-1000`。
- 1,025 项扫描故事验证跨事务发布仍能结束；1,000 项 catalog 故事按 100 项分页消费，避免 UI 或测试
  把数据库一次性读完。

## TDD 与证据

- 新增 domain/Android/adapter 回归，覆盖共享 membership、撤权隔离、provider cursor 上限、超额读取
  拒绝、closed-session 计量、1,025 项分批扫描、1,000 项数据库分页及 gateway 上限。
- CL11 证据提取器仅扫描有界 JUnit/instrumentation 文件并拒绝未知 scenario；三个必需 scenario 少任何
  一个都会让 API34 job 失败。API34 的 `CODEX-JOB` 同时附带
  `chart-library-cl11-evidence.json`，机器证据与精确 commit 绑定。
- 本地执行了 CL11 两个 Python 测试、CL11 静态合同、CI 合同及必要 Kotlin test/androidTest compile；
  Kotlin 编译为 `BUILD SUCCESSFUL`。未在本地重复全仓测试、lint、APK 或 emulator 门禁。
- 最小编译发现 CL08 的 `ChartDisplayRuntimeTest` 仍调用旧的 coverage API，缺少
  `expectedFingerprint`；测试已补传实际 plan fingerprint，产品代码与 CL08 合同未被回改。

## DESIGN DECISIONS

- 在已有 runtime、scan planner、Room catalog 和 loopback gateway 内补充背压与可观察指标，没有建立
  平行的资源管理器或“性能框架”。
- 对 provider 预算耗尽采用“保留已知真相 + 明确 partial”的保守语义；不把未枚举区域批量 missing，
  也不因一次失败抹掉上次有效 catalog。
- 锁采用固定 64 个 stripe，而非永久缓存每个外部 ID 的 Mutex；冲突只影响同一 stripe 上的短事务，
  换来可证明的内存上限。
- gateway 和 reader 在过载时显式失败；海图缺失比无限排队、fd 泄漏或进程崩溃更安全，也更容易解释。
- 机器证据 schema 与产品模型分离；它只用于 CI 证明边界，不进入应用 UI 或持久化数据库。

## 未验证边界

- GitHub Action 尚未回传 API34 instrumentation、全仓 JVM、lint、APK、API36 或 performance job，故本报告
  不把 A02–A42 写成最终 `PASS`。
- `catalog-1000` 是 emulator/CI 合成目录证据；30 分钟连续操作、真实 USB/SD provider、撤权时机、
  物理低内存与真机 fd 观测仍为 `NOT_RUN_PHYSICAL_DEVICE`，留给 CL12 汇总，不以 host 测试冒充。
- >4 GiB sparse fixture、pipe provider 与 native/page-size 仍需由对应 CI 结果或物理设备证据判定；代码
  已保留 typed failure，缺证据时不会宣称兼容。
