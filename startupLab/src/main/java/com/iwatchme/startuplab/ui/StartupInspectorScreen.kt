package com.iwatchme.startuplab.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.orchestration.JetpackStartupManager
import com.iwatchme.startuplab.state.StartupDashboardState
import com.iwatchme.startuplab.state.StartupDashboardStore
import com.iwatchme.startupruntime.model.StartupReport
import com.iwatchme.startupruntime.model.StartupStage
import com.iwatchme.startupruntime.model.StartupTaskReport
import com.iwatchme.startupruntime.model.StartupTaskStatus

@Composable
fun StartupInspectorScreen(
    modifier: Modifier = Modifier,
) {
    val startupState = StartupDashboardStore.state
    val context = LocalContext.current
    val application = context.applicationContext as Application

    LazyColumn(
        modifier = modifier.background(Color(0xFFF3EFE6)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            InspectorHeaderCard(
                startupState = startupState,
                onInitializeDeferredSdk = { JetpackStartupManager.initializeDeferredSdk(application) },
                onScheduleModeSwitch = { JetpackStartupManager.scheduleNextMode(application) },
            )
        }
        item {
            ProviderCard(startupState = startupState)
        }
        item {
            RuntimeBreakdownCard(startupState = startupState.actualReport)
        }
        item {
            ComparisonOverviewCard(startupState = startupState)
        }
        item {
            TimelineCard(startupState = startupState.actualReport)
        }
        item {
            InspectorNotesCard(notes = startupState.notes)
        }
    }
}

@Composable
private fun InspectorHeaderCard(
    startupState: StartupDashboardState,
    onInitializeDeferredSdk: () -> Unit,
    onScheduleModeSwitch: () -> Unit,
) {
    Card(
        backgroundColor = Color(0xFF1D3124),
        elevation = 6.dp,
    ) {
        BoxWithConstraints {
            val compactActions = maxWidth < 420.dp
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Startup Inspector",
                    color = Color(0xFFF7F3E8),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "这里集中看 ContentProvider 治理、阶段耗时拆分和任务甘特图。",
                    color = Color(0xFFD9DFC8),
                    fontSize = 14.sp,
                )
                MetricChip(label = "Current mode", value = startupState.mode.label)
                MetricChip(label = "Next cold start", value = startupState.nextLaunchMode.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(label = "Critical", active = startupState.criticalReady)
                    StatusPill(label = "Full", active = startupState.fullReady)
                    StatusPill(label = "Idle", active = startupState.idleReady)
                }
                if (compactActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onInitializeDeferredSdk,
                            enabled = startupState.mode == StartupMode.OPTIMIZED && startupState.deferredInitStatus != "Completed",
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Run Deferred SDK Init · ${startupState.deferredInitStatus}")
                        }
                        Button(
                            onClick = onScheduleModeSwitch,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Use ${startupState.nextLaunchMode.label} Next Launch")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onInitializeDeferredSdk,
                            enabled = startupState.mode == StartupMode.OPTIMIZED && startupState.deferredInitStatus != "Completed",
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Run Deferred SDK Init · ${startupState.deferredInitStatus}")
                        }
                        Button(
                            onClick = onScheduleModeSwitch,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Use ${startupState.nextLaunchMode.label} Next Launch")
                        }
                    }
                }
                Text(
                    text = "Logcat 过滤建议：StartupLab 或 StartupRuntime。切换模式后需要手动关闭并重新冷启动。",
                    color = Color(0xFFD9DFC8),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(startupState: StartupDashboardState) {
    val snapshot = startupState.sdkSnapshot
    Card(backgroundColor = Color.White, elevation = 4.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(title = "ContentProvider Governance")
            Text(
                text = "文档里这一节的核心是：App Startup 不会把初始化自动变并发，它主要解决的是 Provider 治理和延迟初始化入口。这个 demo 现在改成 1 个重型 SDK Provider 对比 1 个轻型 InitializationProvider。",
                color = Color(0xFF4E5B52),
                fontSize = 13.sp,
            )
            MetricRow(label = "Legacy heavy provider", value = "${snapshot.legacyProviderInitMs}ms")
            MetricRow(label = "InitializationProvider bridge", value = "${snapshot.startupBridgeInitMs}ms")
            MetricRow(label = "Governance Initializer", value = "${snapshot.governanceInitializerMs}ms")
            MetricRow(label = "Deferred SDK init", value = "${snapshot.deferredInitializerMs}ms")
            MetricRow(label = "Current init source", value = snapshot.initSource)
            MetricRow(label = "Governance state", value = snapshot.governanceState)
            MetricRow(
                label = "Legacy provider count",
                value = "1 heavy SDK provider",
            )
            MetricRow(
                label = "Governed provider count",
                value = "1 InitializationProvider",
            )
            MetricRow(
                label = "Current mode expectation",
                value = if (startupState.mode == StartupMode.LEGACY) {
                    "Heavy init still happens before Application.onCreate()"
                } else {
                    "SDK provider is bypassed; InitializationProvider only establishes governance; heavy init is delayed until you call initializeComponent()"
                },
            )
            Text(
                text = "关键点：Optimized 不是“再初始化一次”，而是把原本启动时就偷跑的初始化移出 Provider 阶段，改成后面手动触发一次。为了支持同一个 APK 里切换 legacy / optimized，demo 里保留了旧 Provider，但在 optimized 模式下它只做 bypass。",
                color = Color(0xFF6B735E),
                fontSize = 12.sp,
            )
            Divider()
            MetricRow(label = "StartupInitializerBridge", value = "${snapshot.startupBridgeInitMs}ms")
            MetricRow(label = "ThirdPartySdkGovernanceInitializer", value = "${snapshot.governanceInitializerMs}ms")
            MetricRow(label = "DeferredThirdPartySdkInitializer", value = "${snapshot.deferredInitializerMs}ms")
        }
    }
}

