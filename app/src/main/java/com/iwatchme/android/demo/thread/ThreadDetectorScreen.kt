package com.iwatchme.android.demo.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.android.detect.ThreadDetector
import com.iwatchme.android.detect.ThreadEvent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThreadDetectorScreen(modifier: Modifier = Modifier) {
    var eventList by remember { mutableStateOf(ThreadDetector.events.toList()) }
    var filter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            eventList = ThreadDetector.events.toList()
            delay(1000)
        }
    }

    val filtered = if (filter != null) {
        eventList.filter { it.type == filter }
    } else {
        eventList
    }

    val threadCount = eventList.count { it.type == "Thread" }
    val executorCount = eventList.count { it.type == "Executor" }
    val factoryCount = eventList.count { it.type == "Factory" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3EFE6)),
    ) {
        // Summary header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
        ) {
            Text(
                text = "Detected ${eventList.size} events",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filter chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip("All (${eventList.size})", selected = filter == null) { filter = null }
                FilterChip("Thread ($threadCount)", selected = filter == "Thread") {
                    filter = if (filter == "Thread") null else "Thread"
                }
                FilterChip("Executor ($executorCount)", selected = filter == "Executor") {
                    filter = if (filter == "Executor") null else "Executor"
                }
                FilterChip("Factory ($factoryCount)", selected = filter == "Factory") {
                    filter = if (filter == "Factory") null else "Factory"
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { eventList = ThreadDetector.events.toList() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                ) {
                    Text("Refresh", color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        ThreadDetector.clear()
                        eventList = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336)),
                ) {
                    Text("Clear", color = Color.White)
                }
            }
        }

        // Event list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered.reversed(), key = { it.id }) { event ->
                EventCard(event)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Color.White else Color(0xFF333333),
        modifier = Modifier
            .background(
                color = if (selected) Color(0xFF2196F3) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EventCard(event: ThreadEvent) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    val typeColor = when (event.type) {
        "Thread" -> Color(0xFF2196F3)
        "Executor" -> Color(0xFFFF9800)
        "Factory" -> Color(0xFF9C27B0)
        else -> Color.Gray
    }

    val callerShort = event.callerClass.substringAfterLast('.')

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        elevation = 2.dp,
        backgroundColor = Color.White,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.type,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(typeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = callerShort,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeFormat.format(Date(event.timestamp)),
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "on: ${event.threadName}",
                fontSize = 12.sp,
                color = Color(0xFF666666),
            )

            if (event.detail.isNotEmpty()) {
                Text(
                    text = event.detail,
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Stack Trace:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                )
                Text(
                    text = event.stackTrace,
                    fontSize = 10.sp,
                    color = Color(0xFF666666),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
