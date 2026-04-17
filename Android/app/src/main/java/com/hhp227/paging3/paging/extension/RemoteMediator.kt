package com.hhp227.paging3.paging.extension

import com.hhp227.paging3.paging.LoadType

// 원본과 동일
abstract class RemoteMediator<Key : Any, Value : Any> {
    abstract suspend fun load(
        loadType: LoadType,
        state: PagingState<Key, Value>
    ): MediatorResult

    open suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    sealed class MediatorResult {
        class Error(val throwable: Throwable) : MediatorResult()

        class Success(
            @get:JvmName("endOfPaginationReached") val endOfPaginationReached: Boolean
        ) : MediatorResult()
    }

    enum class InitializeAction {
        LAUNCH_INITIAL_REFRESH,
        SKIP_INITIAL_REFRESH
    }
}
