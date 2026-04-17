package com.hhp227.paging3.paging

import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArrayList

// 원본과 동일
internal class MutableCombinedLoadStateCollection {

    private val listeners = CopyOnWriteArrayList<(CombinedLoadStates) -> Unit>()
    private val _stateFlow = MutableStateFlow<CombinedLoadStates?>(null)
    public val stateFlow = _stateFlow.asStateFlow()

    fun set(sourceLoadStates: LoadStates, remoteLoadStates: LoadStates?) =
        dispatchNewState { currState ->
            computeNewState(currState, sourceLoadStates, remoteLoadStates)
        }

    fun set(type: LoadType, remote: Boolean, state: LoadState) =
        dispatchNewState { currState ->
            var source = currState?.source ?: LoadStates.IDLE
            var mediator = currState?.mediator ?: LoadStates.IDLE

            if (remote) {
                mediator = mediator.modifyState(type, state)
            } else {
                source = source.modifyState(type, state)
            }
            computeNewState(currState, source, mediator)
        }

    fun get(type: LoadType, remote: Boolean): LoadState? {
        val state = _stateFlow.value
        return (if (remote) state?.mediator else state?.source)?.get(type)
    }

    /**
     * When a new listener is added, it will be immediately called with the current
     * [CombinedLoadStates] unless no state has been set yet, and thus has no valid state to emit.
     */
    fun addListener(listener: (CombinedLoadStates) -> Unit) {
        // Note: Important to add the listener first before sending off events, in case the
        // callback triggers removal, which could lead to a leak if the listener is added
        // afterwards.
        listeners.add(listener)
        _stateFlow.value?.also { listener(it) }
    }

