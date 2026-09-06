package com.yokuli.shell.contract

enum class MeasurementUnitSystem { NAUTICAL, METRIC }
enum class MotionPreference { FOLLOW_SYSTEM, REDUCED }

data class AppPreferenceLabel(val chinese: String, val english: String) {
    init {
        require(chinese.isNotBlank() && chinese.length <= 80)
        require(english.isNotBlank() && english.length <= 80)
    }
}

@JvmInline
value class AppPreferenceKey(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9_.-]{2,95}")))
    }
}

sealed interface AppPreferenceValue {
    data class Toggle(val enabled: Boolean) : AppPreferenceValue
    data class Choice(val option: String) : AppPreferenceValue {
        init { require(option.isNotBlank() && option.length <= 64) }
    }
}

sealed interface AppPreferenceDefinition {
    val key: AppPreferenceKey
    val label: AppPreferenceLabel
    val defaultValue: AppPreferenceValue

    data class Toggle(
        override val key: AppPreferenceKey,
        override val defaultValue: AppPreferenceValue.Toggle,
        override val label: AppPreferenceLabel = AppPreferenceLabel(key.value, key.value),
    ) : AppPreferenceDefinition

    data class Choice(
        override val key: AppPreferenceKey,
        val options: List<String>,
        override val defaultValue: AppPreferenceValue.Choice,
        override val label: AppPreferenceLabel = AppPreferenceLabel(key.value, key.value),
        val optionLabels: Map<String, AppPreferenceLabel> = emptyMap(),
    ) : AppPreferenceDefinition {
        init {
            require(options.size in 2..16 && options.none(String::isBlank) && options.distinct().size == options.size)
            require(defaultValue.option in options)
            require(optionLabels.keys.all(options::contains))
        }
    }

    fun accepts(value: AppPreferenceValue): Boolean = when (this) {
        is Toggle -> value is AppPreferenceValue.Toggle
        is Choice -> value is AppPreferenceValue.Choice && value.option in options
    }
}

data class AppPreferenceContribution(
    val appId: LauncherAppId,
    val definitions: List<AppPreferenceDefinition>,
) {
    init {
        require(definitions.size <= MAX_APP_PREFERENCES)
        require(definitions.map { it.key }.distinct().size == definitions.size)
    }

    companion object {
        const val MAX_APP_PREFERENCES = 16
    }
}

class AppPreferenceRegistry private constructor(
    val definitions: Map<AppPreferenceKey, Pair<LauncherAppId, AppPreferenceDefinition>>,
) {
    fun resolve(
        persisted: Map<String, String>,
    ): Map<AppPreferenceKey, AppPreferenceValue> = definitions.mapValues { (key, owned) ->
        decode(persisted[key.value])?.takeIf(owned.second::accepts) ?: owned.second.defaultValue
    }

    companion object {
        val EMPTY = AppPreferenceRegistry(emptyMap())

        fun compose(
            installedAppIds: Set<LauncherAppId>,
            contributions: List<AppPreferenceContribution>,
        ): AppPreferenceRegistry {
            require(contributions.all { it.appId in installedAppIds })
            val entries = contributions.flatMap { contribution ->
                contribution.definitions.map { definition -> definition.key to (contribution.appId to definition) }
            }
            require(entries.size <= MAX_REGISTERED_PREFERENCES)
            require(entries.map { it.first }.distinct().size == entries.size) { "Duplicate app preference key" }
            return AppPreferenceRegistry(entries.toMap())
        }

        const val MAX_REGISTERED_PREFERENCES = 64

        fun encode(value: AppPreferenceValue): String = when (value) {
            is AppPreferenceValue.Toggle -> "b:${if (value.enabled) 1 else 0}"
            is AppPreferenceValue.Choice -> "c:${value.option}"
        }

        private fun decode(value: String?): AppPreferenceValue? = when {
            value == "b:1" -> AppPreferenceValue.Toggle(true)
            value == "b:0" -> AppPreferenceValue.Toggle(false)
            value?.startsWith("c:") == true -> runCatching { AppPreferenceValue.Choice(value.removePrefix("c:")) }.getOrNull()
            else -> null
        }
    }
}
