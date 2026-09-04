# WP8 Reference 测量方法

状态：`HUMAN_REVIEWED`／`APPROVED`。本方法描述如何从仓库所有者提供的 `kuku.mp4` 建立可复核证据；仓库所有者 kuku 已批准 profile revision 1。本文件本身不启动 Stage 3。

## 1. 来源边界

唯一视觉来源是仓库所有者提供的 Windows Phone 8.1 模拟器录屏：

```text
repository path:
docs/reference/wp8/screenshots/owner-emulator-recording/wp81-emulator-reference.mp4

original name: kuku.mp4
SHA-256: 114d8acdb2ae4b8f9e35e5a84bd3077ec52ba7e647ed7e4fc54fd159ea62b94c
bytes: 28760248
video: H.264 / yuv420p / 1920x1080 / nominal 60 fps
duration: 408044 ms
```

没有使用搜索得到的手机宣传照、手持拍摄、倾斜屏摄或第三方截图作为像素来源。Microsoft 文档只校验设计语义、逻辑 viewport 和能力族，不提供本次几何或时序数值。

## 2. 无损取帧

固定依赖位于 `.github/requirements/stage25-extraction.txt`。取帧工具 `.github/scripts/extract_wp8_reference_frames.py` 按升序一次解码视频，并在指定毫秒处保存完整 1920×1080 PNG。Reference 帧不得裁剪、缩放、标注或调色；每一帧在 `SOURCE_MANIFEST.json` 和 `WP8_REFERENCE_MEASUREMENTS.json` 中同时记录时间戳、尺寸、字节数与 SHA-256。

复现命令：

```text
python -m venv <temporary-venv>
<temporary-venv>/bin/pip install -r .github/requirements/stage25-extraction.txt
<temporary-venv>/bin/python .github/scripts/extract_wp8_reference_frames.py \
  docs/reference/wp8/screenshots/owner-emulator-recording/wp81-emulator-reference.mp4 \
  docs/reference/wp8/screenshots/owner-emulator-recording/frames \
  <capture-id>=<timestamp-ms> [...]
```

实际 16 个 capture 与时间戳由 `SOURCE_MANIFEST.json.extractions[]` 锁定。

## 3. 坐标归一化

录屏内模拟器显示矩形测得为原视频坐标：

```text
left = 706 px
top = 62 px
width = 506 px
height = 844 px
```

Microsoft 的 WP8 屏幕缩放资料把 WVGA 基准布局描述为 480×800 逻辑像素。本测量独立按 X/Y 轴将模拟器显示矩形映射到该逻辑 viewport：

```text
logicalX = (rawX - 706) * 480 / 506
logicalY = (rawY - 62)  * 800 / 844
```

显示矩形、色块边缘和 glyph 连通区域均在未裁剪帧上测量。H.264 色度抽样与视频缩放造成的 tile 边缘误差约为 ±1 逻辑像素；大标题 baseline 约为 ±3 逻辑像素。录屏没有暴露物理尺寸，所以 `densityDpi = null`，不得从分辨率猜 DPI。

## 4. 几何结论

`START_PRIMARY_GEOMETRY` 使用三张不同稳定帧，以避免把滚动位置或单个布局误当成固定规范：

```text
left/right outer inset: 24 / 24 logical px
top of first visible tile row: 57 logical px
seam: 12 logical px
small tile: 99 x 99 logical px
medium tile: 210 x 210 logical px
wide tile: 432 x 210 logical px
status strip: 32 logical px
large Settings title baseline: 158 ±3 logical px
Phone glyph optical box: x=104, y=123, 47 x 79 logical px
```

`outerInsets.bottom = 0` 只表示 Start 是纵向连续文档、视频没有显示固定的底边界；它不是“内容贴底”的测量。Wide tile 来自后续滚动位置，`top=153` 只定位该 capture 中的 tile，不代表首行 Y。

## 5. 运动时间线

所有 `visualSamples[].timeMillis` 都等于同一 measurement set 中 capture 源时间戳相对第一帧的差值：

