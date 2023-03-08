package com.iwatchme.startuplauncher.mock

import com.iwatchme.startuplauncher.task.Task

class TaskD : Task() {
    override fun dependsOn(): List<Class<out Task>>? {
        var ret = mutableListOf<Class<out Task>>()
        ret.add(TaskB::class.java)
        ret.add(TaskC::class.java)
        return ret
    }


    override fun run() {
        Thread.sleep(100)
    }
}