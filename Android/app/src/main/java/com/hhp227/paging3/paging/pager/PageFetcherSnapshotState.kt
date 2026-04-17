package com.hhp227.paging3.paging.pager

import android.util.Log
import androidx.annotation.CheckResult
import com.hhp227.paging3.paging.*
import com.hhp227.paging3.paging.PageEvent.Insert.Companion.Append
import com.hhp227.paging3.paging.PageEvent.Insert.Companion.Prepend
import com.hhp227.paging3.paging.PageEvent.Insert.Companion.Refresh
import com.hhp227.paging3.paging.extension.PagingConfig
import com.hhp227.paging3.paging.extension.PagingSource
import com.hhp227.paging3.paging.extension.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

// 확인했고 원본과 차이나지만 로직은 동일
internal class PageFetcherSnapshotState<Key : Any, Value : Any> private constructor(
    private val config: PagingConfig
) {
    private val _pages = mutableListOf<PagingSource.LoadResult.Page<Key, Value>>()
    internal val pages: List<PagingSource.LoadResult.Page<Key, Value>> get() = _pages
    internal var initialPageIndex = 0
        private set

    internal val storageCount
        get() = pages.sumOf { it.data.size }

    private var _placeholdersBefore = 0

    /**
     * Always greater than or equal to 0.
     */
    internal var placeholdersBefore
        get() = when {
            config.enablePlaceholders -> _placeholdersBefore
            else -> 0
        }
        set(value) {
            _placeholdersBefore = when (value) {
                PagingSource.LoadResult.Page.COUNT_UNDEFINED -> 0
                else -> value
            }
        }

    private var _placeholdersAfter = 0

    /**
     * Always greater than or equal to 0.
     */
    internal var placeholdersAfter
        get() = when {
            config.enablePlaceholders -> _placeholdersAfter
            else -> 0
        }
        set(value) {
            _placeholdersAfter = when (value) {
                PagingSource.LoadResult.Page.COUNT_UNDEFINED -> 0
                else -> value
            }
        }

    // Load generation ids used to respect cancellation in cases where suspending code continues to
    // run even after cancellation.
    private var prependGenerationId = 0
    private var appendGenerationId = 0
    private val prependGenerationIdStateFlow = MutableStateFlow(-1)
    private val appendGenerationIdStateFlow = MutableStateFlow(-1)

    internal fun generationId(loadType: LoadType): Int = when (loadType) {
        LoadType.REFRESH -> throw IllegalArgumentException("Cannot get loadId for loadType: REFRESH")
        LoadType.PREPEND -> prependGenerationId
        LoadType.APPEND -> appendGenerationId
    }

    /**
     * Cache previous ViewportHint which triggered any failed PagingSource APPEND / PREPEND that
     * we can later retry. This is so we always trigger loads based on hints, instead of having
     * two different ways to trigger.
     */
    internal val failedHintsByLoadType = mutableMapOf<LoadType, ViewportHint>()

    // Only track the local load states, remote states are injected from PageFetcher. This class
    // only tracks state within a single generation from source side.
    internal var sourceLoadStates = MutableLoadStateCollection().apply {
        // Synchronously initialize REFRESH with Loading.
        // NOTE: It is important that we do this synchronously on init, since PageFetcherSnapshot
        // expects to send this initial state immediately. It is always correct for a new
        // generation to immediately begin loading refresh, so rather than start with NotLoading
        // then updating to Loading, we simply start with Loading immediately to create less
        // churn downstream.
        set(LoadType.REFRESH, LoadState.Loading)
    }
        private set


    fun consumePrependGenerationIdAsFlow(): Flow<Int> {
        return prependGenerationIdStateFlow
            .onStart { prependGenerationIdStateFlow.emit(prependGenerationId) }
    }

    fun consumeAppendGenerationIdAsFlow(): Flow<Int> {
        return appendGenerationIdStateFlow
            .onStart { appendGenerationIdStateFlow.emit(appendGenerationId) }
    }

    internal fun toPageEvent(loadType: LoadType, page: PagingSource.LoadResult.Page<Key, Value>): PageEvent<Value> {
        val sourcePageIndex = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> 0 - initialPageIndex
            LoadType.APPEND -> pages.size - initialPageIndex - 1
        }
        val pages = listOf(TransformablePage(sourcePageIndex, page.data))
        // Mediator state is always set to null here because PageFetcherSnapshot is not responsible
        // for Mediator state. Instead, PageFetcher will inject it if there is a remote mediator.
        return when (loadType) {
            LoadType.REFRESH -> Refresh(
                pages = pages,
                placeholdersBefore = placeholdersBefore,
                placeholdersAfter = placeholdersAfter,
                sourceLoadStates = sourceLoadStates.snapshot(),
                mediatorLoadStates = null,
            )
            LoadType.PREPEND -> Prepend(
                pages = pages,
                placeholdersBefore = placeholdersBefore,
                sourceLoadStates = sourceLoadStates.snapshot(),
                mediatorLoadStates = null,
            )
            LoadType.APPEND -> Append(
                pages = pages,
                placeholdersAfter = placeholdersAfter,
                sourceLoadStates = sourceLoadStates.snapshot(),
                mediatorLoadStates = null,
            )
        }
    }

    /**
     * @return true if insert was applied, false otherwise.
     */
    @CheckResult
    fun insert(loadId: Int, loadType: LoadType, page: PagingSource.LoadResult.Page<Key, Value>): Boolean {
        when (loadType) {
            LoadType.REFRESH -> {
                check(pages.isEmpty()) { "cannot receive multiple init calls" }
                check(loadId == 0) { "init loadId must be the initial value, 0" }

                _pages.add(page)
                initialPageIndex = 0
                placeholdersAfter = page.itemsAfter
                placeholdersBefore = page.itemsBefore
            }
            LoadType.PREPEND -> {
                check(pages.isNotEmpty()) { "should've received an init before prepend" }

                // Skip this insert if it is the result of a cancelled job due to page drop
                if (loadId != prependGenerationId) return false

                _pages.add(0, page)
                initialPageIndex++
                placeholdersBefore = if (page.itemsBefore == PagingSource.LoadResult.Page.COUNT_UNDEFINED) {
                    max(placeholdersBefore - page.data.size, 0)
                } else {
                    page.itemsBefore
                }

                // Clear error on successful insert
                failedHintsByLoadType.remove(LoadType.PREPEND)
            }
            LoadType.APPEND -> {
                check(pages.isNotEmpty()) { "should've received an init before append" }

                // Skip this insert if it is the result of a cancelled job due to page drop
                if (loadId != appendGenerationId) return false

                _pages.add(page)
                placeholdersAfter = if (page.itemsAfter == PagingSource.LoadResult.Page.COUNT_UNDEFINED) {
                    max(placeholdersAfter - page.data.size, 0)
                } else {
                    page.itemsAfter
                }

                // Clear error on successful insert
                failedHintsByLoadType.remove(LoadType.APPEND)
            }
        }
        return true
    }

    fun drop(event: PageEvent.Drop<Value>) {
        check(event.pageCount <= pages.size) {
            "invalid drop count. have ${pages.size} but wanted to drop ${event.pageCount}"
        }

        // Reset load state to NotLoading(endOfPaginationReached = false).
        failedHintsByLoadType.remove(event.loadType)
        sourceLoadStates.set(event.loadType, LoadState.NotLoading(false))

        when (event.loadType) {
            LoadType.PREPEND -> {
                repeat(event.pageCount) { _pages.removeAt(0) }
                initialPageIndex -= event.pageCount

                placeholdersBefore = event.placeholdersRemaining

                prependGenerationId++
                prependGenerationIdStateFlow.value = prependGenerationId
            }
            LoadType.APPEND -> {
                repeat(event.pageCount) { _pages.removeAt(pages.size - 1) }

                placeholdersAfter = event.placeholdersRemaining

                appendGenerationId++
                appendGenerationIdStateFlow.value = appendGenerationId
            }
            else -> throw IllegalArgumentException("cannot drop ${event.loadType}")
        }
    }

    fun dropEventOrNull(loadType: LoadType, hint: ViewportHint): PageEvent.Drop<Value>? {
        if (config.maxSize == Int.MAX_VALUE) return null
        // Never drop below 2 pages as this can cause UI flickering with certain configs and it's
        // much more important to protect against this behaviour over respecting a config where
        // maxSize is set unusually (probably incorrectly) strict.
        if (pages.size <= 2) return null

        if (storageCount <= config.maxSize) return null

        require(loadType != LoadType.REFRESH) {
            "Drop LoadType must be PREPEND or APPEND, but got $loadType"
        }

        // Compute pageCount and itemsToDrop
        var pagesToDrop = 0
        var itemsToDrop = 0
        while (pagesToDrop < pages.size && storageCount - itemsToDrop > config.maxSize) {
            val pageSize = when (loadType) {
                LoadType.PREPEND -> pages[pagesToDrop].data.size
                else -> pages[pages.lastIndex - pagesToDrop].data.size
            }
            val itemsAfterDrop = when (loadType) {
                LoadType.PREPEND -> hint.presentedItemsBefore - itemsToDrop - pageSize
                else -> hint.presentedItemsAfter - itemsToDrop - pageSize
            }
            // Do not drop pages that would fulfill prefetchDistance.
            if (itemsAfterDrop < config.prefetchDistance) break

            itemsToDrop += pageSize
            pagesToDrop++
        }

        return when (pagesToDrop) {
            0 -> null
            else -> PageEvent.Drop(
                loadType = loadType,
                minPageOffset = when (loadType) {
                    // originalPageOffset of the first page.
                    LoadType.PREPEND -> -initialPageIndex
                    // maxPageOffset - pagesToDrop; We subtract one from pagesToDrop, since this
                    // value is inclusive.
                    else -> pages.lastIndex - initialPageIndex - (pagesToDrop - 1)
                },
                maxPageOffset = when (loadType) {
                    // minPageOffset + pagesToDrop; We subtract on from pagesToDrop, since this
                    // value is inclusive.
                    LoadType.PREPEND -> (pagesToDrop - 1) - initialPageIndex
                    // originalPageOffset of the last page.
                    else -> pages.lastIndex - initialPageIndex
                },
                placeholdersRemaining = when {
                    !config.enablePlaceholders -> 0
                    loadType == LoadType.PREPEND -> placeholdersBefore + itemsToDrop
                    else -> placeholdersAfter + itemsToDrop
                }
            )
        }
    }

    internal fun currentPagingState(viewportHint: ViewportHint.Access?) = PagingState(
        pages = pages.toList(),
        anchorPosition = viewportHint?.let { hint ->
            // Translate viewportHint to anchorPosition based on fetcher state (pre-transformation),
            // so start with fetcher count of placeholdersBefore.
            var anchorPosition = placeholdersBefore

            // Compute fetcher state pageOffsets.
            val fetcherPageOffsetFirst = -initialPageIndex
            val fetcherPageOffsetLast = pages.lastIndex - initialPageIndex

            // ViewportHint is based off of presenter state, which may race with fetcher state.
            // Since computing anchorPosition relies on hint.indexInPage, which accounts for
            // placeholders in presenter state, we need iterate through pages to incrementally
            // build anchorPosition and adjust the value we use for placeholdersBefore accordingly.
            for (pageOffset in fetcherPageOffsetFirst until hint.pageOffset) {
                // Aside from incrementing anchorPosition normally using the loaded page's
                // size, there are 4 race-cases to consider:
                //   - Fetcher has extra PREPEND pages
                //     - Simply add the size of the loaded page to anchorPosition to sync with
                //       presenter; don't need to do anything special to handle this.
                //   - Fetcher is missing PREPEND pages
                //     - Already accounted for in placeholdersBefore; so don't need to do anything.
                //   - Fetcher has extra APPEND pages
                //     - Already accounted for in hint.indexInPage (value can be greater than
                //     page size to denote placeholders access).
                //   - Fetcher is missing APPEND pages
                //     - Increment anchorPosition using config.pageSize to estimate size of the
                //     missing page.
                anchorPosition += when {
                    // Fetcher is missing APPEND pages, i.e., viewportHint points to an item
                    // after a page that was dropped. Estimate how much to increment anchorPosition
                    // by using PagingConfig.pageSize.
                    pageOffset > fetcherPageOffsetLast -> config.pageSize
                    // pageOffset refers to a loaded page; increment anchorPosition with data.size.
                    else -> pages[pageOffset + initialPageIndex].data.size
                }
            }

            // Handle the page referenced by hint.pageOffset. Increment anchorPosition by
            // hint.indexInPage, which accounts for placeholders and may not be within the bounds
            // of page.data.indices.
            anchorPosition += hint.indexInPage

            // In the special case where viewportHint references a missing PREPEND page, we need
            // to decrement anchorPosition using config.pageSize as an estimate, otherwise we
            // would be double counting it since it's accounted for in both indexInPage and
            // placeholdersBefore.
            if (hint.pageOffset < fetcherPageOffsetFirst) {
                anchorPosition -= config.pageSize
            }

            return@let anchorPosition
        },
        config = config,
        leadingPlaceholderCount = placeholdersBefore
    )

    /**
     * Wrapper for [PageFetcherSnapshotState], which protects access behind a [Mutex] to prevent
     * race scenarios.
     */
    @Suppress("SyntheticAccessor")
    internal class Holder<Key : Any, Value : Any>(
        config: PagingConfig
    ) {
        private val lock = ReentrantLock()
        private val state = PageFetcherSnapshotState<Key, Value>(config)

        inline fun <T> withLock(
            block: (state: PageFetcherSnapshotState<Key, Value>) -> T
        ): T {
            return lock.withLock {
                block(state)
            }
        }
    }
}
