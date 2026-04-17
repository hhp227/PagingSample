package com.hhp227.paging3.paging

// 원본과 동일
data class LoadStates(
    val refresh: LoadState,
    val prepend: LoadState,
    val append: LoadState
) {
    inline fun forEach(op: (LoadType, LoadState) -> Unit) {
        op(LoadType.REFRESH, refresh)
        op(LoadType.PREPEND, prepend)
        op(LoadType.APPEND, append)
    }

    internal fun modifyState(loadType: LoadType, newState: LoadState): LoadStates {
        return when (loadType) {
            LoadType.APPEND -> copy(
                append = newState
            )
            LoadType.PREPEND -> copy(
                prepend = newState
            )
            LoadType.REFRESH -> copy(
                refresh = newState
            )
        }
    }

    internal fun get(loadType: LoadType) = when (loadType) {
        LoadType.REFRESH -> refresh
        LoadType.APPEND -> append
        LoadType.PREPEND -> prepend
    }

    internal companion object {
        val IDLE = LoadStates(
            refresh = LoadState.NotLoading(false),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false)
        )
    }
}