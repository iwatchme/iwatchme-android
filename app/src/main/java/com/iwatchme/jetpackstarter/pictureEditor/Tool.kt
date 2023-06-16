package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

@Composable
fun Tool(
    modifier: Modifier = Modifier,
    tool: EditorTool,
    onSelected: (tool: EditorTool) -> Unit
) {
    IconButton(onClick = { onSelected(tool) }) {
        Icon(
            modifier = modifier,
            imageVector = tool.icon,
            contentDescription = stringResource(id = tool.description),
            tint = Color.White
        )
    }
}