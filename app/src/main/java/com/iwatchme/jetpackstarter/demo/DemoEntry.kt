package com.iwatchme.jetpackstarter.demo

import androidx.compose.runtime.Composable

data class DemoEntry(
    val route: String,
    val title: String,
    val description: String? = null,
    val content: @Composable () -> Unit,
)
