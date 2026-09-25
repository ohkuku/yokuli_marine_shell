# 磁贴生产渲染接线补充

此页补充既有 IMPLEMENTATION 与仪表契约的源码入口，不替代其中的持久化、访问栈与来源规则。

`instanceTilePresentation` 是桌面和编辑预览的同一入口。`TileReadingPresentationPolicy.styles` 同时提供可选表现和提交能力；工坊目录不再另造不一致的样式列表。更换表现按绑定、表现、尺寸更新组合身份，避免原来的冻结帧或 legacyMode 遮住用户的新选择。

读数使用 `ReadingTileContent(frame, graphic, style)` 与 `ReadingTileFace`；旧应用摘要继续使用原 `TileFrame` / `TileFace`，不把图形塞进不存在的构造字段。数值、趋势主视图、详细视图、刻度量表、真北方位盘、船艏相对风角、横倾与纵倾均有实际绘制。空历史显示等待读数，不生成示例曲线；无实时方位不画指针。图形平滑复用 `rememberInstrumentMotion`，不改数字、采样时间、来源和质量。

`tileReadingDisplayDemand` 只借用既有 display 端口的 `acquireInstruments` / `acquireMapHeading`。可见的气压等手机读数不再依赖驾驶台页面仍然打开；隐藏、改绑、生命周期暂停和离开组合树均释放 lease。暂停时由生命周期观察者同步释放，不等待后台绘制帧。来源是否启用和选用仍由原系统决定。

配置仍写入原 `TilePresentation` 和 Proto：历史窗口 1/5/15 分钟、适用指标的规范单位量程、来源与可选参考说明。坐标跟随系统格式；计数不提供速度量表；风速与风向独立判断可用性；压力变化仍是原系统计算的 1/3/6 小时指标。测深和方向参考必须显示，不因旧 showReference=false 被隐藏。切换全局单位时，打开的量程编辑草稿按新单位重新初始化，避免静默误读旧数字。

限制：本次没有把持久化的 1/6/24 小时气压查询接成磁贴长时段图，没有增加独立来源选择、传感器、天气预报或警报阈值。现有驾驶台长时段气压回看保持原入口。完整 API 自动索引需要在完整源码 checkout 运行原 `scripts/export_api_index.py`；不能从部分工作副本重建而删掉其他声明。
