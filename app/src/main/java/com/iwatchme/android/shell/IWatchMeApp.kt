package com.iwatchme.android.shell

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun IWatchMeApp(
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
