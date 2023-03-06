package com.iwatchme.customcoroutine.coroutine

import kotlin.concurrent.thread
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume


suspend fun returnSuspend() = suspendCoroutineUninterceptedOrReturn<String> {
    Logger("11")

    thread {
        Thread.sleep(1000)
        Logger("12")
        it.resume("return suspend")
    }


    Logger("13")

    COROUTINE_SUSPENDED

}
suspend fun returnImmediately(): String = suspendCoroutineUninterceptedOrReturn {
    "return immediately"
}

fun Logger(content:String) {
    println(content +"++++" + Thread.currentThread().name);
}
