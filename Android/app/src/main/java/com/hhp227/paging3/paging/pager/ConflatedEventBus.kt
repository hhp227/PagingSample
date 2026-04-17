package com.hhp227.paging3.paging.pager

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

// 원본과 동일
internal class ConflatedEventBus<T : Any>(initialValue: T? = null) {
    private val state = MutableStateFlow(Pair(Integer.MIN_VALUE, initialValue))

    val flow = state.mapNotNull { it.second }

    fun send(data: T) {
        state.value = Pair(state.value.first + 1, data)
    }
}