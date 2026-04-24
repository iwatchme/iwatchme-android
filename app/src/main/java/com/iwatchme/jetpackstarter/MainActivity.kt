package com.iwatchme.jetpackstarter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iwatchme.jetpackstarter.shell.JetpackStarterApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JetpackStarterApp()
        }
        reportFullyDrawn()
    }
}
