# DATA-R00 — Presentation / Test Reset

状态：`IMPLEMENTED — PRODUCT WORK CONTINUES`

## 决定

- 仓库所有者已否决旧 Data/NMEA 五段式 presentation。
- `OVERVIEW / INPUTS / SOURCES / FLOW / DIAGNOSTICS` 不再是产品合同。
- `osr_w03_contract` 从 blocking workflow 退出；历史脚本保留供追溯，不删除。
- NMEA framing/parser/checksum/catalog、TCP/UDP runtime、session generation、持久化、Phone demand、原子 source selection 与 no-silent-fallback 仍是硬门禁。
- DATA-R01–R09 将用 Boat / Flow / Connections 心智替换旧界面；DATA-R10 仍只能由真实 APK 人工验收。

## 合同来源

- 文档：`Yokuli_OS_Data_NMEA_Marine_Nervous_System_Recovery_Contract.md`
- SHA-256：`70fa6c56ec9a1124f8509bfd2b7f3f7d0857fb7c883740af96641a215ebcbfd2`
- 该文档约束产品结果、边界和 forbidden outcomes，不强制内部 class/file 布局。

## 本地证据

按项目的 commit-before-test 工作流，定向门禁在本提交之后执行并回填。完整 Gradle/lint/assemble/device 仍交给 hosted CI。

## English summary

DATA-R00 retires the old five-section Data presentation contract while preserving protocol, runtime, persistence, source-selection and safety gates. The replacement presentation remains human-acceptance pending.
