package com.github.michaelbull.result

import arrow.continuations.generic.DelimContScope
import arrow.continuations.generic.DelimitedScope

public fun interface Just<F, V> {
    public fun just(v: V): F
}

public inline fun <V, E> ok(v: V): Result<V, E> = Ok(v)

public inline fun <V, E> bindingArrow(crossinline block: suspend ResultEffect<*, E>.() -> V): Result<V, E> =
    computation(::ok, { ResultEffect { it } }, block)

public inline fun <FV, Eff: Effect<FV>, V> computation(
    just : Just<FV, V>,
    crossinline eff: (DelimitedScope<FV>) -> Eff,
    crossinline block: suspend Eff.() -> V
): FV =
    DelimContScope.reset { just.just(block(eff(this))) }

public fun interface Effect<F> {
    public fun control(): DelimitedScope<F>
}

public fun interface ResultEffect<V, E> : Effect<Result<V, E>> {
    public suspend operator fun <B> Result<B, E>.invoke(): B =
        fold({it}, { e -> control().shift { Err(e) }})
}


// Sample

public object BindingError

public fun main() {
    fun provideX(): Result<Int, BindingError> = Ok(1)
    fun provideY(): Result<String, BindingError> = Ok("2")

    val result: Result<Int, BindingError> = bindingArrow {
        val x = provideX()
        val y = provideY()
        val z = x() + y().toInt()
        println(z)
        z
    }
}



