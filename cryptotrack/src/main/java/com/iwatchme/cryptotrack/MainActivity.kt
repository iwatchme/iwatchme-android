package com.iwatchme.cryptotrack

import NestedScrollDemo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iwatchme.cryptotrack.components.Timeline
import com.iwatchme.cryptotrack.ui.theme.JetpackStarterTheme


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Timeline(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        maxScrollX = Int.MAX_VALUE.toFloat()
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JetpackStarterTheme {
        Greeting("Android")
    }
}