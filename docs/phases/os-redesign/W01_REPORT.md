# W01 报告 / App 模型与迁移脚手架

状态：`IMPLEMENTED — GITHUB_ACTION_PENDING`

起点：`ba0daa09f2675da54e463c9ecccb0cfb7e81d6bf`

## 业务结果

- 最终 App identity 冻结为 Chart、Chart Library、Data、Navigation、Preferences，但当前生产 catalog
  仍保持已真实实现的 Chart、Settings、NMEA Input、Data Sources、Chart Library。
- launcher persistence schema 升到 3，并新增独立 `productModelVersion`。两类 version 不再混用。
- v1 将 NMEA Input/Data Sources 的 placement 和 token 合入 Data；v2 才把 Settings 迁到 Preferences。
  每一步只有在目标 entry 已安装时才执行，所以 W01 本身不改变用户桌面。
- 重复磁贴由最低 rank、再按 tile ID 决定唯一幸存者；保留其 tileId、size、rank、group。动态 token 只
  替换已知 prefix，opaque connection payload 原样保留。

## 测试源交付给 Pipeline

- core：双旧 Data tile、已有 target、未安装 target、分步 token、幂等与 spacer/无关 tile 保持。
- storage：旧 schema 2 fixture 经 DataStore 原子落成 schema 3/product v1/单磁贴/新 token。
- app-shell：最终 identities 精确；当前 composition root 不提前安装或迁移未来 App。
- `osr_w01_contract`：锁定文档结构、版本、测试故事和 Release 当前真相。

## DESIGN DECISIONS

- 通用 Shell Engine 只知道 entry alias 与 token alias，不 import marine/feature；具体五 App 名称与规则由
  `app-shell` 组合根提供。
- migration plan 按目标实际安装集合运行，而不是按代码中“已经定义 ID”运行，避免生成无 host 磁贴。
- Data 和 Preferences 分成两个连续版本，使 W04 与 W13 能各自在真实纵切片到达时独立启用。
- 没有迁移 places/routes/tracks、connection profile 或 source selection；W01 只改 launcher identity。

## 本地与待验

- 遵循 CI-first：本地不跑 Gradle/full Python/emulator；只做本范围静态 sanity、JSON 与 diff 检查。
- 托管 unit/lint/build/API34/API36/performance 结论等待
  `CODEX-CI-REPORT-<W01_SHA12>-<run_id>-<attempt>`。

## English translation

W01 defines the final five app identities and a staged, install-aware launcher migration without changing today’s visible app surface. Storage schema 3 persists a separate product model version. Data consolidation is migration v1; Settings-to-Preferences is v2. Tests are delivered to GitHub Actions, whose exact-commit report remains authoritative.
