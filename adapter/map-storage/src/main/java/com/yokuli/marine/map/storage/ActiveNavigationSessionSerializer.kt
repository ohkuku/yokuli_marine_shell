package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.map.storage.proto.ActiveNavigationSessionProto
import java.io.InputStream
import java.io.OutputStream

internal object ActiveNavigationSessionSerializer : Serializer<ActiveNavigationSessionProto> {
    override val defaultValue: ActiveNavigationSessionProto = ActiveNavigationSessionProtoMapper.encode(null)

    override suspend fun readFrom(input: InputStream): ActiveNavigationSessionProto = try {
        ActiveNavigationSessionProto.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        // Deliberately no corruption handler: a damaged user session is reported, never erased implicitly.
        throw CorruptionException("Cannot read active navigation session", error)
    }

    override suspend fun writeTo(t: ActiveNavigationSessionProto, output: OutputStream) = t.writeTo(output)
}
