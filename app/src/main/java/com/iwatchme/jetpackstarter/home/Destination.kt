package com.iwatchme.jetpackstarter.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(
    val path: String,
    val icon: ImageVector? = null,
    val isRootDestination: Boolean = true
) {
    object Home : Destination("home", icon = null)
    object Feed : Destination("feeds", icon = Icons.Default.List)
    object Contacts : Destination("contacts", icon = Icons.Default.Contacts)
    object Calender : Destination("calender", icon = Icons.Default.DateRange)
    object Settings : Destination("settings", icon = Icons.Default.Settings, isRootDestination =  false)
    object Upgrade : Destination("upgrade", icon = Icons.Default.Upgrade, isRootDestination =  false)
    object Creation : Destination("creation", icon = Icons.Default.Add, isRootDestination =  false)
    object Add: Destination("add", icon = Icons.Default.Add, isRootDestination =  false)


    companion object {
        fun fromString(path: String): Destination {
            return when (path) {
                Feed.path -> Feed
                Contacts.path -> Contacts
                Calender.path -> Calender
                Settings.path -> Settings
                Upgrade.path -> Upgrade
                Creation.path -> Creation
                Add.path -> Add
                else -> Home
            }
        }
    }


}