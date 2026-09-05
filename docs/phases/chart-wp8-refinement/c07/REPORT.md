# C07 海图包验证、任务取消与版本恢复报告

> English translation: C07 turns a selected local MBTiles document into a bounded, cancellable, fully decoded and recoverable package workflow. It supports PNG/JPEG raster tables or views, keeps logical package identity separate from immutable content versions, and never describes unknown legal facts as verified facts.

## 状态

- package：C07
- baseline：`37241c9d2cb9cb84e00ea90b598757b8fd95f811`
- implementation candidate / cumulative verified SHA：`cc1b598343b3dd9cd4ed43c43d43ae76b276c90b`
- status：`VERIFIED_LOCAL`
- hosted CI：最终同 SHA 证据留给 C12；本报告不把本地 Gate 写成托管结果

## 交付

1. 导入只接受可完整解码的 PNG/JPEG Web Mercator raster MBTiles；`metadata`/`tiles` 可为 table 或 view。WebP、PBF、扩展名猜测和标签即格式证明均未宣称支持。
2. 复制、摘要和逐图块检查全程流式、有资源上限并检查 coroutine cancellation；z/x/y 类型及范围、重复坐标、空包、metadata 重复/过长、编码声明矛盾、单块字节、像素边长和混合尺寸都被明确拒绝。
3. 缺少推荐的 bounds/minzoom/maxzoom 时从真实图块索引推导；来源、许可、署名和版本可保持 `Unknown`，UI 明示用户声明不等于官方核验。
4. `ChartPackageLogicalId`、不可变 `ChartPackageVersionId` 与 SHA-256 字节身份分离；XYZ 归一化后重新计算实际安装内容 SHA，避免内容 ID 指向导入前字节。
5. 安装采用 staging → manifest → 同卷版本目录 → active pointer；PREPARED/PUBLISHED/ACTIVATED journal 在进程中止后可重入恢复，并保证至少一个完整版本可用。
6. 同字节去重、同名不同内容不合并、显式同逻辑包升级和回退均有测试。renderer 对活动版本持 lease，删除在 lease 释放前返回 `PACKAGE_IN_USE`，删除新版可切回旧版。
7. UI 状态区分 Copying、Inspecting、ReadyToInstall、Installing、Cancelled、Failed；operation ID + generation + 单一 Job owner 淘汰迟到选择，用户取消不被记录成失败。
8. 活跃 manifest 损坏会成为明确仓储/UI 失败，不再从列表静默消失；错误日志不包含文档 URI、绝对路径或 metadata 内容。

## Red、自查与纠错

- 初始 Red 固定 tables/views、推荐 metadata 推导、非法索引/坏 payload、三层身份、journal、lease、显式进度和旧选择竞态。
- 第一轮实现编译暴露 Kotlin 可见性、value-class 构造和 member extension 引用问题；均以独立纠错 commit 修复。
- 累计合同发现旧 Shell-App 测试仍强制宣称 WebP，已按本阶段“未经目标设备验证不支持”收紧为 PNG/JPEG，没有放宽 C00-C06 合同。
- 第二轮反证补测三个 journal checkpoint、复制中写满、内容去重/同名隔离、空包/重复和超限 metadata、编码矛盾、renderer lease/回退及坏 manifest 可见性。

## 聚焦与累计证据

- C07 repository contract：5 passed。
- `feature:chart` coordinator JVM：4 tests passed。
- API 34 offline package/renderer：15 tests passed；API 34 Room：6；API 34 Shell：49，全部通过。
- 当前 SHA 累计：190 个 Python repository contracts；300 个 JVM XML tests；全仓 test、lint、Standalone Debug/Release 共 1151 Gradle tasks，0 failure/error/skipped。
- 产品表面审计：Release 仍仅 Chart + Settings，无 HOME、Shell Lab 或 Google Maps key 依赖。
- Debug APK SHA-256：`9a52af6c7c88627c600b3d5260466ea95095f69e93e36525c223bc0587f04238`。
- unsigned Release APK SHA-256：`63cd91ffd9c70c518061fe1598b0732ce84c1a572247f5768313acece833a24b`。

## 未宣称完成

- C08 的 GPX 1.1 导入/导出与只读分段轨迹尚未完成。
- C09 的路线走廊实际图块覆盖检查和合法来源获取尚未完成。
- C10 的只读位置观测质量、C11 的 Shell 动态地图磁贴、C12 最终同 SHA 托管发布证据尚未完成。
- 三星方屏实体机触控/性能仍属于独立人工审核，不由 API 34 模拟器结果代替。
