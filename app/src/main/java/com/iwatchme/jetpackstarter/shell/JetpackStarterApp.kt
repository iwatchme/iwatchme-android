package com.iwatchme.jetpackstarter.shell

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun JetpackStarterApp(
    onContentVisible: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier) {
            AppNavHost(
                onContentVisible = onContentVisible,
            )
        }
    }
}
