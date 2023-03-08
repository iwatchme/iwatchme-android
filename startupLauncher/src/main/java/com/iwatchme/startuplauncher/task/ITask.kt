package com.iwatchme.startuplauncher.task

import java.util.concurrent.ExecutorService

interface ITask {



    fun runOn() : ExecutorService

    // 依赖的其他class
    fun dependsOn(): List<Class<out Task>>?

    // 执行任务
    fun run()

    //是否需要在调用await的时候等待
    fun needWait() : Boolean{
       return  false
    }


    // 只在主线程上执行
    fun onlyRunOnMainThread():Boolean


    // 只在主进程上执行
    fun onlyRunOnMainProcess():Boolean



}