# NMEA_SOURCES P5 报告 / P5 Report

状态：`PASS`

起点提交：`4b1cec8f13fdf6d93fb4c69a9609b7209c2d72b5`

## 范围与结果

P5 把 NMEA Input 与 Data Sources 作为两个独立应用正式安装进 Yokuli OS。生产组合根现在恰好有 Chart、Settings、NMEA Input、Data Sources 四个 `InstalledAppBinding`；目录、静态和动态 LaunchToken、三尺寸视觉以及内部宿主均从同一个 binding 派生。默认 Start Document 仍然只有 Chart 与 Settings，因此升级不会自动固定或重排新磁贴。

两个应用都声明并自行渲染 Small、Medium、Wide：Small 使用 42dp 主图标，Medium 给出应用和主状态，Wide 最多给出两条连接或三条来源关系。磁贴只投影进程持有的 `NmeaRuntimeSnapshot` / `MarineSourceSnapshot`，不打开 socket、不请求定位，也不把经纬度或仪表值伪装成输入健康。编辑 Start 时普通内容冻结，但异常变化仍可突破冻结显示。

Shell 顶部状态条新增两种有类型、可独立点击的状态项：NMEA 入口优先定位异常连接，来源入口在有问题时打开“需处理”筛选。无启用 NMEA 时不显示错误；phone-only 是正常来源路径。页面、磁贴和状态条共用同一 snapshot 与纯 projector。

All Apps 可以发现并打开两个应用；应用总览 Back 只回到 Yokuli OS Start，不退出 Android 应用。跨应用跳转只传递 P4 的有界不透明 token。手机权限请求、系统位置设置和本应用权限设置由 composition root 执行；没有 Android HOME 注册或桌面设置入口，Activity 继续强制竖屏。

## TDD 与自查纠错

Red 提交：`11006625006d6a3a72c03821d69361b2f79edb4e`。

- P5 静态合同首跑为 `2 failures / 2 errors / 3 pass`；两个 Feature 的测试因 Tile/Status projector 与应用贡献尚不存在而在测试编译阶段失败。
- `de9eb99` 完成生产安装、视觉、状态条和 effect 接线。首轮最小编译门禁发现 deep-link coordinator 误用另一个文件的私有扩展，以及 Tile 上限常量作用域错误；`6ebd77e` 只修复这两个编译边界。
- API 34 Shell story 验证两个应用可发现、可打开，根 Back 回 Start；NMEA 磁贴由 Shell 固定并按应用声明从 Medium → Wide → Small 循环。
- 自审 Red `041da81` 证明“一条接收、一条等待”曾错误显示整体接收；测试精确失败后，`2f56fb2` 按冻结优先级改为等待高于接收，异常仍最高。`dede896` 再锁定“全部由用户停止”不是等待或故障。
- 最后的视觉矩阵在 API 34 上实际组合两套 Feature renderer 的三种尺寸、深／浅主题和 1.6 倍字体；静态资源合同同时要求中文主资源、英文和中文限定资源键完整。

## 最小 P5 Gate

```text
python3 .github/scripts/test_nmea_sources_p5_contract.py                PASS 8/8
./gradlew :feature:nmea-input:testDebugUnitTest                         PASS（P5 projector 7/7）
./gradlew :feature:data-sources:testDebugUnitTest                       PASS 20/20（P5 projector 4/4）
./gradlew :app-shell:testStandaloneDebugUnitTest                        PASS 7/7
./gradlew :app-shell:compileStandaloneDebugKotlin                       PASS
./gradlew :app-shell:connectedStandaloneDebugAndroidTest \
  -P...class=com.yokuli.marine.shell.NmeaSourcesShellStoryTest             PASS 3/3（API 34 AVD）
git diff --check                                                        PASS
```

遵照用户要求，本阶段没有执行全仓 test、Lint、Debug/Release assemble 或全部设备套件；完整质量门禁统一留到 P7。真实 OEM 锁屏／熄屏、真机网络切换、GNSS、三星方屏触控和功耗仍为 `UNVERIFIED_PHYSICAL_DEVICE`。

## English translation

P5 passes its scoped gate. NMEA Input and Data Sources are independently installed from one binding each, while the existing Start document remains Chart + Settings only. Both apps own explicit Small/Medium/Wide renderers and pure live-state projectors; the Shell owns two typed status-strip entries and routes their opaque destinations. All Apps discovery, in-app Back, pinning, the app-declared resize cycle, two themes, large type, and bilingual resource parity are covered. Full repository/release gates remain P7, and physical-device background, GNSS, radio, square-screen, and power behavior is not claimed.
