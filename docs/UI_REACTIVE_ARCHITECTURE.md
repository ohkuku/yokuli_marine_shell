# Yokuli OS UI／响应式模块架构

状态：`ACTIVE`，后续功能实现必须遵守
主文：中文；英文翻译位于每节后方。
范围：Shell、语言、主题、Chart、Anchor、Trip、NMEA、Map、Sonar、Anchorages、Navigation 与未来数据源。

> English: Active architecture contract for the Shell and every current or future feature. Chinese is normative; each section includes an English translation.

## 1. 结论：不选“纯面向过程”，也不堆对象

Yokuli OS 采用响应式单向数据流。纯函数负责解析、换算、状态投影和 reducer；小型状态对象表达事实；端口隔离模块；`StateFlow` 发布当前状态；`SharedFlow` 只发送可丢弃或可确认的一次性效果。不会建立巨大 ViewModel、层层继承的 manager，也不会用任意模块都能收发的全局 EventBus。

面向过程适合局部算法，因为输入、输出和副作用容易测试；它不适合作为整个应用的模块结构，因为跨页面的可变状态、生命周期和错误恢复会变成隐式调用链。这里选择的是函数式核心＋响应式边界，而不是“面向对象”和“面向过程”二选一。

> English: Yokuli OS uses reactive unidirectional data flow. Pure functions own parsing, conversion, projection, and reducers; small state values express facts; ports isolate modules; `StateFlow` publishes current state; `SharedFlow` is limited to one-shot effects. We reject both a giant object graph and a global event bus. Procedural code is appropriate inside pure algorithms, not as the application-wide dependency model.

## 2. 标准数据流

```text
外部输入 / External input
 GPS · NMEA · 存储 · 用户动作
              │
              ▼
  feature 输入端口 / input ports
              │
              ▼
  纯 reducer + 用例编排 / pure reducer + use-case orchestration
              │
       ┌──────┴────────┐
       ▼               ▼
StateFlow<DomainState>  SharedFlow<Effect>
       │               │
       ▼               └── 只处理通知、导航、权限等一次性效果
 UI projector
       │
       ▼
StateFlow<FeatureUiState>
       │
       ▼
Composable(state, onAction)
       │
       └──────── UiAction ────────► feature action sink
```

Composable 不能读取 repository、传感器、数据库或协议连接。它只渲染完整的不可变 `UiState`，并发布 `UiAction`。生产 main 不允许共享 `*UiFixtures`；当前配置由 production projector 生成真实 UI 状态，布局压力数据只允许出现在 debug-only Shell Lab 并永久标记 `DEMO`。接入真实功能时替换状态生产者，不改页面结构、文案所有权或动效。

> English: Composables render complete immutable state and emit actions. Production main contains no shared fixture objects; current configuration is projected into truthful UI state, while layout stress data is confined to the debug-only Shell Lab and permanently labeled `DEMO`. Runtime integration replaces state producers without redefining page anatomy, copy ownership, or motion.

## 3. 三类流不能混用

| 类型 | Kotlin 载体 | 用途 | 禁止事项 |
|---|---|---|---|
| 当前事实 | `StateFlow<State>` | 船位、连接、主题、语言、告警状态 | 不得放进一次性 event；新订阅者必须立即取得最新值 |
| 输入动作 | 明确的 `dispatch(Action)` 或 feature action sink | 点击、选择、确认、开始/停止请求 | 不得由 UI 直接调用 repository |
| 一次性效果 | `SharedFlow<Effect>` | 导航、toast、权限请求、声音/振动触发意图 | 不得作为告警或安全状态的唯一记录；缓冲/溢出策略必须显式 |

安全语义必须是可重放的状态。比如“锚泊告警中”属于 `StateFlow`；播放一次声音只是效果。即使收集效果的 Activity 重建，告警事实也不能消失。

> English: Current facts use replaying state, actions use an explicit sink, and one-shot effects use a separately configured stream. Safety truth is always replayable state; sound, vibration, navigation, and permission prompts are effects and never the sole record of an alarm.

## 4. 模块边界

- `core:model`：只包含语言无关、平台无关的值和 ID；不包含 Compose、Android resource、颜色、图标或展示文字。
- `core:design`：唯一的 WP8 token、主题、排版、动效和共用控件所有者。
- `core:shell`：纯导航/layout reducer；不启动或停止海事 runtime。
- `feature:*`：拥有 `UiState`、`UiAction`、UI projector 和 Composable；不能拥有底层设备实现。
- 未来 `domain:*`：状态机、策略和纯计算。
- 未来 `data:*`／`runtime:*`：端口实现和生命周期；对外只暴露只读 Flow，不泄漏 mutable flow。
- `app-shell`：composition root，只负责连接发布者、订阅者和平台能力。

