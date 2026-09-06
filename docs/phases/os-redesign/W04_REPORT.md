# W04 报告 / Data Complete Vertical Slice

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`0fbac556943442a79cafa3a05bba4669a280e01a7`

## 业务结果

- Data 成为唯一生产数据 App，包含 Overview、Inputs、Sources、Flow、Diagnostics 五个真实页面语义。
- 生产 All Apps 从五项收敛为 Chart、Settings、Data、Chart Library 四项；默认 Start 仍只有 Chart + Settings。
- W01 migration v1 现在因 Data host 就绪而启用；旧 NMEA/Data Sources tile 与 token 确定性迁移/重定向。
- Overview 使用稳定 LiveField；Flow/Diagnostics 只投影实际 source、sentence、resolved value 与有界 raw evidence。
- Phone location 由进程持有的 Data demand runtime 管理；所选来源与待完成选择产生 demand，页面 lifecycle 不参与。

## DESIGN DECISIONS

- 复用了已通过 P2–P7 的 NMEA connection editor 作为 Data/Inputs 内部子流程，没有复制 socket、parser 或表单状态机。
- 新 Data Feature 不依赖旧 NMEA/Data Sources Feature；composition root 只注入已验证的 Inputs UI slot，因此产品入口统一而依赖方向不反转。
- SourceGroup 没有进入 durable schema；继续通过 W03 atomic adapter 写兼容的 per-key selection store。
- Phone demand controller 放在 Application process scope，而 DataCoordinator 只负责 UI action/effect，避免离开页面停止定位。
- Flow 首版采用稳定文本关系，刻意没有为了“图形化”绘制不存在的节点；后续可以在同一事实投影上替换绘制方式。

## 刻意未做

- 未实现 NMEA output/forwarding、自动 failover、设备控制或主动导航。
- 未删除历史 Feature 源码与报告；只从生产 registry/dependency 中移除旧 Data Sources，并保留 Inputs 兼容子流程。
- 未把 Data 自动 pin 到 Start，也未提前安装 Navigation/Preferences。

## 测试源交付给 Pipeline

- Feature：统一 token、Back、Phone selection demand、permission typed recovery、初始化前不误停 runtime。
- App：唯一 Data registry、W01 migration v1、旧动态 token、三种 tile size。
- API 34：All Apps → Data → Inputs → Sources → Overview → Back，以及 Data tile 三尺寸/双主题/大字体。
- Release：Data workspace 存在，旧 Data Sources workspace 不再作为生产依赖，runtime/service 保持。

## 本地与待验

CI-first：提交前只执行 source/static/JSON/XML/YAML/diff sanity；完整 unit、lint、Debug/Release build、APK 审计与 API 34/36 device suites 由 GitHub 对精确 SHA 执行。

## English translation

W04 installs one real Data app and retires the two visible protocol-oriented entries. It reuses the proven connection editor inside Data/Inputs, projects only real runtime evidence across Overview/Flow/Diagnostics, preserves atomic source selection, and moves Phone demand to process scope. Full build and device evidence remains pending from GitHub Actions for the exact commit.
