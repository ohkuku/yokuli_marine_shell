package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.NmeaSentence
import com.yokuli.marine.data.session.ActiveSessionRegistry
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

enum class SentenceParseStatus {
    PARSED,
    UNSUPPORTED,
    EXPLICIT_INVALID,
}

const val DEFAULT_SENTENCE_SEMANTIC_INSTANCE = "DEFAULT"
private const val MAX_SEMANTIC_INSTANCE_LENGTH = 32

/**
 * One bounded inventory key. Both talker and semantic instance are preserved: GPRMC and GNRMC
 * remain distinct inventory facts, as do MWV relative (R) and true-relative (T) sentences.
 */
data class SentenceCatalogKey(
    val source: SourceIdentity,
    val talker: String,
    val formatter: String,
    val semanticInstance: String = DEFAULT_SENTENCE_SEMANTIC_INSTANCE,
) {
    init {
        require(talker.length == 2 && talker.all(::isUpperAsciiIdentifier))
        require(formatter.length == 3 && formatter.all(::isUpperAsciiIdentifier))
        require(semanticInstance.isNotBlank() && semanticInstance.length <= MAX_SEMANTIC_INSTANCE_LENGTH)
        require(semanticInstance.all { it.code in 0x21..0x7e }) {
            "Sentence semantic instance must be bounded printable ASCII"
        }
    }
}

class SentenceCatalogEvent private constructor(
    val origin: ObservationOrigin,
    val receivedAtMillis: Long,
    val groupId: ObservationGroupId,
    val parseStatus: SentenceParseStatus,
    val semanticInstance: String = DEFAULT_SENTENCE_SEMANTIC_INSTANCE,
) {
    init {
        require(receivedAtMillis >= 0L)
    }

    companion object {
        /** Production entry point: semantic identity is copied from the typed parser result. */
        fun fromSentence(
            sentence: NmeaSentence,
            parseStatus: SentenceParseStatus,
        ): SentenceCatalogEvent = SentenceCatalogEvent(
            origin = sentence.origin,
            receivedAtMillis = sentence.receivedAtMonotonicMillis,
            groupId = sentence.groupId,
            parseStatus = parseStatus,
            semanticInstance = sentence.semanticDiscriminator?.stableKey
                ?: if (sentence.formatter == "MWV") "MWV:UNKNOWN" else DEFAULT_SENTENCE_SEMANTIC_INSTANCE,
        )

        /** Test seam for catalog policy tests that do not need to repeat parser fixtures. */
        internal fun fromOrigin(
            origin: ObservationOrigin,
            receivedAtMillis: Long,
            groupId: ObservationGroupId = ObservationGroupId(receivedAtMillis),
            parseStatus: SentenceParseStatus,
            semanticInstance: String = DEFAULT_SENTENCE_SEMANTIC_INSTANCE,
        ): SentenceCatalogEvent {
            require(origin.formatter != "MWV" || semanticInstance != DEFAULT_SENTENCE_SEMANTIC_INSTANCE) {
                "MWV inventory events must preserve a semantic instance"
            }
            return SentenceCatalogEvent(
                origin = origin,
                receivedAtMillis = receivedAtMillis,
                groupId = groupId,
                parseStatus = parseStatus,
                semanticInstance = semanticInstance,
            )
        }
    }
}

data class SentenceCatalogEntry(
    val key: SentenceCatalogKey,
    val sessionGeneration: SessionGeneration,
    val receivedCount: Long,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val lastGroupId: ObservationGroupId,
    val lastSentenceId: String,
    val lastParseStatus: SentenceParseStatus,
    val lastSender: SenderIdentity?,
    val isCurrentSession: Boolean,
)

data class SentenceCatalogSnapshot(
    val entries: List<SentenceCatalogEntry>,
    val capacityEvictionCount: Long,
    val capacityRejectionCount: Long,
    val staleSessionDropCount: Long,
    val outOfOrderDropCount: Long,
)

/**
 * Bounded, in-memory sentence inventory. Session truth is injected; a packet can never implicitly
 * create or advance a connection session. Retained older entries are projected as historical.
 */
