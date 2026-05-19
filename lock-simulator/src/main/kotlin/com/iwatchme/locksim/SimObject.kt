package com.iwatchme.locksim

import java.util.concurrent.atomic.AtomicLong

class SimObject(val klass: String = "java.lang.Object") {

    val id: Long = nextId.incrementAndGet()
    val header: MarkWord = MarkWord()

    @Volatile
    var escaped: Boolean = false

    override fun toString(): String = "SimObject#$id[$klass]"

    companion object {
        private val nextId = AtomicLong(0)
    }
}
