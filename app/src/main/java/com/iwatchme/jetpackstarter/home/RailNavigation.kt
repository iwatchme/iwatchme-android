package com.iwatchme.jetpackstarter.home

import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RailNavigationBar(
    modifier: Modifier = Modifier,
    currentDestination: Destination,
    onCreateItem: ()->Unit,
    onNavigate: (destination: Destination) -> Unit
) {

    NavigationRail(
        modifier = modifier,
        header = {
            FloatingActionButton(onClick = { onCreateItem() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create new item"
                )
            }
        }
    ) {
        buildNavigationBarItems(
            currentDestination = currentDestination, onNavigate = onNavigate
        ).forEach {
            NavigationRailItem(
                selected = it.selected,
                onClick = it.onClick,
                icon = it.icon,
                label = it.label
            )
        }

    }

}