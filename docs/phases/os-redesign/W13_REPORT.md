# W13 报告 / Preferences

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`1027ad2103ad3bcc03e6632bf11493eae2139d83`

## 产品结果

- 生产 App 的 `settings` 身份已由 `preferences` 替代；旧 Settings module 暂留作未注册兼容代码，W15 再删除依赖。
- Preferences 提供 Appearance、Language、Units、Motion、Start、App Tiles、About 七个真实领域，没有地图资源、provider、连接或数据源管理页。
- W01 预置的 product migration version 2 现在生效：旧 settings tile 保留 tile id、rank、size、group 并原位切到 Preferences；旧 token 有确定 alias。
- Launcher persistence 升到 schema v4，原子保存单位、动画偏好和有界 app preference values；旧 proto 字段缺失时使用确定默认。
- 非法或超界 app preference 值会产生 typed recovery incident；单 App 与全局 registry 都有明确上限。
- reduced motion 偏好真实进入 Shell transition 决策，并继续尊重 Android 系统动画缩放和 safe mode。
- App 可通过 catalog contribution 注册 typed preference definitions；registry 拒绝未安装 owner、重复 key 和类型不合法值。

## DESIGN DECISIONS

- 新建独立 `feature:preferences`，而不是继续给旧 Settings 增加管理页面；生产只注册一个身份，旧模块到 W15 才物理移除以缩小本轮爆炸半径。
- 将 app preference contract 放在稳定 Shell contract，而不是 Preferences Feature；这样 App 声明偏好时不会反向依赖系统 UI。
- OS 偏好继续与 Start 文档使用同一 Launcher snapshot，避免多个 DataStore 之间出现 theme/units/motion 不一致。
- Units 明确只控制展示；本轮不改写底层导航、地图或 NMEA 数学，支持单位偏好的消费者按稳定 Shell contract 读取它。
- App Tiles 从当前已安装 catalog 与 registry 派生；还没有真实 App 专属选项时显示空状态，W14 再注册 live-tile modes。

## 暂不实施

- 不在 W13 添加 W14 的 live tile modes/cadence/snapshot runtime。
- 不在 W13 删除 feature/settings 源码及所有历史测试夹具；属于 W15 的兼容面清理。
- 不把 Android 系统设置、地图资源、NMEA 或设备管理引入 Preferences。

## Pipeline 证据

- W13 static contract、core typed registry、schema migration/storage round trip、Preferences unit/UI stories、product migration 与 app registry tests。
- 本地仅在提交后运行受影响 compile tasks；完整 gate 由 W16 GitHub Actions 承担。

## English translation

W13 replaces the production Settings identity with one real Preferences system app, activates the in-place product migration, persists units and motion in schema v4, applies reduced motion to Shell transitions, and introduces an installed-owner typed app-preference registry without importing feature runtimes.
