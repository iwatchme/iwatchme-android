package com.iwatchme.player.feature.playerpage.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iwatchme.player.R

class PlayerPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_page)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PlayerPageFragment())
                .commit()
        }
    }
}
