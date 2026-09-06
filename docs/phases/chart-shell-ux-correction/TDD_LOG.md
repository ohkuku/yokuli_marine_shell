# Chart / Navigation / Shell UX TDD Log

## 已执行循环

1. Renderer selection：旧逻辑让任意已选择本地资源遮蔽 Standard/Satellite；测试先锁定 Map View 决策，再由 `e594189` 修正为 `MapViewMode + GOOGLE_MAPS_CONFIGURED`。
2. Direct interaction state：新增失败用例覆盖 measurement 第三点、Measure 隐藏 route、Discard ghost、search deep link 与 close/navigation draft guard；`6eb66b3` 实现后窄 JVM、Feature、app-shell compile、AndroidTest compile、W09/W15 均通过。
3. Configuration truth：本轮把 `connectedBaseConfigured` 从 composition root 明确传到 Chart UI；缺 key 时 Standard/Satellite 不可选，且只显示“未配置”，不声称“不可用原因已验证”。
4. Cumulative contract：新增 Python Gate，把用户给出的产品不变量与现有代码主路径绑定，并接入最终 CI report。

## 不伪造的证据

本地窄测试不等同完整产品批准。真实 Google 图块、三星方屏、GNSS、OEM 后台与大型真实 MBTiles 必须保持人工/物理证据状态，直到存在对应报告。
