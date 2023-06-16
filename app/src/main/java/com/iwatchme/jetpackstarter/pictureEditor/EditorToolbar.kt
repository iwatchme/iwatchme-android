package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun EditorToolBar(
    modifier: Modifier,
    onToolSelected: (tool: EditorTool) -> Unit,
    closeEditor: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { closeEditor() }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Tool(
            tool = EditorTool.TextTool,
            onSelected = onToolSelected
        )
        Tool(
            tool = EditorTool.BrushTool,
            onSelected = onToolSelected
        )
        Tool(
            tool = EditorTool.EmojiTool,
            onSelected = onToolSelected
        )
    }
}