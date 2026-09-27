# Yokuli OS · 品牌资产与接入

2026-09-27 最新用户要求取代旧的帆船 O、大写商业字标及此前 Y 标记。当前只使用这套原创方案：小写 **yokuli** 主导、轻量 **os**、十六格像素帆船。文字像朋友的名字，帆船只作识别和短暂的运动。旧 `approved-reference.png` 是历史用户输入，不是当前设计依据，也不参与构建。

## 唯一母版

- `yokuli-wordmark.svg`：原创几何小写轮廓，平面圆形字腹，短直笔画，方形 i 点；没有字体运行依赖。小号 os 与主名称留出呼吸空间。
- `yokuli-mark.svg`：16×16整数网格中的帆、桅杆与船体。没有环形外框、金属材质、渐变、纸纹或投影。
- `yokuli-os.svg` 是生成的字标副本，不能独立编辑。

修改后运行 `python3 scripts/generate_brand_assets.py`。脚本生成11个生产 VectorDrawable、一份共用像素几何和字标副本；不要直接编辑生成文件。帆船 SVG 中的矩形也生成 `YokuliPixelGeometry.kt`，静态 Canvas 与动画因此不会出现两套船形。

## 视觉使用

1. 品牌只用墨黑 `#111111`、白和中性灰。船位、风险、来源与海图依旧采用对应业务语义颜色，不能为了品牌黑白抹去告警区别。
2. `YokuliBrandWordmark` 用于横向签名，`YokuliBrandSignature` 用于关于页；os 降低对比。不要在普通业务标题前再堆整套 Logo。字号、可访问缩放和中文正文沿用共享类型系统。
3. `YokuliBrandMark` 为小入口、通知身份使用帆船。物理像素取整保证阶梯边缘清晰；不是低分辨率位图。安装图标、安全遮罩与主题图标共用同一造型。
4. 不强制覆盖用户壁纸、透明磁贴、应用绑定和显式强调色。留白和读数层级比装饰更重要。

## 实际启动动效

`MainActivity → BrandArrivalHost → YokuliBrandArrival` 已接生产入口。系统起始窗口展示帆船，首个 Compose 帧立即运行桌面与 Core 连接，然后用760ms完成帆船驶入字标位置、短尾迹、文字展开和自然淡出。DrawScope/graphicsLayer读取进度，避免整页逐帧重组；不以假加载条、旋转缩放模糊或重复粒子动画填时间。

只在 Shell 进程首次显式 Launcher/HOME 冷启动领一次。通知打开、状态恢复、旋转与热切换不播；任何触摸可略过，收到导航、通知展开或后台事件立即结束。动效不会阻止业务恢复/权限流程；不等待网络，不修改导航栈，不推迟采集。内部应用仍使用原冷启动与 Home turnstile规则，小型图标与名称占主位。

这是一套原创实现，参考内容优先和一眼可读的原则，不复制 Apple 或 Garmin 商标。没有独立 ROM bootanimation；普通 APK 与 ROM HOME APK 共用实际启动链。
