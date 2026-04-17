package com.hhp227.paging3.paging

// 일단 원본과 동일
sealed class ViewportHint(
    val presentedItemsBefore: Int,
    val presentedItemsAfter: Int,
    val originalPageOffsetFirst: Int,
    val originalPageOffsetLast: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ViewportHint) return false

        return presentedItemsBefore == other.presentedItemsBefore &&
                presentedItemsAfter == other.presentedItemsAfter &&
                originalPageOffsetFirst == other.originalPageOffsetFirst &&
                originalPageOffsetLast == other.originalPageOffsetLast
    }

    internal fun presentedItemsBeyondAnchor(loadType: LoadType): Int = when (loadType) {
        LoadType.REFRESH -> throw IllegalArgumentException(
            "Cannot get presentedItems for loadType: REFRESH"
        )
        LoadType.PREPEND -> presentedItemsBefore
        LoadType.APPEND -> presentedItemsAfter
    }

    override fun hashCode(): Int {
        return presentedItemsBefore.hashCode() + presentedItemsAfter.hashCode() +
                originalPageOffsetFirst.hashCode() + originalPageOffsetLast.hashCode()
    }

    class Initial(
        presentedItemsBefore: Int,
        presentedItemsAfter: Int,
        originalPageOffsetFirst: Int,
        originalPageOffsetLast: Int
    ) : ViewportHint(
        presentedItemsBefore = presentedItemsBefore,
        presentedItemsAfter = presentedItemsAfter,
        originalPageOffsetFirst = originalPageOffsetFirst,
        originalPageOffsetLast = originalPageOffsetLast,
    )

    class Access(
        val pageOffset: Int,
        val indexInPage: Int,
        presentedItemsBefore: Int,
        presentedItemsAfter: Int,
        originalPageOffsetFirst: Int,
        originalPageOffsetLast: Int
    ) : ViewportHint(
        presentedItemsBefore = presentedItemsBefore,
        presentedItemsAfter = presentedItemsAfter,
        originalPageOffsetFirst = originalPageOffsetFirst,
        originalPageOffsetLast = originalPageOffsetLast,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Access) return false

            return pageOffset == other.pageOffset &&
                    indexInPage == other.indexInPage &&
                    presentedItemsBefore == other.presentedItemsBefore &&
                    presentedItemsAfter == other.presentedItemsAfter &&
                    originalPageOffsetFirst == other.originalPageOffsetFirst &&
                    originalPageOffsetLast == other.originalPageOffsetLast
        }

        override fun hashCode(): Int {
            return super.hashCode() + pageOffset.hashCode() + indexInPage.hashCode()
        }
    }
}
