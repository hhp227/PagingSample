package com.hhp227.paging3.paging.extension

import androidx.annotation.IntRange
import com.hhp227.paging3.paging.extension.PagingSource.LoadResult.Page.Companion.COUNT_UNDEFINED

// 원본과 동일
class PagingConfig @JvmOverloads constructor(
    @JvmField
    public val pageSize: Int,
    @JvmField
    @IntRange(from = 0)
    public val prefetchDistance: Int = pageSize,
    @JvmField
    public val enablePlaceholders: Boolean = true,
    @JvmField
    @IntRange(from = 1)
    public val initialLoadSize: Int = pageSize * DEFAULT_INITIAL_PAGE_MULTIPLIER,
    @JvmField
    @IntRange(from = 2)
    public val maxSize: Int = MAX_SIZE_UNBOUNDED,
    @JvmField
    public val jumpThreshold: Int = COUNT_UNDEFINED
) {
    init {
        if (!enablePlaceholders && prefetchDistance == 0) {
            throw IllegalArgumentException(
                "Placeholders and prefetch are the only ways" +
                        " to trigger loading of more data in PagingData, so either placeholders" +
                        " must be enabled, or prefetch distance must be > 0."
            )
        }
        if (maxSize != MAX_SIZE_UNBOUNDED && maxSize < pageSize + prefetchDistance * 2) {
            throw IllegalArgumentException(
                "Maximum size must be at least pageSize + 2*prefetchDist" +
                        ", pageSize=$pageSize, prefetchDist=$prefetchDistance" +
                        ", maxSize=$maxSize"
            )
        }

        require(jumpThreshold == COUNT_UNDEFINED || jumpThreshold > 0) {
            "jumpThreshold must be positive to enable jumps or COUNT_UNDEFINED to disable jumping."
        }
    }

    companion object {
        @Suppress("MinMaxConstant")
        const val MAX_SIZE_UNBOUNDED: Int = Int.MAX_VALUE
        internal const val DEFAULT_INITIAL_PAGE_MULTIPLIER = 3
    }
}