依赖方向始终朝向稳定合同。模块之间不能通过单例查找对方，也不能订阅字符串 topic。任何共享流都必须有类型、所有者、生命周期、replay、buffer、overflow 和错误策略。

> English: Models are platform-free, design owns the WP8 language, shell owns pure navigation reducers, features own UI contracts and projectors, domain owns policy, data/runtime own port implementations, and app-shell is the composition root. No service locator, string topic, mutable-flow leakage, or ownerless global stream is allowed.

## 5. 语言模块借鉴旧应用，但不复制旧债

旧应用已经证明以下做法有效：显式 `AppLanguage`、语言选择持久化、打包完整翻译、对外暴露只读 `StateFlow`。这些语义保留。

旧应用中的 `localized(language, english, chinese)`、页面内 `tr(...)` 和散落的双语字符串不会迁移，因为它们让领域层知道展示语言，也无法由 Android lint 完整校验。Yokuli OS 改为：简体中文放在未限定 `values` 中作为默认资源；英文放在 `values-en`；显式语言选择写入应用偏好，并与 Android/AndroidX 应用级 locale 同步；UI 使用 `stringResource`；协议值和领域错误先保持结构化，最后由 UI projector 选择资源。这样覆盖安装清空 framework locale 时仍能恢复用户选择，首次安装则稳定使用中文。

> English: We retain the legacy app's explicit language choice, persistence, complete packaging, and read-only flow publication. We do not retain manual `localized()`/`tr()` calls or bilingual literals in domain code. Chinese is the unqualified Android resource, English lives in `values-en`, the persisted choice stays synchronized with Android/AndroidX per-app locales, and structured domain facts are localized only at the UI projection edge. The saved choice also recovers an app locale cleared during an update; first install defaults to Chinese.

## 6. 每个新功能的固定骨架

```kotlin
data class FeatureUiState(/* immutable display facts */)

sealed interface FeatureUiAction {
    data class Select(/* typed value */) : FeatureUiAction
    data object Home : FeatureUiAction
}

@Composable
fun FeatureWorkspace(
    state: FeatureUiState,
    onAction: (FeatureUiAction) -> Unit,
)
```

接入 runtime 后再增加 feature store。Store 的 reducer 和 projector 必须 JVM 可测；UI 不因 store 技术变化而改变。禁止把 `Context`、`NavController`、repository 或 mutable collection 放进 `UiState`。

> English: Every feature starts with immutable state, a sealed action set, and a stateless workspace. Runtime integration later adds a feature store whose reducer/projector is JVM-testable. UI state cannot contain Context, NavController, repositories, or mutable collections.

## 7. TDD 与评审门禁

每个功能切片依次证明：纯 reducer → feature store 的流行为 → UI projector → 真实 Activity 故事。涉及安全状态时，还要证明晚订阅仍能收到当前状态、效果丢失不会丢失事实、取消订阅不会停止仍被其他 owner 使用的 runtime。

评审必须拒绝：Composable 内启动连接、跨 feature mutable flow、全局 EventBus、以文本代表领域状态、无 buffer 策略的高频 `SharedFlow`、以及为了方便而绕开 `UiAction` 的回调。

> English: TDD proceeds through pure reducer, store stream behavior, UI projection, and real-Activity story. Safety tests additionally prove late-subscriber state replay, fact survival when an effect is lost, and runtime ownership independent of a UI collector. Reviews reject connection startup in Compose, mutable-flow leakage, global buses, text-as-domain-state, unspecified overflow, and action-bypassing callbacks.

## 8. 当前阶段边界

这次提交建立 UI state/action、双语资源、平台语言选择与 Shell store 端口；尚未引入持久化 store 实现或海事 runtime。Shell 内的临时 `remember` 只用于真实配置投影和瞬时编辑状态，不是最终业务状态容器。真实功能进入前，先为对应 feature 按本合同写 Red 测试，再引入 Flow publisher。

> English: This slice establishes UI contracts, bilingual resources, platform locale selection, and Shell store ports, but not durable store implementations or marine runtimes. Temporary Shell `remember` values project real configuration and hold ephemeral editing state only; each runtime feature begins with a Red stream contract before a Flow publisher is introduced.
