package com.iwatchme.jetpackstarter.emailbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DismissValue
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp


@Composable
fun EmailItemBackground(
    modifier: Modifier = Modifier,
    targetValue: DismissValue
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (targetValue) {
            DismissValue.DismissedToEnd -> MaterialTheme.colors.error
            else -> MaterialTheme.colors.background
        },
        animationSpec = tween()
    )
    val iconColor by animateColorAsState(
        targetValue = when (targetValue) {
            DismissValue.DismissedToEnd -> MaterialTheme.colors.onError
            else -> MaterialTheme.colors.onSurface
        },
        animationSpec = tween()
    )
    val scale by animateFloatAsState(
        targetValue = if (targetValue == DismissValue.DismissedToEnd) {
            1f
        } else 0.75f
    )

    Box(modifier = modifier.background(backgroundColor).padding(horizontal = 20.dp)) {

        Icon(
            modifier = Modifier.align(Alignment.CenterStart).scale(scale),
            imageVector = Icons.Default.Delete,
            tint = iconColor,
            contentDescription = "Delete"
        )

    }

}