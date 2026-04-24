package com.iwatchme.jetpackstarter.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iwatchme.jetpackstarter.R
import com.iwatchme.jetpackstarter.demo.DemoRegistry
import com.iwatchme.jetpackstarter.home.HomeScreen

private const val HOME_ROUTE = "home"

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDemo = DemoRegistry.demos.firstOrNull { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = currentDemo?.title ?: androidx.compose.ui.res.stringResource(R.string.app_name))
                },
                backgroundColor = MaterialTheme.colors.surface,
                actions = {
                    if (currentDemo != null) {
                        TextButton(onClick = { navController.navigateUp() }) {
                            Text(text = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            navController = navController,
            startDestination = HOME_ROUTE,
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    demos = DemoRegistry.demos,
                    onDemoClick = { entry -> navController.navigate(entry.route) },
                )
            }
            DemoRegistry.demos.forEach { demo ->
                composable(demo.route) {
                    demo.content()
                }
            }
        }
    }
}
