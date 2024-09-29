package com.iwatchme.jetpackstarter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.iwatchme.jetpackstarter.gallery.Gallery
import com.iwatchme.jetpackstarter.pictureEditor.Stories


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gallery()
        }


    }
}