@Composable
private fun RuntimeBreakdownCard(startupState: StartupReport?) {
    Card(backgroundColor = Color.White, elevation = 4.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(title = "Timing Breakdown")
            if (startupState == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF7A8F47))
                Text(text = "Startup report is still collecting…", color = Color(0xFF4E5B52))
            } else {
                val summary = startupState.summary
                Text(
                    text = "当前 Demo 里 SplashScreen 的 keep condition 绑定的是 criticalReady；Idle drain 则是在 reportFullyDrawn() 之后，通过 MessageQueue.IdleHandler 作为 gate 开始的。IdleHandler 本身只负责触发，不直接承担重活。",
                    color = Color(0xFF4E5B52),
                    fontSize = 13.sp,
                )
                MetricRow(label = "Critical ready", value = "${summary.criticalReadyAtMs ?: -1}ms")
                MetricRow(label = "Splash hold", value = "${summary.criticalReadyAtMs ?: -1}ms")
                MetricRow(label = "Full ready", value = "${summary.fullReadyAtMs ?: -1}ms")
                MetricRow(label = "Idle complete", value = "${summary.idleCompletedAtMs ?: -1}ms")
                MetricRow(label = "Key path", value = "${summary.keyPathDurationMs}ms")
                MetricRow(label = "Key path tasks", value = summary.keyPathTaskIds.joinToString(" -> "))
                Divider()
                MetricRow(label = "Blocking work", value = "${stageDuration(startupState.tasks, StartupStage.BLOCKING)}ms")
                MetricRow(label = "Non-blocking work", value = "${stageDuration(startupState.tasks, StartupStage.NON_BLOCKING)}ms")
                MetricRow(label = "Idle work", value = "${stageDuration(startupState.tasks, StartupStage.IDLE)}ms")
                MetricRow(label = "On-demand work", value = "${stageDuration(startupState.tasks, StartupStage.ON_DEMAND)}ms")
            }
        }
    }
}

@Composable
private fun ComparisonOverviewCard(startupState: StartupDashboardState) {
    val comparison = startupState.comparison
    Card(backgroundColor = Color(0xFFF7F3E8), elevation = 4.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(title = "Mode Comparison")
            if (comparison == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFA85D34))
                Text(text = "Building comparison model…", color = Color(0xFF6D4C36))
            } else {
                MetricRow(label = "Legacy critical", value = "${comparison.baseline.criticalReadyMs}ms")
                MetricRow(label = "Optimized critical", value = "${comparison.optimized.criticalReadyMs}ms")
                MetricRow(label = "Critical delta", value = "-${comparison.criticalImprovementMs}ms")
                Divider()
                MetricRow(label = "Legacy full", value = "${comparison.baseline.fullReadyMs}ms")
                MetricRow(label = "Optimized full", value = "${comparison.optimized.fullReadyMs}ms")
                MetricRow(label = "Full delta", value = "-${comparison.fullImprovementMs}ms")
                Divider()
                MetricRow(label = "Legacy main-thread block", value = "${comparison.baseline.mainThreadLongestBlockMs}ms")
                MetricRow(label = "Optimized main-thread block", value = "${comparison.optimized.mainThreadLongestBlockMs}ms")
                MetricRow(label = "Optimized main batches", value = "${comparison.optimized.mainThreadBatchCount}")
            }
        }
    }
}

