# 工单索引 · CL00–CL12

每次只投递一张工单。`REQUIREMENTS.md`为行为权威；`IMPLEMENTATION_MAP.md`为已核实路径。实际HEAD有相关变更时补读diff，不重复全仓研究。

| 工单 | 目标 | 前置 |
|---|---|---|
| [CL00](tasks/CL00.md) | 基线确认与合同覆盖 | 无 |
| [CL01](tasks/CL01.md) | SAF原件与FD SQLite读取 | CL00 |
| [CL02](tasks/CL02.md) | MapLibre零复制显示闭环 | CL01 |
| [CL03](tasks/CL03.md) | 目录模型、版本和持久化 | CL02 |
| [CL04](tasks/CL04.md) | 来源授权与增量目录扫描 | CL03 |
| [CL05](tasks/CL05.md) | 分层验证与共享瓦片查询 | CL04 |
| [CL06](tasks/CL06.md) | Runtime、会话与外部变更 | CL05 |
| [CL07](tasks/CL07.md) | 独立海图库应用页面 | CL06 |
| [CL08](tasks/CL08.md) | Chart显示消费、多图与覆盖 | CL06 |
| [CL09](tasks/CL09.md) | 旧受管包迁移与显式副本 | CL06 |
| [CL10](tasks/CL10.md) | Shell安装、磁贴与跨应用导航 | CL07, CL08, CL09 |
| [CL11](tasks/CL11.md) | 故障、数据安全与资源压力 | CL10 |
| [CL12](tasks/CL12.md) | 最终回归与产品验收 | CL11 |

## 可并行边界

默认顺序执行，减少共享文件冲突。CL07、CL08、CL09只有在CL06接口冻结、任务有独立工作树且组合根修改被协调时才并行；不要三个agent同时改MapModel、build文件和ProductionShellGraph。并行不是本轮验收前提。

## 两个特别门禁

- CL01+CL02：原件只读到MapLibre出图，必须拿真实SAF/provider证据。不能把这个问题拖到所有UI完成后。
- CL12：产品完整性，不是“文件可列出来”。A01–A42每项都有PASS/FAIL/BLOCKED/NOT_RUN与证据类型。
