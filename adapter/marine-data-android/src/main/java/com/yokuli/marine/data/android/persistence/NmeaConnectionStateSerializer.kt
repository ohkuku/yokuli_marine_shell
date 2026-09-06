package com.yokuli.marine.data.android.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.data.android.proto.PersistedNmeaConnectionStore
import java.io.InputStream
import java.io.OutputStream

internal object NmeaConnectionStateSerializer : Serializer<PersistedNmeaConnectionStore> {
    override val defaultValue: PersistedNmeaConnectionStore = PersistedNmeaConnectionStore.newBuilder()
        .setSchemaVersion(CURRENT_SCHEMA_VERSION)
        .build()

    override suspend fun readFrom(input: InputStream): PersistedNmeaConnectionStore = try {
        PersistedNmeaConnectionStore.parseFrom(input).also { store ->
            if (store.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
                throw CorruptionException("Unsupported NMEA connection schema ${store.schemaVersion}")
            }
        }
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read NMEA connection state", error)
    }

    override suspend fun writeTo(t: PersistedNmeaConnectionStore, output: OutputStream) {
        t.writeTo(output)
    }

    const val CURRENT_SCHEMA_VERSION = 1
    private val SUPPORTED_SCHEMA_VERSIONS = setOf(0, CURRENT_SCHEMA_VERSION)
}
