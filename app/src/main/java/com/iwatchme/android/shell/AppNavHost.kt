package com.iwatchme.android.shell

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
import com.iwatchme.android.R
import com.iwatchme.android.demo.DemoRegistry
import com.iwatchme.android.home.HomeScreen
import com.iwatchme.startuplab.state.StartupDashboardStore

private const val HOME_ROUTE = "home"

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    onContentVisible: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDemo = DemoRegistry.demos.firstOrNull { it.route == currentRoute }
    val startupState = StartupDashboardStore.state

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
                    startupState = startupState,
                    demos = DemoRegistry.demos,
                    onDemoClick = { entry -> navController.navigate(entry.route) },
                    onContentVisible = onContentVisible,
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
