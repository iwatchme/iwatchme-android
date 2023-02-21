package com.iwatchme.jetpackstarter.home

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fy.kotlindemo.home.DestinationTopBar
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun Home(modifier: Modifier) {

    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val coroutineScope = rememberCoroutineScope()
    val drawState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scalfoldState = rememberScaffoldState(drawerState = drawState)
    val orientation = LocalConfiguration.current.orientation

    val currentDestination = remember(navBackStackEntry) {
        derivedStateOf {
            navBackStackEntry.value?.destination?.route?.let { route ->
                Destination.fromString(route)
            } ?: Destination.Home
        }
    }

    Scaffold(
        modifier = modifier,
        scaffoldState = scalfoldState,
        topBar = {
            DestinationTopBar(
                currentDestination = currentDestination.value,
                onNavigationUp = {
                        navController.popBackStack()
                },
                openDrawer = {
                    coroutineScope.launch {
                        scalfoldState.drawerState.open()
                    }
                },
                showSnakbar = {
                    coroutineScope.launch {
                        scalfoldState.snackbarHostState.showSnackbar("Not available yet")
                    }
                }
            )
        },
        drawerContent = {
            DrawContent(
                modifier = Modifier.fillMaxSize(),
                onNavigationSelected = {
                      navController.navigate(it.path)
                    coroutineScope.launch {
                        scalfoldState.drawerState.close()
                    }
                },
                logout = {

                }
            )
        },
        bottomBar = {
            if (orientation != Configuration.ORIENTATION_LANDSCAPE
                && currentDestination.value.isRootDestination
            ) {
                BottomNavigationBar(
                    modifier = Modifier,
                    currentDestination = currentDestination.value,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.path) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

        },
        floatingActionButton = {
            if (orientation != Configuration.ORIENTATION_LANDSCAPE
                && currentDestination.value.path  == Destination.Feed.path
            ) {
                FloatingActionButton(onClick = {
                    navController.navigate(
                        Destination.Creation.path
                    )

                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "create a new item"
                    )

                }
            }
        }
    ) {

       Body(
           navController = navController,
           destination = currentDestination.value,
           orientation =  orientation,
           onCreateItem = {
                navController.navigate(Destination.Add.path)
           },
           onNavigate = {
               navController.navigate(it.path) {
                   popUpTo(Destination.Home.path) {
                       saveState = true
                   }
                   launchSingleTop = true
                   restoreState = true
               }
           }
       )

    }
}