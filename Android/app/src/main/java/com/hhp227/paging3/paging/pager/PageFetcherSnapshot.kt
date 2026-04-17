package com.hhp227.paging3.paging.pager

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.hhp227.paging3.paging.LoadState
import com.hhp227.paging3.paging.LoadType
import com.hhp227.paging3.paging.PageEvent
import com.hhp227.paging3.paging.ViewportHint
import com.hhp227.paging3.paging.extension.PagingConfig
import com.hhp227.paging3.paging.extension.PagingSource
import com.hhp227.paging3.paging.extension.PagingState
import com.hhp227.paging3.paging.extension.RemoteMediatorConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

// 조금 더 봐야함 일단 패스 에러의 핵심??
internal class PageFetcherSnapshot<Key : Any, Value : Any>(
    internal val initialKey: Key?,
    internal val pagingSource: PagingSource<Key, Value>,
    private val config: PagingConfig,
    private val retryFlow: Flow<Unit>,
    val remoteMediatorConnection: RemoteMediatorConnection<Key, Value>? = null,
    private val previousPagingState: PagingState<Key, Value>? = null,
    private val invalidate: () -> Unit = {},
) {
    private val hintHandler = HintHandler()

    private val pageEventChCollected = AtomicBoolean(false)
    private val pageEvent = MutableStateFlow<PageEvent<Value>>(PageEvent.StaticList(emptyList()))
    private val pageEventCh = Channel<PageEvent<Value>>(Channel.BUFFERED)
    private val stateHolder = PageFetcherSnapshotState.Holder<Key, Value>(config = config)

    // 아래는 gpt가 추천해준 코드1

    private val pageEventChannelFlowJob = Job()

    // 임시
    val pageEventFlow: Flow<PageEvent<Value>> = flow {
        check(pageEventChCollected.compareAndSet(false, true)) {
            "Attempt to collect twice from pageEventFlow, which is an illegal operation. Did you " +
                    "forget to call Flow<PagingData<*>>.cachedIn(coroutineScope)?"
        }

        /*remoteMediatorConnection?.let {
            val pagingState = previousPagingState ?: stateHolder.withLock { state ->
                state.currentPagingState(null)
            }
            it.requestRefreshIfAllowed(pagingState)
        }*/

        doInitialLoad()
        if (stateHolder.withLock { state -> state.sourceLoadStates.get(LoadType.REFRESH) } !is LoadState.Error) {
            startConsumingHints()
        }
        pageEvent.collect {
            try {
                if (it !is PageEvent.StaticList<Value>)
                    emit(it)
            } catch (e: ClosedSendChannelException) {
            }
        }
    }

    /*val pageEventFlow: Flow<PageEvent<Value>> = flow {
        check(pageEventChCollected.compareAndSet(false, true)) {
            "Attempt to collect twice from pageEventFlow, which is an illegal operation. Did you " +
                    "forget to call Flow<PagingData<*>>.cachedIn(coroutineScope)?"
        }

        /*remoteMediatorConnection?.let {
            val pagingState = previousPagingState ?: stateHolder.withLock { state ->
                state.currentPagingState(null)
            }
            it.requestRefreshIfAllowed(pagingState)
        }*/

        doInitialLoad()

        if (stateHolder.withLock { state -> state.sourceLoadStates.get(LoadType.REFRESH) } !is LoadState.Error) {
            startConsumingHints()
        }

        // 감시작업을 coroutineScope 내에서 실행
        coroutineScope {
            val pageEventChannel = Channel<PageEvent<Value>>(Channel.RENDEZVOUS)

            // Job이 완료되면 채널을 닫음
            pageEventChannelFlowJob.invokeOnCompletion {
                pageEventChannel.close()
            }

            launch {
                pageEvent.collect {
                    try {
                        if (it !is PageEvent.StaticList<Value>)
                            pageEventChannel.send(it)
                    } catch (e: ClosedSendChannelException) {
                    }
                }
            }

            for (event in pageEventChannel) {
                emit(event)
            }
        }
    }.onStart {
        emit(PageEvent.LoadStateUpdate(stateHolder.withLock { it.sourceLoadStates.snapshot() }))
    }*/

    // 아래는 원본에 가까운 코드

    /*private val pageEventChannelFlowJob = Job()

    val pageEventFlow: Flow<PageEvent<Value>> = cancelableChannelFlow<PageEvent<Value>>(
        pageEventChannelFlowJob
    ) {
        Log.e("IDIS_TEST", "CHECK $pageEventChCollected")
        check(pageEventChCollected.compareAndSet(false, true)) {
            "Attempt to collect twice from pageEventFlow, which is an illegal operation. Did you " +
                    "forget to call Flow<PagingData<*>>.cachedIn(coroutineScope)?"
        }

        launch {
            pageEvent.collect {
                try {
                    if (it !is PageEvent.StaticList<Value>)
                        send(it)
                } catch (e: ClosedSendChannelException) {
                }
            }
        }
        /*remoteMediatorConnection?.let {
            val pagingState = previousPagingState ?: stateHolder.withLock { state ->
                state.currentPagingState(null)
            }
            it.requestRefreshIfAllowed(pagingState)
        }*/
        doInitialLoad()

        if (stateHolder.withLock { state -> state.sourceLoadStates.get(LoadType.REFRESH) } !is LoadState.Error) {
            startConsumingHints()
        }
    }.onStart {
        emit(PageEvent.LoadStateUpdate(stateHolder.withLock { it.sourceLoadStates.snapshot() }))
    }*/

    fun accessHint(viewportHint: ViewportHint) {
        hintHandler.processHint(viewportHint)
    }

    fun close() {
        Log.e("IDIS_TEST", "close")
        pageEventChannelFlowJob.cancel()
    }

    fun currentPagingState(): PagingState<Key, Value> {
        return stateHolder.withLock { state ->
            state.currentPagingState(hintHandler.lastAccessHint)
        }
    }

    private fun startConsumingHints() {
        if (config.jumpThreshold != PagingSource.LoadResult.Page.COUNT_UNDEFINED) {
            listOf(LoadType.APPEND, LoadType.PREPEND)
                .forEach { loadType ->
                    CoroutineScope(Dispatchers.Default).launch {
                        hintHandler.hintFor(loadType)
                            .filter { hint ->
                                hint.presentedItemsBefore * -1 > config.jumpThreshold || hint.presentedItemsAfter * -1 > config.jumpThreshold
                            }.collectLatest { invalidate() }
                    }
                }
        }

        CoroutineScope(Dispatchers.Default).launch {
            collectAsGenerationalViewportHints(
                stateHolder.withLock { state -> state.consumePrependGenerationIdAsFlow() },
                LoadType.PREPEND
            )
        }

        CoroutineScope(Dispatchers.Default).launch {
            collectAsGenerationalViewportHints(
                stateHolder.withLock { state -> state.consumeAppendGenerationIdAsFlow() },
                LoadType.APPEND
            )
        }
    }

    private suspend fun collectAsGenerationalViewportHints(
        flow: Flow<Int>,
        loadType: LoadType
    ) = flow.flatMapLatest { generationId ->
        stateHolder.withLock { state ->
            //Log.e("TEST", "state: $state")
            if (state.sourceLoadStates.get(loadType) == LoadState.NotLoading(true)) {

            } else if (state.sourceLoadStates.get(loadType) !is LoadState.Error) {
                //Log.e("TEST", "TEST!!!!!!!!!")
                state.sourceLoadStates.set(loadType, LoadState.NotLoading(false))
            }
        }
        hintHandler.hintFor(loadType)
            .drop(if (generationId == 0) 0 else 1)
            .map { hint -> GenerationalViewportHint(generationId, hint) }
    }.runningReduce { previous, next ->
        if (next.shouldPrioritizeOver(previous, loadType)) next else previous
    }.collectLatest { generationalHint ->
        doLoad(loadType, generationalHint)
    }

    private fun loadParams(loadType: LoadType, key: Key?) = PagingSource.LoadParams.create(
        loadType = loadType,
        key = key,
        loadSize = if (loadType == LoadType.REFRESH) config.initialLoadSize else config.pageSize,
        placeholdersEnabled = config.enablePlaceholders
    )

    private suspend fun doInitialLoad() {
        stateHolder.withLock { state -> state.setLoading(LoadType.REFRESH, pageEvent) }

        val params = loadParams(LoadType.REFRESH, initialKey)

        when (val result = pagingSource.load(params)) {
            is PagingSource.LoadResult.Page<Key, Value> -> {
                val insertApplied = stateHolder.withLock { state ->
                    state.sourceLoadStates.set(
                        type = LoadType.REFRESH,
                        state = LoadState.NotLoading(false)
                    )
                    if (result.prevKey == null) {
                        state.sourceLoadStates.set(
                            type = LoadType.PREPEND,
                            state = LoadState.NotLoading(true)
                        )
                    }
                    if (result.nextKey == null) {
                        state.sourceLoadStates.set(
                            type = LoadType.APPEND,
                            state = LoadState.NotLoading(true)
                        )
                    }
                    state.insert(0, LoadType.REFRESH, result)
                }

                if (insertApplied) {
                    stateHolder.withLock { state ->
                        pageEvent.value = state.toPageEvent(LoadType.REFRESH, result)
                    }
                }

                /*if (remoteMediatorConnection != null) {
                    if (result.prevKey == null || result.nextKey == null) {
                        val pagingState = stateHolder.withLock { state ->
                            state.currentPagingState(hintHandler.lastAccessHint)
                        }

                        if (result.prevKey == null) {
                            remoteMediatorConnection.requestLoad(LoadType.PREPEND, pagingState)
                        }

                        if (result.nextKey == null) {
                            remoteMediatorConnection.requestLoad(LoadType.APPEND, pagingState)
                        }
                    }
                }*/
            }
            is PagingSource.LoadResult.Error -> stateHolder.withLock { state ->
                val loadState = LoadState.Error(result.throwable)

                state.setError(loadType = LoadType.REFRESH, error = loadState, pageEvent = pageEvent)
            }
            is PagingSource.LoadResult.Invalid -> onInvalidLoad()
        }
    }

    private suspend fun doLoad(
        loadType: LoadType,
        generationalHint: GenerationalViewportHint
    ) {
        require(loadType != LoadType.REFRESH) { "Use doInitialLoad for LoadType == REFRESH" }

        var itemsLoaded = 0

        stateHolder.withLock { state ->
            when (loadType) {
                LoadType.PREPEND -> {
                    var firstPageIndex =
                        state.initialPageIndex + generationalHint.hint.originalPageOffsetFirst - 1

                    if (firstPageIndex > state.pages.lastIndex) {
                        itemsLoaded += config.pageSize * (firstPageIndex - state.pages.lastIndex)
                        firstPageIndex = state.pages.lastIndex
                    }
                    if (firstPageIndex > 0) {
                        for (pageIndex in 0..firstPageIndex) {
                            itemsLoaded += state.pages[pageIndex].data.size
                        }
                    }
                }
                LoadType.APPEND -> {
                    var lastPageIndex =
                        state.initialPageIndex + generationalHint.hint.originalPageOffsetLast + 1

                    if (lastPageIndex < 0) {
                        itemsLoaded += config.pageSize * -lastPageIndex
                        lastPageIndex = 0
                    }
                    if (state.pages.lastIndex > lastPageIndex) {
                        for (pageIndex in lastPageIndex..state.pages.lastIndex) {
                            itemsLoaded += state.pages[pageIndex].data.size
                        }
                    }
                }
                LoadType.REFRESH -> throw IllegalStateException("Use doInitialLoad for LoadType == REFRESH")
            }
        }

        var loadKey: Key? = stateHolder.withLock { state ->
            state.nextLoadKeyOrNull(
                loadType,
                generationalHint.generationId,
                generationalHint.hint.presentedItemsBeyondAnchor(loadType) + itemsLoaded,
                config
            )?.also {
                state.setLoading(loadType, pageEvent)
            }
        }
        var endOfPaginationReached = false
        suspend fun loop() {
            while (loadKey != null) {
                val params = loadParams(loadType, loadKey)
                val result: PagingSource.LoadResult<Key, Value> = pagingSource.load(params)

                when (result) {
                    is PagingSource.LoadResult.Page<Key, Value> -> {
                        val nextKey = when (loadType) {
                            LoadType.PREPEND -> result.prevKey
                            LoadType.APPEND -> result.nextKey
                            else -> throw IllegalArgumentException(
                                "Use doInitialLoad for LoadType == REFRESH"
                            )
                        }

                        check(pagingSource.keyReuseSupported || nextKey != loadKey) {
                            val keyFieldName = if (loadType == LoadType.PREPEND) "prevKey" else "nextKey"
                            """The same value, $loadKey, was passed as the $keyFieldName in two
                            | sequential Pages loaded from a PagingSource. Re-using load keys in
                            | PagingSource is often an error, and must be explicitly enabled by
                            | overriding PagingSource.keyReuseSupported.
                            """.trimMargin()
                        }

                        val insertApplied = stateHolder.withLock { state ->
                            state.insert(generationalHint.generationId, loadType, result)
                        }

                        if (!insertApplied) return

                        itemsLoaded += result.data.size

                        if ((loadType == LoadType.PREPEND && result.prevKey == null) ||
                            (loadType == LoadType.APPEND && result.nextKey == null)
                        ) {
                            endOfPaginationReached = true
                        }
                    }
                    is PagingSource.LoadResult.Error -> {
                        stateHolder.withLock { state ->
                            val loadState = LoadState.Error(result.throwable)
                            state.setError(loadType = loadType, error = loadState, pageEvent = pageEvent)

                            state.failedHintsByLoadType[loadType] = generationalHint.hint
                        }
                        return
                    }
                    is PagingSource.LoadResult.Invalid -> {
                        onInvalidLoad()
                        return
                    }
                }
                val dropType = when (loadType) {
                    LoadType.PREPEND -> LoadType.APPEND
                    else -> LoadType.PREPEND
                }
                stateHolder.withLock { state ->
                    state.dropEventOrNull(dropType, generationalHint.hint)?.let { event ->
                        state.drop(event)
                        pageEvent.value = event
                    }
                    loadKey = state.nextLoadKeyOrNull(
                        loadType,
                        generationalHint.generationId,
                        generationalHint.hint.presentedItemsBeyondAnchor(loadType) + itemsLoaded,
                        config
                    )
                    if (loadKey == null && state.sourceLoadStates.get(loadType) !is LoadState.Error) {
                        state.sourceLoadStates.set(
                            type = loadType,
                            state = when {
                                endOfPaginationReached -> LoadState.NotLoading(true)
                                else -> LoadState.NotLoading(false)
                            }
                        )
                    }
                    val pageEvent = state.toPageEvent(loadType, result as PagingSource.LoadResult.Page)

                    this.pageEvent.value = pageEvent
                }
                /*val endsPrepend = params is PagingSource.LoadParams.Prepend && (result as? PagingSource.LoadResult.Page)?.prevKey == null
                val endsAppend = params is PagingSource.LoadParams.Append && (result as? PagingSource.LoadResult.Page)?.nextKey == null
                if (remoteMediatorConnection != null && (endsPrepend || endsAppend)) {
                    val pagingState = stateHolder.withLock { state ->
                        state.currentPagingState(hintHandler.lastAccessHint)
                    }

                    if (endsPrepend) {
                        remoteMediatorConnection.requestLoad(LoadType.PREPEND, pagingState)
                    }

                    if (endsAppend) {
                        remoteMediatorConnection.requestLoad(LoadType.APPEND, pagingState)
                    }
                }*/
            }
        }

        loop()
    }

    private fun onInvalidLoad() {
        close()
        pagingSource.invalidate()
    }

    init {
        require(config.jumpThreshold == PagingSource.LoadResult.Page.COUNT_UNDEFINED || pagingSource.jumpingSupported) {
            "PagingConfig.jumpThreshold was set, but the associated PagingSource has not marked " +
                    "support for jumps by overriding PagingSource.jumpingSupported to true."
        }
    }
}

