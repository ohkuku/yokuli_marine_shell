# W13 Product & Engineering Contract — Preferences

## Intent / 产品愿景

Preferences 是 Yokuli OS 唯一有资格“像系统设置”的应用。用户在这里决定整个 Shell 的外观、语言、显示单位、动画偏好、Start 文档以及各 App 主动公开的磁贴偏好；地图资源、NMEA 连接、数据源选择和航线管理必须留在各自的产品应用中。

它应当像 WP8 Settings：文本层级明确、选择直接、没有控制台式配置表。功能可以随 App 增长，但 Preferences 不应演变成理解所有 Feature 内部类型的中央巨石。

## Experience / 用户体验

- Overview 一眼列出 Appearance、Language、Units、Motion、Start、App Tiles 和 About，并显示当前选择。
- Appearance 继续使用一致的黑/白背景与全局主题色；改变后 Shell 和所有磁贴同步更新。
- Language 从应用内容到入口文案保持中文主文和英文翻译。
- Units 只改变数值呈现，不重写保存坐标、海图、NMEA 或历史记录。
- Motion 默认跟随 Android 动画缩放；“减少动画”在此基础上关闭应用内透视转场，同时保留上下文。
- Start 只展示当前文档事实并允许恢复默认空间布局；不得清除任何地图、数据、航点或航线。
- App Tiles 从已安装 App 的 catalog 与 typed preference contribution 投影；没有 App 专属选项时明确说明为空，不造开关。
- About 只陈述构建与 Shell 文档事实，不冒充诊断页面。
- App 内 Back 回 Overview，Overview 根部交给 Shell 回 Start，永不退出应用。

## Domain / 领域关系

- `preferences` 是稳定的新 App/entry/token 身份；`settings.*` 只作为旧持久化 token 和深链兼容输入。
- Product migration 将 `settings` tile 原位转换为 `preferences`，保留 tile id、rank、size 与 group。
- OS 级偏好保存在 Launcher persistence：theme、accent、language、unit system、motion preference 和有界 app preference values。
- 每个 App 可通过 `LauncherCatalogContribution` 提供 typed `AppPreferenceContribution`；全局 registry 只接受已安装 owner、唯一 key 和类型合法值。
- Preferences Feature 只依赖稳定合同与设计系统，不依赖 Chart/Data/Navigation runtime 类型或 Android adapter。

## Opportunities / 可发展空间

后续 App 可以注册有限的磁贴模式、刷新节奏或纯展示选项；可以加入更清楚的单位细分、无障碍字体与数据导出策略。新增项必须说明作用域、默认值、迁移和不生效时的真实状态，不能让 Preferences 变成隐藏开发者面板。

## Non-negotiables / 不可协商边界

- 生产 registry 只能安装 Preferences，不能同时安装 Settings；旧 Settings module 在 W15 前仅作为未注册兼容代码保留。
- 默认 Start 的既有 `tile-settings` identity、1×1 size 与 rank 不变，只把 entry 改为 Preferences。
- schema v4 必须向前兼容读取旧 proto；新增字段有确定默认值，非法值恢复时产生 typed incident。
- app preference registry 必须有界、按类型解析、非法持久值回到定义默认；未安装 App 不能注入设置。
- Motion 偏好必须真正参与 Shell transition policy，不只是 UI 选中状态。
- Preferences 不导入 Feature runtime，不拥有地图、socket、GNSS、数据库或文件扫描。
- 不提供 Android HOME/桌面设置入口，不改变竖屏沉浸式 Shell 与虚拟实体键。

## Forbidden outcomes / 禁止结果

- 禁止保留 Map/provider/package 管理页，禁止把 Chart Library、Data Sources 或 Navigation 管理嵌进 Preferences。
- 禁止用字符串 switch 猜 App 内部配置，禁止无类型的任意 key/value 设置表。
- 禁止迁移时重建或重新排序用户 Start，禁止清库。
- 禁止让“公制”改变底层航海数学或 NMEA 单位语义。
- 禁止让“跟随系统”忽略 Android reduced-motion，或让“减少动画”关闭必要状态反馈。
- 禁止生产同时出现 Settings 和 Preferences 两个入口。

## Acceptance stories

1. 旧 `settings` 小磁贴升级后仍是同一 tile id、同一位置和尺寸，但打开 Preferences。
2. 旧 `settings.appearance/start/language/about` token 被确定性迁移；旧 `settings.map` 安全落到 Preferences overview，而不是保留资源管理页。
3. 用户切换 theme/language/units/motion 后重启，值从同一 Launcher store 恢复；保存一个字段不覆盖其他字段或 Start 文档。
4. 用户选择 reduced motion，Shell 的 app/pager transition 立即采用 reduced policy；系统已经减少动画时 FOLLOW_SYSTEM 仍然尊重系统。
5. App Tiles 只展示当前已安装 App 声明的尺寸和 typed preference keys；空定义显示真实空状态。
6. 未安装 App、重复 key、错误 choice 值不能进入 registry；错误持久值回到 typed default。
7. Preferences UI 不出现地图包、provider、NMEA、连接或数据源管理操作。
8. App 内 Back 先回 Preferences overview，overview 根才回 Start。

## Evidence required

- core JVM：registry owner/duplicate/type/default，schema v4 默认与非法值迁移。
- storage JVM：units、motion、app preferences 与完整 Launcher snapshot 原子 round trip。
- feature JVM/API34：新/旧 token、七个设置领域、typed actions、App Tiles 声明投影与 Back。
- app-shell：Settings→Preferences product migration 保留 tile identity/size/order；生产只有五个已完成 App。
- static：无 peer Feature/adapter 依赖、无地图管理、workflow/report/lock/state。
- 完整 test/lint/device/release gate 留在 W16 Actions；本轮提交后只编译受影响源码与测试源码。

## Existing code landmarks

- `YokuliProductModel` 已冻结 migration version 2 与 Settings→Preferences aliases。
- `LauncherPersistence` / `launcher_state.proto` 是现有 OS 偏好和 Start 文档的同一原子存储。
- `InstalledAppBinding` 已是 App 对 Shell 的单点贡献入口。
- `feature/settings` 是历史兼容实现；W13 不再生产注册，W15 才移除依赖与旧 surface。

## Implementation freedom / 实现自由

只要满足身份迁移、数据不变量、typed registry、真实 UI 行为和证据要求，实现者可以自由决定 Compose 拆分、registry 投影和 persistence API；不得为了文档新增第二个偏好数据库或 Feature 专属 service。

## English translation

W13 makes Preferences the only OS-like settings app. It owns appearance, language, display units, motion, Start, typed app-tile preferences, and build facts while excluding resource and runtime administration. Legacy Settings identities migrate in place, schema v4 preserves the complete Launcher snapshot, and apps may contribute bounded typed preferences without coupling Preferences to feature runtimes.
