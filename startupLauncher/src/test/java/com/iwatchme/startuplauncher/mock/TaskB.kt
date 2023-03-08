package com.iwatchme.startuplauncher.mock

import com.iwatchme.startuplauncher.task.Task

class TaskB : Task(){
    override fun dependsOn(): List<Class<out Task>>? {
        var ret = mutableListOf<Class<out Task>>()
        ret.add(TaskC::class.java)
        ret.add(TaskA::class.java)
        return ret
    }


    override fun run() {
        Thread.sleep(5000)
    }

}