package com.hhp227.paging3.paging

// 확인 일단 로직은 원본과 동일
sealed class LoadState(val endOfPaginationReached: Boolean) {
    class NotLoading(endOfPaginationReached: Boolean) : LoadState(endOfPaginationReached)

    object Loading : LoadState(false)

    class Error(val error: Throwable) : LoadState(false)
}
