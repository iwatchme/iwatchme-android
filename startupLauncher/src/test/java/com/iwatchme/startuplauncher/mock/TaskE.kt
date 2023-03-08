package com.iwatchme.startuplauncher.mock

import com.iwatchme.startuplauncher.task.Task


class TaskE : Task() {


    override fun run() {
        Thread.sleep(10000)
    }
}