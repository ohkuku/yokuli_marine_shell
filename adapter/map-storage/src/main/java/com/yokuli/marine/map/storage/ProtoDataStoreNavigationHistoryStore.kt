package com.yokuli.marine.map.storage

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.yokuli.marine.map.storage.proto.NavigationHistoryStateProto
import com.yokuli.marine.navigation.domain.NavigationHistoryLoadResult
import com.yokuli.marine.navigation.domain.NavigationHistorySaveResult
import com.yokuli.marine.navigation.domain.NavigationHistorySnapshot
import com.yokuli.marine.navigation.domain.NavigationHistoryStore
import com.yokuli.marine.navigation.domain.NavigationHistoryStoreFailure
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class ProtoDataStoreNavigationHistoryStore private constructor(
    private val store: DataStore<NavigationHistoryStateProto>,
) : NavigationHistoryStore {
    override suspend fun loadNavigationHistory(): NavigationHistoryLoadResult = try {
        NavigationHistoryLoadResult.Loaded(NavigationHistoryProtoMapper.decode(store.data.first()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: CorruptionException) {
        NavigationHistoryLoadResult.Failed(error.toFailure())
    } catch (error: IllegalArgumentException) {
        NavigationHistoryLoadResult.Failed(error.toFailure())
    } catch (_: IOException) {
        NavigationHistoryLoadResult.Failed(NavigationHistoryStoreFailure.IO)
    } catch (_: Throwable) {
        NavigationHistoryLoadResult.Failed(NavigationHistoryStoreFailure.UNKNOWN)
    }

    override suspend fun saveNavigationHistory(snapshot: NavigationHistorySnapshot): NavigationHistorySaveResult = try {
        store.updateData { NavigationHistoryProtoMapper.encode(snapshot) }
        NavigationHistorySaveResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        NavigationHistorySaveResult.Failed(NavigationHistoryStoreFailure.IO)
    } catch (_: Throwable) {
        NavigationHistorySaveResult.Failed(NavigationHistoryStoreFailure.UNKNOWN)
    }

    companion object {
        private const val FILE_NAME = "navigation_history.pb"

        fun create(context: Context, scope: CoroutineScope): ProtoDataStoreNavigationHistoryStore =
            create(context.dataStoreFile(FILE_NAME), scope)

        internal fun create(file: File, scope: CoroutineScope): ProtoDataStoreNavigationHistoryStore =
            ProtoDataStoreNavigationHistoryStore(
                DataStoreFactory.create(
                    serializer = NavigationHistorySerializer,
                    scope = scope,
                    produceFile = { file },
                ),
            )
    }
}

private fun Throwable.toFailure(): NavigationHistoryStoreFailure =
    if (message.orEmpty().contains("Unsupported navigation history schema")) {
        NavigationHistoryStoreFailure.FUTURE_SCHEMA
    } else NavigationHistoryStoreFailure.CORRUPT
