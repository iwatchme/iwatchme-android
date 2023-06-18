package com.iwatchme.jetpackstarter.pictureEditor.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.jetpackstarter.R
import com.iwatchme.jetpackstarter.pictureEditor.ColorPicker

@Composable
fun TextSettings(
    modifier: Modifier = Modifier,
    addText: (text: String, color: Color) -> Unit
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.8f))
    ) {
        var content by remember {
            mutableStateOf("")
        }
        var color by remember {
            mutableStateOf(Color.White)
        }
        val focusRequester = FocusRequester()
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        var showingColors by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth()) {
            if (!showingColors) {
                IconButton(onClick = { showingColors = !showingColors }) {
                    Icon(
                        modifier = Modifier
                            .padding(20.dp),
                        imageVector = Icons.Default.FormatColorText,
                        contentDescription = "",
                        tint = Color.White
                    )
                }
            }
            if (showingColors) {
                ColorPicker(
                    selectedColor = color,
                    onColorSelected = {
                        color = it
                        showingColors = !showingColors
                    },
                    onClose = {
                        showingColors = !showingColors
                    }
                )
            } else {
                Spacer(Modifier.weight(1f))
                TextButton(
                    modifier = Modifier.padding(end = 12.dp, top = 14.dp),
                    onClick = {
                        if (content.isNotEmpty()) {
                            addText(content, color)
                        }
                    }) {
                    Text(
                        text = stringResource(id = R.string.label_done),
                        color = Color.White
                    )
                }
            }
        }

        TextField(
            modifier = Modifier
                .widthIn(min = 28.dp)
                .padding(16.dp)
                .focusRequester(focusRequester)
                .align(Alignment.Center),
            value = content,
            onValueChange = {
                content = it
            },
            textStyle = TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.Unspecified,
                textColor = color,
                focusedIndicatorColor = Color.Unspecified,
                unfocusedIndicatorColor = Color.Unspecified,
                cursorColor = Color.White
            )
        )
    }
}