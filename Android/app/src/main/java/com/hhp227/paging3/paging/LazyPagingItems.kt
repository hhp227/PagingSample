package com.hhp227.paging3.paging

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

class LazyPagingItems<T : Any> internal constructor(
    private val flow: Flow<PagingData<T>>
) : DifferCallback {
    var itemSnapshotList by mutableStateOf(
        ItemSnapshotList<T>(0, 0, emptyList())
    )
        private set

    val itemCount: Int get() = pagingDataDiffer.size

    private val pagingDataDiffer = PagingDataDiffer<T>(
        differCallback = this,
        updateItemSnapshotList = ::updateItemSnapshotList
    )

    private fun updateItemSnapshotList() {
        itemSnapshotList = pagingDataDiffer.snapshot()
    }

    operator fun get(index: Int): T? {
        pagingDataDiffer[index] // this registers the value load
        return itemSnapshotList[index]
    }

    fun peek(index: Int): T? {
        return itemSnapshotList[index]
    }

    fun retry() {
        pagingDataDiffer.retry()
    }

    fun refresh() {
        pagingDataDiffer.refresh()
    }

    var loadState: CombinedLoadStates by mutableStateOf(
        CombinedLoadStates(
            refresh = InitialLoadStates.refresh,
            prepend = InitialLoadStates.prepend,
            append = InitialLoadStates.append,
            source = InitialLoadStates
        )
    )
        private set

    internal suspend fun collectLoadState() {
        pagingDataDiffer.loadStateFlow.filterNotNull().collect {
            loadState = it
        }
    }

    internal suspend fun collectPagingData() {
        flow.onStart { Log.e("IDIS_TEST", "TEST3") }.collectLatest(pagingDataDiffer::collectFrom)
    }

    override fun onChanged(position: Int, count: Int) {
        if (count > 0) {
            updateItemSnapshotList()
        }
    }

    override fun onInserted(position: Int, count: Int) {
        if (count > 0) {
            updateItemSnapshotList()
        }
    }

    override fun onRemoved(position: Int, count: Int) {
        if (count > 0) {
            updateItemSnapshotList()
        }
    }
}

private val InitialLoadStates = LoadStates(
    LoadState.Loading,
    LoadState.NotLoading(false),
    LoadState.NotLoading(false)
)

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(): LazyPagingItems<T> {
    val lazyPagingItems = remember(this) { LazyPagingItems(this) }

    LaunchedEffect(lazyPagingItems) {
        lazyPagingItems.collectPagingData()
    }
    LaunchedEffect(lazyPagingItems) {
        lazyPagingItems.collectLoadState()
    }
    return lazyPagingItems
}

fun <T : Any> LazyListScope.items(
    items: LazyPagingItems<T>,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(value: T?) -> Unit
) {
    Log.e("TEST", "??? ${items.itemCount}")
    items(
        count = items.itemCount,
        key = if (key == null) null else { index ->
            val item = items.peek(index)
            if (item == null) {
                PagingPlaceholderKey(index)
            } else {
                key(item)
            }
        }
    ) { index ->
        itemContent(items[index])
    }
}

fun <T : Any> LazyListScope.itemsIndexed(
    items: LazyPagingItems<T>,
    key: ((index: Int, item: T) -> Any)? = null,
    itemContent: @Composable LazyItemScope.(index: Int, value: T?) -> Unit
) {
    items(
        count = items.itemCount,
        key = if (key == null) null else { index ->
            val item = items.peek(index)
            if (item == null) {
                PagingPlaceholderKey(index)
            } else {
                key(index, item)
            }
        }
    ) { index ->
        itemContent(index, items[index])
    }
}

private data class PagingPlaceholderKey(private val index: Int) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(index)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @Suppress("unused")
        @JvmField
        val CREATOR: Parcelable.Creator<PagingPlaceholderKey> =
            object : Parcelable.Creator<PagingPlaceholderKey> {
                override fun createFromParcel(parcel: Parcel) =
                    PagingPlaceholderKey(parcel.readInt())

                override fun newArray(size: Int) = arrayOfNulls<PagingPlaceholderKey?>(size)
            }
    }
}