    fun removeListener(listener: (CombinedLoadStates) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Computes and dispatches the new CombinedLoadStates. No-op if new value is same as
     * previous value.
     *
     * We manually de-duplicate emissions to StateFlow and to listeners even though
     * [MutableStateFlow.update] de-duplicates automatically in that duplicated values are set but
     * not sent to collectors. However it doesn't indicate whether the new value is indeed a
     * duplicate or not, so we still need to manually compare previous/updated values before
     * sending to listeners. Because of that, we manually de-dupe both stateFlow and listener
     * emissions to ensure they are in sync.
     */
    private fun dispatchNewState(
        computeNewState: (currState: CombinedLoadStates?) -> CombinedLoadStates
    ) {
        var newState: CombinedLoadStates? = null
        _stateFlow.update { currState ->
            val computed = computeNewState(currState)
            if (currState != computed) {
                newState = computed
                computed
            } else {
                // no-op, doesn't dispatch
                return
            }
        }
        newState?.apply { listeners.forEach { it(this) } }
    }

    private fun computeNewState(
        previousState: CombinedLoadStates?,
        newSource: LoadStates,
        newRemote: LoadStates?
    ): CombinedLoadStates {
        val refresh = computeHelperState(
            previousState = previousState?.refresh ?: LoadState.NotLoading(false),
            sourceRefreshState = newSource.refresh,
            sourceState = newSource.refresh,
            remoteState = newRemote?.refresh

        )
        val prepend = computeHelperState(
            previousState = previousState?.prepend ?: LoadState.NotLoading(false),
            sourceRefreshState = newSource.refresh,
            sourceState = newSource.prepend,
            remoteState = newRemote?.prepend
        )
        val append = computeHelperState(
            previousState = previousState?.append ?: LoadState.NotLoading(false),
            sourceRefreshState = newSource.refresh,
            sourceState = newSource.append,
            remoteState = newRemote?.append
        )

        return CombinedLoadStates(
            refresh = refresh,
            prepend = prepend,
            append = append,
            source = newSource,
            mediator = newRemote,
        )
    }

    /**
     * Computes the next value for the convenience helpers in [CombinedLoadStates], which
     * generally defers to remote state, but waits for both source and remote states to become
     * [NotLoading] before moving to that state. This provides a reasonable default for the common
     * use-case where you generally want to wait for both RemoteMediator to return and for the
     * update to get applied before signaling to UI that a network fetch has "finished".
     */
    private fun computeHelperState(
        previousState: LoadState,
        sourceRefreshState: LoadState,
        sourceState: LoadState,
        remoteState: LoadState?
    ): LoadState {
        if (remoteState == null) return sourceState

        return when (previousState) {
            is LoadState.Loading -> when {
                sourceRefreshState is LoadState.NotLoading && remoteState is LoadState.NotLoading -> remoteState
                remoteState is Error -> remoteState
                else -> previousState
            }
            else -> remoteState
        }
    }
}


// 너무 다름
/*internal class MutableCombinedLoadStateCollection {
    private var isInitialized: Boolean = false
    private val listeners = CopyOnWriteArrayList<(CombinedLoadStates) -> Unit>()

    private var refresh: LoadState = LoadState.NotLoading(false)
    private var prepend: LoadState = LoadState.NotLoading(false)
    private var append: LoadState = LoadState.NotLoading(false)
    var source: LoadStates = LoadStates.IDLE
        private set
    var mediator: LoadStates? = null
        private set

    private val _stateFlow = MutableStateFlow<CombinedLoadStates?>(null)
    val flow: Flow<CombinedLoadStates> get() = _stateFlow.filterNotNull()

    fun set(sourceLoadStates: LoadStates, remoteLoadStates: LoadStates?) {
        isInitialized = true
        source = sourceLoadStates
        mediator = remoteLoadStates

        updateHelperStatesAndDispatch()
    }

    fun set(type: LoadType, remote: Boolean, state: LoadState): Boolean {
        isInitialized = true
        val didChange = if (remote) {
            val lastMediator = mediator
            mediator = (mediator ?: LoadStates.IDLE).modifyState(type, state)
            mediator != lastMediator
        } else {
            val lastSource = source
            source = source.modifyState(type, state)
            source != lastSource
        }

        updateHelperStatesAndDispatch()
        return didChange
    }

    fun get(type: LoadType, remote: Boolean): LoadState? {
        return (if (remote) mediator else source)?.get(type)
    }

    fun addListener(listener: (CombinedLoadStates) -> Unit) {
        listeners.add(listener)
        snapshot()?.also { listener(it) }
    }

    fun removeListener(listener: (CombinedLoadStates) -> Unit) {
        listeners.remove(listener)
    }

    private fun snapshot(): CombinedLoadStates? = when {
        !isInitialized -> null
        else -> CombinedLoadStates(
            refresh = refresh,
            prepend = prepend,
            append = append,
            source = source,
            mediator = mediator
        )
    }

    private fun updateHelperStatesAndDispatch() {
        refresh = computeHelperState(
            previousState = refresh,
            sourceRefreshState = source.refresh,
            sourceState = source.refresh,
            remoteState = mediator?.refresh
        )
        prepend = computeHelperState(
            previousState = prepend,
            sourceRefreshState = source.refresh,
            sourceState = source.prepend,
            remoteState = mediator?.prepend
        )
        append = computeHelperState(
            previousState = append,
            sourceRefreshState = source.refresh,
            sourceState = source.append,
            remoteState = mediator?.append
        )

        val snapshot = snapshot()
        if (snapshot != null) {
            _stateFlow.value = snapshot
            listeners.forEach { it(snapshot) }
        }
    }

    private fun computeHelperState(
        previousState: LoadState,
        sourceRefreshState: LoadState,
        sourceState: LoadState,
        remoteState: LoadState?
    ): LoadState {
        if (remoteState == null) return sourceState

        return when (previousState) {
            is LoadState.Loading -> when {
                sourceRefreshState is LoadState.NotLoading && remoteState is LoadState.NotLoading -> remoteState
                remoteState is LoadState.Error -> remoteState
                else -> previousState
            }
            else -> remoteState
        }
    }
}
*/