| 场景 | 观察结果 | 数值边界 |
| --- | --- | --- |
| Start → All Apps | Start plane 横向离开，All Apps 在完整一页偏移处稳定 | 约 700 ms settle；中间帧 offset 约 -103，终点 -480 |
| App open | Start tile plane 分离，经黑色中间态进入 app plane | 约 1000 ms 可见区间；不是进程启动耗时 |
| App → Start | app plane 离开，Start tile planes 进入并稳定 | 约 750 ms 可见 settle；不是按键 latency |
| Live Tile cycle | People tile 在邻接磁贴不动时改变内容 | 约 1250 ms 的一次可见变化窗口；不是数据刷新周期 |

录屏没有 pointer overlay，也没有按键发光／触觉轨迹。因此 page swipe、app open 与 Back 的 `inputTimeline` 只是与首个可见变化对齐的逻辑 trigger bracket，不能被解释为测得的 touch-down、touch-up 或硬件 latency。Live Tile 是自主事件，使用 `AUTONOMOUS_CYCLE_START/END`。

## 6. 明确未观察内容

以下能力在这段视频中没有足够证据，保持 `NOT_OBSERVED`：

```text
Edit mode
Fast fling
Pin
Long-press drag
Resize
Unpin
Isolated tile press feedback
Virtual Back/Start/Search activation feedback
```

虚拟 Back／Start／Search glyph 只在模拟器外框中可见，标记为 `VISUAL_ONLY`。后续实现不得从本次证据发明按压缩放、拖拽轨迹、阈值、弹性、按键灯效或触觉参数；缺口必须由新增合法 capture 和新的人工审核补齐。

## 7. 语义校验

```text
python3 .github/scripts/validate_wp8_reference.py
python3 .github/scripts/validate_wp8_reference.py --require-human-review
```

校验器验证 schema、文件存在性与路径边界、MP4/PNG signature、文件字节数、SHA-256、PNG 尺寸、source/capture 引用、时间戳一致性、覆盖状态、逻辑 viewport、时间线单调性、visual delta、关键几何和 review hash。第一条允许准备中的 `MEASURED`；第二条只允许非 Codex 人工给出 `APPROVED`、`HUMAN_REVIEWED` 且 hash 匹配后通过。

Schema 在 Stage 2.5 只做向后兼容的真实性扩展：模拟器未暴露物理 DPI 时允许 `null`；运动枚举增加 `BACK_RETURN`、`LIVE_TILE_CYCLE` 和对应自主事件。Stage 0 的有效/无效 fixtures 继续执行，旧合同没有被静默替换。

## English translation

Status is `HUMAN_REVIEWED` / `APPROVED`. Repository owner kuku approved profile revision 1 and the hash-bound measurement package. The sole visual source is the repository-owner-supplied `kuku.mp4`, stored content-addressed in the reference package. Sixteen uncropped 1920×1080 PNG frames were decoded at exact timestamps without resize, annotation, or colour adjustment. The source and every frame are bound by byte count, dimensions, timestamp, and SHA-256 in both the source manifest and measurements.

The emulator display rectangle at raw `(706, 62, 506, 844)` is mapped independently to Microsoft's documented 480×800 logical viewport. Measured geometry is 24-pixel side insets, a 12-pixel seam, 99×99 small, 210×210 medium, and 432×210 wide tiles. Edge uncertainty is approximately one logical pixel; title-baseline uncertainty is approximately three. Physical DPI is unknown and remains null.

Visual samples show a roughly 700 ms Start-to-All-Apps settle, a 1000 ms app-open visual interval, a 750 ms app-to-Start settle, and a 1250 ms visible People-tile content transition. They do not establish input latency, process startup time, or data-refresh cadence. Because the recording has no pointer overlay or key illumination, input markers are inferred trigger brackets. Edit, fast fling, pin, long-press drag, resize, unpin, isolated press feedback, and virtual-key activation feedback remain `NOT_OBSERVED`; the Back/Start/Search glyphs are `VISUAL_ONLY`.

The semantic validator verifies schema state, path containment, content hashes, byte sizes, signatures, dimensions, references, timestamp deltas, coverage, geometry, motion, and hash-bound human review. Preparation may remain `MEASURED`; Stage 3 stays blocked until a human reviewer approves the canonical measurement hash and the status becomes `HUMAN_REVIEWED`.
