package com.ksxkq.cmm_clicker.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    val latch = CountDownLatch(1)
    block.startCoroutine(
        object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(resumeResult: Result<T>) {
                result = resumeResult
                latch.countDown()
            }
        },
    )
    val completed = latch.await(3, TimeUnit.SECONDS)
    check(completed) { "Suspend block timeout" }
    return result!!.getOrThrow()
}
