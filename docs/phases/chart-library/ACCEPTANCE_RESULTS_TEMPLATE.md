# 最终验收结果（CI 候选）

文件名保留历史 `TEMPLATE` 以免破坏既有引用；内容已是 CL12 的逐项活验收表。托管结果回传前，
`CI_PENDING` 不是 PASS；物理设备未覆盖项继续明确写 `NOT_RUN`。

基线HEAD：未填写。最终HEAD：未填写。设备/系统/provider：未填写。

| ID | 状态 | 测试层次/命令/退出码 | 证据路径 | 限制 |
|---|---|---|---|---|
| A01 | CI_PENDING | CL12 cumulative install + empty-state stories | `CODEX-CI-REPORT-<CL12_SHA12>-<run_id>-<attempt>` | 等待 Action |
| A02 | CI_PENDING | CL11 bounded provider enumeration + zero-copy evidence | `CODEX-CI-REPORT-<CL11_SHA12>-<run_id>-<attempt>` | 真实provider留CL12 |
| A03 | CI_PENDING | single-document reader stories | 同上 | 等待Action |
| A04 | CI_PENDING | picker rejection/cancellation stories | 同上 | 等待Action |
| A05 | CI_PENDING | persisted grant/catalog restore stories | 同上 | 真实重启留CL12 |
| A06 | CI_PENDING | permission-loss isolation stories | 同上 | 真实USB/SD离线未执行 |
| A07 | CI_PENDING | source repair/idempotency stories | 同上 | 等待Action |
| A08 | CI_PENDING | shared membership/remove-one-reference stories | 同上 | 等待Action |
| A09 | CI_PENDING | complete refresh/missing membership stories | 同上 | 等待Action |
| A10 | CI_PENDING | cancel/partial/generation stories | 同上 | 等待Action |
| A11 | CI_PENDING | document identity/revision stories | 同上 | 不确定身份仍需用户确认 |
| A12 | CI_PENDING | pipe-provider explicit rejection | 同上 | 物理provider未执行 |
| A13 | CI_PENDING | no-network SAF→MapLibre pixel story | 同上 | 等待API34 |
| A14 | CI_PENDING | >4GiB sparse random-read story | 同上 | synthetic；真实大文件未执行 |
| A15 | CI_PENDING | TMS/XYZ equivalence + source hash evidence | 同上 | 等待Action |
| A16 | CI_PENDING | SQLite view-backed tile query | 同上 | 等待Action |
| A17 | CI_PENDING | raster format/tile-size validation | 同上 | 设备codec差异留CL12 |
| A18 | CI_PENDING | hostile/unknown metadata validation | 同上 | 等待Action |
| A19 | CI_PENDING | corrupt DB/blob/schema bounded failure | 同上 | 等待Action |
| A20 | CI_PENDING | BASIC scan does not hash/decode full source | 同上 | 等待Action |
| A21 | CI_PENDING | cancelled/full validation revision guard | 同上 | 等待Action |
| A22 | CI_PENDING | CL12 independent host/runtime story | 同上 | 等待 Action |
| A23 | CI_PENDING | process runtime consumes catalog without Library UI | 同上 | 等待Action |
| A24 | CI_PENDING | bounded display-plan candidate/session stories | 同上 | 等待Action |
| A25 | CI_PENDING | fixed-selection stability story | 同上 | 等待Action |
| A26 | CI_PENDING | deterministic layer order/overlay story | 同上 | 等待Action |
| A27 | CI_PENDING | real tile-key coverage gaps | 同上 | 等待Action |
| A28 | CI_PENDING | antimeridian/zoom/overzoom stories | 同上 | 等待Action |
| A29 | CI_PENDING | revision/session invalidation stories | 同上 | 实体外部改写时机留CL12 |
| A30 | CI_PENDING | forced revision probe + documented detection limit | 同上 | 无provider通知的静默替换不可自动保证 |
| A31 | CI_PENDING | CL12 cumulative catalog/source safety tests | `CODEX-CI-REPORT-<CL12_SHA12>-<run_id>-<attempt>` | 等待 Action |
| A32 | CI_PENDING | managed copy/journal/cancellation/space tests | 同上 | 真实低磁盘留 CL12 |
| A33 | CI_PENDING | managed read lease + confirmed delete tests | 同上 | 外部 renderer lease 真机组合留 CL12 |
| A34 | CI_PENDING | legacy fixture + every journal checkpoint + idempotent catalog sync | 同上 | 进程杀死设备故事留 CL12 |
| A35 | CI_PENDING | full existing unit/integration/build gates | 同上 | 等待 Action |
| A36 | CI_PENDING | token/path/LAN/gateway bounded attack stories | `CODEX-CI-REPORT-<CL11_SHA12>-<run_id>-<attempt>` | 等待Action |
| A37 | CI_PENDING | session/gateway/task bounded counters | 同上 | 30分钟物理soak未执行 |
| A38 | CI_PENDING | CL12 cumulative three-size/theme/large-type stories | `CODEX-CI-REPORT-<CL12_SHA12>-<run_id>-<attempt>` | 真机圆角/方屏仍NOT_RUN |
| A39 | CI_PENDING | CL12 linked Back/Start/deep-link stories | 同上 | 等待 Action |
| A40 | CI_PENDING | CL12 five-app product-surface/APK contracts | 同上 | 等待 Action |
| A41 | PARTIAL_CI_PENDING | API34/API36 build/load jobs | 同上 | 物理16KiB设备NOT_RUN |
| A42 | PARTIAL_CI_PENDING | 1000-item bounded catalog evidence | `chart-library-cl11-evidence.json` | 30分钟物理负载NOT_RUN |

## 零复制独立证据

原件测试hash前后：`CI_PENDING`。应用隐式副本字节：目标值0，`CI_PENDING`。源随机读取字节：`CI_PENDING`。
SAF provider与权限：模拟provider为`CI_PENDING`、物理provider为`NOT_RUN`。MapLibre无外网显示：`CI_PENDING`。
大于4GB sparse瓦片查询：`CI_PENDING_SYNTHETIC`；真实大文件：`NOT_RUN`。

## 数据迁移

旧受管包/旧ID/活动图/history/journal：已实现 fixtures，等待 GitHub Action。
地点/路线/轨迹/地图状态/Start Document/NMEA回归：代码路径保持隔离，等待累计 CI。

## 不得隐去的限制

物理设备未执行项、native ABI/page-size未覆盖项、不支持provider/文件类型、并发外部改写限制、未解决失败。
