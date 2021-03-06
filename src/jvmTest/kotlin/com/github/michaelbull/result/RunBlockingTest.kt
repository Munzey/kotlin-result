package com.github.michaelbull.result

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

actual fun runBlockingTest(block: suspend (scope : CoroutineScope) -> Unit) = runBlocking { block(this) }
