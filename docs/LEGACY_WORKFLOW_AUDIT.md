# Yokuli OS — 旧应用工作流保留审计

## 目的

Yokuli OS 是 clean-slate 工程，但不是 clean-slate 产品行为。本文件记录旧应用中已经形成的优秀工作流、安全语义和可复用领域边界。迁移时保留用户意图与系统反应，不保留旧 UI、路由、数据库格式、兼容字段或巨大 ViewModel。

旧项目审计基线：`/Users/kuku/Documents/ChatGPT/yokuli_nmea_anchor_system`。本文件只记录已从源码、测试和审计文档中确认的行为；没有通过新工程测试的项目不得标记为已迁移。

## 必须保留的产品原则

1. 页面不是任务。回到桌面、切换应用或关闭 UI task，不能停止 Anchor、Trip、Navigation、Sonar 或 NMEA runtime。
2. 安全状态必须诚实。缺失数据不得显示为 `0`，旧值必须显示 `HELD` 或 `STALE`，断开的能力显示 `OFF`。
3. 数据源不能静默切换。活动锚警锁定自己的定位源；切换只能在暂停状态发生，并要求新来源提供新鲜、有效的定位。
4. 配置只有一个 owner。其他页面和磁贴只能显示状态或跳转到 owner，不能维护第二份开关或选择器。
5. 所有破坏性操作必须显式。Lift、End Trip、Stop Survey、Disconnect 等动作不能由 Back、Home、关闭应用或磁贴点击隐式触发。
6. 真实告警高于所有 Shell UI。Desktop、应用、Recents 和 System Center 都不能遮挡或关闭真实安全告警。

## 工作流合同

### Anchor Watch

主流程：

`Set Anchor → Preflight → Setup → 等待所选来源的新鲜定位 → Arm → Monitor → Alarm/Recovery → Pause/Resume/Adjust → Lift → History`

保留点：

- Preflight 中阻塞原因始终可见，点击不能“无响应”。
- 活动 session 拥有中心、半径、轨迹和定位源；Pause 全部保留，只有 Lift 结束。
- NMEA 丢失后保持原 session 和原来源，不自动切手机 GPS。
- Resume 必须等待恢复后的新定位；失败时保持暂停并显示原因。
- 调整半径立即基于可信当前位置重新评估告警。
- 告警确认代表暂时 snooze，不代表永久消除仍存在的危险。
- 多来源告警由统一音频仲裁器管理；清除一个来源不能静音其他告警。
- 动态锚心估算只能提出候选，不得静默移动已经采用的锚心。

### Sail 与 Trip

主流程：

`Sail instruments → Start Trip preflight → 选择 Auto/Boat NMEA/Phone GPS → Record → Pause/Resume → Waypoint → End → Report/Replay/Export`

保留点：

- Trip 的定位源是 session 级选择，不改变 Anchor 来源或全局 Vessel 默认值。
- 开始记录前，所选来源必须证明新鲜可用。
- 可选仪表缺失时记录 gap，不伪造零值。
- Pause 或 End 前先落盘缓冲；落盘失败时任务继续运行并允许重试。
- 历史页明确区分可用轨迹、空轨迹和不可用状态。
- Live 与 History 使用相同 canonical sample 语义。

### Vessel Data 与来源仲裁

保留 canonical path：

`传感器/NMEA → decode + validity → Source Registry → Source Arbitrator → Data Bus → Apps`

每条观测保留：值、单位、来源身份、测量时间、来源心跳时间、质量、`FRESH/HELD/STALE`、选择原因与冲突状态。

- 心跳可以证明来源还活着，但不能刷新旧测量值的年龄。
- RMC/GLL/MWV 无效状态和 GGA fix quality 0 等显式无效信号必须立即失效。
- COG 是运动方向，不能伪装为船艏 Heading。
- Anchor 可以消费统一观测，但必须附加更严格的安全证据门槛。

### NMEA

三个产品生命周期独立：

1. NMEA Input：配置、连接、流量健康、原始接收、断开。
2. Boat Output：Phone/App 数据写入船载网络，显式 Start/Stop，不自动恢复运行租约。
3. Phone NMEA Service：手机作为监听服务，独立端口、客户端状态和 Start/Stop。

保留点：

- `CONNECTED` 不等于有可用数据；保留 `CONNECTED_NO_DATA`。
- 输入 RX 和专用 TX 故障互不关闭对方。
- 显式断开前列出并安全处理真实 runtime owners。
- 防止 Phone output 与 Boat input 形成位置回环。
- 输出不得自动启动，也不得把 Boat 数据重新包装为 Phone 数据。

### Map

保留浏览、跟随/解锁、回中、测距、地图样式、海图、LINZ、MBTiles、个人声呐、锚地/航点/轨迹展示。

- Map 是表现层，不拥有 GPS 来源。
- 地图瓦片或网络失败不能停止数值与后台锚警。
- Launcher 的 Map Tile 只能是轻量预览，不能常驻完整地图组件。
- 地图手势归 Map 所有；Shell 级横向手势只存在于 Start 与 All Apps 之间。

### Sonar

主流程：

`Start Survey → 选择 tide mode → 等待同源位置与深度 → Record → Pause/Stop → Survey history/grid`

- 深度或同源定位缺失时显示 waiting，而不是 recording。
- Held depth 受时间和移动距离限制，不能无限生成新测深点。
- 真实调查不能用手机 GPS 替代同一 NMEA 流的船位。
- 长时间失效应安全关闭并保存，而不是制造数据。

### Anchorages 与 Navigation

- 使用 `Place → one or more Spots → Visits/notes/photos → Detail → Approach` 模型。
- 单 Spot 仍显示一张完整 Spot card；多 Spot 要求显式选择。
- Approach 必须有明确、可见、可取消的目标指引。
- 缺失目标显示错误，不能静默返回。
- 活动 Anchor session 与 Anchorage approach 的互斥关系必须显式。

## 旧测试资产的处理

旧项目当前包含 131 个 JVM 测试文件和 21 个 instrumented test 文件。新项目不直接复制全部测试；按以下方式迁移：

| 旧资产 | 新项目处理 |
|---|---|
| 纯策略/几何/parser 测试 | 改写为新领域接口的 unit contract test |
| Repository/Room 测试 | 针对新 schema v1 重写 instrumented test |
| Foreground service 流程 | 改写为 Runtime Gateway 的集成故事测试 |
| Compose 页面测试 | 改写为真实 ShellActivity 导航故事 |
| 旧 migration/backup 测试 | 不迁移 |
| 高负载、掉线、重连和恢复测试 | 保留场景，适配新 runtime |

## 迁移状态定义

- `CONTRACT_CAPTURED`：行为已经写入合同，但新代码尚未实现。
- `TEST_RED`：新工程中已经存在失败测试。
- `IMPLEMENTED_GREEN`：最小实现已使测试通过。
- `VERIFIED_DEVICE`：通过模拟器或真实设备集成测试。
- `VERIFIED_VESSEL`：经过实船验证。

禁止仅因页面已经存在就写“已迁移”。
