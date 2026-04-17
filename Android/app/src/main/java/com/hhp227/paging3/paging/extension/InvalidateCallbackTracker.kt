package com.hhp227.paging3.paging.extension

import androidx.annotation.VisibleForTesting
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// 원본과 동일
internal class InvalidateCallbackTracker<T>(
    private val callbackInvoker: (T) -> Unit,
    /**
     * User-provided override of DataSource.isInvalid
     */
    private val invalidGetter: (() -> Boolean)? = null,
) {
    private val lock = ReentrantLock()
    private val callbacks = mutableListOf<T>()
    internal var invalid = false
        private set

    @VisibleForTesting
    internal fun callbackCount() = callbacks.size

    internal fun registerInvalidatedCallback(callback: T) {
        // This isn't sufficient, but is the best we can do in cases where DataSource.isInvalid
        // is overridden, since we have no way of knowing when the result gets flipped if user
        // never calls .invalidate().
        if (invalidGetter?.invoke() == true) {
            invalidate()
        }

        if (invalid) {
            callbackInvoker(callback)
            return
        }

        var callImmediately = false
        lock.withLock {
            if (invalid) {
                callImmediately = true
            } else {
                callbacks.add(callback)
            }
        }

        if (callImmediately) {
            callbackInvoker(callback)
        }
    }

    internal fun unregisterInvalidatedCallback(callback: T) {
        lock.withLock {
            callbacks.remove(callback)
        }
    }

    internal fun invalidate(): Boolean {
        if (invalid) return false

        var callbacksToInvoke: List<T>?
        lock.withLock {
            if (invalid) return false

            invalid = true
            callbacksToInvoke = callbacks.toList()
            callbacks.clear()
        }

        callbacksToInvoke?.forEach(callbackInvoker)
        return true
    }
}