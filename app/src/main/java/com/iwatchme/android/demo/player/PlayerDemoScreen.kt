package com.iwatchme.android.demo.player

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iwatchme.player.feature.playerpage.ui.PlayerPageActivity

@Composable
fun PlayerDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Player Demo")
        Text(text = "Multi-scope Dagger 2 architecture with ExoPlayer (Media3). Demonstrates PageScope / BizScope / MediaScope hierarchy with Flow-driven scope switching.")

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                context.startActivity(Intent(context, PlayerPageActivity::class.java))
            },
        ) {
            Text(text = "Launch Player")
        }
    }
}
