# Base Apps Human Acceptance Reset 报告

状态：`PRODUCT_RECOVERY_WINDOW — IMPLEMENTATION IN PROGRESS`

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

下一提交开始替换真实 App presentation。某个 App 只有完成用户旅程并由仓库所有者确认“像正常 App”后，才会加入新的 UI behavior Gate。

## English translation

This reset changes test authority and artifact truthfulness, not the protected domain/runtime behavior. Rejected presentation contracts are visible but skipped, broad Python discovery is removed, and recovery APKs remain explicitly pending human acceptance.
