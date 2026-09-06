package com.yokuli.marine.data.android.persistence

import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.data.android.proto.PersistedSourceSelectionStore
import java.io.InputStream
import java.io.OutputStream

object SourceSelectionStateSerializer : Serializer<PersistedSourceSelectionStore> {
    const val CURRENT_SCHEMA_VERSION: Int = 1

    override val defaultValue: PersistedSourceSelectionStore =
        PersistedSourceSelectionStore.newBuilder()
            .setSchemaVersion(CURRENT_SCHEMA_VERSION)
            .setRevision(0L)
            .build()

    override suspend fun readFrom(input: InputStream): PersistedSourceSelectionStore = try {
        PersistedSourceSelectionStore.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        // Do not install a corruption handler that silently erases user choice. The repository
        // exposes this failure and leaves the original file for diagnosis/recovery.
        throw error
    }

    override suspend fun writeTo(t: PersistedSourceSelectionStore, output: OutputStream) {
        t.writeTo(output)
    }
}
