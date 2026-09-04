# Stage 3 — WP Geometry & Start Document

状态：`PENDING_HUMAN_REVIEW`。本报告只覆盖 Stage 3；Batch A 可在 Stage 4 Gate 后一次人工审核，但本报告不自行批准 Stage 3。

## 基线

```text
approved Stage 2.5 tag: launcher-engine-stage2.5-approved-v1
approved Stage 2.5 commit: b5f935f6e2e0a6e98e1ee9013ef561b1676e71e2
reviewed measurement hash: af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5
reference status: HUMAN_REVIEWED
Batch A foundation / starting SHA: 53a239cd735a5db9c9727b047e67d605f46045a4
branch: codex/launcher-engine-batch-a
approval: PENDING_HUMAN_REVIEW
```

Stage 3 从人工批准的 Stage 2.5 profile revision 1 及已验证的安装 binding 基础提交开始。Stage 2.5 与本批次没有合并或重写；批准 tag 保持指向原提交。

## 范围

```text
implemented:
- hash-bound WpReferenceProfile revision 1
- explicit PHONE_PORTRAIT_4COL and SQUARE_4COL profiles
- StartViewport and integer-pixel ResolvedStartGeometry
- measured 480x800 tile/inset/seam/status-strip geometry
- standard Small, Medium and Wide formulae
- spatial StartDocument with explicit GridCell placements
- document validation and deterministic repair
- Chart Wide + Settings Small default document with intentional whitespace
- renderer migration from guessed ratios to the approved profile
- named Stage 3 CI contract gate

explicitly not implemented:
- reducer/controller/action queue, effects, transactions or persistence ports
- interactive pager or unified gesture arena
- drag/reflow/resize/pin behavior beyond preserving the existing candidate UI
- virtual/physical Back, Start or Search input routing
- real Samsung square-device verification
- any marine runtime capability
```

`SQUARE_4COL` 只复用已批准宽度比例并明确标为 `DERIVED_UNVERIFIED_HARDWARE`；没有把 320/360 方屏边界测试误报为三星真机 fidelity。录屏没有观察到 long-press、press scale 或 fast-fling 阈值，因此 profile 对这些字段保持 `null / NOT_OBSERVED`。

## TDD

Stage 3 静态合同第一次运行：

```text
Ran 6 tests
FAILED (failures=2, errors=4)
```

失败分别来自 Stage 3 baseline/report、reference profile、viewport geometry、StartDocument 命名/合同、默认文档和命名 CI Gate 尚不存在；不是环境或拼写造成的假 Red。

最终 Gate：

```text
Stage 0 contract: PASS (10/10)
Stage 1 contract: PASS (9/9)
Stage 2 contract: PASS (10/10)
Stage 2.5 contract/reference validator: PASS (9/9; HUMAN_REVIEWED)
Stage 3 contract: PASS (6/6)
all Python contracts: PASS (74/74)
shell-engine geometry/document JVM tests: PASS
CI/release metadata/secrets Bash contracts: PASS
Gradle test + lint + dual Debug/Release + AndroidTest assembly: PASS (954 tasks)
standalone/home Release binary product-surface audit: PASS
git diff --check: PASS
```

本 Stage 不以本地边界测试替代真机结论：API 34/36 hosted CI 结果留给分支 push，Samsung 方屏仍为 `UNVERIFIED_HARDWARE`。

## English translation

Stage 3 starts from the immutable, human-approved Stage 2.5 evidence hash and the separately verified Batch A foundation commit. It replaces guessed ratios with a revisioned, hash-bound WP reference profile; adds integer-pixel viewport geometry; and establishes a spatial, versioned Start document with explicit cells, validation, deterministic repair, and intentional whitespace. The 480x800 phone values are measured evidence. Square profiles are derived and explicitly remain unverified on real hardware. Unobserved press, long-press, and fast-fling values remain unknown. Stage 4 reducer, effects, queue, persistence ports, and unified key input are not started in this commit.
