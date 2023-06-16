package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingArea(
    modifier: Modifier,
    selectTool: EditorTool?,
    drawObjects: List<EditorObject>,
    currentPath: EditorObject?,
    handleEvent: (event: EditorEvent) -> Unit
) {
    Canvas(modifier = modifier.pointerInteropFilter { event ->
        if (selectTool is EditorTool.BrushTool) {
            handleEvent(EditorEvent.BrushEvent(event))
            true
        } else {
            false
        }


    }) {
        (drawObjects + currentPath)
            .filterIsInstance<EditorObject.BrushPath>().forEach { drawingObject ->
                drawPath(
                    path = drawingObject.path.value,
                    color = drawingObject.brushConfiguration.color,
                    style = Stroke(
                        drawingObject
                            .brushConfiguration.thickness,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
    }

}