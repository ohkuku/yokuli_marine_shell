package com.yokuli.anchorwatch.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Ordered, non-blocking mailbox for a single runtime such as the GPS proxy. */
class SerialRuntimeActor(
    scope: CoroutineScope,
    private val awaitRestore: suspend () -> Unit,
    mailboxCapacity: Int = 256,
    private val onFailure: (Throwable) -> Unit,
) {
    private data class Work(val action: suspend () -> Unit, val completion: CompletableDeferred<Unit>?)
    private val channel = Channel<Work>(mailboxCapacity.coerceAtLeast(1))

    private val worker = scope.launch {
        try {
            for (work in channel) {
                try {
                    coroutineContext.ensureActive()
                    awaitRestore()
                    coroutineContext.ensureActive()
                    work.action()
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

    fun submit(action: suspend () -> Unit): Boolean = channel.trySend(Work(action, null)).isSuccess

    suspend fun execute(action: suspend () -> Unit) {
        val completion = CompletableDeferred<Unit>()
        channel.send(Work(action, completion))
        completion.await()
    }

    fun shutdown() { channel.close(); worker.cancel() }
}
