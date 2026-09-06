# 用户直接覆盖项 / Direct User Overrides

本文件只记录用户在 2026-09-06 对施工包冲突条款的直接覆盖；其余行为仍以
`REQUIREMENTS.md` 为准。These are direct user request overrides; every other
requirement remains governed by `REQUIREMENTS.md`.

## 1. 在线底图与密钥

- GitHub Actions 和本地构建必须消费 `GOOGLE_MAPS_ANDROID_API_KEY`，通过
  Android manifest placeholder 注入，真实值不得写入源码、文档、日志或
  `BuildConfig`。
- 密钥已配置时，Chart 必须能显示 Google 在线底图；“已提供密钥”与“SDK
  已授权、网络可用、图块实际加载成功”仍是不同事实。
- 本地 MBTiles、海图库和离线覆盖仍是独立能力。Google 在线底图不得被计入
  本地覆盖检查，也不得掩盖本地资源缺片、失效或未验证状态。
- 无密钥构建仍须成功，并明确显示在线底图未配置；不得显示假地图。

## 2. 顶部圆角与可用内容区

- 圆角屏顶部控件采用左右边缘避让：只把靠近左上角、右上角的交互项稍微向
  内移动。
- 不得整体下移顶部菜单，不得通过一个等于圆角半径的大块 top padding 牺牲
  Chart 或其它应用的垂直内容。
- 地图相机的 obscured insets 只反映真实遮挡；视觉边缘避让不能被重复计入地图
  viewport。

## 3. 旧导入长期“解析”问题

当前 `AndroidMbTilesRepository.inspect()` 会先复制整个输入、计算完整 SHA，
再逐瓦片解码完成检查。默认添加资源不得继续执行这条完整 SHA + 逐瓦片解码
链路。新海图库按 `DISCOVERED → BASIC_READABLE → FULL_VERIFIED` 分层：基础检查
有界、可快速返回；完整验证只能由用户明确触发，必须显示进度且可取消。

