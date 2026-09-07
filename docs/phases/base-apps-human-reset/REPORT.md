# Base Apps Human Acceptance Reset 报告

状态：`PRODUCT_RECOVERY_WINDOW — ACTIVE`

## 本提交只改变测试与发布权威性

- 不删除历史测试和报告。
- 不修改 domain/runtime/persistence 数据语义。
- 停止自动发现所有 Python `test_*.py`，改为显式 CI helper 清单。
- 停止根工程 `./gradlew test`，改为 core/adapter 受保护单元测试清单。
- 被人工否决的旧 presentation contracts 在 workflow 中明确 `if: false`，不再进入累计或最终 gate。
- API 34/36 只运行 adapter、MBTiles、持久化、NMEA 和最小宿主安全故事；Chart Library、Navigation 与整套 app-shell 旧 presentation stories 退出 gate。
- domain/runtime/migration/MBTiles/bounds/Launcher Engine 与正常 Gradle 测试继续执行。
- 自动化通过的 APK 改名为 `PRODUCT-RECOVERY`，明确等待人工验收，不再声称 Fully Verified。
- signed release 暂停；性能门禁只保留冷/热启动信号。
- 旧 `run_marine_shell_final_gate.sh` 已改为同一恢复期白名单，不能从本地旁路复活旧 presentation tests 或输出 `MACHINE_VERIFIED`。

下一提交开始替换真实 App presentation。某个 App 只有完成用户旅程并由仓库所有者确认“像正常 App”后，才会加入新的 UI behavior Gate。

## P0 MBTiles 功能回归纠正

人工补充审计把 Chart Library 从单纯 UX rejection 提升为 `P0 FUNCTIONAL REGRESSION`。本轮已按补充材料 SHA-256 `9d5d1df21e88f63541fc0a7351eaaf20c1c3ea08ebca00e16ec069c8f7e625dd` 修正：

- stream/pipe-only DocumentsProvider 不再直接判定 chart 无效；运行时建立原子、受控、可重用的本地兼容访问，原文件保持只读；
- `metadata` 表恢复为可选，`tiles` 与标准字段仍为硬要求；
- Basic 只以可访问 SQLite、可读 tiles、合法 raster sample 和可映射坐标判定可用；metadata/bounds/zoom/encoding 质量成为独立 warnings；
- 缩放范围缺失时从 tile rows 推导，bounds 缺失时 display planner 仍尝试显示，不再静默排除；
- access mode、content/readability、verification/warnings 分开持久化并展示；
- Room catalog 增加非破坏性 v3→v4 migration；
- 新增独立 `Legacy known-good MBTiles compatibility contract`，API 34 继续运行真实 SAF/SQLite/renderer instrumentation。

当前没有用户提供的那一份真实 MBTiles fixture，因此这里的代码证据覆盖 legacy-shaped no-metadata archive 与真实 pipe DocumentsProvider；最终仍需要用用户原文件做一次人工导入/显示验收。

## 本地窄门禁证据

提交 `79e2255` 后只运行本次 CI 策略直接相关的测试，没有运行全量 Gradle：

- `test_product_recovery_window.py`：6/6 PASS；
- `run_ci_helper_tests.sh`：17/17 PASS；
- `test-ci-contract.sh`：PASS；
- `android.yml`、`nightly.yml`、`release.yml` YAML parse：PASS。

完整 core/adapter、lint、assemble、API 34/36 和启动性能证据由该提交的 GitHub Actions 提供。

## English translation

This reset changes test authority and artifact truthfulness, not the protected domain/runtime behavior. Rejected presentation contracts are visible but skipped, broad Python discovery is removed, and recovery APKs remain explicitly pending human acceptance.