@Composable
private fun TimelineCard(startupState: StartupReport?) {
    Card(backgroundColor = Color.White, elevation = 4.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(title = "Task Timeline")
            if (startupState == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF7A8F47))
                Text(text = "Waiting for runtime report…", color = Color(0xFF4E5B52))
            } else {
                val firstTaskStart = startupState.tasks
                    .mapNotNull { it.startOffsetMs }
                    .minOrNull()
                    ?: 0L
                val sessionEnd = startupState.tasks.maxOfOrNull { (it.startOffsetMs ?: 0L) + (it.durationMs ?: 0L) } ?: 1L
                val executionWindow = (sessionEnd - firstTaskStart).coerceAtLeast(1L)
                Text(
                    text = "任务条现在以首个任务真正开始执行为 0ms。Session start 到 first task start 的空白时间单独显示为 scheduler gap。",
                    color = Color(0xFF4E5B52),
                    fontSize = 13.sp,
                )
                MetricRow(label = "Session start", value = "0ms")
                MetricRow(label = "First task start", value = "t+$firstTaskStart ms")
                MetricRow(label = "Scheduler gap", value = "${firstTaskStart}ms")
                MetricRow(label = "Execution window", value = "${executionWindow}ms")
                Divider()
                GanttAxis(maxEnd = executionWindow)
                startupState.tasks
                    .sortedWith(compareBy({ it.startOffsetMs ?: Long.MAX_VALUE }, { it.id }))
                    .forEach { task ->
                        GanttRow(task = task, maxEnd = executionWindow, firstTaskStart = firstTaskStart)
                    }
            }
        }
    }
}

@Composable
private fun GanttAxis(maxEnd: Long) {
    val markers = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.width(132.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            markers.forEach { marker ->
                Text(
                    text = "${(maxEnd * marker).toLong()}ms",
                    color = Color(0xFF6B735E),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun GanttRow(task: StartupTaskReport, maxEnd: Long, firstTaskStart: Long) {
    val executionOffset = task.startOffsetMs?.minus(firstTaskStart)?.coerceAtLeast(0L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.description,
                    color = Color(0xFF1E2B1F),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = "${task.stage}/${task.dispatcher}",
                    color = Color(0xFF6B735E),
                    fontSize = 11.sp,
                )
            }
            GanttBar(task = task, maxEnd = maxEnd, firstTaskStart = firstTaskStart, modifier = Modifier.weight(1f))
        }
        Text(
            text = buildString {
                append("session t+${task.startOffsetMs ?: -1}ms")
                append(" · exec+${executionOffset ?: -1}ms")
                append(" · ${task.durationMs ?: -1}ms")
                append(" · ${task.threadName ?: "n/a"}")
                task.batchId?.let { append(" · batch $it") }
                append(" · deps: ${task.dependencies.ifEmpty { setOf("none") }.joinToString()}")
            },
            color = Color(0xFF6B735E),
            fontSize = 12.sp,
        )
        Divider()
    }
}

@Composable
private fun GanttBar(task: StartupTaskReport, maxEnd: Long, firstTaskStart: Long, modifier: Modifier = Modifier) {
    val total = maxEnd.toFloat().coerceAtLeast(1f)
    val executionOffset = ((task.startOffsetMs ?: firstTaskStart) - firstTaskStart).coerceAtLeast(0L)
    val startRatio = (executionOffset.toFloat() / total).coerceIn(0f, 1f)
    val durationRatio = ((task.durationMs ?: 1L).toFloat() / total).coerceIn(0.02f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .height(26.dp)
            .background(Color(0xFFE8E0CE)),
    ) {
        val barOffset = maxWidth * startRatio
        val barWidth = (maxWidth * durationRatio).coerceAtLeast(6.dp)
        Box(
            modifier = Modifier
                .offset(x = barOffset)
                .padding(vertical = 4.dp)
                .fillMaxHeight()
                .width(barWidth)
                .background(
                    color = when (task.status) {
                        StartupTaskStatus.COMPLETED -> when (task.stage) {
                            StartupStage.BLOCKING -> Color(0xFFB85C38)
                            StartupStage.NON_BLOCKING -> Color(0xFF7A8F47)
                            StartupStage.IDLE -> Color(0xFF4D8D92)
                            StartupStage.ON_DEMAND -> Color(0xFF8C6BB1)
                        }
                        StartupTaskStatus.RUNNING -> Color(0xFFA85D34)
                        StartupTaskStatus.PENDING -> Color(0xFFCBBFA5)
                        else -> Color(0xFF6D4C36)
                    },
                ),
        )
        if (task.status == StartupTaskStatus.PENDING) {
            Text(
                text = "pending",
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                color = Color(0xFF6B735E),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun InspectorNotesCard(notes: List<String>) {
    Card(backgroundColor = Color(0xFFFAF8F0), elevation = 4.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(title = "Startup Notes")
            notes.takeLast(8).forEach { note ->
                Text(text = note, color = Color(0xFF5A6353), fontSize = 13.sp)
            }
        }
    }
}

private fun stageDuration(tasks: List<StartupTaskReport>, stage: StartupStage): Long {
    return tasks
        .filter { it.stage == stage && it.status == StartupTaskStatus.COMPLETED }
        .sumOf { it.durationMs ?: 0L }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E382C))
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), color = Color(0xFF4E5B52))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color(0xFF1E2B1F),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .background(color = Color(0xFF2E4A38), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "$label:", color = Color(0xFFD9DFC8), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    val color = if (active) Color(0xFFB4D47B) else Color(0xFF455241)
    Row(
        modifier = Modifier
            .background(color = color, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, color = if (active) Color(0xFF1D3124) else Color(0xFFF7F3E8), fontSize = 12.sp)
    }
}
