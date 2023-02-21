package com.iwatchme.jetpackstarter.home

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BottomNavigationBar(
    modifier: Modifier,
    currentDestination: Destination,
    onDestinationSelected: (Destination) -> Unit
) {

    BottomNavigation(
        modifier = modifier
    ) {

        buildNavigationBarItems(
            currentDestination = currentDestination, onNavigate = onDestinationSelected
        ).forEach {
            BottomNavigationItem(
                selected =

                it.selected, onClick = it.onClick, icon = it.icon, label = it.label
            )
        }

    }


}