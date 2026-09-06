package com.yokuli.marine.map.storage

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.yokuli.marine.map.storage.proto.ActiveNavigationSessionProto
import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.ActiveNavigationSessionStore
import com.yokuli.marine.navigation.domain.NavigationSessionLoadResult
import com.yokuli.marine.navigation.domain.NavigationSessionSaveResult
import com.yokuli.marine.navigation.domain.NavigationSessionStoreFailure
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class ProtoDataStoreActiveNavigationSessionStore private constructor(
    private val store: DataStore<ActiveNavigationSessionProto>,
) : ActiveNavigationSessionStore {
    override suspend fun loadActiveNavigationSession(): NavigationSessionLoadResult = try {
        ActiveNavigationSessionProtoMapper.decode(store.data.first())?.let(NavigationSessionLoadResult::Loaded)
            ?: NavigationSessionLoadResult.Empty
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: CorruptionException) {
        NavigationSessionLoadResult.Failed(
            if (error.message.orEmpty().contains("Unsupported active navigation schema")) {
                NavigationSessionStoreFailure.FUTURE_SCHEMA
            } else NavigationSessionStoreFailure.CORRUPT,
        )
    } catch (error: IllegalArgumentException) {
        NavigationSessionLoadResult.Failed(
            if (error.message.orEmpty().contains("Unsupported active navigation schema")) {
                NavigationSessionStoreFailure.FUTURE_SCHEMA
            } else NavigationSessionStoreFailure.CORRUPT,
        )
    } catch (_: IOException) {
        NavigationSessionLoadResult.Failed(NavigationSessionStoreFailure.IO)
    } catch (_: Throwable) {
        NavigationSessionLoadResult.Failed(NavigationSessionStoreFailure.UNKNOWN)
    }

    override suspend fun saveActiveNavigationSession(session: ActiveNavigationSession?): NavigationSessionSaveResult = try {
        store.updateData { ActiveNavigationSessionProtoMapper.encode(session) }
        NavigationSessionSaveResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        NavigationSessionSaveResult.Failed(NavigationSessionStoreFailure.IO)
    } catch (_: Throwable) {
        NavigationSessionSaveResult.Failed(NavigationSessionStoreFailure.UNKNOWN)
    }

    companion object {
        private const val FILE_NAME = "active_navigation_session.pb"

        fun create(context: Context, scope: CoroutineScope): ProtoDataStoreActiveNavigationSessionStore =
            create(context.dataStoreFile(FILE_NAME), scope)

        internal fun create(file: File, scope: CoroutineScope): ProtoDataStoreActiveNavigationSessionStore =
            ProtoDataStoreActiveNavigationSessionStore(
                DataStoreFactory.create(
                    serializer = ActiveNavigationSessionSerializer,
                    scope = scope,
                    produceFile = { file },
                ),
            )
    }
}
