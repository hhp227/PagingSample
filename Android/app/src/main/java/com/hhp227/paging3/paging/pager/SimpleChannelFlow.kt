package com.hhp227.paging3.paging.pager

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.resume

internal fun <T> simpleChannelFlow(
    block: suspend SimpleProducerScope<T>.() -> Unit
): Flow<T> {
    return flow {
        coroutineScope {
            val channel = Channel<T>(capacity = Channel.RENDEZVOUS)
            val producer = launch {
                try {
                    // run producer in a separate inner scope to ensure we wait for its children
                    // to finish, in case it does more launches inside.
                    coroutineScope {
                        val producerScopeImpl = SimpleProducerScopeImpl(
                            scope = this,
                            channel = channel,
                        )
                        producerScopeImpl.block()
                    }
                    channel.close()
                } catch (t: Throwable) {
                    channel.close(t)
                }
            }
            for (item in channel) {
                emit(item)
            }
            // in case channel closed before producer completes, cancel the producer.
            producer.cancel()
        }
    }.buffer(Channel.BUFFERED)
}

internal interface SimpleProducerScope<T> : CoroutineScope, SendChannel<T> {
    val channel: SendChannel<T>
    suspend fun awaitClose(block: () -> Unit)
}

internal class SimpleProducerScopeImpl<T>(
    scope: CoroutineScope,
    override val channel: SendChannel<T>,
) : SimpleProducerScope<T>, CoroutineScope by scope, SendChannel<T> by channel {
    override suspend fun awaitClose(block: () -> Unit) {
        try {
            val job = checkNotNull(coroutineContext[Job]) {
                "Internal error, context should have a job."
            }
            suspendCancellableCoroutine<Unit> { cont ->
                job.invokeOnCompletion {
                    cont.resume(Unit)
                }
            }
        } finally {
            block()
        }
    }
}