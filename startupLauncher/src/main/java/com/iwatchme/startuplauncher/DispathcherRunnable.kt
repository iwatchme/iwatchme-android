package com.iwatchme.startuplauncher

import com.iwatchme.startuplauncher.task.Task


class DispathcherRunnable: Runnable {

    var task: Task
    var dispatcher: TaskDispatcher

    constructor(task: Task, dispatcher: TaskDispatcher) {
        this.task = task
        this.dispatcher = dispatcher
    }


    override fun run() {

        task.isWaiting = true
        task.waitAllDependsFinish()


        task.isRunning = true
        task.run()


        task.isFinished = true

        dispatcher.satisfyChildren(task)
        dispatcher.markTaskDone(task)

    }
}