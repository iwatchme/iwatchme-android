package com.fy.kotlindemo.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iwatchme.jetpackstarter.home.ChildDestinationTopBar
import com.iwatchme.jetpackstarter.home.Destination
import com.iwatchme.jetpackstarter.home.RootDestinationTopBar

@Composable
fun DestinationTopBar(
    modifier: Modifier = Modifier,
    currentDestination: Destination,
    onNavigationUp: ()-> Unit,
    openDrawer: () -> Unit,
    showSnakbar: (message: String) -> Unit
) {
    if (currentDestination.isRootDestination) {
        RootDestinationTopBar(
            modifier = modifier,
            currentDestination = currentDestination,
            openDrawer = openDrawer,
            showSnakbar = showSnakbar
        )
    } else {
        ChildDestinationTopBar(
            modifier = modifier,
            onNavigateUp = onNavigationUp,
            title = currentDestination.path
        )
    }

}