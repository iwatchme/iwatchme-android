package com.iwatchme.jetpackstarter.pictureEditor

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BrushSettings(
    modifier: Modifier = Modifier,
    brushConfiguration: BrushConfiguration,
    onColorSelected: (color: Color) -> Unit,
    onThicknessChanged: (thickness: Float) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier) {
        Slider(
            modifier = Modifier
                .align(Start)
                .rotate(270f)
                .offset(x = 0.dp, y = (-165).dp)
                .weight(1f),
            valueRange = 1f..50f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
            ),
            value = brushConfiguration.thickness,
            onValueChange = {
                onThicknessChanged(it)
            }
        )
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .align(CenterHorizontally)
        ) {
            ColorPicker(
                onColorSelected = onColorSelected,
                selectedColor = brushConfiguration.color,
                onClose = onClose
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun Preview_BrushSettings() {
    BrushSettings(
        modifier = Modifier.wrapContentSize(),
        brushConfiguration = BrushConfiguration(),
        onColorSelected = {},
        onThicknessChanged = {},
        onClose = {},
    )
}