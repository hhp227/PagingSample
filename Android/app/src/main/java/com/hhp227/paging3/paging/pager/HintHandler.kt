package com.hhp227.paging3.paging.pager

import com.hhp227.paging3.paging.LoadType
import com.hhp227.paging3.paging.ViewportHint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// 원본과 동일
internal class HintHandler {
    private val state = State()

    /**
     * Latest call to [processHint]. Note that this value might be ignored wrt prepend and append
     * hints if it is not expanding the range.
     */
    val lastAccessHint: ViewportHint.Access?
        get() = state.lastAccessHint

    /**
     * Returns a flow of hints for the given [loadType].
     */
    fun hintFor(loadType: LoadType): Flow<ViewportHint> = when (loadType) {
        LoadType.PREPEND -> state.prependFlow
        LoadType.APPEND -> state.appendFlow
        else -> throw IllegalArgumentException("invalid load type for hints")
    }

    /**
     * Resets the hint for the given [loadType].
     * Note that this won't update [lastAccessHint] or the other load type.
     */
    fun forceSetHint(
        loadType: LoadType,
        viewportHint: ViewportHint
    ) {
        require(
            loadType == LoadType.PREPEND || loadType == LoadType.APPEND
        ) {
            "invalid load type for reset: $loadType"
        }
        state.modify(
            accessHint = null
        ) { prependHint, appendHint ->
            if (loadType == LoadType.PREPEND) {
                prependHint.value = viewportHint
            } else {
                appendHint.value = viewportHint
            }
        }
    }

    /**
     * Processes the hint coming from UI.
     */
    fun processHint(viewportHint: ViewportHint) {
        state.modify(viewportHint as? ViewportHint.Access) { prependHint, appendHint ->
            if (viewportHint.shouldPrioritizeOver(
                    previous = prependHint.value,
                    loadType = LoadType.PREPEND
                )
            ) {
                prependHint.value = viewportHint
            }
            if (viewportHint.shouldPrioritizeOver(
                    previous = appendHint.value,
                    loadType = LoadType.APPEND
                )
            ) {
                appendHint.value = viewportHint
            }
        }
    }

    private inner class State {
        private val prepend = MutableStateFlow<ViewportHint>(ViewportHint.Initial(0, 0, 0, 0))
        private val append = MutableStateFlow<ViewportHint>(ViewportHint.Initial(0, 0, 0, 0))
        var lastAccessHint: ViewportHint.Access? = null
            private set
        val prependFlow
            get() = prepend.asStateFlow()
        val appendFlow
            get() = append.asStateFlow()
        private val lock = ReentrantLock()

        /**
         * Modifies the state inside a lock where it gets access to the mutable values.
         */
        fun modify(
            accessHint: ViewportHint.Access?,
            block: (prepend: MutableStateFlow<ViewportHint>, append: MutableStateFlow<ViewportHint>) -> Unit
        ) {
            lock.withLock {
                if (accessHint != null) {
                    lastAccessHint = accessHint
                }
                block(prepend, append)
            }
        }
    }
}

internal fun ViewportHint.shouldPrioritizeOver(
    previous: ViewportHint?,
    loadType: LoadType
): Boolean {
    return when {
        previous == null -> true
        // Prioritize Access hints over Initialize hints
        previous is ViewportHint.Initial && this is ViewportHint.Access -> true
        this is ViewportHint.Initial && previous is ViewportHint.Access -> false
        // Prioritize hints from most recent presenter state
        // not that this it not a gt/lt check because we would like to prioritize any
        // change in available pages, not necessarily more or less as drops can have an impact.
        this.originalPageOffsetFirst != previous.originalPageOffsetFirst -> true
        this.originalPageOffsetLast != previous.originalPageOffsetLast -> true
        // Prioritize hints that would load the most items
        previous.presentedItemsBeyondAnchor(loadType) <= presentedItemsBeyondAnchor(loadType) ->
            false
        else -> true
    }
}