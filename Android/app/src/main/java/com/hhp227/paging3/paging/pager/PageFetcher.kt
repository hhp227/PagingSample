package com.hhp227.paging3.paging.pager

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.hhp227.paging3.paging.HintReceiver
import com.hhp227.paging3.paging.PagingData
import com.hhp227.paging3.paging.UiReceiver
import com.hhp227.paging3.paging.ViewportHint
import com.hhp227.paging3.paging.extension.*
import com.hhp227.paging3.paging.extension.RemoteMediatorAccessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

// 일단 확인함 LegacyPagingSource 추가 해볼것
internal class PageFetcher<Key : Any, Value : Any>(
    private val pagingSourceFactory: suspend () -> PagingSource<Key, Value>,
    private val initialKey: Key?,
    private val config: PagingConfig,
    private val remoteMediator: RemoteMediator<Key, Value>? = null
) {
    private val refreshEvents = ConflatedEventBus<Boolean>()

    private val retryEvents = ConflatedEventBus<Unit>()

    val flow: Flow<PagingData<Value>> get() {
        val remoteMediatorAccessor = remoteMediator?.let {
            RemoteMediatorAccessor(CoroutineScope(Dispatchers.Default), it)
        }
        return refreshEvents
            .flow
            .onStart {
                emit(remoteMediatorAccessor?.initialize() == RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH)
            }
            .scan(null) { previousGeneration: GenerationInfo<Key, Value>?, triggerRemoteRefresh: Boolean ->
                if (triggerRemoteRefresh) {
                    remoteMediatorAccessor?.allowRefresh()
                }
                val pagingSource = generateNewPagingSource(previousGeneration?.snapshot?.pagingSource)
                var previousPagingState = previousGeneration?.snapshot?.currentPagingState()

                if (previousPagingState?.pages?.isEmpty() == true && !(previousGeneration?.state?.pages?.isEmpty())!!) {
                    previousPagingState = previousGeneration.state
                }

                if (previousPagingState?.anchorPosition == null && previousGeneration?.state?.anchorPosition != null) {
                    previousPagingState = previousGeneration.state
                }

                val initialKey: Key? = when (previousPagingState) {
                    null -> initialKey
                    else -> pagingSource.getRefreshKey(previousPagingState)
                }

                previousGeneration?.snapshot?.close()

                GenerationInfo(
                    snapshot = PageFetcherSnapshot(
                        initialKey = initialKey,
                        pagingSource = pagingSource,
                        config = config,
                        retryFlow = retryEvents.flow,
                        remoteMediatorConnection = remoteMediatorAccessor,
                        invalidate = this@PageFetcher::refresh,
                        previousPagingState = previousPagingState,
                    ),
                    state = previousPagingState
                )
            }
            .filter { it != null }
            .map { generation ->
                PagingData(
                    flow = generation!!.snapshot.pageEventFlow,
                    receiver = PagerUiReceiver(retryEvents, ::refresh),
                    hintReceiver = PagerHintReceiver(generation.snapshot)
                )
            }
    }

    fun refresh() {
        refreshEvents.send(true)
    }

    private fun invalidate() {
        refreshEvents.send(false)
    }

    private fun PageFetcherSnapshot<Key, Value>.injectRemoteEvents(
        job: Job,
        accessor: RemoteMediatorAccessor<Key, Value>?
    ) {
        // TODO
    }

    private suspend fun generateNewPagingSource(
        previousPagingSource: PagingSource<Key, Value>?
    ): PagingSource<Key, Value> {
        val pagingSource = pagingSourceFactory()

        check(pagingSource !== previousPagingSource) {
            """
            An instance of PagingSource was re-used when Pager expected to create a new
            instance. Ensure that the pagingSourceFactory passed to Pager always returns a
            new instance of PagingSource.
            """.trimIndent()
        }

        Log.e("IDIS_TEST", "generateNewPagingSource")
        pagingSource.registerInvalidatedCallback(::invalidate)
        previousPagingSource?.unregisterInvalidatedCallback(::invalidate)
        previousPagingSource?.invalidate()
        return pagingSource
    }

    inner class PagerUiReceiver(
        private val retryEventBus: ConflatedEventBus<Unit>,
        private val refreshCallback: () -> Unit
    ) : UiReceiver {
        override fun retry() {
            retryEventBus.send(Unit)
        }

        override fun refresh() = refreshCallback()
    }

    inner class PagerHintReceiver<Key : Any, Value : Any> constructor(
        internal val pageFetcherSnapshot: PageFetcherSnapshot<Key, Value>,
    ) : HintReceiver {

        override fun accessHint(viewportHint: ViewportHint) {
            pageFetcherSnapshot.accessHint(viewportHint)
        }
    }

    private class GenerationInfo<Key : Any, Value : Any>(
        val snapshot: PageFetcherSnapshot<Key, Value>,
        val state: PagingState<Key, Value>?
    )
}