# CL02 报告 / Zero-copy renderer report

状态：`PASS — API34_LOOPBACK_PIXEL_TESTED`

起点：`4fadfc5`

## 实现

- `ChartLoopbackTileGateway` 仅绑定 `127.0.0.1`，为每个进程生成随机 token，
  路由同时绑定 asset 与 content revision；只接受有界 `GET z/x/y`，限制 8 个
  worker，并返回 `Cache-Control: no-store`。它不是文件服务器，也不监听 LAN。
- MapLibre `RasterSource` 通过该网关按需调用 CL01 的 `ChartReadSession`。SAF
  原件保持打开但不复制、不写入；注册关闭后 token/asset/revision 路由立即失效。
- 修正 Android 上 `InetAddress.getLoopbackAddress()` 可能得到 `::1`、进而生成无效
  URL 的问题，明确使用 IPv4 loopback；应用网络策略默认禁止明文，仅允许
  `127.0.0.1`。
- 按用户直接覆盖要求恢复 Google 在线底图：Actions 的
  `GOOGLE_MAPS_ANDROID_API_KEY` 通过 manifest placeholder 注入，代码只读取布尔
  `GOOGLE_MAPS_CONFIGURED`。未选择本地海图且已配置 key 时使用 Google adapter；
  本地海图仍由 MapLibre 独立渲染，Google 不计入离线覆盖。
- Google adapter 已对齐共享 renderer 合同：generation/lifecycle、相机 command
  ack、viewport inset、投影/反投影、点击/长按、点拖动和 overlay。删除旧的
  `96dp/150dp` 硬编码内容牺牲。

## TDD 与验证证据

- Red：Google 注入合同 4 项中 3 项失败；回环 gateway 类型缺失导致编译失败；
  初次真实 MapLibre 测试因 `::1` URL 无法解析而失败。
- Green：`ChartLoopbackTileGatewayTest` 通过，覆盖正确 tile、错误 token/revision、
  非 GET、路径穿越与越界坐标。
- API 34：adapter instrumentation 5/5；其中 `MapLibreSafGatewayRenderTest` 从真实
  DocumentsProvider 原件读取 tile，经 loopback RasterSource 截图命中已知像素；
  源 SHA 与目录文件清单不变。
- 无 key 构建生成 `GOOGLE_MAPS_CONFIGURED=false` 与占位 manifest 值；显式
  `TEST_ONLY_NOT_A_REAL_KEY` 生成 `true` 并进入 merged manifest。测试值不是 API key。
- Google、MapLibre、CI workflow 静态合同和受影响模块编译通过。

## 真实性边界

- CI secret 注入能力已验证；真实 Google key 的 API 授权、billing、包名/SHA
  限制、联网 tile 像素仍为 `NOT_RUN`，不能据此声称“地图已就绪”。
- 真实厂商设备、云盘 provider、arm64 与 16 KiB 页仍留 CL11/CL12；本工单证据
  只覆盖 API 34 emulator 的 seekable DocumentsProvider。
- 旧 `AndroidMbTilesRepository.inspect()` 的整文件复制/完整 SHA/逐 tile 解码尚未
  在本工单删除；新的 Chart Library 默认路径不调用它，迁移/兼容清理在 CL09。
