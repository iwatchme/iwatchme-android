package com.iwatchme.jetpackstarter.pictureEditor

import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import com.iwatchme.crashlib.CrashLib
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class EditViewModel : ViewModel() {

    val uiState = MutableStateFlow(EditorState())


    private fun Offset.rotateBy(angle: Float): Offset {
        val angleInRadians = angle * PI / 180
        return Offset(
            (x * cos(angleInRadians) - y *
                    sin(angleInRadians)).toFloat(),
            (x * sin(angleInRadians) + y *
                    cos(angleInRadians)).toFloat()
        )
    }


    fun handleEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.ToolSelected -> {
                CrashLib.raiseError()
                uiState.value = uiState.value.copy(selectTool = event.tool)
            }

            is EditorEvent.UnselectTool -> {
                uiState.value = uiState.value.copy(selectTool = null)
            }

            is EditorEvent.AddText -> {
                val text = EditorObject.Text(
                    textId = UUID.randomUUID().toString(),
                    text = event.text,
                    offset = Offset(event.x - event.width / 2, event.y - event.height / 2),
                    color = event.color ?: Color.Unspecified

                )
                uiState.value = uiState.value.copy(
                    drawObjects = uiState.value.drawObjects + text,
                    selectTool = null
                )
            }

            is EditorEvent.Undo -> {
                uiState.value = uiState.value.copy(
                    drawObjects = uiState.value.drawObjects.toMutableList().apply { removeLast() }
                        .toList(),
                )
            }

            is EditorEvent.CloseEditor -> {

            }

            is EditorEvent.BrushEvent -> {
                when (event.event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val path = Path().apply {
                            moveTo(event.event.x, event.event.y)
                        }
                        uiState.value = uiState.value.copy(
                            currentDrawPath = EditorObject.BrushPath(
                                mutableStateOf(path),
                                uiState.value.currentBrushConfiguration
                            )
                        )
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val brushPath = (uiState.value.currentDrawPath as EditorObject.BrushPath)
                        val updatedPath = brushPath.path.value.apply {
                            lineTo(
                                event.event.x,
                                event.event.y
                            )
                        }
                        uiState.value = uiState.value.copy(
                            currentDrawPath = EditorObject.BrushPath(
                                mutableStateOf(updatedPath),
                                brushPath.brushConfiguration
                            )
                        )
                    }

                    MotionEvent.ACTION_UP -> {
                        val brushPath = (uiState.value.currentDrawPath as EditorObject.BrushPath)
                        brushPath?.let { path ->
                            uiState.value = uiState.value.copy(
                                drawObjects = uiState.value.drawObjects + path,
                                currentDrawPath = null
                            )

                        }
                    }

                }
            }

            is EditorEvent.TransformObject -> {
                val selectedObject =
                    uiState.value.drawObjects.find { it.id == event.id } as EditorObject.Text
                val scale = selectedObject.scale * (event.scale ?: 1f)
                val rotation = selectedObject.rotation + (event.rotation ?: 1f)
                val transformOffset = selectedObject.offset.copy(
                    x = event.offset.x * scale,
                    y = event.offset.y * scale
                ).rotateBy(rotation)

                uiState.value = uiState.value.copy(
                    drawObjects = uiState.value.drawObjects
                        .toMutableList()
                        .apply {
                            val index = uiState.value
                                .drawObjects
                                .indexOf(selectedObject)
                            val offset = selectedObject.offset.plus(transformOffset)
                            set(
                                index,
                                selectedObject.copy(
                                    rotation = rotation,
                                    scale = scale,
                                    offset = offset
                                )
                            )
                        }.toList()
                )
            }

            is EditorEvent.UpdateToolColor -> {
                uiState.value = uiState.value.copy(
                    currentBrushConfiguration = uiState.value.currentBrushConfiguration.copy(
                        color = event.color
                    )
                )

            }


            is EditorEvent.UpdateToolThickness -> {
                uiState.value = uiState.value.copy(
                    currentBrushConfiguration = uiState.value.currentBrushConfiguration.copy(
                        thickness = event.thickness
                    )
                )
            }

            else -> {

            }
        }
    }
}