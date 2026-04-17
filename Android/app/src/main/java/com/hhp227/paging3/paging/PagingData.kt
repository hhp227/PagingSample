package com.hhp227.paging3.paging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// HintReceiver 가 추가되어야 할듯
class PagingData<T : Any> internal constructor(
    internal val flow: Flow<PageEvent<T>>,
    internal val receiver: UiReceiver,
    internal val hintReceiver: HintReceiver
) {
    companion object {
        internal val NOOP_RECEIVER = object : UiReceiver {
            override fun retry() {}

            override fun refresh() {}
        }

        internal val NOOP_HINT_RECEIVER = object : HintReceiver {
            override fun accessHint(viewportHint: ViewportHint) {}
        }

        fun <T : Any> empty(): PagingData<T> = PagingData(
            flow = flowOf(
                PageEvent.StaticList(
                    data = listOf(),
                    sourceLoadStates = null,
                    mediatorLoadStates = null,
                )
            ),
            receiver = NOOP_RECEIVER,
            hintReceiver = NOOP_HINT_RECEIVER
        )

        fun <T : Any> empty(
            sourceLoadStates: LoadStates,
            mediatorLoadStates: LoadStates? = null,
        ): PagingData<T> = PagingData(
            flow = flowOf(
                PageEvent.StaticList(
                    data = listOf(),
                    sourceLoadStates = sourceLoadStates,
                    mediatorLoadStates = mediatorLoadStates,
                )
            ),
            receiver = NOOP_RECEIVER,
            hintReceiver = NOOP_HINT_RECEIVER
        )

        fun <T : Any> from(
            data: List<T>,
        ): PagingData<T> = PagingData(
            flow = flowOf(
                PageEvent.StaticList(
                    data = data,
                    sourceLoadStates = null,
                    mediatorLoadStates = null,
                )
            ),
            receiver = NOOP_RECEIVER,
            hintReceiver = NOOP_HINT_RECEIVER
        )

        fun <T : Any> from(
            data: List<T>,
            sourceLoadStates: LoadStates,
            mediatorLoadStates: LoadStates? = null,
        ): PagingData<T> = PagingData(
            flow = flowOf(
                PageEvent.StaticList(
                    data = data,
                    sourceLoadStates = sourceLoadStates,
                    mediatorLoadStates = mediatorLoadStates,
                )
            ),
            receiver = NOOP_RECEIVER,
            hintReceiver = NOOP_HINT_RECEIVER
        )
    }
}
