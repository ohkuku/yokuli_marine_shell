# 仪表体验 / Instrument stories

仪表首先是一眼理解船舶状态的工作空间。原有总览、方向玫瑰、航迹预览、
帆航 / NAV / 运动 / 天气、自定义仪表页及驾驶舱模式都保留；补足的是原来
只有横倾、纵倾数字而没有船体空间表达的缺口。

| 故事 / Story | 行为 / Experience |
| --- | --- |
| 出航先看当前状态 / Start with the current vessel | 默认仍为原有总览，保留 NAV、风与航迹。横向大字 pivot 直接显示各仪表页，可点击或左右滑动；关闭后重开恢复上次页面。 |
| 直观看懂船体倾斜 / See the vessel's attitude | 运动页中真实船体 mesh 随已采用的横倾、纵倾和船首向投影；海面网格保持水平，船艏以强调色标识。空间、船尾、舷侧、俯视四个相机可切换。船尾/舷侧视角跟随船体，方便比较姿态。 |
| 读数可以追溯 / Know where a reading came from | 点横倾、纵倾、船首向可看当前来源、接收年龄、新鲜度、质量与冲突。真北 T / 磁北 M 明确区分，不用 GPS 航迹向替代船首向。详情打开期间仍跟随当前数据。 |
| 手机放好再作为船体传感器 / Mount before measuring | 没有确认安装时显示灰色轮廓和“仅示意”，数值显示缺失而不是 0°。直接确认手机朝艏方向，复用旧安装逻辑；不会把船当下的倾斜归零。拿起前可暂停姿态。 |
| 数据失效时看得出来 / See when data is unavailable | 过期、未收到或非有限数值不驱动实时船体。演示来源始终显示演示标记。没有姿态时即使船首向可用也只显示轮廓参考。 |
| 保留自己的仪表 / Keep a personal workspace | 运动空间图下面保留原运动仪表与自定义绑定；页面管理、排序、尺寸、NMEA 自定义字段、记录、快照、夜间/锁定/驾驶舱操作继续使用原实现。 |

The spatial view is integrated into the existing Motion page. It observes the
same canonical `VesselDataSnapshot` as the recorder and other instruments; it
does not create sensors, infer a position source, zero the boat, or fabricate
missing measurements. A small Compose Canvas projects an actual polygon hull,
chine and cabin with sorted faces, independent camera transforms and a level
water reference. Angle interpolation follows the shortest wrap across north.

The page remains useful without a recording. Its existing visible-workspace
sensor lease is released on leaving the instrument application. Phone mounting
confirmation uses the established calibration and paused-segment workflow.

涉及 `WatchWorkspaceScreen.kt` 与新增 `VesselSpatialInstrument.kt`。没有引入
新的渲染引擎、业务数据存储或另一套仪表 App。本轮编译验证不能代替实船的
安装轴向、动态姿态或夜间可读性验收。

2026-09-09 最终 APK 界面核对：运动页标题、左上返回、横滑页签、四个视角、
灰色船体轮廓和“仅示意”提示均正确显示；未确认安装时横倾/纵倾为 `—`，
NMEA 真船首向为 `084°T`。实际点击船尾视角后，投影相机即时改变；
原有开始记录按钮为方角。稳定截图：`screenshots/instruments-spatial-v03.png`
及 `screenshots/instruments-stern-v03.png`。本次没有执行实船动态姿态或安装校准。
