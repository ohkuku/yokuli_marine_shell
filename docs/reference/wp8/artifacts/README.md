# WP8 比较 Artifact 合同

状态：`NOT_YET_MEASURED`。

本目录定义可再生输出的位置，例如当前截图、像素 diff、overlay、测量报告、trace 摘要和 Golden 测试报告。Artifact 不作为规范输入，默认由 CI artifact 保存；若为复现问题而提交，必须带生成命令、输入 SHA 和工具版本。

建议结构：

```text
artifacts/<renderer-sha>/<scenario>/actual.png
artifacts/<renderer-sha>/<scenario>/diff.png
artifacts/<renderer-sha>/<scenario>/report.json
```

Stage 0 不生成比较结果；Golden、帧耗时和输入延迟仍为 `NOT_YET_MEASURED`。

## English translation

Status is `NOT_YET_MEASURED`. This directory contract covers reproducible outputs such as actual screenshots, pixel diffs, overlays, measurement reports, trace summaries, and golden-test reports. Artifacts are not normative inputs and normally belong in CI artifact storage. Any committed diagnostic artifact must record its command, input SHA, and tool version.
