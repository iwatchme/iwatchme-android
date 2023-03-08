package com.iwatchme.startuplauncher.mock

import com.iwatchme.startuplauncher.task.Task


class TaskA : Task() {
    override fun run() {
        Thread.sleep(3000)
    }
}