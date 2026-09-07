package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.map.storage.proto.NavigationHistoryStateProto
import com.yokuli.marine.navigation.domain.NavigationHistorySnapshot
import java.io.InputStream
import java.io.OutputStream

internal object NavigationHistorySerializer : Serializer<NavigationHistoryStateProto> {
    override val defaultValue: NavigationHistoryStateProto =
        NavigationHistoryProtoMapper.encode(NavigationHistorySnapshot.EMPTY)

    override suspend fun readFrom(input: InputStream): NavigationHistoryStateProto = try {
        NavigationHistoryStateProto.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read navigation history", error)
    }

    override suspend fun writeTo(t: NavigationHistoryStateProto, output: OutputStream) = t.writeTo(output)
}
