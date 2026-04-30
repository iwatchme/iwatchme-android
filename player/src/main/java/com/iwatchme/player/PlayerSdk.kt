package com.iwatchme.player

import android.content.Context
import android.util.Log
import com.iwatchme.player.feature.playerpage.di.AppComponent
import com.iwatchme.player.feature.playerpage.di.DaggerAppComponent

object PlayerSdk {

    lateinit var appComponent: AppComponent
        private set

    fun init(context: Context) {
        Log.d("Player", "[PlayerSdk] Initializing AppComponent")
        appComponent = DaggerAppComponent.factory().create(context.applicationContext)
    }
}
