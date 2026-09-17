# NMEA 分享与全局航行实测 · 2026-09-17

## 环境和边界

在 `emulator-5554`（Android ARM64 模拟器）的实际 APK 内操作 UI。测试数据来自本机临时 TCP fixture，包含有效校验码的 RMC、HDT、DPT、MWV；位置和仪表值为测试数据，不能作为真实航海数据。没有连接真实船载设备，没有做长期耗电、断网恢复或压力验收。

模拟器原为飞行模式开启、WiFi 关闭。首次测试连接没有收到数据，检查发现所有外部网卡关闭；临时开启模拟器 WiFi 后连接成功。这个环境问题没有当作应用接收失败。

## 已验证

| 用户故事 | 实际操作和结果 |
| --- | --- |
| 最近任务恢复未保存连接 | 新建 `QA_RX_19171` 并输入名称，回船桥，长按返回打开真实截图任务卡，再恢复 NMEA；编辑页和名称草稿保留。 |
| 独立输入接收 | UI 新建 `QA_RX_19171`，TCP 接收 `10.0.2.2:19171`；原有 `Boat` 保持停止、未编辑。实际有效接收计数持续增长。 |
| 同 IP 不同端口禁止回送 | UI 新建 `QA_TX_19172`，TCP 发送 `10.0.2.2:19172`，RAW 模式只选上述 QA 输入，启用相关能力。输出 TCP 成功连接并持续 **861.3 秒**；应用实际写出为 **0**，fixture 也没有任何 `output_received` 或反向输入数据。期间 fixture 共向输入连接发送 **9800 句**，所以不是没有输入造成的零输出。 |
| 本机服务可以向其他接收端分享 | 本机服务选择 RAW、只选 QA 输入、监听 10111；主机通过 `127.0.0.1:19173 → adb forward → Android:10111` 接入。一次 5 秒窗口收到 **RMC 13、HDT 12、DPT 12、MWV 12**，共 **49** 句，全部校验码有效。 |
| 能力关闭确实改变实际报文 | UI 停止本机服务，在分享内容中仅关闭“船首向”，保存并启动。新的 5 秒窗口收到 **RMC 13、DPT 12、MWV 12、HDT 0**，共 **37** 句，全部校验码有效；其余能力继续发送。 |
| 一次全局航行跨应用控制 | NMEA UI 选 QA 输入船位；日志提示船位就绪，开始 `QA_VOYAGE_INTEGRATION`。海图按钮同步为“记录中”，控制对话框显示同一名称；从海图暂停后按钮变“已暂停”并收到航行日志通知；继续后恢复“记录中”；结束后显示“航行记录已保存”，海图恢复“开始记录”。日志历史出现同名记录，随后通过 UI 删除，原有 9 月 9 日记录仍在。 |

实测还发现窄状态栏中未读通知数会挤掉 REC。已在 `WpStatusStrip.StatusContents` 修复为航行、锚警运行状态优先于普通未读计数，并纳入后续最终构建；上述场景运行于修复前候选包，**不宣称修复后的 REC 布局已在这次测试中再次实图验证**。

## 清理

- QA 航行通过 UI 结束并删除；保留原历史航行。
- 本机服务以及两条 QA 连接通过 UI 停止。
- 停止 App 进程后，通过二进制文件传输恢复测试前 `os_nmea_connections`、`settings`、`vessel_data_settings` 三份 DataStore 配置，分别为 266、1666、748 字节；逐份 SHA-256 校验完全一致。没有清空 App 数据。
- 本机服务原本没有配置文件，仅移除本次测试新建的配置，恢复原默认状态。
- 清除本次 ADB 端口转发，停止临时 19171/19172 listener；确认无遗留测试监听器。
- 模拟器 WiFi 恢复为 0，飞行模式保持 1。交还模拟器时 App 停止，供最终包安装启动验收。

## 证据

- `docs/experience/screenshots/experience3-nmea-same-ip-blocked.png`
- `docs/experience/screenshots/experience3-local-nmea-filtered.png`
- `docs/experience/screenshots/experience3-voyage-log-active.png`
- `docs/experience/screenshots/experience3-chart-voyage-paused.png`

原始 fixture 日志和两次收到的测试报文保留在本机 `/private/tmp/yokuli-nmea-fixture.jsonl`、`/private/tmp/yokuli-local-before.txt`、`/private/tmp/yokuli-local-after.txt`，不属于交付运行依赖。
