package com.iwatchme.jetpackstarter.pictureEditor.emoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EmojiSheet(
    modifier: Modifier = Modifier,
    onEmojiSelected: (emoji: String) -> Unit
) {
    val emojis = listOf(
        "❤️", "🙌", "🥳️", "👏️",
        "🤩️", "👀", "🙄", "😇",
        "😂️", "😅", "🤣", "🙃"
    )
    Column(modifier = modifier) {
        emojis.chunked(4).map { emojiRow ->
            EmojiRow(
                modifier = Modifier.fillMaxWidth(),
                emojis = emojiRow,
                onSelected = onEmojiSelected
            )
        }
    }
}


@Composable
fun EmojiRow(
    modifier: Modifier,
    emojis: List<String>,
    onSelected: (emoji: String) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        emojis.forEach { emoji ->
            EmojiOption(
                text = emoji,
                onClick = onSelected
            )
        }
    }
}