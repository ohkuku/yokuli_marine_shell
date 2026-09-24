package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import java.math.BigDecimal
import java.math.MathContext

/**
 * 编辑器始终保留规范单位的精确值，显示文字只负责输入体验。
 * 换单位时转换已有草稿；未经编辑的舍入文字不会反写米、节等领域值。
 * 未完成/无效输入仍保留文字且 value 为空，调用方不得把它当作零保存。
 */
@Stable
internal class UnitNumberDraft internal constructor(
    initialCanonicalText: String,
    private var displayedUnit: String,
    private var toDisplay: (Double) -> Double,
    private var toCanonical: (Double) -> Double,
    restoredText: String? = null,
) {
    private var canonicalAmount by mutableStateOf(initialCanonicalText.toDoubleOrNull()?.takeIf(Double::isFinite))
    var text by mutableStateOf(restoredText ?: canonicalAmount?.let { displayNumber(toDisplay(it)) } ?: initialCanonicalText.removePrefix(INVALID_DRAFT_PREFIX))
        private set
    val value: Double? get() = canonicalAmount
    val canonicalText: String get() = canonicalAmount?.toString() ?: if (text.trim().replace(',', '.').toDoubleOrNull()?.isFinite() == true)
        INVALID_DRAFT_PREFIX + text else text

    fun edit(input: String) {
        text = input
        canonicalAmount = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
            ?.let(toCanonical)?.takeIf(Double::isFinite)
    }

    fun setCanonical(value: Double?) {
        canonicalAmount = value?.takeIf(Double::isFinite)
        text = canonicalAmount?.let { displayNumber(toDisplay(it)) }.orEmpty()
    }

    internal fun useUnit(unit: String, display: (Double) -> Double, canonical: (Double) -> Double) {
        toDisplay = display
        toCanonical = canonical
        if (displayedUnit != unit) {
            displayedUnit = unit
            // 无效文字没有物理量可转换，保持无效；不能在新单位下重新解释它。
            this.canonicalAmount?.let { text = displayNumber(display(it)) }
        }
    }

    internal fun saved() = listOf(canonicalText, text, displayedUnit)
}

// 极大但可解析的输入可能在单位换算时溢出；持久化必须继续标记无效，不能重启后当作规范值。
private const val INVALID_DRAFT_PREFIX = "invalid:"

/** 六位有效数字兼顾短距离与长航程；领域精度独立于这个显示精度。 */
private fun displayNumber(value: Double): String = if (value.isFinite()) BigDecimal.valueOf(value).round(MathContext(6)).stripTrailingZeros().toPlainString() else value.toString()

@Composable
internal fun rememberUnitNumberDraft(
    initialCanonical: Double?,
    unitKey: String,
    toDisplay: (Double) -> Double,
    toCanonical: (Double) -> Double,
    resetKey: Any? = null,
): UnitNumberDraft = rememberUnitNumberDraft(initialCanonical?.toString().orEmpty(), unitKey, toDisplay, toCanonical, resetKey)

@Composable
internal fun rememberUnitNumberDraft(
    initialCanonicalText: String,
    unitKey: String,
    toDisplay: (Double) -> Double,
    toCanonical: (Double) -> Double,
    resetKey: Any? = null,
): UnitNumberDraft {
    val saver = listSaver<UnitNumberDraft, String>(
        save = { it.saved() },
        restore = { UnitNumberDraft(it[0], it[2], toDisplay, toCanonical, it[1]) },
    )
    return rememberSaveable(resetKey, saver = saver) {
        UnitNumberDraft(initialCanonicalText, unitKey, toDisplay, toCanonical)
    }.also { it.useUnit(unitKey, toDisplay, toCanonical) }
}
