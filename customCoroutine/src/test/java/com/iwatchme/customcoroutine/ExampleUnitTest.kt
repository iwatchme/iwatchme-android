package com.iwatchme.customcoroutine

import com.iwatchme.customcoroutine.coroutine.KotlinCoroutineImpl
import com.iwatchme.customcoroutine.coroutine.Logger
import com.iwatchme.customcoroutine.coroutine.RunSuspend
import com.iwatchme.customcoroutine.coroutine.returnImmediately
import com.iwatchme.customcoroutine.coroutine.returnSuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

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
}