private fun <Key : Any, Value : Any>PageFetcherSnapshotState<Key, Value>.setLoading(loadType: LoadType, pageEvent: MutableStateFlow<PageEvent<Value>>) {
    if (sourceLoadStates.get(loadType) != LoadState.Loading) {
        sourceLoadStates.set(type = loadType, state = LoadState.Loading)
        pageEvent.value = PageEvent.LoadStateUpdate(
            source = sourceLoadStates.snapshot(),
            mediator = null,
        )
    }
}

private fun <Key : Any, Value : Any>PageFetcherSnapshotState<Key, Value>.setError(
    loadType: LoadType,
    error: LoadState.Error,
    pageEvent: MutableStateFlow<PageEvent<Value>>
) {
    if (sourceLoadStates.get(loadType) != error) {
        sourceLoadStates.set(type = loadType, state = error)
        pageEvent.value = PageEvent.LoadStateUpdate(
            source = sourceLoadStates.snapshot(),
            mediator = null,
        )
    }
}

private fun <Key : Any, Value : Any>PageFetcherSnapshotState<Key, Value>.nextLoadKeyOrNull(
    loadType: LoadType,
    generationId: Int,
    presentedItemsBeyondAnchor: Int,
    config: PagingConfig
): Key? {
    if (generationId != generationId(loadType)) return null
    if (sourceLoadStates.get(loadType) is LoadState.Error) return null

    if (presentedItemsBeyondAnchor >= config.prefetchDistance) return null

    return if (loadType == LoadType.PREPEND) {
        pages.first().prevKey
    } else {
        pages.last().nextKey
    }
}

@VisibleForTesting
internal data class GenerationalViewportHint(val generationId: Int, val hint: ViewportHint)

internal fun GenerationalViewportHint.shouldPrioritizeOver(
    previous: GenerationalViewportHint,
    loadType: LoadType
): Boolean {
    return when {
        generationId > previous.generationId -> true
        generationId < previous.generationId -> false
        else -> hint.shouldPrioritizeOver(previous.hint, loadType)
    }
}