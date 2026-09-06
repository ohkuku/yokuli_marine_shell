# CL00 基线报告 / Baseline Report

状态：`PASS`

实际起点：`codex/shell-map-contract@8d2376bd76c38bd5d6e559d08b00a59d7261c837`

## 结论

- 文档审阅 SHA `4b1cec8…` 不是回退指令。实际起点已经完成 NMEA Input 与
  Data Sources，生产面为 Chart、Settings、NMEA Input、Data Sources；海图库
  必须作为第五个应用追加，不能删除既有入口。
- 项目只有 `standalone`，仍为竖屏普通 `MAIN + LAUNCHER`，没有 Android HOME。
- `adapter:chart-google` 和 Google surface 仍在仓库，但生产组合根已断开；CI、
  manifest 与产品合同还主动拒绝 `GOOGLE_MAPS_ANDROID_API_KEY`。这与本轮用户
  直接要求冲突，后续以 `USER_OVERRIDES.md` 为准重新接入。
- 当前 Chart 内 `ChartPackageCoordinator` 拥有 picker、inspect、install、delete，
  `AndroidMbTilesRepository.inspect()` 复制整库后执行完整 SHA 和逐瓦片解码。
  这是“大海图一直解析”的直接代码原因，不是 Secret 问题。
- 圆角屏已能读取 rounded-corner metrics，但现有安全区策略把 top inset 作为
  全宽空间消费。后续改成角落控件横向内收，地图和应用主体不整体下移。

## TDD 证据

- Red：`python3 .github/scripts/test_chart_library_cl00_contract.py`，4 tests 中
  2 errors；缺少用户覆盖与实际基线锁。
- Green：同一命令必须在提交前 4/4 通过。
- 按用户要求未运行完整 Gradle 门禁；完整仓库门禁只在 CL12 执行。

## 偏离与阻塞

- 偏离：附件的起点 SHA 较旧；保留当前工作树，不回退 NMEA P7。
- 偏离：附件禁止 Google 在线填洞，但用户直接要求 Secret 驱动可见在线底图；
  在线底图与本地离线覆盖将保持两套清晰状态，不互相冒充。
- 阻塞：无。

