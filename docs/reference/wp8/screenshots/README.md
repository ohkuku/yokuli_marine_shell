# WP8 原始截图合同

状态：`NOT_YET_MEASURED`。

本目录未来只保存合法取得、可追溯的 WP8 原始参考截图或视频帧。每个文件必须在 `WP8_REFERENCE_MEASUREMENTS.json` 的 `captures[]` 中出现，并记录稳定 capture ID、路径、内容 SHA-256、字节数、MIME type、像素尺寸、来源类型、原始/裁剪状态、可用的设备与 OS build，以及所有权/许可说明。视频帧还要记录毫秒时间点。禁止裁剪后冒充原图，禁止把 Yokuli 实现截图放入本目录。

命名格式：

```text
wp8_classic_phone_4col__<surface>__<theme>__<width>x<height>__source.<ext>
```

Stage 0 不提交任何参考媒体；实际采集和权利检查等待人工 Reference Lab。

## English translation

Status is `NOT_YET_MEASURED`. This directory is reserved for legally obtained, traceable WP8 screenshots or video frames. Every file must have a `captures[]` record containing its stable ID, path, content SHA-256, byte size, MIME type, pixel dimensions, source type, original/crop state, available device/build details, and ownership or license note; video frames also record a millisecond timestamp. Yokuli implementation screenshots are never WP8 reference evidence. Stage 0 adds no media.
