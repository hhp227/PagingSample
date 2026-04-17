package com.hhp227.paging3.paging.extension

import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting
import com.hhp227.paging3.paging.LoadType
import com.hhp227.paging3.paging.PageEvent
import com.hhp227.paging3.paging.pager.PageFetcherSnapshotState

// 원본과 조금 차이 있지만 동일
open class PagingSource<Key : Any, Value : Any> {
    private val invalidateCallbackTracker = InvalidateCallbackTracker<() -> Unit>(
        callbackInvoker = { it() }
    )

    internal val invalidateCallbackCount: Int
        @VisibleForTesting
        get() = invalidateCallbackTracker.callbackCount()

    sealed class LoadParams<Key : Any>(val loadSize: Int, val placeholdersEnabled: Boolean) {
        abstract val key: Key?

        class Refresh<Key : Any>(override val key: Key?, loadSize: Int, placeholdersEnabled: Boolean) : LoadParams<Key>(loadSize = loadSize, placeholdersEnabled = placeholdersEnabled)

        class Append<Key : Any>(override val key: Key, loadSize: Int, placeholdersEnabled: Boolean) : LoadParams<Key>(loadSize = loadSize, placeholdersEnabled = placeholdersEnabled)

        class Prepend<Key : Any>(override val key: Key, loadSize: Int, placeholdersEnabled: Boolean) : LoadParams<Key>(loadSize = loadSize, placeholdersEnabled = placeholdersEnabled)

        internal companion object {
            fun <Key : Any> create(loadType: LoadType, key: Key?, loadSize: Int, placeholdersEnabled: Boolean): LoadParams<Key> = when (loadType) {
                LoadType.REFRESH -> Refresh(key = key, loadSize = loadSize, placeholdersEnabled = placeholdersEnabled)
                LoadType.PREPEND -> Prepend(loadSize = loadSize, key = requireNotNull(key) { "key cannot be null for prepend" }, placeholdersEnabled = placeholdersEnabled)
                LoadType.APPEND -> Append(loadSize = loadSize, key = requireNotNull(key) { "key cannot be null for append" }, placeholdersEnabled = placeholdersEnabled,)
            }
        }
    }

    open class LoadResult<Key : Any, Value : Any> {
        data class Error<Key : Any, Value : Any>(val throwable: Throwable) : LoadResult<Key, Value>()
        class Invalid<Key : Any, Value : Any> : LoadResult<Key, Value>()

        data class Page<Key : Any, Value : Any> constructor(
            val data: List<Value>,
            val prevKey: Key?,
            val nextKey: Key?,
            @IntRange(from = COUNT_UNDEFINED.toLong())
            val itemsBefore: Int = COUNT_UNDEFINED,
            @IntRange(from = COUNT_UNDEFINED.toLong())
            val itemsAfter: Int = COUNT_UNDEFINED
        ) : LoadResult<Key, Value>() {
            constructor(
                data: List<Value>,
                prevKey: Key?,
                nextKey: Key?
            ) : this(data, prevKey, nextKey, COUNT_UNDEFINED, COUNT_UNDEFINED)

            init {
                require(itemsBefore == COUNT_UNDEFINED || itemsBefore >= 0) {
                    "itemsBefore cannot be negative"
                }

                require(itemsAfter == COUNT_UNDEFINED || itemsAfter >= 0) {
                    "itemsAfter cannot be negative"
                }
            }

            companion object {
                const val COUNT_UNDEFINED: Int = Int.MIN_VALUE

                private val EMPTY = Page(emptyList(), null, null, 0, 0)

                @Suppress("UNCHECKED_CAST") // Can safely ignore, since the list is empty.
                internal fun <Key : Any, Value : Any> empty() = EMPTY as Page<Key, Value>
            }
        }
    }

    open val jumpingSupported: Boolean
        get() = false

    open val keyReuseSupported: Boolean
        get() = false

    val invalid: Boolean
        get() = invalidateCallbackTracker.invalid

    fun invalidate() {
        invalidateCallbackTracker.invalidate()
    }

    fun registerInvalidatedCallback(onInvalidatedCallback: () -> Unit) {
        invalidateCallbackTracker.registerInvalidatedCallback(onInvalidatedCallback)
    }

    fun unregisterInvalidatedCallback(onInvalidatedCallback: () -> Unit) {
        invalidateCallbackTracker.unregisterInvalidatedCallback(onInvalidatedCallback)
    }

    open suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> {
        TODO()
    }

    open fun getRefreshKey(state: PagingState<Key, Value>): Key? {
        TODO()
    }
}
