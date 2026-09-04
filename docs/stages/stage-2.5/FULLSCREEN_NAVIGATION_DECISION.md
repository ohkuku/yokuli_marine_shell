# Yokuli OS 沉浸式全屏与虚拟实体键决定

状态：`DECIDED_FOR_LATER_STAGES`。本文件记录产品所有者在 Stage 2.5 增加的强制输入与宿主边界；本 Stage 不修改 Android 生产代码，也不宣称该能力已经实现。

## 产品决定

Yokuli OS 作为 Launcher／Shell 运行时，默认采用类似全屏游戏的沉浸式宿主：应用内容占据可用显示区域，Android 状态栏和导航栏默认隐藏。用户主要通过 Yokuli 自己的手势与壳内虚拟实体键操作，而不是把 Android 系统导航当作产品 UI。

壳内必须提供与 Windows Phone 语义对应的三个虚拟键：

```text
Back   / 返回
Start  / 开始
Search / 搜索
```

它们是 Yokuli OS 的产品输入，不是装饰性图片，也不是三个 Composable 各自维护的局部回调。触摸、键盘／遥控器、Android Back 与未来物理按键适配都必须转换为同一组 `LauncherAction`，经单一串行 `LauncherEngine.dispatch(action)` 队列处理。

## 语义边界

- `Back`：先交给当前内部应用或 Shell 的可返回层级；没有可返回内容时回到 Start。不得直接结束进程或产生并发导航写入。
- `Start`：无论当前内部页面是什么，返回 Yokuli Start；它不等同 Android launcher intent，也不重新创建一套并行状态。
- `Search`：未来打开 Yokuli 自己的搜索入口；在真实搜索能力实现前必须明确不可用，不得偷偷调用 Android Assistant 或伪造结果。
- 系统 Back callback、硬件键和壳内虚拟键必须使用相同 action 与 reducer 路径，不能维护第二套导航逻辑。
- Android 边缘手势仍可能临时显露系统栏；普通 App 不能把沉浸模式描述成不可退出的 kiosk。系统行为必须如实保留。
- 无障碍语义、焦点／键盘操作、触觉反馈、减少动态效果和中英文标签是验收条件，不是后补装饰。

## 视觉与空间所有权

`kuku.mp4` 的模拟器外框持续显示 Back／Start／Search 图形，但只构成 `VISUAL_ONLY` 证据：视频没有记录按键激活反馈，不能由它推导点击时序、发光强度或触觉参数。

Stage 3 必须区分：

1. WP 逻辑内容 viewport；
2. Yokuli 壳内虚拟键区域；
3. Android window insets 与暂时显露的系统栏。

不得把模拟器外框的物理像素直接算进 480×800 Start 几何，也不得让内部应用自行绘制或重复预留虚拟键区域。

## 分阶段归属

```text
Stage 2.5  记录证据、缺口与产品决定；不改 runtime
Stage 3    WpReferenceProfile 明确内容 viewport 与虚拟键区域合同
Stage 4    LauncherAction / State / Effect / transaction 与串行 dispatch
Stage 5    手势和虚拟键统一输入映射、取消与冲突规则
Stage 9    Android immersive host、window insets 与跨应用转场接入
Stage 10   恢复、安全逃生、无障碍与异常降级验收
```

Stage 4 的四项既有 carryover 继续强制：未解析 token 不崩溃而产生 incident effect；action 串行化；安装 binding 单点组合；HostPort catalog 成为 runtime 唯一 catalog source。

## English translation

Status is `DECIDED_FOR_LATER_STAGES`. Yokuli OS will default to a game-like immersive host that hides Android system bars and uses shell-owned gestures plus virtual Back, Start, and Search keys. These keys are product inputs, not decorative controls. Touch, Android Back, keyboard or remote input, and future hardware-key adapters must all become the same `LauncherAction` stream and pass through one serialized `LauncherEngine.dispatch(action)` path.

Back first delegates to the active internal surface or Shell history, then returns to Start without exiting the process. Start always returns to Yokuli Start. Search will open Yokuli search only when that capability exists; it must not invoke Android Assistant or fabricate results. Android edge gestures may temporarily reveal system bars, so the app must not claim kiosk-level suppression. Accessibility semantics, focus, haptics, reduced motion, and bilingual labels are required.

The emulator chrome in `kuku.mp4` is only visual evidence for the three glyphs. It does not prove key activation timing, illumination, or haptics. Stage 3 owns viewport geometry, Stage 4 owns serialized actions and reducer semantics, Stage 5 owns input arbitration, Stage 9 owns the Android immersive host, and Stage 10 owns recovery and accessibility validation. Stage 2.5 records this boundary without changing production runtime.
