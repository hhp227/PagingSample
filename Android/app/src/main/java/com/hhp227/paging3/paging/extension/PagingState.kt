package com.hhp227.paging3.paging.extension

import androidx.annotation.IntRange

// 원본이랑 동일 TODO closestItemToPosition()가 사용되고 있지 않음 LegacyPagingSource????
class PagingState<Key : Any, Value : Any>(
    val pages: List<PagingSource.LoadResult.Page<Key, Value>>,
    val anchorPosition: Int?,
    public val config: PagingConfig,
    @IntRange(from = 0)
    private val leadingPlaceholderCount: Int
) {
    override fun equals(other: Any?): Boolean {
        return other is PagingState<*, *> &&
                pages == other.pages &&
                anchorPosition == other.anchorPosition &&
                config == other.config &&
                leadingPlaceholderCount == other.leadingPlaceholderCount
    }

    override fun hashCode(): Int {
        return pages.hashCode() + anchorPosition.hashCode() + config.hashCode() +
                leadingPlaceholderCount.hashCode()
    }

    fun closestItemToPosition(anchorPosition: Int): Value? {
        if (pages.all { it.data.isEmpty() }) return null

        anchorPositionToPagedIndices(anchorPosition) { pageIndex, index ->
            val firstNonEmptyPage = pages.first { it.data.isNotEmpty() }
            val lastNonEmptyPage = pages.last { it.data.isNotEmpty() }
            return when {
                index < 0 -> firstNonEmptyPage.data.first()
                pageIndex == pages.lastIndex && index > pages.last().data.lastIndex -> {
                    lastNonEmptyPage.data.last()
                }
                else -> pages[pageIndex].data[index]
            }
        }
    }

    fun closestPageToPosition(anchorPosition: Int): PagingSource.LoadResult.Page<Key, Value>? {
        if (pages.all { it.data.isEmpty() }) return null

        anchorPositionToPagedIndices(anchorPosition) { pageIndex, index ->
            return when {
                index < 0 -> pages.first()
                else -> pages[pageIndex]
            }
        }
    }

    fun isEmpty(): Boolean = pages.all { it.data.isEmpty() }

    fun firstItemOrNull(): Value? {
        return pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
    }

    fun lastItemOrNull(): Value? {
        return pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
    }

    private inline fun <T> anchorPositionToPagedIndices(
        anchorPosition: Int,
        block: (pageIndex: Int, index: Int) -> T
    ): T {
        var pageIndex = 0
        var index = anchorPosition - leadingPlaceholderCount
        while (pageIndex < pages.lastIndex && index > pages[pageIndex].data.lastIndex) {
            index -= pages[pageIndex].data.size
            pageIndex++
        }

        return block(pageIndex, index)
    }
}