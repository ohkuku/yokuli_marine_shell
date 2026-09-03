# 参与 Yokuli OS 开发

## 中文（主文）

所有产品工作必须遵守 [TDD 规范](docs/TDD_PLAYBOOK.md)、[UI／响应式架构](docs/UI_REACTIVE_ARCHITECTURE.md) 和 [WP8 UI 范式](docs/WP8_UI_PATTERN.md)。

固定顺序：

1. 用 Given／When／Then 定义行为和禁止副作用。
2. 先加入能因缺失行为而失败的最小测试并运行 Red。
3. 实现最小 Green。
4. 仅在相关门禁保持绿色时重构。
5. 在 `docs/TDD_LOG.md` 记录命令/结果，在 `CHANGELOG.md` 记录用户可见变化。

新页面必须接收不可变 `UiState` 并只发出封闭 `UiAction`，使用 `WpPageHeader`、`WpApplicationBar`、`WpText` 和经过测试的 `WpNavigationIntent`。用户文案必须来自中文默认资源和完整 `values-en` 翻译。例外需要 ADR。装饰动效不得延迟安全状态。

不得把模拟器结果写成 Samsung 方屏硬件或实船验证；未实际执行时使用 `UNVERIFIED_HARDWARE` 和 `UNVERIFIED_VESSEL`。

```text
python3 -m unittest discover .github/scripts 'test_*.py'
bash .github/scripts/test-resolve-release-metadata.sh
bash .github/scripts/test-ci-contract.sh
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

## English translation

All product work follows the linked TDD, reactive UI architecture, and WP8 pattern contracts. Define behavior and forbidden side effects, run a meaningful Red test, implement the minimum Green, refactor while gates remain green, and record evidence in the TDD log and changelog.

New pages accept immutable `UiState`, emit sealed `UiAction`, use shared WP8 primitives, and source visible copy from Chinese default resources with complete `values-en` translations. Exceptions require an ADR; decorative motion cannot delay safety state. Emulator evidence must never be reported as physical square-device or vessel verification. Run the local gate above before delivery.
