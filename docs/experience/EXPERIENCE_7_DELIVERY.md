# experience.7 · 应用按 OS 运行时边界组织

日期：2026-09-18。分支：`codex/yokuli-os-rom`。版本：`0.5.0-experience.7` / `0.5.0-experience.7-rom`，versionCode 11。

本轮处理的是执行归属和源码依赖。页面交互延续 experience.6，并按末轮反馈修正通知快捷按钮；没有换一套 UI，也没有把这轮重构称为完整 ROM 或跨进程 OS。

## 已改动的执行路径

- `MainActivity` 不再创建业务 ViewModel；它只连接应用进程中的 `MarineSystem` 并负责 Android 权限交互。初始化经过 `MarineSystemBootstrap`，不由 UI 直接启动旧后端。
- 原 ViewModel 的业务执行移到 Hilt 单例 `LegacyMarineController`。`MainViewModel` 保留为旧 UI 源码兼容转发器；Activity 销毁不会清除全局业务范围。
- `MarineServices` 分为来源、航行、守锚、联网、分享、偏好、反馈、显示需求、内容九个端口。活动页面不再直接访问 Controller、ViewModel、业务数据库或 DAO。
- `MarinePresentationBridge` 只生成界面读数和双语通知。开始/暂停/继续/结束航行与 pending 状态归 `VoyageSessionCoordinator`；多个应用观察同一会话，按钮点击不等于业务成功。确认超时保持真实状态，不自动重发。
- 定位请求使用 `PositionSourceRequest`，取消万能字符串动作分发；网络与共享页面不再接收无关的定位权限回调。
- 地点、集合成员、历史轨迹、警报事件、照片经内容端口读取/修改。收藏锚泊的事务与去重也在服务侧；集合成员变更通过 Flow 实时通知。
- 船舶资料、语言、声音、仪表布局改为窄命令与字段级 DataStore 更新。逐指标选源也在原子 edit 中更新自己的键，防止两边互相覆盖旧快照。
- 地图/仪表的临时显示需求使用可独立关闭的句柄；一个消费者释放不会撤销其他消费者的需求。
- 新增纯 Kotlin `core:runtime-contract` 与 Android 组合模块 `runtime:marine-local`；源码边界检查进入 `app-shell:preBuild`，没有例外豁免。

实际拓扑、全部端口、生命周期与兼容限制见 [系统边界](../os/10-INPROCESS-SYSTEM-BOUNDARIES.md)。应用关系见 [产品拓扑](../product/OS_INTERFACE_TOPOLOGY.md)，源码声明见 [API 索引](../product/API_INDEX.md)。

## 通知中心末轮修改

姿态重新确认是一次动作，不再按已经确认的安装状态点亮。日夜只用太阳/月亮图标与当前模式文字表达；更多/收起也不使用持续高亮。GPS、常亮、运行会话仍显示真实开关状态，动作按钮保留按压反馈。

## 验证记录

已完成本地针对性验证，未等待 GitHub CI：

- 全量源码边界：149 个生产文件，0 个显式例外，通过；检查已作为 `app-shell:preBuild` 依赖执行。
- `VoyageSessionCoordinatorTest`：5 项通过，覆盖跨应用重复请求、关闭 UI 后继续确认、18 秒未确认、匹配会话的暂停/结束及系统范围取消清理。
- `DisplayLeaseRegistryTest`：3 项通过，覆盖两个消费者独立释放、重复关闭、独立能力与重新申请、申请失败后恢复。
- 普通 APK 与 ROM HOME APK：架构拆分本地组合编译成功（3 分 38 秒；同轮执行上述测试）；通知按钮末轮修改后的产物仅重新编译，结果在交付时记录。
- 用户随后要求停止测试；以上 8 项是在通知按钮末轮修改前执行。此后只编译打包，不运行测试或继续模拟器检查。此前已发起的模拟器安装不作为最终产物的体验验收。

## 当前边界

这是同一 APK、同一 UID、同一进程的本地实现。没有 Binder、独立应用进程或硬件/崩溃隔离，`BINDER` 请求明确返回尚未实现。不能把 `@Singleton` 当成系统后台存活保证。

领域端口当前仍读取 legacy `MainUiState` 兼容投影，部分参数仍为 Room 实体、Android `Uri`、`ComponentName`、协程 `Job`。这不是最终公共 IPC schema。Shell 内仍持有图册和部分坐标/路线文件状态；下一阶段应逐领域迁出，而非复制整个旧模型并改名。

现有 ROM R0 的 AOSP 产品、构建脚本及密钥注入继续使用。此次提供 APK 和源码边界，不声称在这台 macOS 主机编出了 AOSP 系统镜像。真实船网、GNSS、报警输出、进程回收恢复及长时间船上使用仍需对应环境验证。
