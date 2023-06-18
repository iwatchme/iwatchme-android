package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.iwatchme.jetpackstarter.pictureEditor.tool.BrushSettings
import com.iwatchme.jetpackstarter.pictureEditor.tool.TextSettings

@Composable
fun ToolConfig(
    modifier: Modifier = Modifier,
    selectedTool: EditorTool?,
    configuration: BrushConfiguration,
    currentObject: EditorObject?,
    handleEvent: (event: EditorEvent) -> Unit,
    addText: (test: String, color: Color) -> Unit
) {
    if (selectedTool is EditorTool.BrushTool) {
        BrushSettings(
            modifier = modifier,
            brushConfiguration = configuration,
            onColorSelected = {
                handleEvent(EditorEvent.UpdateToolColor(it))
            },
            onThicknessChanged = {
                handleEvent(EditorEvent.UpdateToolThickness(it))
            },
            onClose = {

            }
        )
    } else if (selectedTool is EditorTool.TextTool) {
        TextSettings(
            modifier = modifier,
            addText = addText
        )

    }
}