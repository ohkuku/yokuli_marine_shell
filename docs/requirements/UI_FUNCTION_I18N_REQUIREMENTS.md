# Yokuli OS UI／功能隔离与双语需求合同

状态：`ACTIVE`
文档语言：中文主文；每条合同附英文翻译。
范围：Shell、所有 `feature:*` UI、展示状态、交互事件、Android 文本资源、可访问性文案、测试与后续功能接入。

> English: Active contract for Shell and feature UI separation, presentation state/actions, Android resources, accessibility copy, tests, and future runtime integration. Chinese is the normative primary text; English is the maintained translation.

## 前提

当前阶段只完善 UI/UX，不实现 GPS、NMEA、Anchor、Trip、Navigation、Sonar 或海图运行时。现在出现的航海数据都是明确的 UI 样例状态。后续功能只能把不可变状态交给 UI，并接收 UI 事件；不能在功能实现中重新定义颜色、间距、动效、页面骨架或用户文案。

> English: This phase completes UI/UX only. Marine values are explicit preview state. Future runtime code may provide immutable state and receive UI events, but must not redefine color, spacing, motion, page anatomy, or user-facing copy.

## UI-I18N-001 — 中文为默认资源

给定设备语言不是应用明确支持的语言，应用必须使用 `values/strings.xml` 中的简体中文默认资源，而不是英语兜底；`values-zh-rCN` 是与默认值完全一致的显式简体中文镜像，避免 app locale 后面的系统英文抢先匹配；`values-en/strings.xml` 提供完整英文翻译，三者的 key 必须完全一致。

> English: Unqualified resources are Simplified Chinese. An identical `values-zh-rCN` mirror ensures an explicit app locale wins over a secondary system-English locale. `values-en` provides complete English translations and all key sets match.

## UI-I18N-002 — 应用级语言选择

System → Display 必须提供中文和 English 两个至少 48dp 的选项。选择后，应用偏好、Android 应用级 locale、Compose `stringResource` 和系统“应用语言”设置保持一致；覆盖安装或 framework locale 为空时从应用偏好恢复，首次安装默认中文；Android 12 及以下通过 AndroidX 应用 locale。

> English: System → Display exposes 48dp Chinese and English choices. The persisted app preference, Android per-app locale APIs, Compose resources, and system app-language settings remain synchronized. A missing framework locale is restored after an update and first install defaults to Chinese; AndroidX provides app locales on Android 12 and lower.

## UI-I18N-003 — 禁止硬编码用户文案

用户可见的静态文案、可访问性描述、状态名称和按钮标签必须来自 Android 文本资源。稳定 ID、测试 tag、协议名、单位符号、品牌名和运行时数据可以保持语言无关。

> English: Static visible copy, accessibility descriptions, state names, and action labels come from Android string resources. Stable IDs, test tags, protocol names, unit symbols, brand names, and runtime data remain language-neutral.

## UI-I18N-004 — 功能模型不拥有视觉元数据

`core:model` 和未来 runtime/domain 模块不得保存 launcher 标题、glyph、颜色、字符串资源 ID 或 Compose/Android UI 类型。Launcher 的标题、glyph 与样例磁贴内容由 `feature:desktop` 的视觉目录拥有。

> English: Domain and runtime models contain no launcher title, glyph, color, resource ID, or UI framework type. `feature:desktop` owns launcher visuals and preview tile copy.

## UI-I18N-005 — UiState／UiAction 边界

每个 UI feature 必须公开不可变 `*UiState` 与封闭 `*UiAction`。Composable 只渲染 UiState 并发出 UiAction；样例数据集中在明确命名的 `*UiFixtures`，不得伪装成已连接的实时数据。

> English: Every UI feature exposes immutable `*UiState` and closed `*UiAction`. Composables render state and emit actions. Preview values live in named `*UiFixtures` and never imply a connected runtime.

## UI-I18N-006 — UI 一致性所有权

只有 `core:design` 可以定义 WP8 颜色、主题、排版、间距、动效和共用控件。功能接入不得绕过 `WpPageHeader`、`WpApplicationBar`、`WpText`、`WpThemeSpec` 和动效策略；不得引入 Material 卡片、独立主题或 feature 私有动效常量。

> English: `core:design` exclusively owns WP8 tokens, theme, typography, spacing, motion, and shared controls. Runtime integration cannot bypass the canonical primitives or introduce feature-local design systems.

## UI-I18N-007 — 语言无关测试

导航和安全流程测试使用稳定语义 tag/state，不依赖某一种翻译。另有真实 Activity 故事分别切换 English 与中文，证明标题、Launcher、Display 选项和可访问性资源同步更新。

> English: Navigation and safety stories use locale-independent semantics. A dedicated real-Activity story switches between English and Chinese and verifies localized launcher and settings content.

## UI-I18N-008 — 文档与注释

新需求、ADR、TDD 记录和关键架构注释采用中文主文＋英文翻译。代码标识符保持英文；注释只有在解释所有权、安全边界或非显然决策时存在，并先写中文，再给简短英文对照。

> English: New requirements, ADRs, TDD records, and important architecture comments are Chinese-first with English translation. Identifiers stay English; comments explain ownership, safety, or non-obvious decisions in Chinese followed by concise English.

## UI-RX-001 — 响应式发布／订阅

后续功能使用 Kotlin Flow 的单向数据流：`StateFlow` 发布可重放的当前状态，显式 action sink 接收 `UiAction`，`SharedFlow` 仅发送一次性效果。不得引入无所有者的全局 EventBus、字符串 topic 或跨模块可写 Flow；安全事实不得只作为可能丢失的 event。

> English: Future features use Kotlin Flow UDF: replaying `StateFlow` for current state, explicit action sinks for `UiAction`, and `SharedFlow` only for one-shot effects. Ownerless global buses, string topics, cross-module mutable flows, and event-only safety truth are forbidden.

## UI-RX-002 — 函数式核心，端口化副作用

解析、单位换算、reducer、状态投影采用纯函数；设备、数据库、网络和系统 API 放在窄端口后。面向过程只用于局部、显式输入输出的流程，不能以共享可变状态和任意调用链组织整个 App。

> English: Parsing, conversion, reducers, and projections are pure functions; devices, storage, networking, and platform APIs sit behind narrow ports. Procedural composition is local and explicit, never an app-wide web of shared mutable state.

## 验收门禁／Acceptance gate

```text
python3 -m unittest discover .github/scripts 'test_*.py'
./gradlew test lintStandaloneDebug assembleStandaloneDebug assembleHomeDebug
./gradlew :app-shell:connectedStandaloneDebugAndroidTest
```

真机与船上验证仍分别标记为 `UNVERIFIED_HARDWARE` 与 `UNVERIFIED_VESSEL`。

> English: Physical-device and vessel validation remain explicitly unverified.

## 官方依据／Primary references

- [Android：本地化应用，禁止硬编码字符串](https://developer.android.com/guide/topics/resources/localization)
- [Android：应用级语言偏好与 Compose 更新](https://developer.android.com/guide/topics/resources/app-languages)
- [Android：Compose `stringResource`](https://developer.android.com/develop/ui/compose/resources)
- [Android：默认与限定语言资源](https://developer.android.com/guide/topics/resources/providing-resources)
- [UI／响应式模块架构](../UI_REACTIVE_ARCHITECTURE.md)
