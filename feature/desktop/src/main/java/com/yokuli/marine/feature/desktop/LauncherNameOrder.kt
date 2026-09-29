package com.yokuli.marine.feature.desktop

import android.icu.text.Transliterator
import java.util.Locale

/** 系统及安装应用使用同一拼音索引；标题变化才重新计算，不在每帧音译。 */
object LauncherNameOrder {
    private val transliterator by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII") }
    private val cache = object : LinkedHashMap<String, String>(96, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 256
    }
    @Synchronized fun key(title: String): String = cache.getOrPut(title) {
        runCatching { transliterator.transliterate(title.trim()) }.getOrDefault(title.trim())
            .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            .ifEmpty { title.lowercase(Locale.ROOT) }
    }
    fun initial(title: String): Char = key(title).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}
