package com.iwatchme.jetpackstarter.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       setContent {
           Home(modifier = Modifier)
       }




    }
}
