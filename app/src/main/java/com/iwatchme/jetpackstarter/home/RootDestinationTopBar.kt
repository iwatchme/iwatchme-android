package com.iwatchme.jetpackstarter.home

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iwatchme.jetpackstarter.home.Destination

@Composable
fun RootDestinationTopBar(
    modifier: Modifier = Modifier,
    currentDestination: Destination,
    openDrawer: () -> Unit,
    showSnakbar: (message: String) -> Unit
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = "Home")
        },
        navigationIcon = {
            IconButton(onClick = {
                openDrawer()
            }) {
                Icon(Icons.Default.Menu, contentDescription = "Open menu")
            }
        },
        actions = {
            if (currentDestination != Destination.Feed) {
                IconButton(onClick = {
                   showSnakbar("Not available yet")
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "More Information"
                    )
                }
            }

        }
    )

}