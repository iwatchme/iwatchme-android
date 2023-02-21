package com.iwatchme.jetpackstarter.home

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ChildDestinationTopBar(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    title: String
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = title)
        },
        navigationIcon = {
            IconButton(onClick = {
                onNavigateUp()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
    )
}