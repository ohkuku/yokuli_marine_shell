# WP8 原始截图合同

状态：`HUMAN_REVIEWED`／`APPROVED`；Stage 0 的历史状态为 `NOT_YET_MEASURED`。

本目录只保存合法取得、可追溯的 WP8 原始参考截图或视频帧。当前唯一来源是仓库所有者提供的 `owner-emulator-recording/wp81-emulator-reference.mp4`；16 张 PNG 是由固定工具在清单时间戳直接解码的完整 1920×1080 帧。每个文件必须在 `WP8_REFERENCE_MEASUREMENTS.json` 的 `captures[]` 与 `SOURCE_MANIFEST.json.extractions[]` 中相互引用，并记录稳定 capture ID、路径、内容 SHA-256、字节数、MIME type、像素尺寸、来源类型、原始/裁剪状态、可用的设备与 OS build，以及所有权/许可说明。禁止裁剪后冒充原图，禁止把 Yokuli 实现截图或搜索得到的手持/倾斜手机照片放入本目录。

当前命名格式：

```text
owner-emulator-recording/frames/<capture-id>.png
```

所有媒体的权利边界见 `../THIRD_PARTY_NOTICES.md`。模拟器录屏证明内容画面与可见运动，不等于物理 WP8 真机、触摸 latency 或按键激活证据。Edit、fast fling、pin、long-press drag、resize、unpin 和独立 press feedback 保持 `NOT_OBSERVED`。

## English translation

Status is `HUMAN_REVIEWED` / `APPROVED`; the Stage 0 historical state was `NOT_YET_MEASURED`. The only current visual source is the repository-owner-supplied emulator recording. Its sixteen reference PNGs are exact uncropped 1920×1080 frames decoded at manifest timestamps. Every file is cross-referenced by source extraction and capture records with its SHA-256, byte size, dimensions, source, timestamp, and rights note. Search-result handheld or angled-phone imagery and Yokuli implementation screenshots are not reference evidence. The emulator video proves rendered states and visible motion, not physical-device input latency or key activation. Missing edit/direct-manipulation scenarios remain `NOT_OBSERVED`. Repository owner kuku approved the hash-bound reference package as profile revision 1.
