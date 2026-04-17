package com.hhp227.paging3.paging

// 원본과 동일
data class CombinedLoadStates(
    val refresh: LoadState,
    val prepend: LoadState,
    val append: LoadState,
    val source: LoadStates,
    val mediator: LoadStates? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CombinedLoadStates

        if (refresh != other.refresh) return false
        if (prepend != other.prepend) return false
        if (append != other.append) return false
        if (source != other.source) return false
        if (mediator != other.mediator) return false

        return true
    }

    override fun hashCode(): Int {
        var result = refresh.hashCode()
        result = 31 * result + prepend.hashCode()
        result = 31 * result + append.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (mediator?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CombinedLoadStates(refresh=$refresh, prepend=$prepend, append=$append, " +
                "source=$source, mediator=$mediator)"
    }

    internal fun forEach(op: (LoadType, Boolean, LoadState) -> Unit) {
        source.forEach { type, state ->
            op(type, false, state)
        }
        mediator?.forEach { type, state ->
            op(type, true, state)
        }
    }
}