package com.iwatchme.jetpackstarter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import com.iwatchme.jetpackstarter.home.Home


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       setContent {
           Home(modifier = Modifier)
       }




    }
}
