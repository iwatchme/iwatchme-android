package com.iwatchme.jetpackstarter

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.iwatchme.jetpackstarter.pictureEditor.Stories


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThreadTestUtils().test2()
        setContent {
//          Stories()
//            Blog(
//                modifier = Modifier.fillMaxSize(),
//                posts = PostFactory.makePosts(),
//            )
            Stories()
        }


    }
}
