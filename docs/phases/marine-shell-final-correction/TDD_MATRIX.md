# Marine Shell Final Correction TDD Matrix

状态：`RED_BASELINE`。

| Slice | Red 合同 | Green 证据 |
|---|---|---|
| Product identity | HOME flavor/intent、`SHELL_HOME_MODE`、`ACTION_HOME_SETTINGS` 必须消失；恢复 task 不强制 Desktop | Gradle variant、merged manifest、Activity stories |
| Surface model | Search 为一等 Surface；Transition Resolver 由 from/to/trigger 唯一决定 | reducer/unit + real Activity story |
| Bridge | 中键为 Desktop/Bridge；Module List 使用 pager-back，Module 使用 exit | unit + semantics + screenshot candidate |
| Safe chrome | rounded/cutout/IME/system-gesture metrics 有纯模型与 Android adapter | viewport unit + API 34/36 stories |
| Marine tiles | 六种尺寸、size-specific content、presentation kind | contract/unit + screenshot candidates |
| Adaptive packing | rank/insertion/spacer；混合尺寸无重叠、确定性、跨 viewport | randomized/property tests |
| Direct editing | local pointer offset、semantic target changes、44dp hit target、cancelable resize | reducer + Compose Activity stories |
| Settings | 无逐行 accent bullet/tilt；紧凑 swatch | static contract + Compose story |
| Quality | 单测、lint、Debug/Release、APK surface、benchmark/profile/golden | cumulative local and hosted gates |

每个功能 Slice 先加入能因缺失行为失败的测试，再实现 Green；修正不得静默改写已通过的旧安全合同。最终状态只可写 `MACHINE_VERIFIED / HUMAN_FIDELITY_PENDING / PHYSICAL_DEVICE_PENDING`。
