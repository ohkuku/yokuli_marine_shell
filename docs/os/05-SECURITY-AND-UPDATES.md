# 05 · 安全、签名、更新与恢复

R0 的安全边界是：**把 Yokuli 作为普通 HOME APK 预置到 AOSP 产品中，保留 Android 的权限、安装、锁屏和系统服务边界。** 本轮交付产品配置与 APK，不是已签发、已启动或可刷入真机的 ROM 镜像。

本文中的“现有”表示代码已有，“本轮配置”表示已定义构建输入，“后续计划”表示尚未实现或验证的发行要求。`userdebug`、APK `debug/release` 和设备 bootloader 锁定状态是不同概念，不能混用。

## 1. 基线与信任边界

**本轮配置：** AOSP 16 `android-16.0.0_r4`，`BP4A.251205.006`，补丁日期 `2025-12-05`；目标为 `yokuli_cf_x86_64_phone-trunk_staging-userdebug`。这一固定版本用于 R0 兼容验证，不代表最新补丁水平。生产候选必须重新选择受维护基线、合入适用补丁，并记录 BSP、内核、固件和应用的对应版本。

| 主体 | 当前身份 | 不随 HOME 身份获得的能力 |
| --- | --- | --- |
| Yokuli APK | `com.yokuli.marine`，普通预置应用，`product/app`，保留 APK 证书 | 平台 UID、任意传感器权限、静默安装、跨应用任务截图、系统通知接管 |
| Yokuli 内部应用 | 同一 APK、UID、进程中的业务模块 | 独立 Android 沙箱或独立安全主体；模块间契约不是系统隔离 |
| Android SystemUI / Settings / 权限组件 | AOSP 系统组件，R0 保留 | 不由 Yokuli 内部开关取代其授权与设备安全策略 |
| NMEA 对端 / 海图文件 / 外部 APK | 外部输入 | 不能因为位于船内网络或用户文件夹，就视为可信代码/可信定位 |

