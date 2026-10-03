# AIS 空间显示资产

`traffic-vessel.glb` 由 `scripts/assets/generate_traffic_fleet.py` 构建，原创程序几何采用 CC0-1.0。
旧命令 `generate_traffic_model.py` 也调用同一个生成器。没有外部贴图或模型。

单个 fleet 包含 `display:GENERIC / SAILING / MOTOR / FISHING / TUG / PASSENGER / CARGO / TANKER`
八个独立网格；每个 AIS 实例只把广播类别对应的一个网格加入 Scene。未报告船型使用通用轮廓；
没有可靠船首向时仍使用中性位置符号，不能用 COG 把示意船变成有船首向的实船。

网格是类别轮廓，不是目标的 CAD、帆况、装载、高度或照片。广播长宽可靠时按其缩放，
低于可读尺寸时沿既有规则放大；碰撞与导航仍使用领域事实，不能读取示意模型。
X 右舷、Y 上方、-Z 船艏，长宽归一为 1。帆船顶部 0.93，其他类别上限由 `AisVesselForm.top`
与模型一致定义，供拾取使用。所有类别共享四种中性材质；选中与危险状态由渲染器应用。
