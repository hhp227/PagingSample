package com.hhp227.paging3.paging.pager

import com.hhp227.paging3.paging.LoadState
import com.hhp227.paging3.paging.LoadStates
import com.hhp227.paging3.paging.LoadType

// 원본과 동일
internal class MutableLoadStateCollection {
    var refresh: LoadState = LoadState.NotLoading(false)
    var prepend: LoadState = LoadState.NotLoading(false)
    var append: LoadState = LoadState.NotLoading(false)

    fun snapshot() = LoadStates(
        refresh = refresh,
        prepend = prepend,
        append = append,
    )

    fun get(loadType: LoadType) = when (loadType) {
        LoadType.REFRESH -> refresh
        LoadType.APPEND -> append
        LoadType.PREPEND -> prepend
    }

    fun set(type: LoadType, state: LoadState) = when (type) {
        LoadType.REFRESH -> refresh = state
        LoadType.APPEND -> append = state
        LoadType.PREPEND -> prepend = state
    }

    fun set(states: LoadStates) {
        refresh = states.refresh
        append = states.append
        prepend = states.prepend
    }
}