package com.hhp227.paging3.paging

import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

private class MulticastedPagingData<T : Any>(
    val scope: CoroutineScope,
    val parent: PagingData<T>,
    // used in tests
    val tracker: ActiveFlowTracker? = null
) {
    private val accumulated = CachedPageEventFlow(
        src = parent.flow,
        scope = scope
    ).also {
        tracker?.onNewCachedEventFlow(it)
    }

    fun asPagingData() = PagingData(
        flow = accumulated.downstreamFlow.onStart {
            tracker?.onStart(ActiveFlowTracker.FlowType.PAGE_EVENT_FLOW)
        }.onCompletion {
            tracker?.onComplete(ActiveFlowTracker.FlowType.PAGE_EVENT_FLOW)
        },
        receiver = parent.receiver,
        hintReceiver = parent.hintReceiver
    )

    suspend fun close() = accumulated.close()
}

/**
 * Caches the [PagingData] such that any downstream collection from this flow will share the same
 * [PagingData].
 *
 * The flow is kept active as long as the given [scope] is active. To avoid leaks, make sure to
 * use a [scope] that is already managed (like a ViewModel scope) or manually cancel it when you
 * don't need paging anymore.
 *
 * A common use case for this caching is to cache [PagingData] in a ViewModel. This can ensure that,
 * upon configuration change (e.g. rotation), then new Activity will receive the existing data
 * immediately rather than fetching it from scratch.
 *
 * Calling [cachedIn] is required to allow calling
 * [submitData][androidx.paging.AsyncPagingDataAdapter] on the same instance of [PagingData]
 * emitted by [Pager] or any of its transformed derivatives, as reloading data from scratch on the
 * same generation of [PagingData] is an unsupported operation.
 *
 * Note that this does not turn the `Flow<PagingData>` into a hot stream. It won't execute any
 * unnecessary code unless it is being collected.
 *
 * @sample androidx.paging.samples.cachedInSample
 *
 * @param scope The coroutine scope where this page cache will be kept alive.
 */
@CheckResult
public fun <T : Any> Flow<PagingData<T>>.cachedIn(
    scope: CoroutineScope
): Flow<PagingData<T>> = cachedIn(scope, null)

internal fun <T : Any> Flow<PagingData<T>>.cachedIn(
    scope: CoroutineScope,
    // used in tests
    tracker: ActiveFlowTracker? = null
): Flow<PagingData<T>> {
    return this.mapLatest {
        MulticastedPagingData(
            scope = scope,
            parent = it,
            tracker = tracker
        )
    }.runningReduce { prev, next ->
        prev.close()
        next
    }.map {
        it.asPagingData()
    }.onStart {
        tracker?.onStart(ActiveFlowTracker.FlowType.PAGED_DATA_FLOW)
    }.onCompletion {
        tracker?.onComplete(ActiveFlowTracker.FlowType.PAGED_DATA_FLOW)
    }.shareIn(
        scope = scope,
        started = SharingStarted.Lazily,
        // replay latest multicasted paging data since it is re-connectable.
        replay = 1
    )
}

/**
 * This is only used for testing to ensure we don't leak resources
 */
@VisibleForTesting
internal interface ActiveFlowTracker {
    fun onNewCachedEventFlow(cachedPageEventFlow: CachedPageEventFlow<*>)
    suspend fun onStart(flowType: FlowType)
    suspend fun onComplete(flowType: FlowType)

    enum class FlowType {
        PAGED_DATA_FLOW,
        PAGE_EVENT_FLOW
    }
}
