# CL04 报告 / Source authorization and incremental scan

状态：`PASS — API34_SAF_TESTED`

起点：`80f2a7ec6d4cf821dbc95e2bbfcbff35b0aaa1fd`

## 实现

- Tree 与 single-document picker 使用带 operation ID 的 effect/result 合同；Android
  composition 持久取得只读 URI grant。相同 locator 重放不会创建重复来源，移除来源
  只释放对应且已无引用的 grant。
- SAF 枚举只读取 document ID、名称、MIME、size 与 modified time，不打开 Chart、
  不解码 tile、不计算全文件 hash。扫描硬限制为 10,000 文档、32 层、30 秒和两个
  并行来源；目录用 visited identity 防循环。
- 每个来源使用 mutex 和 generation epoch。取消后迟到结果不能覆盖 CANCELLED；单个
  子树失败发布 PARTIAL 并保留成功 sibling。只有 Complete 枚举才能把未见资产标成
  MISSING；权限丢失明确标 REVOKED/PERMISSION_LOST。
- 文档身份为 authority + document ID。稳定身份改名保留角色、优先级和验证；内容
  revision 变化会显式降回 DISCOVERED/CHANGED，等待 CL05 重新验证。

## TDD 与验证证据

- Red：新增扫描合同与 Android controller/enumerator 前，core/androidTest 编译失败。
- 首轮设备测试暴露 tree URI 需要 provider descendant 证明；修正后又暴露测试 provider
  把 `readme.txt` 错标 SQLite MIME。修复 provider 语义后通过。
- `:core:map-domain:test` 通过。
- API 34 `AndroidChartDocumentEnumeratorTest` 3/3；
  `AndroidChartSourceControllerTest` 4/4。
- CL04 静态合同检查纯 core、扫描上限、权限 API、并发上限、禁止跨入 Chart/NMEA/
  location 和关键回归场景。

## 真实性边界

- CL04 只发现并持久化 `DISCOVERED` 资产，不声称可读/完整；BASIC/FULL 验证属于
  CL05。
- emulator 已验证 provider/SAF 行为；厂商文件提供方、真实 SD 卡撤权和 16 KiB
  page-size 设备仍为 `NOT_RUN_PHYSICAL_DEVICE`，不伪装为已验证。
