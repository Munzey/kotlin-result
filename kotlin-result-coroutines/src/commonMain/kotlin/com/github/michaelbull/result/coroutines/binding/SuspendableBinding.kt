package com.github.michaelbull.result.coroutines.binding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.cancel
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * Suspending variant of [binding][com.github.michaelbull.result.binding].
 * The suspendable block runs in a new Coroutine Scope inheriting the parent coroutine context.
 * This new scope is cancelled once a failing bind is encountered, eagerly cancelling all children.
 */
public suspend inline fun <V, E> binding(crossinline block: suspend SuspendableResultBinding<E>.() -> V): Result<V, E> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    lateinit var receiver: SuspendableResultBindingImpl<E>
    return try {
        coroutineScope {
            receiver = SuspendableResultBindingImpl(this.coroutineContext)
            with(receiver) { Ok(block()) }
        }
    } catch (ex: BindCancellationException) {
        receiver.internalError
    }
}

internal object BindCancellationException : CancellationException(null)

public interface SuspendableResultBinding<E> : CoroutineScope {
    public suspend fun <V> Result<V, E>.bind(): V
}

@PublishedApi
internal class SuspendableResultBindingImpl<E>(
    override val coroutineContext: CoroutineContext
) : SuspendableResultBinding<E> {

    private val _internalError = atomic<Err<E>?>(null)
    val internalError: Err<E>
        get() = _internalError.value!!

    override suspend fun <V> Result<V, E>.bind(): V {
        return suspendCancellableCoroutine {
            when (this) {
                is Ok -> it.resume(value)
                is Err -> {
                    _internalError.compareAndSet(null, this)
                    this@SuspendableResultBindingImpl.cancel(BindCancellationException)
                }
            }
        }
    }
}
