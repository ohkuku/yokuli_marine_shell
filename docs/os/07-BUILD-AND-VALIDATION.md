# ROM 构建与验证

状态日期：2026-09-18。R0 集成目标为 Cuttlefish，不是任何实体手机。APK、AOSP 产品集成包、系统镜像是三个不同交付物，不能互相代称。

## 1. 固定输入

| 输入 | 值 |
| --- | --- |
| 应用参照 | `codex/yokuli-os-rebuild` 的 `b5fc247`，experience.6 |
| OS 工作分支 | `codex/yokuli-os-rom` |
| AOSP manifest | `https://android.googlesource.com/platform/manifest` |
| AOSP revision | `android-16.0.0_r4`，Android 16 / BP4A.251205.006 |
| lunch | `yokuli_cf_x86_64_phone-trunk_staging-userdebug` |
| 上游设备产品 | `device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk` |
| 本仓产品配置 | `rom/aosp/AndroidProducts.mk`、`yokuli_cf_x86_64_phone.mk`、`Android.bp` |
| 应用变体 | `romDebug`；正式签名后才讨论 `romRelease` 与发布镜像 |

固定旧的兼容基线用于先验证构建链，不能当作安全补丁已跟进。官方版本表列此 tag 为 2025-12-05 安全补丁；正式发布须重新选择受维护基线并重新验收。[AOSP 版本表](https://source.android.com/docs/setup/reference/build-numbers)

已从官方 Gitiles 核对 5 个产品、板级和 Soong 文件；`rom/upstream-reference.json` 保存确切 URL 与内容 SHA-256。它只证明配置参考来源，不证明整个 Soong 构建成功。构建时另保存 `repo manifest -r`，记录每个仓库实际提交。

## 2. 构建机

AOSP 官方工作站要求为 Linux x86_64、至少约 400 GB 可用空间和 64 GB RAM。Cuttlefish 启动还需要可用 KVM；Docker 本身不会让 macOS 获得可用的 Linux KVM。源码已同步时本项目 doctor 用额外 150 GiB 构建空间检查。系统包、JDK、Repo 和宿主支持以官方指南为准。[构建要求](https://source.android.com/docs/setup/start/requirements) · [Cuttlefish 宿主安装](https://source.android.com/docs/devices/cuttlefish/get-started)

```bash
python3 rom/tools/doctor.py --path /srv/aosp-yokuli
```

返回 `can_build: false` 或退出码 2 时先处理原因。doctor 只读，不下载或安装依赖。本轮本机为 Darwin arm64，剩余约 14–16 GiB，缺 Repo 与 KVM；因此完整系统构建和启动都没有执行。无需为了这一步删除用户资料或下载数百 GB 源码到该 Mac。

## 3. 编译、校验系统桌面

沿用现有 Android SDK 与 Java 17；`local.properties` 或 `ANDROID_HOME` 指向已安装 SDK：

```bash
./gradlew :app-shell:assembleRomDebug :app-shell:assembleStandaloneDebug
python3 rom/tools/package.py \
  --apk app-shell/build/outputs/apk/rom/debug/app-shell-rom-debug.apk \
  --sdk "$ANDROID_HOME" \
  --output /srv/artifacts/yokuli-r0-bundle \
  --development-apk
```

输出目录必须不存在，避免覆盖旧产物。`--development-apk` 明确接受可调试 APK；不传时拒绝 debug 包。Release 使用同一参数形状但换已签名 `romRelease` APK，并去掉 development 标志。沿用原签名与密钥注入脚本，不能把平台签名给业务 APK。

包内包括 APK、Soong/Make 产品配置、`yokuli-build.json` 和文件哈希清单。元数据包括应用版本、签名指纹、ABI、源码提交及是否有未提交修改，明确 `is_rom_image: false`。AOSP `preprocessed` 导入保持 APK 签名与字节不变，不禁用预置 APK 检查。

## 4. Linux 上同步并集成

下面命令在已经准备好工具和空间的 Linux 构建机执行。本轮没有自动执行下载。

```bash
mkdir -p /srv/aosp-yokuli
cd /srv/aosp-yokuli
repo init -u https://android.googlesource.com/platform/manifest -b android-16.0.0_r4
repo sync -c -j8
```

回到 Yokuli 源码目录：

```bash
python3 rom/tools/doctor.py --path /srv/aosp-yokuli --source-present
python3 rom/tools/integrate.py --bundle /srv/artifacts/yokuli-r0-bundle --aosp /srv/aosp-yokuli
bash rom/tools/build.sh /srv/aosp-yokuli
```

`integrate.py` 校验包内全部哈希，拒绝未知目录、链接和本地改动；相同包可重复执行，不同包必须先把旧 `vendor/yokuli` 移到源码树外备份。脚本不会删除它。`build.sh` 固定 userdebug 产品，先核对 manifest 提交号和所有仓库的修订/本地改动，再执行 `m dist target-files-package otatools`；默认 8 个并行任务，可用 `YOKULI_ROM_JOBS` 调整。R0 拒绝额外 local manifests 和环境中的 `OUT_DIR / OUT_DIR_COMMON_BASE / DIST_DIR`，避免把产物写入另一套构建目录；升级构建工具支持独立输出路径后再解除此限制。

预期产物位于 `out/target/product/vsoc_x86_64_only` 与 `out/dist`；实际文件名包含构建 ID，必须读取本次输出，不能假定存在某个 ZIP。必须保留 target-files、完整 manifest、应用元数据与构建日志。`out/yokuli-evidence/aosp-manifest.xml` 是脚本自动保存的确切源码快照。记录 Linux发行版、编译器版本、构建耗时及失败日志。

## 5. Cuttlefish 启动

先按官方指南安装对应宿主 Debian 包、设置 KVM 权限，再使用同一次构建生成的镜像与 host package。以下是已执行 `lunch` 后的源码树启动方式，尚未在本轮运行：

```bash
launch_cvd --daemon
adb devices
```

不要随便选第一个 adb 设备。人工确认 Cuttlefish serial 后：

```bash
adb -s "$YOKULI_CVD_SERIAL" shell getprop ro.product.name
adb -s "$YOKULI_CVD_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$YOKULI_CVD_SERIAL" shell cat /product/etc/yokuli/build.json
```

产品应为 `yokuli_cf_x86_64_phone`，HOME 应指向 `com.yokuli.marine.shell.rebuild.MainActivity`。验证 `/product/app/YokuliHome` 是系统预置，而非误用后来 adb 安装的 `/data/app` 副本；确认首次配置完成后可进入 Yokuli。收集截图、boot log、SELinux denials、崩溃记录与 manifest。没有这些证据，不标记“ROM 启动通过”。

## 6. 真机与 OTA

本产品的 `physical_device_supported` 为 false。不能把 Cuttlefish 的 boot/vendor/super 镜像刷进手机，也不能以相同 CPU 架构推断兼容。先补具体设备、BSP、可解锁情况、分区布局、vendor blobs 授权、恢复方案，再制作该机型独立 target。本文不提供通用 `fastboot flash` 或解锁命令。

R0 不发布 OTA。正式 OTA 必须有正式 AVB/平台/应用签名、安全补丁、A/B 健康确认、数据迁移和回退验收；详见安全文档。普通 GitHub runner 不承担数百 GB ROM 构建；没有暗中新增昂贵 workflow，也不等待 CI。

## 7. 本轮证据

最终执行记录见本目录 `09-R0-DELIVERY.md`。R0 状态只在真实证据存在时推进：APK 可安装 ≠ ROM 可启动 ≠ 可刷真机 ≠ 可海上值守。
