package com.iwatchme.netopt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Experiment(
    val id: String,
    val title: String,
    val subtitle: String,
    val available: Boolean,
)

val experiments = listOf(
    Experiment("E1", "全链路瀑布", "新建连接 vs 复用 · 预期 -300ms 握手", available = true),
    Experiment("E2", "DNS 优化", "系统 DNS vs HttpDNS+SWR · 预期 P99 -1500ms", available = true),
    Experiment("E3", "HTTP/1.1 vs H2 多路复用", "6 并发请求 · 队头阻塞 vs 多 stream", available = true),
    Experiment("E4", "TLS 1.2 vs 1.3", "2 RTT vs 1 RTT vs 0-RTT 恢复", false),
    Experiment("E5", "编码对比", "JSON / Gzip / Brotli / Protobuf", available = true),
    Experiment("E6", "HTTP 缓存", "200 vs 304 vs 强缓存", available = true),
    Experiment("E7", "增量同步", "全量 100 条 vs since=v 增量 · 预期 -98%", available = true),
    Experiment("E8", "图片优化", "JPEG q=92/60/30 + BlurHash 占位", available = true),
    Experiment("E9", "域名收敛/连接合并", "4 host 独立 client vs 通配符复用 1 条 H2", available = true),
    Experiment("E10", "弱网降级", "Naive vs Retry+Failover · 成功率 50%→100%", available = true),
    Experiment("E11", "离线优先", "本地优先 + 后台同步 (SharedPref 简化版)", available = true),
    Experiment("E12", "三级容灾", "网络 → 缓存 → Asset 兜底", available = true),
)

@Composable
fun ExperimentsScreen(onExperimentClick: (Experiment) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("NetOpt Demo · 12 实验") })
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(experiments) { exp ->
                ExperimentCard(exp, onClick = { if (exp.available) onExperimentClick(exp) })
            }
        }
    }
}

@Composable
private fun ExperimentCard(exp: Experiment, onClick: () -> Unit) {
    Card(
        elevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = exp.available, onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${exp.id} · ${exp.title}${if (!exp.available) "  (TODO)" else ""}",
                style = MaterialTheme.typography.subtitle1,
                color = if (exp.available) MaterialTheme.colors.onSurface
                else MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = exp.subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
