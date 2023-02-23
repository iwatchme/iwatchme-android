package com.iwatchme.jetpackstarter.emailbox

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun EmailItem(
    modifier: Modifier = Modifier,
    email: Email,
    dismissDirection: DismissDirection?
) {
    val cardElevation by animateDpAsState(
        targetValue = if (dismissDirection != null) {
            4.dp
        } else 0.dp
    )
    Card(modifier = modifier.padding(16.dp), elevation = cardElevation) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = email.title,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = email.description,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

        }


    }


}