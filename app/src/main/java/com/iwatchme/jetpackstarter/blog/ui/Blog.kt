package com.iwatchme.jetpackstarter.blog.ui

import Post
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val KEY_LIST = "KEY_LIST"
private const val KEY_POST = "KEY_POST"
private const val ARG_INDEX = "ARG_INDEX"

@ExperimentalComposeUiApi
@ExperimentalMotionApi
@Composable
fun Blog(
    modifier: Modifier = Modifier,
    posts: List<Post>
) {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(modifier = modifier, navController = navController, startDestination = KEY_LIST) {
            composable(KEY_LIST) {
                BlogList(
                    modifier = Modifier.fillMaxSize(),
                    posts = posts,
                    onPostSelected = { index ->
                        navController.navigate("$KEY_POST/$index")
                    }
                )
            }
            composable(
                "$KEY_POST/{$ARG_INDEX}",
                arguments = listOf(navArgument(ARG_INDEX) { type = NavType.IntType })
            ) { backStackEntry ->
                PostDetail(
                    modifier = Modifier.fillMaxWidth(),
                    post = posts[backStackEntry.arguments?.getInt(ARG_INDEX, 0) ?: 0],
                    handleNavigateUp = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
