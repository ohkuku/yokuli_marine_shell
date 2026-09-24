# experience.8 · 导航与页面头部一致性

2026-09-24，`codex/yokuli-os-rom`，versionCode 12。

- 地点进入下锚使用明确的 `anchor:setup` 关联入口；设置和取消回调用者，选项先回设置。本次下锚参数不泄漏到下一次独立应用访问。
- 设置中的磁贴入口保留调用者；图层修复带文件夹身份，完成后恢复原海图视角。
- 普通页面与地图页统一 `PageNavigation`。标题与应用身份不再冒充返回目的地，返回按钮和系统 Back 走同一个局部处理器/Shell 栈。
- 去掉普通页面中的 YOKULI/OS 品牌占位，保留应用与对象必要身份；移除海图工具中的泛应用跳转。
- 日夜/姿态动作按钮的上一轮修复保留。

契约和交接场景见 [应用访问与返回契约](../product/APP_NAVIGATION_CONTRACT.md)。本轮通过本地 `:app-shell:assembleRomDebug :app-shell:assembleStandaloneDebug` 编译（43 秒）。按用户要求未运行单元测试、模拟器或真机操作，也不等待 CI；上述是实现内容，不是已完成全部场景验收的声明。
