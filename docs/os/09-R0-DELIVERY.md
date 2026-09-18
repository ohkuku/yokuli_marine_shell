# R0 本轮交付与实际证据

2026-09-18。新分支 `codex/yokuli-os-rom` 以 `b5fc247` 为参照，原 `codex/yokuli-os-rebuild` 保留。

## 已交付

- 10 篇系统文档：现状、分层架构、领域/数据/接口、生命周期、硬件适配、安全升级、系统 UX、构建验证、阶段路线与本记录；[总入口](../../rom/README.md)。文档有中文数据字典和 Mermaid 拓扑，计划协议明确标注尚未实现。
- `rom` Android flavor：同包名 HOME/DEFAULT Activity；冷、热 Android HOME Intent 回开始屏幕，不经过局部 Back 处理器。保留普通 standalone flavor。
- AOSP16 Cuttlefish x86_64 userdebug 产品配置和 PRESIGNED HOME 导入，使用上游板级配置；无平台签名、特权权限或自动定位授权。
- 纯 AOSP ROM 使用 MapLibre/离线世界参考底图/OSM，原 API Key 注入入口保留；不捆绑 GMS。
- `doctor.py`、`package.py`、`integrate.py`、`build.sh`：宿主检查、APK 校验、可追溯产品包、拒绝覆盖的源码集成、固定源码构建入口。脚本不会下载系统源码或刷设备。

## 实际运行

| 检查 | 结果 | 证明范围 |
| --- | --- | --- |
| `:app-shell:assembleRomDebug :app-shell:assembleStandaloneDebug` | BUILD SUCCESSFUL，2 分 12 秒 | 两个 APK 变体都可编译；不是 ROM 编译 |
| ROM APK 签名、包名、HOME、x86_64、16 KiB ZIP 对齐、未压缩 JNI | 通过 | 可以被产品配置导入的应用输入；仍需完整 Soong 验证 |
| ROM APK 安装到现有 `emulator-5554` | Success | 应用安装成功；模拟器原系统未被换成 Yokuli ROM |
| PackageManager 查询 HOME/DEFAULT | 返回 `com.yokuli.marine/.shell.rebuild.MainActivity` | 注册真实 HOME Intent |
| 显式 HOME 冷启动、通知中心打开后 HOME 热返回 | 启动成功；热 Intent 投递现有 Activity，通知关闭并回桌面，实图已查看 | HOME Activity 的接入；未改变模拟器默认桌面、未验证 Cuttlefish 开机选择 |
| 集成器 5 项临时目录检查 | 通过 | 首次/幂等集成；拒绝未知目录、已有改动、篡改输入、vendor 链接 |
| Python / Bash 语法与文档链接 | 通过 | 本地工具输入与文档完整性 |
| 本机 ROM doctor | `can_build=false`、`can_run_cuttlefish=false` | Darwin arm64、可用空间约 14–16 GiB、没有 Repo/KVM |
| AOSP 全量构建、Soong 产品解析、Cuttlefish 启动 | **NOT RUN** | 缺合适 Linux 构建机，不以 APK 成功代替 |
| 真机 BSP、刷机、AVB、OTA、海上长时运行 | **NOT RUN / 未实现** | 不对外宣称这些能力已具备 |

ROM HOME APK：`0.5.0-experience.6-rom` / versionCode 10，Android Debug 签名。SHA-256：`4ce1a2c005e5b305f15b9c21f33d3c2d908f6dbb57d928b49b80382195f3fac4`。

本地交付位于工作区 `deliverables/rom-r0/`：`Yokuli-OS-R0-Home-debug.apk`、产品集成包及 HOME 前后截图。集成包元数据明确写 `is_rom_image: false`，包含实际源码 commit、APK hash、证书指纹和 AOSP revision。它不能用于 fastboot，也不是 OTA ZIP。

## 还需提供的外部条件

1. 可用 Linux x86_64 构建机，符合空间/内存要求；启动 Cuttlefish 另需 KVM。没有购买或创建云主机。
2. 真机型号及其可维护 BSP/解锁/恢复条件。在此之前以 Cuttlefish 为默认验证目标。

拿到构建机后，按 [构建手册](07-BUILD-AND-VALIDATION.md) 完成完整镜像和首次启动；通过后才能把 R0 从“产品输入已准备”推进为“ROM 原型可启动”。当前分支没有添加自动执行数小时 ROM 构建的 CI，也没有等待应用 CI。
