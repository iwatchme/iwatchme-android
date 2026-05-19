package com.iwatchme.locksim

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock

class ObjectMonitor(val target: SimObject) {

    val mutex = ReentrantLock()
    val notEmpty = mutex.newCondition()

    @Volatile
    var owner: SimThread? = null
    var recursions: Int = 0
    val entryList: ArrayDeque<SimThread> = ArrayDeque()
    val waitSet: ArrayDeque<SimThread> = ArrayDeque()
}
