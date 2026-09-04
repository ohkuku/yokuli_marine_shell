# WP8 比较 Artifact 合同

状态：`STAGE11_EMULATOR_EVIDENCE_AVAILABLE`。

Stage 0 冻结时的历史状态为 `NOT_YET_MEASURED`；新增 artifact 不会篡改该历史记录。

本目录定义可再生输出的位置，例如当前截图、像素 diff、overlay、测量报告、trace 摘要和 Golden 测试报告。Artifact 不作为规范输入，默认由 CI artifact 保存；若为复现问题而提交，必须带生成命令、输入 SHA 和工具版本。

建议结构：

```text
artifacts/<renderer-sha>/<scenario>/actual.png
artifacts/<renderer-sha>/<scenario>/diff.png
artifacts/<renderer-sha>/<scenario>/report.json
```

Stage 11 提交 `STAGE11_REFERENCE_COMPARISON.json`，并由 CI 保存原始 AndroidX benchmark JSON、trace 和趋势摘要。Golden 仍待人工批准；模拟器帧数据不替代真机门限，输入延迟也没有被宣称已测量。

## English translation

Status is `STAGE11_EMULATOR_EVIDENCE_AVAILABLE`; the frozen Stage 0 historical state remains `NOT_YET_MEASURED`. This directory contains the Stage 11 reference-comparison manifest, while CI retains raw AndroidX benchmark JSON, traces, and trend summaries. Emulator evidence is not a physical-device threshold or input-latency claim. Artifacts are not normative inputs; any committed diagnostic artifact must record its command, input SHA, and tool version.
