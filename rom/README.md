# Yokuli OS ROM · R0

此目录属于 `codex/yokuli-os-rom`，从应用体验分支的 `b5fc247` 开始。**目前是可编译 HOME APK + AOSP 产品集成包，尚未编译/启动完整 ROM，不能刷手机。**

产品决定：先做航海专用 Android 发行版，沿用 AOSP 的内核、HAL、权限、系统服务及更新机制；Yokuli 拥有 WP 交互、航海业务和统一数据契约。先在 Cuttlefish 上验证，不猜测任何手机的分区和驱动。

| 层次 | 文档 |
| --- | --- |
| 当前能力与实际差距 | [00 · 成熟度审查](../docs/os/00-READINESS.md) |
| 分层、依赖和系统拓扑 | [01 · 系统架构](../docs/os/01-ARCHITECTURE.md) |
| 业务、数据、所有权与接口 | [02 · 领域与契约](../docs/os/02-DOMAIN-AND-CONTRACTS.md) |
| 开机、崩溃、进程和恢复 | [03 · 生命周期](../docs/os/03-LIFECYCLE-AND-RECOVERY.md) |
| 内核、HAL、设备及船载硬件 | [04 · 设备适配](../docs/os/04-DEVICE-AND-HARDWARE.md) |
| UID、权限、签名、OTA、回退 | [05 · 安全与更新](../docs/os/05-SECURITY-AND-UPDATES.md) |
| WP Shell、系统交互及偏好 | [06 · 系统 UX](../docs/os/06-SYSTEM-UX.md) |
| 环境、产品包、构建与验收 | [07 · 构建手册](../docs/os/07-BUILD-AND-VALIDATION.md) |
| 从桌面到 OS 的交付顺序 | [08 · 路线与门槛](../docs/os/08-ROADMAP-AND-ACCEPTANCE.md) |

## 当前应用侧系统接入

普通 APK 与 ROM HOME 共用 `app-shell/src/rebuild` 和同一海事实现。通知已通过实际 `NotificationClient` → `NotificationBinderService` 进入同包 `:notifications`，协议 1.0、非导出且检查真实同 UID，Application 按角色不在消息进程重建海事资源。消息历史只有一份写者；服务/客户端的版本、重连与持久化边界见 [10 · 系统接入](../docs/os/10-INPROCESS-SYSTEM-BOUNDARIES.md) 和 [通知契约](../docs/product/NOTIFICATION_CENTER_CONTRACT.md)。

该 service 来自现有 runtime 模块 Manifest 合并，仍随同一个 APK 交付，不新增独立产品包或平台签名权限。海事核心、导航、完整 SystemUI/Recents、独立 UID、AVB/OTA 和真机/BSP 尚未迁移完成；本轮不构建/运行 AOSP 镜像，不把 APK 编译等同于设备可刷入。

## 实际配置

- 固定 AOSP `android-16.0.0_r4`，与现有 Android 16 / SDK 36 应用基线一致。它是集成验证基线，**不是当前最新安全版本**；联网产品发布前须完成安全版本升级。
- `yokuli_cf_x86_64_phone-trunk_staging-userdebug`，继承上游 `vsoc_x86_64_only` 的内核、板级配置、HAL 和分区。
- `YokuliHome` 预置于 `/product/app`，使用应用自己的证书、UID 和运行时权限；替代 Launcher3，保留 SystemUI、Settings、PermissionController 和首次配置。
- `rom` flavor 增加真正的 Android HOME 入口。普通 `standalone` flavor 仍用于手机上的全屏应用，不注册 HOME。
- 纯 AOSP 使用 MapLibre、离线世界参考底图与 OSM；没有捆绑 GMS 或 Google 授权。原应用分支的 API Key 管理保留。

## 最短可执行路径

1. `./gradlew :app-shell:assembleRomDebug` 编译系统桌面 APK。
2. `python3 rom/tools/package.py --apk app-shell/build/outputs/apk/rom/debug/app-shell-rom-debug.apk --sdk "$ANDROID_HOME" --output /path/to/new-bundle --development-apk` 校验签名、HOME、ABI 与 ZIP 对齐并生成集成包。
3. Linux 构建机同步固定 AOSP 版本，运行 `doctor.py`，再用 `integrate.py` 将产品包放入 `vendor/yokuli`。
4. `bash rom/tools/build.sh /path/to/aosp` 构建镜像和 target-files；随后在 Cuttlefish 启动并收集证据。

完整命令、限制和交付状态见构建手册。没有脚本会自动解锁、刷机、清除设备或付费创建服务器。
