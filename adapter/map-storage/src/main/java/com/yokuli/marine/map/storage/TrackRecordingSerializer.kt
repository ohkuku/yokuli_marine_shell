package com.yokuli.marine.map.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.marine.map.storage.proto.TrackRecordingStateProto
import java.io.InputStream
import java.io.OutputStream

internal object TrackRecordingSerializer : Serializer<TrackRecordingStateProto> {
    override val defaultValue: TrackRecordingStateProto = TrackRecordingProtoMapper.encode(null)

    override suspend fun readFrom(input: InputStream): TrackRecordingStateProto = try {
        TrackRecordingStateProto.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read track recording", error)
    }

    override suspend fun writeTo(t: TrackRecordingStateProto, output: OutputStream) = t.writeTo(output)
}
