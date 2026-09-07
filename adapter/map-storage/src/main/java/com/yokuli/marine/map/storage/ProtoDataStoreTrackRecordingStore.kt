package com.yokuli.marine.map.storage

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.yokuli.marine.map.storage.proto.TrackRecordingStateProto
import com.yokuli.marine.navigation.domain.TrackRecordingLoadResult
import com.yokuli.marine.navigation.domain.TrackRecordingSaveResult
import com.yokuli.marine.navigation.domain.TrackRecordingSession
import com.yokuli.marine.navigation.domain.TrackRecordingStore
import com.yokuli.marine.navigation.domain.TrackRecordingStoreFailure
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class ProtoDataStoreTrackRecordingStore private constructor(
    private val store: DataStore<TrackRecordingStateProto>,
) : TrackRecordingStore {
    override suspend fun loadTrackRecording(): TrackRecordingLoadResult = try {
        TrackRecordingProtoMapper.decode(store.data.first())?.let(TrackRecordingLoadResult::Loaded)
            ?: TrackRecordingLoadResult.Empty
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: CorruptionException) {
        TrackRecordingLoadResult.Failed(
            if (error.message.orEmpty().contains("Unsupported track recording schema")) {
                TrackRecordingStoreFailure.FUTURE_SCHEMA
            } else TrackRecordingStoreFailure.CORRUPT,
        )
    } catch (error: IllegalArgumentException) {
        TrackRecordingLoadResult.Failed(
            if (error.message.orEmpty().contains("Unsupported track recording schema")) {
                TrackRecordingStoreFailure.FUTURE_SCHEMA
            } else TrackRecordingStoreFailure.CORRUPT,
        )
    } catch (_: IOException) {
        TrackRecordingLoadResult.Failed(TrackRecordingStoreFailure.IO)
    } catch (_: Throwable) {
        TrackRecordingLoadResult.Failed(TrackRecordingStoreFailure.UNKNOWN)
    }

    override suspend fun saveTrackRecording(session: TrackRecordingSession?): TrackRecordingSaveResult = try {
        store.updateData { TrackRecordingProtoMapper.encode(session) }
        TrackRecordingSaveResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        TrackRecordingSaveResult.Failed(TrackRecordingStoreFailure.IO)
    } catch (_: Throwable) {
        TrackRecordingSaveResult.Failed(TrackRecordingStoreFailure.UNKNOWN)
    }

    companion object {
        private const val FILE_NAME = "track_recording.pb"

        fun create(context: Context, scope: CoroutineScope): ProtoDataStoreTrackRecordingStore =
            create(context.dataStoreFile(FILE_NAME), scope)

        internal fun create(file: File, scope: CoroutineScope): ProtoDataStoreTrackRecordingStore =
            ProtoDataStoreTrackRecordingStore(
                DataStoreFactory.create(
                    serializer = TrackRecordingSerializer,
                    scope = scope,
                    produceFile = { file },
                ),
            )
    }
}