class SentenceCatalog(
    private val sessionRegistry: ActiveSessionRegistry,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries > 0) { "Sentence catalog capacity must be positive" }
    }

    private val entries = linkedMapOf<SentenceCatalogKey, SentenceCatalogEntry>()
    private var capacityEvictionCount = 0L
    private var capacityRejectionCount = 0L
    private var staleSessionDropCount = 0L
    private var outOfOrderDropCount = 0L

    @Synchronized
    fun record(
        event: SentenceCatalogEvent,
        protectedKeys: Set<SentenceCatalogKey> = emptySet(),
    ): SentenceCatalogSnapshot {
        val generation = event.origin.sessionGeneration
        val accepted = sessionRegistry.commitInbound(event.origin.source.connectionId, generation) {
            recordAuthorized(event, protectedKeys)
        }
        if (!accepted) {
            staleSessionDropCount++
        }
        return snapshotLocked()
    }

    private fun recordAuthorized(
        event: SentenceCatalogEvent,
        protectedKeys: Set<SentenceCatalogKey>,
    ) {
        val generation = event.origin.sessionGeneration
        val key = SentenceCatalogKey(
            source = event.origin.source,
            talker = event.origin.talker,
            formatter = event.origin.formatter,
            semanticInstance = event.semanticInstance,
        )
        val current = entries[key]
        if (
            current != null &&
            current.sessionGeneration == generation &&
            (
                event.receivedAtMillis < current.lastSeenMillis ||
                    (
                        event.receivedAtMillis == current.lastSeenMillis &&
                            event.groupId.frameSequence < current.lastGroupId.frameSequence
                    )
                )
        ) {
            outOfOrderDropCount++
            return
        }

        if (current == null && entries.size >= maxEntries && !makeRoom(protectedKeys)) {
            capacityRejectionCount++
            return
        }

        entries[key] = if (current == null || current.sessionGeneration != generation) {
            SentenceCatalogEntry(
                key = key,
                sessionGeneration = generation,
                receivedCount = 1L,
                firstSeenMillis = event.receivedAtMillis,
                lastSeenMillis = event.receivedAtMillis,
                lastGroupId = event.groupId,
                lastSentenceId = event.origin.sentenceId,
                lastParseStatus = event.parseStatus,
                lastSender = event.origin.sender,
                isCurrentSession = true,
            )
        } else {
            current.copy(
                receivedCount = current.receivedCount + 1L,
                lastSeenMillis = event.receivedAtMillis,
                lastGroupId = event.groupId,
                lastSentenceId = event.origin.sentenceId,
                lastParseStatus = event.parseStatus,
                lastSender = event.origin.sender,
                isCurrentSession = true,
            )
        }
    }

    @Synchronized
    fun snapshot(): SentenceCatalogSnapshot = snapshotLocked()

    private fun makeRoom(protectedKeys: Set<SentenceCatalogKey>): Boolean {
        val eviction = entries.values
            .asSequence()
            .filterNot { it.key in protectedKeys }
            .minWithOrNull(compareBy<SentenceCatalogEntry> { it.lastSeenMillis }.thenBy { it.key.stableOrder() })
            ?: return false
        entries.remove(eviction.key)
        capacityEvictionCount++
        return true
    }

    private fun snapshotLocked() = SentenceCatalogSnapshot(
        entries = entries.values
            .sortedWith(compareByDescending<SentenceCatalogEntry> { it.lastSeenMillis }.thenBy { it.key.stableOrder() })
            .map { entry ->
                entry.copy(
                    isCurrentSession = sessionRegistry.isCurrent(
                        entry.key.source.connectionId,
                        entry.sessionGeneration,
                    ),
                )
            },
        capacityEvictionCount = capacityEvictionCount,
        capacityRejectionCount = capacityRejectionCount,
        staleSessionDropCount = staleSessionDropCount,
        outOfOrderDropCount = outOfOrderDropCount,
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES = MAX_SENTENCE_KEYS
    }
}

