package com.hhp227.paging3.paging.pager

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

internal fun <T> cancelableChannelFlow(
    controller: Job,
    block: suspend SimpleProducerScope<T>.() -> Unit
): Flow<T> {
    return simpleChannelFlow {
        controller.invokeOnCompletion {
            close()
        }
        this.block()
    }
}