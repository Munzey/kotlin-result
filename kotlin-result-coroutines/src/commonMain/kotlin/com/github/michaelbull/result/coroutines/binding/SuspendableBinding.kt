package com.github.michaelbull.result.coroutines.binding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Suspending variant of [binding][com.github.michaelbull.result.binding].
 * Wraps the suspendable block in a new coroutine scope.
 * This scope is cancelled once a failing bind is encountered, allowing deferred child jobs to be eagerly cancelled.
 */
public suspend inline fun <V, E> binding(crossinline block: suspend SuspendableResultBinding<E>.() -> V): Result<V, E> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val receiver = SuspendableResultBindingImpl<E>()

    return try {
        coroutineScope {
            receiver.coroutineScope = this@coroutineScope
            with(receiver) { Ok(block()) }
        }
    } catch (ex: BindCancellationException) {
        receiver.internalError
    }
}

internal object BindCancellationException : CancellationException(null)

public interface SuspendableResultBinding<E> {
    public suspend fun <V> Result<V, E>.bind(): V
}

@PublishedApi
internal class SuspendableResultBindingImpl<E> : SuspendableResultBinding<E> {

    private val mutex = Mutex()
    lateinit var internalError: Err<E>
    var coroutineScope: CoroutineScope? = null

    override suspend fun <V> Result<V, E>.bind(): V {
        return when (this) {
            is Ok -> value
            is Err -> {
                mutex.withLock {
                    if (::internalError.isInitialized.not()) {
                        internalError = this
                    }
                }
                coroutineScope?.cancel(BindCancellationException)
                throw BindCancellationException
            }
        }
    }
}
