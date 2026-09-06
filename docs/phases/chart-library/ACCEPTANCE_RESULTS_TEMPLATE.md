# 最终验收结果模板

本文件为未执行模板，不是测试报告。每项从NOT_RUN开始，实施后附真实证据。

基线HEAD：未填写。最终HEAD：未填写。设备/系统/provider：未填写。

| ID | 状态 | 测试层次/命令/退出码 | 证据路径 | 限制 |
|---|---|---|---|---|
| A01 | NOT_RUN | — | — | 未执行 |
| A02 | NOT_RUN | — | — | 未执行 |
| A03 | NOT_RUN | — | — | 未执行 |
| A04 | NOT_RUN | — | — | 未执行 |
| A05 | NOT_RUN | — | — | 未执行 |
| A06 | NOT_RUN | — | — | 未执行 |
| A07 | NOT_RUN | — | — | 未执行 |
| A08 | NOT_RUN | — | — | 未执行 |
| A09 | NOT_RUN | — | — | 未执行 |
| A10 | NOT_RUN | — | — | 未执行 |
| A11 | NOT_RUN | — | — | 未执行 |
| A12 | NOT_RUN | — | — | 未执行 |
| A13 | NOT_RUN | — | — | 未执行 |
| A14 | NOT_RUN | — | — | 未执行 |
| A15 | NOT_RUN | — | — | 未执行 |
| A16 | NOT_RUN | — | — | 未执行 |
| A17 | NOT_RUN | — | — | 未执行 |
| A18 | NOT_RUN | — | — | 未执行 |
| A19 | NOT_RUN | — | — | 未执行 |
| A20 | NOT_RUN | — | — | 未执行 |
| A21 | NOT_RUN | — | — | 未执行 |
| A22 | NOT_RUN | — | — | 未执行 |
| A23 | NOT_RUN | — | — | 未执行 |
| A24 | NOT_RUN | — | — | 未执行 |
| A25 | NOT_RUN | — | — | 未执行 |
| A26 | NOT_RUN | — | — | 未执行 |
| A27 | NOT_RUN | — | — | 未执行 |
| A28 | NOT_RUN | — | — | 未执行 |
| A29 | NOT_RUN | — | — | 未执行 |
| A30 | NOT_RUN | — | — | 未执行 |
| A31 | CI_PENDING | CL09 contract + catalog/source tests | `CODEX-CI-REPORT-<CL09_SHA12>-<run_id>-<attempt>` | 等待 Action |
| A32 | CI_PENDING | managed copy/journal/cancellation/space tests | 同上 | 真实低磁盘留 CL12 |
| A33 | CI_PENDING | managed read lease + confirmed delete tests | 同上 | 外部 renderer lease 真机组合留 CL12 |
| A34 | CI_PENDING | legacy fixture + every journal checkpoint + idempotent catalog sync | 同上 | 进程杀死设备故事留 CL12 |
| A35 | CI_PENDING | full existing unit/integration/build gates | 同上 | 等待 Action |
| A36 | NOT_RUN | — | — | 未执行 |
| A37 | NOT_RUN | — | — | 未执行 |
| A38 | NOT_RUN | — | — | 未执行 |
| A39 | NOT_RUN | — | — | 未执行 |
| A40 | NOT_RUN | — | — | 未执行 |
| A41 | NOT_RUN | — | — | 未执行 |
| A42 | NOT_RUN | — | — | 未执行 |

## 零复制独立证据

原件测试hash前后：未执行。应用新增文件及字节：未执行。源随机读取字节：未执行。
SAF provider与权限：未执行。MapLibre无外网显示：未执行。大于4GB真实瓦片查询：未执行。

## 数据迁移

旧受管包/旧ID/活动图/history/journal：已实现 fixtures，等待 GitHub Action。
地点/路线/轨迹/地图状态/Start Document/NMEA回归：代码路径保持隔离，等待累计 CI。

## 不得隐去的限制

物理设备未执行项、native ABI/page-size未覆盖项、不支持provider/文件类型、并发外部改写限制、未解决失败。
