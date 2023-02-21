package com.iwatchme.jetpackstarter.home

import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

class NavigationBarItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
    val label: @Composable () -> Unit
)


fun buildNavigationBarItems(
    currentDestination: Destination,
    onNavigate: (destination: Destination) -> Unit
): List<NavigationBarItem> {
    return listOf(
        Destination.Feed,
        Destination.Contacts,
        Destination.Calender
    ).map{
        NavigationBarItem(
            label = {
                Text(text = it.path.replaceFirstChar { char ->
                    char.titlecase()
                })
            },

            icon = {
                it.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = it.path
                    )
                }
            },
            selected = currentDestination.path == it.path,
            onClick = {
                onNavigate(it)
            }
        )

    }


}