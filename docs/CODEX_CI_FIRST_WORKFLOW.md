# Yokuli OS CI-first 开发与返工合同

## 中文（主文）

从 Chart Library CL07 开始，业务代码与对应测试源码在同一个实现提交中完成。本地默认只执行：

```text
git status --short
git diff --check
```

完整 JVM、lint、APK、API 34、API 36 与性能门禁交给 `Yokuli OS Android CI`。每次 push 后，开发者按提交的前 12 位 SHA 在该次 Actions run 的 Artifacts 中下载：

```text
CODEX-CI-REPORT-<sha12>-<run_id>-<attempt>
```

它是默认需要交回 Codex 的唯一制品。上传后说明：

```text
继续当前 CL。只根据这个 CODEX-CI-REPORT 做返工；不要在本地重跑全量测试。
```

Codex 必须先验证 `manifest.json.headSha` 与目标提交一致，再读 `CODEX_REPORT.md`，只修报告指向的失败。报告若明确包含 `EXTRA_ARTIFACT_REQUIRED`，再下载其中指定的原始制品；不要笼统提供整页 Actions 日志。

统一报告不采集环境变量、Gradle properties、`local.properties`、签名材料或个人加密 vault。它只包含允许的测试 XML、lint XML、已捕获步骤状态以及经过截断和密钥模式脱敏的相关日志片段。

## English translation

Starting with Chart Library CL07, production code and its required test source are delivered in the same implementation commit. Full JVM, lint, APK, API 34, API 36, and performance execution belongs to `Yokuli OS Android CI`; local work defaults to Git status and diff checks only.

After each push, download the single artifact whose name begins with `CODEX-CI-REPORT-<sha12>-`. Codex verifies its exact `headSha`, reads `CODEX_REPORT.md`, and performs only targeted repairs. A raw report is needed only when the unified report explicitly names it with `EXTRA_ARTIFACT_REQUIRED`.
