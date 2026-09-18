# Yokuli OS · 系统文档导航

先看 [10 · 已落代码的进程内系统边界](10-INPROCESS-SYSTEM-BOUNDARIES.md)：本轮已将活动 Shell 从 ViewModel/DAO 调用迁到领域端口，并把运行时组合与纯 Kotlin 契约分成独立模块。文档中仍标为后续计划的 Binder、独立进程、SystemUI/系统 Recents 和真机适配，没有因添加模块而自动完成。

## 从产品到代码

| 阅读顺序 | 说明 |
| --- | --- |
| [00 · 就绪度](00-READINESS.md) | 开始 ROM 工作时的条件与缺口；结合最新交付记录阅读 |
| [01 · 分层架构](01-ARCHITECTURE.md) | 长期系统方向、R0 原始拓扑和后续跨进程边界 |
| [02 · 领域与契约](02-DOMAIN-AND-CONTRACTS.md) | 应用职责、数据所有者、命令及事件原则 |
| [03 · 生命周期与恢复](03-LIFECYCLE-AND-RECOVERY.md) | 前台/后台、持久状态与失效恢复要求 |
| [04 · 设备与硬件](04-DEVICE-AND-HARDWARE.md) | Cuttlefish、BSP/GKI/HAL、GNSS/传感器/USB 与真机适配 |
| [05 · 安全与更新](05-SECURITY-AND-UPDATES.md) | 应用/系统签名、AVB/OTA、数据迁移、密钥和恢复边界 |
| [06 · 系统体验](06-SYSTEM-UX.md) | HOME、三键、通知、内部应用与 Android Task 的区别 |
| [07 · 构建与验证](07-BUILD-AND-VALIDATION.md) | APK/AOSP 构建输入、宿主环境与分层验收 |
| [08 · 路线与验收](08-ROADMAP-AND-ACCEPTANCE.md) | 逐阶段交付与证据要求 |
| [09 · R0 交付](09-R0-DELIVERY.md) | HOME 产品配置阶段的历史交付记录 |
| [10 · 进程内系统边界](10-INPROCESS-SYSTEM-BOUNDARIES.md) | 当前实际模块、服务端口、命令路径和编译前守卫 |

## 当前命令路径

界面发起明确动作 → `MarineServices` 对应领域端口 → 本地适配和进程级控制器 → 原有仓储/Android 服务 → 真实状态回流。全局航行另由 `MarineSystem.voyage` 统一协调确认；纯契约不包含中文/英文界面文案。应用的选择、草稿和页面返回由 Shell 管理。

状态读取目前保留 `StateFlow<MainUiState>` 兼容投影，Android 参数和部分实体仍在 legacy 层。这不是公共 Binder 协议，也不是编译期已完全去除 legacy 的声明。

```sh
python3 scripts/check_runtime_boundaries.py
```

以上命令在仓库根运行。相同检查已接入 `:app-shell:preBuild`，需要 Python 3；缺失生产模块、Shell 直连实现/DAO/旧整对象偏好、反向依赖或纯契约使用 Android/legacy 类型会失败。检查只证明规则覆盖的静态边界，构建及体验结果见 [experience.7 交付记录](../experience/EXPERIENCE_7_DELIVERY.md)。

产品配置与固定 AOSP 基线见 [ROM 总入口](../../rom/README.md)，应用数据关系见 [产品拓扑](../product/OS_INTERFACE_TOPOLOGY.md)。本分支当前不把 HOME APK 或产品配置称为已构建 ROM 镜像。
