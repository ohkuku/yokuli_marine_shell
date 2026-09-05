package com.yokuli.marine.map.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.yokuli.marine.map.domain.MapPersistencePort
import com.yokuli.marine.map.domain.MapPersistedState
import com.yokuli.marine.map.domain.MapState
import com.yokuli.marine.map.storage.proto.MapStateProto
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class ProtoDataStoreMapPersistence private constructor(
    private val dataStore: DataStore<MapStateProto>,
) : MapPersistencePort {
    override suspend fun load(): MapPersistedState = MapProtoMapper.decode(dataStore.data.first())

    override suspend fun save(snapshot: MapPersistedState) {
        dataStore.updateData { MapProtoMapper.encode(snapshot) }
    }

    companion object {
        private const val FILE_NAME = "map_state.pb"

        fun create(context: Context, scope: CoroutineScope): ProtoDataStoreMapPersistence =
            create(context.dataStoreFile(FILE_NAME), scope)

        fun create(file: File, scope: CoroutineScope): ProtoDataStoreMapPersistence {
            val defaults = MapProtoMapper.encode(MapState().persisted())
            return ProtoDataStoreMapPersistence(
                DataStoreFactory.create(
                    serializer = MapStateSerializer(defaults),
                    corruptionHandler = ReplaceFileCorruptionHandler { defaults },
                    scope = scope,
                    produceFile = { file },
                ),
            )
        }
    }
}
