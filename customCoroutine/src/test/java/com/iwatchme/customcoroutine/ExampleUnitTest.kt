package com.iwatchme.customcoroutine

import com.iwatchme.customcoroutine.coroutine.CustomKotlinCoroutine
import com.iwatchme.customcoroutine.coroutine.Logger
import com.iwatchme.customcoroutine.coroutine.returnImmediately
import com.iwatchme.customcoroutine.coroutine.returnSuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        if (exception is CancellationException) {
            Logger("generatorJob is cancelled")
        } else {
            Logger("generatorJob is failed")
        }
    }
    @Test
    fun addition_isCorrect() = runBlocking(exceptionHandler) {
        Logger("start")
        try {
            Logger("1")
            Logger(returnSuspend())
            Logger("2")
            delay(1000)
            Logger("3")
            Logger(returnImmediately())
            Logger("4")
        } catch (e: Exception) {

        } finally {
            Logger("end")
        }

    }

    @Test
    fun testCustomCoroutine() {
        var isFinished = false
        val kotlinCoroutine =
            CustomKotlinCoroutine(object :
                Continuation<Unit> {
                override val context: CoroutineContext = EmptyCoroutineContext;

                override fun resumeWith(result: Result<Unit>) {
                    Logger("finish")
                    isFinished = true
                }

            })
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger("catch: $throwable")
        }

        kotlinCoroutine.resumeWith(Any())
        Logger("here")
        while(!isFinished) {
            Thread.sleep(100)
        }

    }

}