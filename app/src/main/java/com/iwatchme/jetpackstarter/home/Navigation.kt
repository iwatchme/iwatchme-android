package com.iwatchme.jetpackstarter.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.iwatchme.jetpackstarter.home.ContentArea
import com.iwatchme.jetpackstarter.home.Destination

@Composable
fun Navigation(
    modifier: Modifier = Modifier,
    navController: NavHostController

) {

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Destination.Home.path
    ) {

        navigation(
            startDestination = Destination.Feed.path,
            route = Destination.Home.path
        ) {
            composable(Destination.Feed.path) {
                ContentArea(
                    modifier = Modifier.fillMaxSize(),
                    destination = Destination.Feed
                )
            }

            composable(Destination.Contacts.path) {
                ContentArea(
                    modifier = Modifier.fillMaxSize(),
                    destination = Destination.Contacts
                )
            }

            composable(Destination.Calender.path) {
                ContentArea(
                    modifier = Modifier.fillMaxSize(),
                    destination = Destination.Calender
                )
            }
        }

        composable(Destination.Settings.path) {
            ContentArea(
                modifier = Modifier.fillMaxSize(),
                destination = Destination.Settings
            )
        }


        composable(Destination.Upgrade.path) {
            ContentArea(
                modifier = Modifier.fillMaxSize(),
                destination = Destination.Upgrade
            )
        }

        navigation(
            startDestination = Destination.Add.path,
            route = Destination.Creation.path
        ) {
            composable(Destination.Add.path) {
                ContentArea(
                    modifier = Modifier.fillMaxSize(),
                    destination = Destination.Add
                )
            }
        }


    }

}