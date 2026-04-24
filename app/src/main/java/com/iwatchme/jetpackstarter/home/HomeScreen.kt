package com.iwatchme.jetpackstarter.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.jetpackstarter.demo.DemoEntry

@Composable
fun HomeScreen(
    demos: List<DemoEntry>,
    onDemoClick: (DemoEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(demos, key = { it.route }) { demo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDemoClick(demo) },
                elevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = demo.title, fontSize = 18.sp)
                    demo.description?.let { text ->
                        Text(text = text, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