预置、privileged 与平台签名是不同维度。特权权限有专门的授予规则；SELinux 仍约束进程，不能把“系统应用”解释为全权访问。R0 不新增特权权限白名单，不通过 permissive 绕过设备适配问题。[特权权限白名单](https://source.android.com/docs/core/permissions/perms-allowlist)、[SELinux](https://source.android.com/docs/security/features/selinux)

**现有代码待持续审计：** 合并 Manifest 中仍包含继承的前台服务、开机恢复、模拟定位等声明。声明权限不等于已获授权或所有场景可用。发行前以各 variant 的最终合并 Manifest 为准，移除不需要的入口，验证导出组件、FileProvider、URI 授权和后台启动行为。

## 2. 四种密钥不要混成一种

| 密钥/身份 | 保护什么 | R0 与后续发行要求 |
| --- | --- | --- |
| APK 应用签名 | 包身份、APK 更新链 | 本轮预置模块使用 `PRESIGNED` 保留输入 APK 证书；开发 APK 不等于发行 APK |
| AOSP 平台及组件签名 | 对应平台组件的身份/签名权限关系 | 继承开发基线；Yokuli HOME 不改为平台签名 |
| AVB 签名与设备信任根 | 启动链和受验证分区 | 后续按真实设备落实根密钥、分区链和 rollback index；没有真机锁定验证记录 |
| OTA 包/载荷签名 | 升级包及载荷的可信来源 | 后续在发行工具链配置并验证；不能用 APK 证书存在来证明 OTA 安全 |

AOSP 的公开 test keys 仅适合开发。正式镜像要使用由发行方保管的生产密钥；预置 APK 是否重新签名必须显式控制，`PRESIGNED` 不会自动把开发证书变成生产证书。[AOSP 发布签名](https://source.android.com/docs/core/ota/sign_builds)

**现有：** Gradle 通过 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 接入应用 release 签名。未提供合法发行签名的构建，不能标为可长期覆盖升级的正式交付。

**本轮配置：** `standalone` 与 `rom` 复用同一个 applicationId 和业务数据结构，ROM flavor 增加 HOME 能力及纯 AOSP 地图策略。两者不是天然可并排安装的两个独立应用。同包覆盖时必须匹配签名更新规则与版本号；换证书不应通过卸载清数据来假装“无缝升级”。

**后续计划：** 记录每次交付的包名、versionCode、APK SHA-256、签名证书摘要、源码提交、构建输入和发行渠道。私钥、密码、签名服务凭据不进仓库或日志；密钥轮换、离线恢复副本和失窃应对需独立制定。

## 3. AVB、锁定与启动失败

AVB 的信任从设备启动链开始，覆盖受验证分区，并支持回滚保护。是否允许锁定、用什么根密钥、哪些分区参与验证，取决于实际 bootloader 和板级实现；Cuttlefish 的开发启动不证明真机的可信启动。[Verified Boot](https://source.android.com/docs/security/features/verifiedboot)、[启动与回滚保护](https://source.android.com/docs/security/features/verifiedboot/verified-boot)

**R0 边界：** 不宣称安全锁定，不关闭验证来掩盖镜像错误，不提供未知型号的解锁、重锁或刷写命令。本轮没有实体设备镜像、密钥烧录或恢复演练。

**后续真机发行前：**

1. 确认 bootloader 接受的信任根、分区链、签名算法和解锁清数据行为。
2. 用独立测试设备验证正确镜像、被篡改镜像、旧 rollback index、错误设备镜像和中断更新的行为。
3. 保留可验证的回原厂或维修恢复路径；恢复材料对应准确硬件版本，不能混用 vendor/boot/system。
4. 在这些条件确认前不要求用户重锁 bootloader。重锁不是普通安装步骤，也不是让系统“看起来正式”的装饰。

## 4. 系统 OTA 与 APK 更新是两条通路

| 项目 | APK 更新 | 系统 OTA |
| --- | --- | --- |
| 更新对象 | Yokuli 代码与资源 | Android/内核/厂商分区及预置内容，取决于包定义 |
| 当前状态 | 已有 APK 构建与签名接入口 | 本轮只有 AOSP 产品输入；无 Yokuli OTA 客户端、发行服务或已验证载荷 |
| 主要身份检查 | 包名、版本、应用证书与兼容性 | 设备/构建匹配、OTA 及载荷签名、AVB、分区/快照兼容 |
| 数据结果 | 正常覆盖安装保留沙箱；应用迁移仍须正确 | 切回旧系统不等于业务数据库自动回到旧版本 |

**后续方案：** 优先沿用目标设备支持的 A/B 或 Virtual A/B 更新链，使用平台的 `update_engine`、启动槽位和健康确认机制。Virtual A/B 的动态分区快照及合并有自己的空间和断电恢复要求，不能仅新增一个“更新”按钮就称为 OTA 完成。[A/B 更新](https://source.android.com/docs/core/ota/ab)、[Virtual A/B](https://source.android.com/docs/core/ota/virtual_ab)

发行流程至少要实现：下载与签名/设备校验 → 电量和存储检查 → 提示受影响的持续任务 → 写入/应用 → 用户决定安全重启时机 → 启动健康检查 → 确认成功或平台回退。是否完成槽位切换与快照合并应读取系统真实状态，不以 UI 动画进度代替。

守锚、航行记录和 NMEA 输出不能承诺跨系统重启连续工作。更新前必须明确说明中断，持久化可恢复的业务状态；重启后验证权限、来源和最后数据时间，不能在没有真实位置时悄悄显示“正在守护”。是否恢复某项服务应沿用其业务恢复规则，不让 OTA 客户端自行决定。

移除 Launcher3/Launcher3QuickStep 后，HOME 崩溃也可能影响操作入口。Cuttlefish 首次启动须验证 HOME 失败时的可诊断性与重装恢复；未来真机要设计受控恢复入口。保留 Settings/SystemUI 本身不等于已经验证所有情况下都能从界面到达它们。

## 5. 业务资料迁移与失败恢复

**现有数据分布：**

| 资料 | 当前存储/入口 | 迁移关注点 |
| --- | --- | --- |
| 守锚、航行、历史记录及相关实体 | legacy Room 数据库与仓储 | 外键、会话状态、时间、地理坐标、schema 版本与逐版本迁移 |
| 船舶信息、单位、来源/发布偏好 | Repository / DataStore 等持久化 | 单位含义、来源 ID、连接 ID、缺省值与旧值兼容 |
| Shell、地图选择、内部通知等 | 各自的应用持久化状态 | schema/字段演进；临时 UI 状态与长期业务资料分开 |
| 用户海图与文件夹 | 用户选取的文件/目录和 URI 授权 | 外部文件不属于 APK；文件移动、介质丢失、恢复后重新授权 |
| 备份 | `YokuliBackupManager` 的版本化容器与验证 | 已有备份能力不代表覆盖整套 OS 或所有 Shell 文件；须按容器清单确认范围 |

**现有：** `YokuliBackupManager` 有格式版本、文件校验、记录校验与恢复前活动状态检查；外部海图文件不自动包含在备份内。应用 Manifest 的 `allowBackup=false` 也意味着不能把 Android 自动备份当成当前完整恢复方案。

**后续发行契约：**

- 为 Room、JSON 和偏好分别声明版本与迁移路径，先验证输入再替换原资料。数据库迁移失败时保留原库和可导出资料，不自动清库“修好启动”。Room 的 destructive fallback 会删除资料，不能用于掩盖漏写迁移。[Room 数据迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- 升级前备份应包含清单、格式版本、记录数和校验摘要；恢复先做容量和兼容检查，再原子或可恢复地提交。当前备份并非所有资料的跨仓储原子快照，不能直接承诺整机一键回滚。
- 明确旧应用/旧系统能否读取新数据。A/B 回退后 `/data` 仍可能是升级后的结构；对不可逆迁移延迟执行，或提供兼容读写与可验证恢复包。
- 恢复目录 URI 字符串不等于恢复 Android 授权。海图库应显示需要重新选择的文件夹，保留名称与索引线索，不把失效目录伪装为空图库。
- 不自动恢复开放的网络监听、过时的目的地址或尚未确认的船位来源；先恢复配置，再依业务策略确认可运行性。

### 加密与开机未解锁

生产设备应继承并验证 Android 文件加密与 KeyMint/启动链能力。CE 资料在首次解锁前不可用；DE 资料可用于有限的 Direct Boot 场景，但不能把全部航迹、照片和地址搬到 DE 来绕开解锁。[文件加密与 Direct Boot](https://source.android.com/docs/security/features/encryption/file-based)

**当前限制：** 有开机恢复 Receiver 不等于已实现 Direct Boot 安全运行。现有数据与服务没有在本轮改成完整的解锁前架构。换主板、清数据或重置密钥后，不能假设原设备绑定的密钥和 URI 授权仍可恢复。

## 6. API 密钥、网络与纯 AOSP

**现有：** Google Maps 密钥通过构建环境 `GOOGLE_MAPS_ANDROID_API_KEY` 注入。密钥管理入口保留，不把真实值写入文档。Android 客户端密钥会进入 APK，因此要同时限制包名、签名证书及可用 API，监控配额并能够轮换；不能当作保存在客户端的不可提取秘密。[Maps API 安全建议](https://developers.google.com/maps/api-security-best-practices)

**本轮配置：** ROM flavor 禁用 Google Maps 后端选择，采用 MapLibre 的离线/在线路径；即使构建环境提供了 Maps key，也不会因此假设设备有 GMS。旧 Google 卫星偏好转为可用底图，不宣称仍是卫星影像。standalone 的原有 Maps 配置方式保留。

**兼容性事实：** 本机 GNSS 路径使用 `LocationManager`，而旧 `GlobalMockLocationManager` 的全局定位代理包含 Google Fused Location 依赖。后者不能在无 GMS 环境中被宣布可用；它需要单独替代、能力探测与开发者选定模拟定位应用等授权。Google Maps SDK 也需要其支持的 Google 运行环境。[Google Play services 环境检查](https://developers.google.com/android/guides/setup)、[Maps Android 配置](https://developers.google.com/maps/documentation/android-sdk/config)

NMEA 连接和本机共享要保留用户选择的发布内容与来源，默认不把预置身份解释为网络信任。后续网络威胁模型要覆盖未经认证的帧、畸形输入、重复数据、回送循环和远端发送目标；UI 中能显示校验结果不等于 NMEA 链路具备加密或身份认证。

## 7. 交付状态必须怎样写

允许写：已添加 ROM flavor/HOME 声明、已准备固定基线的产品配置、已生成某个可核验 APK（有实际构建证据时）。

未取得对应证据前不能写：ROM 已构建、Cuttlefish 已启动、真机已刷入、AVB 已锁定、OTA 已跑通、系统通知或 Recents 已接管、全量备份已验证、纯 AOSP 全部功能已兼容。

设备资料见 [04 · 设备与硬件](04-DEVICE-AND-HARDWARE.md)，HOME 和系统能力分期见 [06 · 系统体验](06-SYSTEM-UX.md)。代码入口：[应用构建与签名](../../app-shell/build.gradle.kts)、[合并前主 Manifest](../../app-shell/src/rebuild/AndroidManifest.xml)、[业务数据库](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/database/Database.kt)、[备份管理](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/data/backup/YokuliBackupManager.kt)、[旧全局定位代理](../../legacy-marine/src/main/java/com/yokuli/anchorwatch/location/GlobalMockLocationManager.kt)。
