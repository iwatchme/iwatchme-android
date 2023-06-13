package com.iwatchme.jetpackstarter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import com.iwatchme.jetpackstarter.emailbox.Email
import com.iwatchme.jetpackstarter.emailbox.Inbox
import com.iwatchme.jetpackstarter.gallery.Gallery
import com.iwatchme.jetpackstarter.home.Home
import com.iwatchme.jetpackstarter.pictureEditor.Stories
import com.iwatchme.jetpackstarter.settings.SettingsScreen
import com.iwatchme.jetpackstarter.video.Video


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       setContent {
          Stories()
       }




    }
}
