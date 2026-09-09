# 海图真机反馈修正 / Chart device recovery

输入：6112–6117 真机截图。当前人工验收仍未通过。构建成功不代表海图、拖拽或布局已经通过真机验收。

## 参考与产品决定 / References and decisions

- [Navionics 测距](https://support.garmin.com/en-MY/?faq=9iPUVsnE3l39lkuj3CpshA)：打开工具出现两个可移动点，再次点击退出。Yokuli 保留 A/B 字母、实时距离和真方位。
- [Navionics 朝向](https://support.garmin.com/id-ID/?faq=l9vww3PByg4gJ8UOwEjrL6)：GPS 按钮连接回中与朝向模式；设备朝向和 COG 是不同来源。Yokuli 船首向只消费真实可用的 heading，不把手机朝向冒充船首。
- [Garmin 船首线与 COG](https://www8.garmin.com/manuals/webhelp/gpsmap7400-7600/EN-US/GUID-4A0FF573-96C7-4F03-9452-67AA2C01140E.html)：船首向与运动方向分别表达；速度可以决定预测线长度。本次 Google 地图补一分钟 COG 预测线，沿用领域层可用性判断。
- [Navionics 航线编辑](https://support.garmin.com/en-US/?faq=j5iQx43cQE8TpTiq5bDzV9)：直接移动航点和插入路线点；Save 保存但不启动导航。Yokuli 保存后结束显示，明确预览/使用时才显示；历史 session 中的预览标识不在冷启动恢复。
- [Navionics 标记](https://support.garmin.com/en-PH/?faq=cVXGFNe11a2l7BtfsLhWz5)：移动地图、用准星选目标再标记。准星和测距手柄避免同时争夺视觉焦点。
- [Android 触摸分发](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup)：子视图处理触摸时，父容器普通 OnTouchListener 并不拥有手势。本次在 dispatchTouchEvent 先命中手柄，命中后整段事件不交给地图子视图。

## 已修改的失败链路 / Changes

1. 两个 renderer 使用图钉优先的触摸分发；缩小 Google 图钉和船标，保留独立命中范围。
2. MapLibre 在注册相机监听之前恢复相机，防止新建地图的默认坐标覆盖新西兰视口。
3. 显示准备跨协程返回时保留资源所有权，取消时关闭已打开连接；瓦片路由加入 source generation，避免复用旧扫描代的失效连接。
4. 读取预算匹配两个最多 32 文件的显示计划及两个验证任务，解除第 13 个文件等待自身释放连接的死锁。预算仍有上限。
5. 文件夹遍历和 SQLite 验证离开主线程执行。
6. 航点管理通过 Shell 正式打开 Navigation；此前只改变其内部页面，用户仍留在 Chart。
7. 保存不自动保持路线显示，测距清除预览，Back 可结束预览；已保存数据不删除。
8. 底部控件按实际 HUD 高度避让，去除重复顶部 42dp 偏移；轨迹面板给左侧快捷键预留空间。

## 验证边界 / Evidence limits

本轮遵从 Product Recovery Window：先实现，不新增锁死 UI 形状的测试。提交后进行编译，CI 单独提供可安装 APK。仍须真机确认：文件夹海图切换后稳定显示、A/B 拖动地图不移动、航点管理能打开、面板不互相遮挡、真实 COG/heading 输入下朝向正确。

English: This change addresses concrete dispatch, camera initialization, resource lifetime, read-budget, navigation handoff, route-preview lifetime and layout failures. Device acceptance remains pending; compilation alone does not establish usability. Persistent chart files, waypoints and routes are retained.
