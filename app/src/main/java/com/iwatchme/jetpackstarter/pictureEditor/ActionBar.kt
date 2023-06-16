package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.iwatchme.jetpackstarter.R

@Composable
fun ActionsBar(
    modifier: Modifier = Modifier,
    selectedTool: EditorTool?,
    drawingObjects: List<EditorObject>?,
    handleEvent: (event: EditorEvent) -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        if (selectedTool == null) {
            EditorToolBar(
                modifier = Modifier.fillMaxWidth(),
                onToolSelected = {
                    handleEvent(EditorEvent.ToolSelected(it))
                },
                closeEditor = {
                    handleEvent(EditorEvent.CloseEditor)
                }
            )
        } else if (selectedTool is EditorTool.BrushTool) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!drawingObjects.isNullOrEmpty()) {
                    IconButton(onClick = {
                        handleEvent(EditorEvent.Undo)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "",
                            tint = Color.White
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    handleEvent(EditorEvent.UnselectTool)
                }) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "",
                        tint = Color.White
                    )
                }
            }
        }
    }
}