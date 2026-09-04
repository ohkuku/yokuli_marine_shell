package com.yokuli.shell.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.yokuli.shell.storage.proto.LauncherStateProto
import java.io.InputStream
import java.io.OutputStream

class LauncherStateSerializer(
    override val defaultValue: LauncherStateProto,
) : Serializer<LauncherStateProto> {
    override suspend fun readFrom(input: InputStream): LauncherStateProto = try {
        LauncherStateProto.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read launcher state proto", error)
    }

    override suspend fun writeTo(t: LauncherStateProto, output: OutputStream) {
        t.writeTo(output)
    }
}
