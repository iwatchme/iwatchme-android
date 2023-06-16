package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.iwatchme.jetpackstarter.R

@Composable
fun StoriesEditor(
    modifier: Modifier = Modifier,
    state: EditorState,
    handleEvent: (event: EditorEvent) -> Unit
) {
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
            state.selectTool, state.drawObject, state.currentDrawPath, handleEvent
        )

        ActionsBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            selectedTool = state.selectTool,
            drawingObjects = state.drawObject,
            handleEvent = {

            }
        )

    }
}