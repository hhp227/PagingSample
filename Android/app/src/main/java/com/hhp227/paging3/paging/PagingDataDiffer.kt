package com.hhp227.paging3.paging

import android.util.Log
import androidx.annotation.IntRange
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

class PagingDataDiffer<T : Any>(
    private val differCallback: DifferCallback,
    private val updateItemSnapshotList: () -> Unit // 기존에 없던 코드
) : PagePresenter.ProcessPageEventCallback {
    private var presenter: PagePresenter<T> = PagePresenter.initial()
    private var hintReceiver: HintReceiver? = null
    private var receiver: UiReceiver? = null
    private val combinedLoadStatesCollection = MutableCombinedLoadStateCollection()
    private val onPagesUpdatedListeners = mutableListOf<() -> Unit>()

    @Volatile
    private var lastAccessedIndexUnfulfilled: Boolean = false

    @Volatile
    private var lastAccessedIndex: Int = 0

    private fun dispatchLoadStates(source: LoadStates, mediator: LoadStates?) {
        combinedLoadStatesCollection.set(
            sourceLoadStates = source,
            remoteLoadStates = mediator
        )
    }

    private fun presentNewList(
        previousList: NullPaddedList<T>,
        newList: NullPaddedList<T>,
        lastAccessedIndex: Int,
        onListPresentable: () -> Unit
    ): Int? {
        // 2
        onListPresentable()
        updateItemSnapshotList()
        return null
    }

    suspend fun collectFrom(pagingData: PagingData<T>) {
        Log.e("IDIS_TEST", "collectFrom")
        receiver = pagingData.receiver

        pagingData.flow.collect { event ->
            Log.e("TEST", "event: $event")
            if (event is PageEvent.Insert && event.loadType == LoadType.REFRESH) {
                presentNewList(
                    pages = event.pages,
                    placeholdersBefore = event.placeholdersBefore,
                    placeholdersAfter = event.placeholdersAfter,
                    dispatchLoadStates = true,
                    sourceLoadStates = event.sourceLoadStates,
                    mediatorLoadStates = event.mediatorLoadStates,
                    newHintReceiver = pagingData.hintReceiver
                )
            } else if (event is PageEvent.StaticList) {
                presentNewList(
                    pages = mutableListOf(TransformablePage(originalPageOffset = 0, data = event.data)),
                    placeholdersBefore = 0,
                    placeholdersAfter = 0,
                    dispatchLoadStates = event.sourceLoadStates != null || event.mediatorLoadStates != null,
                    sourceLoadStates = event.sourceLoadStates,
                    mediatorLoadStates = event.mediatorLoadStates,
                    newHintReceiver = pagingData.hintReceiver
                )
            } else {
                presenter.processEvent(event, this)
                if (event is PageEvent.Drop) {
                    lastAccessedIndexUnfulfilled = false
                }
                if (event is PageEvent.Insert) {
                    val source = combinedLoadStatesCollection.stateFlow.value?.source
                    checkNotNull(source) {
                        "PagingDataDiffer.combinedLoadStatesCollection.stateFlow should" +
                                "not hold null CombinedLoadStates after Insert event."
                    }
                    val prependDone = source.prepend.endOfPaginationReached
                    val appendDone = source.append.endOfPaginationReached
                    val canContinueLoading = !(event.loadType == LoadType.PREPEND && prependDone) &&
                            !(event.loadType == LoadType.APPEND && appendDone)

                    /**
                     *  If the insert is empty due to aggressive filtering, another hint
                     *  must be sent to fetcher-side to notify that PagingDataDiffer
                     *  received the page, since fetcher estimates prefetchDistance based on
                     *  page indices presented by PagingDataDiffer and we cannot rely on a
                     *  new item being bound to trigger another hint since the presented
                     *  page is empty.
                     */
                    val emptyInsert = event.pages.all { it.data.isEmpty() }
                    if (!canContinueLoading) {
                        // Reset lastAccessedIndexUnfulfilled since endOfPaginationReached
                        // means there are no more pages to load that could fulfill this
                        // index.
                        lastAccessedIndexUnfulfilled = false
                    } else if (lastAccessedIndexUnfulfilled || emptyInsert) {
                        val shouldResendHint = emptyInsert ||
                                lastAccessedIndex < presenter.placeholdersBefore ||
                                lastAccessedIndex > presenter.placeholdersBefore +
                                presenter.storageCount

                        if (shouldResendHint) {
                            hintReceiver?.accessHint(
                                presenter.accessHintForPresenterIndex(lastAccessedIndex)
                            )
                        } else {
                            // lastIndex fulfilled, so reset lastAccessedIndexUnfulfilled.
                            lastAccessedIndexUnfulfilled = false
                        }
                    }
                }
            }

            if (event is PageEvent.Insert || event is PageEvent.Drop || event is PageEvent.StaticList) {
                onPagesUpdatedListeners.forEach { it() }
            }
        }
    }

    public operator fun get(@IntRange(from = 0) index: Int): T? {
        lastAccessedIndexUnfulfilled = true
        lastAccessedIndex = index

        hintReceiver?.accessHint(presenter.accessHintForPresenterIndex(index))
        return presenter.get(index)
    }

    public fun peek(@IntRange(from = 0) index: Int): T? {
        return presenter.get(index)
    }

    public fun snapshot(): ItemSnapshotList<T> = presenter.snapshot()

    fun retry() {
        receiver?.retry()
    }

    fun refresh() {
        receiver?.refresh()
    }

    public val size: Int
        get() = presenter.size

    public val loadStateFlow: StateFlow<CombinedLoadStates?> =
        combinedLoadStatesCollection.stateFlow

    fun addOnPagesUpdatedListener(listener: () -> Unit) {
        onPagesUpdatedListeners.add(listener)
    }

    fun removeOnPagesUpdatedListener(listener: () -> Unit) {
        onPagesUpdatedListeners.remove(listener)
    }

    fun addLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        combinedLoadStatesCollection.addListener(listener)
    }

    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        combinedLoadStatesCollection.removeListener(listener)
    }

    private fun presentNewList(
        pages: List<TransformablePage<T>>,
        placeholdersBefore: Int,
        placeholdersAfter: Int,
        dispatchLoadStates: Boolean,
        sourceLoadStates: LoadStates?,
        mediatorLoadStates: LoadStates?,
        newHintReceiver: HintReceiver
    ) {
        lastAccessedIndexUnfulfilled = false

        val newPresenter = PagePresenter(
            pages = pages.toMutableList(),
            placeholdersBefore = placeholdersBefore,
            placeholdersAfter = placeholdersAfter
        )
        var onListPresentableCalled = false
        val transformedLastAccessedIndex = presentNewList(
            previousList = presenter,
            newList = newPresenter,
            lastAccessedIndex = lastAccessedIndex,
            onListPresentable = {
                presenter = newPresenter
                onListPresentableCalled = true
                hintReceiver = newHintReceiver
            }
        )
        check(onListPresentableCalled) {
            "Missing call to onListPresentable after new list was presented. If " +
                    "you are seeing this exception, it is generally an indication of " +
                    "an issue with Paging. Please file a bug so we can fix it at: " +
                    "https://issuetracker.google.com/issues/new?component=413106"
        }

        // Dispatch LoadState updates as soon as we are done diffing, but after
        // setting presenter.
        if (dispatchLoadStates) {
            dispatchLoadStates(sourceLoadStates!!, mediatorLoadStates)
        }

        if (transformedLastAccessedIndex == null) {
            // Send an initialize hint in case the new list is empty, which would
            // prevent a ViewportHint.Access from ever getting sent since there are
            // no items to bind from initial load.
            hintReceiver?.accessHint(newPresenter.initializeHint())
        } else {
            // Transform the last loadAround index from the old list to the new list
            // by passing it through the DiffResult, and pass it forward as a
            // ViewportHint within the new list to the next generation of Pager.
            // This ensures prefetch distance for the last ViewportHint from the old
            // list is respected in the new list, even if invalidation interrupts
            // the prepend / append load that would have fulfilled it in the old
            // list.
            lastAccessedIndex = transformedLastAccessedIndex
            hintReceiver?.accessHint(
                newPresenter.accessHintForPresenterIndex(
                    transformedLastAccessedIndex
                )
            )
        }
    }

    override fun onChanged(position: Int, count: Int) {
        differCallback.onChanged(position, count)
    }

    override fun onInserted(position: Int, count: Int) {
        differCallback.onInserted(position, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        differCallback.onRemoved(position, count)
    }

    // for state updates from LoadStateUpdate events
    override fun onStateUpdate(source: LoadStates, mediator: LoadStates?) {
        dispatchLoadStates(source, mediator)
    }

    // for state updates from Drop events
    override fun onStateUpdate(
        loadType: LoadType,
        fromMediator: Boolean,
        loadState: LoadState
    ) {
        val currentLoadState = combinedLoadStatesCollection.get(loadType, fromMediator)

        // No change, skip update + dispatch.
        if (currentLoadState == loadState) return

        combinedLoadStatesCollection.set(loadType, fromMediator, loadState)
    }
}

public interface DifferCallback {
    public fun onChanged(position: Int, count: Int)
    public fun onInserted(position: Int, count: Int)
    public fun onRemoved(position: Int, count: Int)
}
