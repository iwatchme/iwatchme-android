package com.iwatchme.customcoroutine.coroutine

import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

class MyContinuationIntercepter : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*>
        get() = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
       return  MyContinuation(continuation)
    }


    inner class  MyContinuation<T>(val continuation: Continuation<T>) : Continuation<T> {
        override val context: CoroutineContext
            get() = continuation.context


        override fun resumeWith(result: Result<T>) {

            thread {
                continuation.resumeWith(result)
            }

        }

    }
}