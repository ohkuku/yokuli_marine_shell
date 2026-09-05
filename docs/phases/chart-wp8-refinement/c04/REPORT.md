# C04 坐标、选点与测量报告

> English translation: C04 delivers WGS84 geodesic measurement, truthful point-candidate actions, coordinate entry, reversible point editing, dateline-safe rendering and a zoom-aware screen-error budget. It does not claim a completed Places library, saved-route workflow, GPX exchange, package management, coverage, or live position.

## 状态

- package：C04
- baseline：`7cf15b3e53ae9229b98fe87d827428943cde651d`
- implementation candidate：`c15d3d17f832608b328d8722f475ff5fed694e59`
- cumulative verified SHA：`c15d3d17f832608b328d8722f475ff5fed694e59`
- status：`VERIFIED_LOCAL`
- hosted CI：留给 C12 的最终同 SHA Gate；本报告不把本地 Gate 冒充托管结果

## 交付

1. 地理数学固定使用 GeographicLib Java 2.1 的 WGS84 椭球逆算/正算。内部距离保持米，UI 按量级显示 m/NM；段方向明确为起始真方位 `°T`。同点和多解对跖情形显示未定义“—”，不伪造磁方位或操舵建议。
2. 持久模型保留合法 WGS84 纬度 `[-90, 90]`；Web Mercator 的 `±85.05112878°` 只用于相机显示边界。十进制度与度分小数输入支持半球，拒绝 NaN/Infinity、越界、分钟 `>=60`、符号/半球冲突，并保留负零半球语义。
3. renderer 的 GeoJSON 边界直接验证为 longitude/latitude 顺序。测量和路线线段按同一 WGS84 大地线加密，跨日期线拆线，fit 使用最小合理经度包络，不让短跨界横穿世界。
4. 加密密度来自 zoom 与纬度相关的屏幕弓高误差预算，目标约 `0.75 px`，并限制在 500m–25km；该预算只控制绘制采样，不降低 WGS84 距离/方向计算精度。缩放/旋转只重新生成图形，不改变地理结果。
5. 测量 0/1/2+ 点分别给出下一步、段距离/起始真方位和总长。用户可选中任意点，手势移动、精确准星移动、坐标输入、插入、删除、清除、fit、undo/redo；连续拖动只在 drop 时形成一次确认历史。
6. preview 绑定稳定 gesture ID；旧帧、多指、取消或 viewport 改变不会半提交。最终 `UP` 坐标参与触摸阈值和提交，tap 不再被误判为 drag。
7. 临时选点明确显示来源事实，并提供保存地点、从此测量、从此规划、复制坐标。测量模式下“保存地点”不会顺带增加测量点；保存仍受 C01 durable ack 语义约束。
8. “转路线”复制不可变坐标值到独立草稿，原测量与其他草稿不被覆盖；C06 才完成正式路线库和完整再编辑流程。

## Red、修正与自查

- Red 先锁定官方独立参考值、同点/近对跖、日期线/高纬、坐标格式、三点编辑、最终 drop、转换隔离、GeoJSON 轴序和屏幕误差预算。
- 自查发现保存候选在测量模式可能先污染测量、最终 `UP` 位移可能漏算、含冒号的路线 ID 解析不稳、320dp 英文操作行溢出、旧恢复回调签名未迁移；均以独立测试或设备故事修正。
- 初版固定 25km 加密只能说明“有加密”，不能证明屏幕精度。追加 Red 后改为 zoom/纬度驱动的有界屏幕误差预算，并在当前 SHA 上重跑完整 Host 与设备 Gate。
- 第一次完整 Host Gate 使用系统 Python 时仅因缺少固定 `jsonschema` 依赖而退出；之后使用隔离的锁定依赖环境重新从头执行，没有跳过或放宽任何产品测试。

## 聚焦与累计证据

- C04 repository contract：4 passed。
- `core:map-domain`、`adapter:map-offline`、`feature:chart` 聚焦 JVM tests：passed；Android test compilation：passed。
- API 34 C04 Shell stories：测量结果/详情、精确坐标移动、字段错误与方屏候选全部 passed。
- 当前 SHA 完整 Host Gate：175 个 Python repository contracts；全仓 test、lint、Standalone Debug/Release、benchmark 与产品表面审计通过，共 1207 Gradle tasks。
- JVM XML：271 tests，0 failure/error/skipped。
- 当前 SHA API 34：offline renderer 6、Room 4、app-shell 45，全部通过。
- Debug APK SHA-256：`32e091c2a27810cfd053305ada2e3bf3edb0c7973e3e1cf3a9ec2e2c40ea4850`。
- unsigned Release APK SHA-256：`01aaa18ad1adb890171ebfdecea0a8f4e6d0ed08cb4637f73e725d399935fb49`。

## 未宣称完成

- C05 的地点完整字段、列表/详情、搜索/排序、显式位置编辑、删除撤销和进程重启 UI 闭环尚未完成。
- C06–C10 的正式路线、海图包作业/恢复、GPX、覆盖/下载、位置观测尚未完成。
- C11/C12 的最终证据矩阵、托管同 SHA CI、Alpha 产物和三星方屏实体机审核仍未完成。
