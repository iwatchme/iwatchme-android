package com.iwatchme.jetpackstarter.pictureEditor

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetState
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.iwatchme.jetpackstarter.R
import com.iwatchme.jetpackstarter.pictureEditor.emoji.EmojiSheet
import com.iwatchme.jetpackstarter.pictureEditor.sticker.StickerArea
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun StoriesEditor(
    modifier: Modifier = Modifier,
    state: EditorState,
    handleEvent: (event: EditorEvent) -> Unit
) {

    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = BottomSheetState(BottomSheetValue.Collapsed)
    )
    val defaultTextPaint = remember {
        Paint().apply {
            textSize = 80f
        }
    }
    var centerCanvas by remember {
        mutableStateOf(Offset(0f, 0f))
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetContent = {
            EmojiSheet(onEmojiSelected = {
                Rect().also { bounds ->
                    defaultTextPaint.getTextBounds(it, 0, it.length, bounds)
                    handleEvent(
                        EditorEvent.AddText(
                            centerCanvas.x,
                            centerCanvas.y,
                            bounds.width(),
                            bounds.height(),
                            it
                        )
                    )
                }

            })
        }) {
        BoxWithConstraints(
            modifier = Modifier
                .background(Color.Gray)
                .fillMaxSize()
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(id = R.drawable.dog),
                contentDescription = null,
                contentScale = ContentScale.Crop
            )


            DrawingArea(
                modifier = Modifier.fillMaxSize(),
                state.selectTool, state.drawObjects, state.currentDrawPath, handleEvent
            )

            StickerArea(
                modifier = Modifier.fillMaxSize(),
                drawingObjects = state.drawObjects.filterIsInstance(EditorObject.Text::class.java),
                onTransform = { id, offset, rotation, scale ->
                    handleEvent(
                        EditorEvent.TransformObject(
                            id = id,
                            offset = offset,
                            rotation = rotation,
                            scale = scale
                        )
                    )
                }
            )

            ActionsBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                selectedTool = state.selectTool,
                drawingObjects = state.drawObjects,
                handleEvent = {
                    if (it is EditorEvent.ToolSelected && it.tool == EditorTool.EmojiTool) {
                        coroutineScope.launch {
                            scaffoldState.bottomSheetState.expand()
                        }
                    } else handleEvent(it)
                }
            )

            ToolConfig(
                modifier = Modifier.fillMaxSize(),
                selectedTool = state.selectTool,
                configuration = state.currentBrushConfiguration,
                currentObject = state.currentDrawPath,
                handleEvent = handleEvent,
                addText = { text, color ->
                }
            )

        }
    }


}