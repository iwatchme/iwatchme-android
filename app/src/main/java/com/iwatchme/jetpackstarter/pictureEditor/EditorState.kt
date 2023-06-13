package com.iwatchme.jetpackstarter.pictureEditor

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import com.iwatchme.jetpackstarter.R


data class EditorState(
    val selectTool: EditorTool? = null,
    val drawObject: List<EditorObject> = emptyList(),
    val currentDrawPath: EditorObject.BrushPath? = null,
    val currentBrushConfiguration: BrushConfiguration = BrushConfiguration()
)


sealed class EditorObject(val id: String? = null) {
    data class BrushPath(
        val path: MutableState<Path>,
        val brushConfiguration: BrushConfiguration
    ) : EditorObject()


    data class Text(
        val textId: String,
        val text: String,
        val offset: Offset,
        val scale: Float = 1f,
        val rotation: Float = 1f,
        val color: Color = Color.Black
        ) : EditorObject(textId)
}

data class BrushConfiguration(
    val color: Color = Color.Black,
    val thickness: Float = 20f
)


sealed class EditorTool(
    @StringRes val description: Int,
    val icon: ImageVector
) {

    class TextTool : EditorTool(R.string.label_text, Icons.Default.TextFormat)


    class BrushTool : EditorTool(R.string.label_brush, Icons.Default.Brush)


    class EmojiTool : EditorTool(R.string.label_emoji, Icons.Default.EmojiEmotions)

}

