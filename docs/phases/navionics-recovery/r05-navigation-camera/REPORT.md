# NAVREF-R05 — Navigation Camera 报告

状态：`IMPLEMENTED_LOCAL_GREEN — CI / HUMAN ACCEPTANCE PENDING`

## Product outcome

- 地图内容 `Marine / Standard / Satellite`、航行相机 `Follow / Look Ahead / Next Waypoint / Route Overview / Free Browse`、方向 `North / Course / Heading Up` 现在是三条独立状态。
- Chart 与 Navigation active map 都可直接切换跟踪视角和方向；Chart 的 GPS/Recenter 仍是一层地图操作。
- 用户 pan 的 renderer idle event 会进入 `Free Browse` 并记住上一个 tracking mode；位置继续更新、active session/leg/route 均不改变，也不会抢回地图。
- Recenter 一次回到此前 tracking mode。
- Look Ahead 需要可靠的 true heading 或 usable COG；两者都不可用时明确回退 Vessel Follow，不伪造前进方向。
- Next Waypoint 自动 fit vessel + next point；Route Overview 使用 vessel + remaining route，而不是 map content 或原始文件范围。
- Course/Heading 不可用时只对地图 bearing 回退 north；请求的 orientation 与 map content 不合并成同一个枚举。

## Design decisions

- 复用 `MapState`、renderer command/ack 和既有 WGS84 计算，没有新建第二个地图 runtime。
- renderer 的有 command-id camera idle 被视为程序化完成；只有无 command-id 的真实用户 camera idle 才进入 Free Browse。
- camera state 是 UI/session truth，不进入 `ActiveNavigationSessionStore`，所以任何 camera 操作都不能改变或恢复错 navigation session。
- Navigation surface 使用自己的 map projection；它操作相机，不拥有导航 service。

## Preserved invariants

- map view 切换不改变 active navigation geometry/session。
- active leg 与 remaining route 由同一 `ActiveNavigationSnapshot` 在 composition root 投影。
- position freshness、heading reference 与 minimum COG speed 判定继续由现有 domain policy 决定。

## TDD evidence

- Red：新 camera mode/state/actions/remaining-route contract 均缺失，定向测试在 compileTest 阶段失败。
- Green：`NavigationCameraContractTest` PASS，包括 pan/recenter、Look Ahead fallback、camera/orientation/content independence。
- `:feature:chart:compileDebugKotlin` PASS。
- `:feature:navigation:compileDebugKotlin` PASS。
- `:app-shell:compileStandaloneDebugKotlin` PASS。
- 全量 gate 留给 push CI。

## Human acceptance pending

- underway 时依次检查 Follow、Look Ahead、Next、Route Overview。
- 每个 tracking mode 中 pan 后必须停留在 Browse；一次 GPS/Recenter 回到原模式。
- 切 map view 与 orientation 后 active route、leg 和数字不得变化。
- 缺 heading/COG 时 Look Ahead 不得显示虚构的运动方向。

## English summary

NAVREF-R05 separates navigation camera, orientation, and map content. Real user pan enters Free Browse without touching the OS navigation session, one recenter restores the prior tracking mode, and Look Ahead truthfully falls back when motion direction is unavailable. CI and physical acceptance remain pending.
