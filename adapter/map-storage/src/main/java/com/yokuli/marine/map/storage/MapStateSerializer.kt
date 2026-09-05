package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.map.storage.proto.MapStateProto
import java.io.InputStream
import java.io.OutputStream

internal class MapStateSerializer(
    override val defaultValue: MapStateProto,
) : Serializer<MapStateProto> {
    override suspend fun readFrom(input: InputStream): MapStateProto = try {
        MapStateProto.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read map state proto", error)
    }

    override suspend fun writeTo(t: MapStateProto, output: OutputStream) = t.writeTo(output)
}
