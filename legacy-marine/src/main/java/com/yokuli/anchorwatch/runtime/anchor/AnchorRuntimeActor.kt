package com.yokuli.anchorwatch.runtime.anchor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * The only writer to [AnchorWatchRuntime] after restoration. High-rate fixes,
 * health transitions and user commands share this ordered mailbox, but never
 * block unrelated proxy, sharing, notification or sonar work.
 */
class AnchorRuntimeActor(
    scope: CoroutineScope,
    private val awaitRestore: suspend () -> Unit,
    private val runtime: AnchorWatchRuntime,
    mailboxCapacity: Int = 512,
    private val onFailure: (Throwable) -> Unit,
) {
    private data class Work(
        val action: suspend AnchorWatchRuntime.() -> Unit,
        val completion: CompletableDeferred<Unit>?,
    )

    private val channel = Channel<Work>(mailboxCapacity.coerceAtLeast(1))

    private val worker = scope.launch {
        try {
            for (work in channel) {
                try {
                    coroutineContext.ensureActive()
                    awaitRestore()
                    coroutineContext.ensureActive()
                    work.action(runtime)
                    work.completion?.complete(Unit)
                } catch (cancelled: CancellationException) {
                    work.completion?.completeExceptionally(cancelled)
                    throw cancelled
                } catch (error: Throwable) {
                    work.completion?.completeExceptionally(error) ?: onFailure(error)
                }
            }
        } finally {
            channel.close()
            while (true) {
                val waiting = channel.tryReceive().getOrNull() ?: break
                waiting.completion?.completeExceptionally(CancellationException("RUNTIME_ACTOR_STOPPED"))
            }
        }
    }

    fun submit(action: suspend AnchorWatchRuntime.() -> Unit): Boolean =
        channel.trySend(Work(action, null)).isSuccess

    suspend fun execute(action: suspend AnchorWatchRuntime.() -> Unit) {
        val completion = CompletableDeferred<Unit>()
        channel.send(Work(action, completion))
        completion.await()
    }

    fun shutdown() { channel.close(); worker.cancel() }
}