data class RawPreviewEntry(
    val source: SourceIdentity,
    val sessionGeneration: SessionGeneration,
    val sender: SenderIdentity? = null,
    val sentenceId: String?,
    val receivedAtMillis: Long,
    val raw: String,
    /** Snapshot projection only; callers cannot make an unauthorized entry current. */
    val isCurrentSession: Boolean = false,
) {
    init {
        require(receivedAtMillis >= 0L)
        source.udpOrigin?.let { identity ->
            val observed = requireNotNull(sender) {
                "A UDP source identity requires observed sender provenance"
            }
            require(observed.hostAddress == identity.hostAddress) {
                "UDP source host and observed sender host must match"
            }
            require(identity.port == null || observed.port == identity.port) {
                "UDP endpoint identity and observed sender port must match"
            }
        }
        require(source.udpOrigin != null || sender == null) {
            "Observed UDP sender provenance requires a UDP source identity"
        }
    }

    val utf8Bytes: Int = raw.toByteArray(StandardCharsets.UTF_8).size
}

data class RawPreviewSnapshot(
    val entries: List<RawPreviewEntry>,
    val retainedBytes: Int,
    val overflowEntryCount: Long,
    val overflowByteCount: Long,
    val oversizeRejectionCount: Long,
    val staleSessionDropCount: Long,
)

/** Volatile raw diagnostics with simultaneous count and UTF-8 byte budgets. */
class RawPreviewBuffer(
    private val sessionRegistry: ActiveSessionRegistry,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    init {
        require(maxEntries > 0) { "Raw preview count must be positive" }
        require(maxBytes > 0) { "Raw preview byte budget must be positive" }
    }

    private val entries = ArrayDeque<RawPreviewEntry>()
    private var retainedBytes = 0
    private var overflowEntryCount = 0L
    private var overflowByteCount = 0L
    private var oversizeRejectionCount = 0L
    private var staleSessionDropCount = 0L

    @Synchronized
    fun record(entry: RawPreviewEntry): RawPreviewSnapshot {
        val accepted = sessionRegistry.commitInbound(entry.source.connectionId, entry.sessionGeneration) {
            recordAuthorized(entry)
        }
        if (!accepted) {
            staleSessionDropCount++
        }
        return snapshotLocked()
    }

    private fun recordAuthorized(entry: RawPreviewEntry) {
        if (entry.utf8Bytes > maxBytes) {
            oversizeRejectionCount++
            overflowEntryCount++
            overflowByteCount += entry.utf8Bytes.toLong()
            return
        }

        while (
            entries.size >= maxEntries ||
            retainedBytes.toLong() + entry.utf8Bytes.toLong() > maxBytes.toLong()
        ) {
            val removed = entries.removeFirst()
            retainedBytes -= removed.utf8Bytes
            overflowEntryCount++
            overflowByteCount += removed.utf8Bytes.toLong()
        }
        entries.addLast(entry.copy(isCurrentSession = false))
        retainedBytes += entry.utf8Bytes
    }

    @Synchronized
    fun snapshot(): RawPreviewSnapshot = snapshotLocked()

    private fun snapshotLocked() = RawPreviewSnapshot(
        entries = entries.map { entry ->
            entry.copy(
                isCurrentSession = sessionRegistry.isCurrent(
                    entry.source.connectionId,
                    entry.sessionGeneration,
                ),
            )
        },
        retainedBytes = retainedBytes,
        overflowEntryCount = overflowEntryCount,
        overflowByteCount = overflowByteCount,
        oversizeRejectionCount = oversizeRejectionCount,
        staleSessionDropCount = staleSessionDropCount,
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES = MAX_RAW_ENTRIES
        const val DEFAULT_MAX_BYTES = MAX_RAW_BYTES
    }
}

private fun SentenceCatalogKey.stableOrder(): String = buildString {
    append(source.stableOrder())
    append('|')
    append(talker)
    append('|')
    append(formatter)
    append('|')
    append(semanticInstance)
}

internal fun SourceIdentity.stableOrder(): String = buildString {
    append(connectionId.value)
    append('|')
    append(udpOrigin?.hostAddress.orEmpty())
    append('|')
    append(udpOrigin?.port ?: 0)
}

private fun isUpperAsciiIdentifier(character: Char): Boolean =
    character in '0'..'9' || character in 'A'..'Z'

const val MAX_SENTENCE_KEYS = 256
const val MAX_RAW_ENTRIES = 200
const val MAX_RAW_BYTES = 256 * 1024
