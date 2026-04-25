package com.iwatchme.jetpackstarter.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.jetpackstarter.demo.DemoEntry
import com.iwatchme.startuplab.state.StartupDashboardState

@Composable
fun HomeScreen(
    startupState: StartupDashboardState,
    demos: List<DemoEntry>,
    onDemoClick: (DemoEntry) -> Unit,
    onContentVisible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (startupState.fullReady) {
        SideEffect {
            onContentVisible()
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3EFE6))
            .testTag("home_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SectionTitle(title = "Demo Routes")
        }
        items(demos, key = { it.route }) { demo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("demo_card_${demo.route}")
                    .clickable { onDemoClick(demo) },
                elevation = 4.dp,
                backgroundColor = Color.White,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = demo.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    demo.description?.let { text ->
                        Text(text = text, fontSize = 14.sp, color = Color(0xFF4E5B52))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E382C))
}
