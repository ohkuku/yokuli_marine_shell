# Yokuli Marine Shell 产品模型纠错需求

状态：`IN_PROGRESS`。本文件是两份 owner 附件的仓库内执行索引，不冒充附件原文。

## 规范优先级

1. 当前代码、可执行测试和 owner 本轮明确请求；
2. `Yokuli_Marine_Shell_Product_Model_Correction_Final_Phase.md`，附件 SHA-256 `036245e7192c1fdb5c38e3afe80ff25584cda0ba1e6105ff13fbe875d0f1e958`；
3. `FINAL_LAUNCHER_SHELL_PHASE.md` 中不与产品纠错冲突的架构、TDD、持久化、性能和机器验证要求，附件 SHA-256 `947f06421ac439cd9c02cdaea4b514759a651fcde6cf68e56eda8c7f5125d82d`；
4. Stage 2.5 已批准且哈希锁定的 WP8 参考包；
5. 旧 Stage 报告仅作历史证据。

## 产品事实

Yokuli 是沉浸式全屏 Android 航海应用，内部拥有 WP 风格 Shell。它不是 Android 默认 Launcher、第三方应用启动器、Kiosk 或 SystemUI 替代品。Android Home 离开应用；底部 Bridge 键只导航到 Yokuli Desktop。

当前 Release 模块仍严格只有 Chart 与 Settings。不得在本阶段新增 GPS、NMEA、Anchor、Trip、Navigation、Survey 或虚假海事状态。

## 可交付合同

- 只保留普通应用构建；Manifest 不注册 `HOME` / `DEFAULT`，不再提供默认桌面设置逃生语义。
- 冷启动显示 Desktop；已有 task 被重新带到前台时保留内部 Surface。
- Desktop、Module List、Search、Recents、Module 是一等视觉 Surface；一次用户动作只产生一次原子过渡。
- Bridge 根据来源选择 Pager 返回、Module exit 或 Search dismiss；不得统一套用 deeper-back。
- Search 结果直接进入 Module，不露出中间 Desktop/Module List 帧；Launch transaction 防重复点击。
- 中间虚拟键使用四向罗经花，不使用 Windows 四窗格品牌图形。
- 状态条和底部导航读取真实 safe drawing、cutout、rounded-corner、IME 与 system-gesture insets。
- Settings 回归克制的黑白排版；Accent 只用于选择、焦点、Toggle、链接和必要强调。
- Marine Tile 支持 1x1、2x1、2x2、4x2、2x4、4x4，且每种尺寸有独立内容布局。
- Tile 文档持久化 rank、size、group；自适应 packer 按 insertion 生成当前 viewport cell，显式 Spacer 才表达留白。
- 高频 pointer offset 留在 Renderer；Engine 只接收 Begin/TargetChanged/Drop/Cancel/Resize 等语义事件。
- Desktop 内直接编辑；邻居动态 reflow；小磁贴编辑控件 hit target 至少 44dp；Resize 可取消和恢复。
- 中英文资源、无障碍标签、单元测试、Activity stories、性能趋势和 Release 二进制审计同步更新。

## 真实性边界

模拟器指标只作为趋势。三星方屏、普通真机、60/90/120 Hz 和主观 WP 手感保持 `PENDING`，只能由 owner 最终批准。
