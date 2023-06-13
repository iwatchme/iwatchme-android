package com.iwatchme.jetpackstarter.pictureEditor

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color


sealed class EditorEvent {


    class ToolSelected(val tool: EditorTool) : EditorEvent()

    object UnselectTool : EditorEvent()

    object CloseEditor : EditorEvent()

    class BrushEvent(val event: MotionEvent) : EditorEvent()

    class UpdateToolColor(val color: Color) : EditorEvent()

    class UpdateToolThickness(val thickness: Float): EditorEvent()

    object Undo : EditorEvent()

    class AddText(
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
        val text: String,
        val color: Color? = null
    ) : EditorEvent()


    class TransformObject(
        val id: String,
        val offset: Offset,
        val scale: Float = 1f,
        val rotation: Float = 1f
    ) : EditorEvent()
}