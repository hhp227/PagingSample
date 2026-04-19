package com.hhp227.paging3.paging

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executor

private inline fun <T : Any, R : Any> PagingData<T>.transform(
    crossinline transform: suspend (PageEvent<T>) -> PageEvent<R>
): PagingData<R> {
    return PagingData(
        flow = flow.map { Log.e("IDIS_TEST", "map: $it");transform(it) },
        receiver = receiver,
        hintReceiver = hintReceiver
    )
}

public fun <T : Any, R : Any> PagingData<T>.map(
    transform: suspend (T) -> R
): PagingData<R> = transform { it.map(transform) }

public fun <T : Any, R : Any> PagingData<T>.map(
    executor: Executor,
    transform: (T) -> R,
): PagingData<R> = transform { event ->
    withContext(executor.asCoroutineDispatcher()) {
        event.map { transform(it) }
    }
}

public fun <T : Any, R : Any> PagingData<T>.flatMap(
    transform: suspend (T) -> Iterable<R>
): PagingData<R> = transform { it.flatMap(transform) }

public fun <T : Any, R : Any> PagingData<T>.flatMap(
    executor: Executor,
    transform: (T) -> Iterable<R>
): PagingData<R> = transform { event ->
    withContext(executor.asCoroutineDispatcher()) {
        event.flatMap { transform(it) }
    }
}

public fun <T : Any> PagingData<T>.filter(
    predicate: suspend (T) -> Boolean
): PagingData<T> {
    return transform { it.filter(predicate) }
}

public fun <T : Any> PagingData<T>.filter(
    executor: Executor,
    predicate: (T) -> Boolean
): PagingData<T> = transform { event ->
    withContext(executor.asCoroutineDispatcher()) {
        event.filter { predicate(it) }
    }
}

/*public fun <T : R, R : Any> PagingData<T>.insertSeparators(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    generator: suspend (T?, T?) -> R?,
): PagingData<R> {
    // This function must be an extension method, as it indirectly imposes a constraint on
    // the type of T (because T extends R). Ideally it would be declared not be an
    // extension, to make this method discoverable for Java callers, but we need to support
    // the common UI model pattern for separators:
    //     class UiModel
    //     class ItemModel: UiModel
    //     class SeparatorModel: UiModel
    return PagingData(
        flow = flow.insertEventSeparators(terminalSeparatorType, generator),
        receiver = receiver
    )
}

public fun <R : Any, T : R> PagingData<T>.insertSeparators(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    executor: Executor,
    generator: (T?, T?) -> R?,
): PagingData<R> {
    return insertSeparators(terminalSeparatorType) { before, after ->
        withContext(executor.asCoroutineDispatcher()) {
            generator(before, after)
        }
    }
}

public fun <T : Any> PagingData<T>.insertHeaderItem(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    item: T,
): PagingData<T> = insertSeparators(terminalSeparatorType) { before, _ ->
    if (before == null) item else null
}

public fun <T : Any> PagingData<T>.insertFooterItem(
    terminalSeparatorType: TerminalSeparatorType = FULLY_COMPLETE,
    item: T,
): PagingData<T> = insertSeparators(terminalSeparatorType) { _, after ->
    if (after == null) item else null
}
*/