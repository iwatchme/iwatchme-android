package com.iwatchme.cryptotrack.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.sql.Blob
import kotlin.math.abs


data class ScrollSyncState(
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val maxScrollX: Float = Float.POSITIVE_INFINITY,
    val maxScrollY: Float = Float.POSITIVE_INFINITY
) {
    fun updateScrollX(delta: Float): ScrollSyncState {
        val newScrollX = (scrollX - delta).coerceIn(0f, maxScrollX)
        return copy(scrollX = newScrollX)
    }
}

@Composable
fun rememberScrollSyncState(
    initialScrollX: Float = 0f,
    initialScrollY: Float = 0f,
    maxScrollX: Float = Float.POSITIVE_INFINITY,
    maxScrollY: Float = Float.POSITIVE_INFINITY
): MutableState<ScrollSyncState> {
    return remember {
        mutableStateOf(
            ScrollSyncState(
                scrollX = initialScrollX,
                scrollY = initialScrollY,
                maxScrollX = maxScrollX,
                maxScrollY = maxScrollY
            )
        )
    }
}

// Timeline.kt
@Composable
fun Timeline(
    modifier: Modifier = Modifier,
    initialScrollX: Float = 0f,
    maxScrollX: Float,
    onScrollChanged: ((Float) -> Unit)? = null
) {
    val scrollState = rememberScrollSyncState(
        initialScrollX = initialScrollX,
        maxScrollX = maxScrollX
    )
    val scope = rememberCoroutineScope()

    // 处理滚动状态
    val scrollableState = rememberScrollableState { delta ->
        val oldScrollX = scrollState.value.scrollX
        scope.launch {
            scrollState.value = scrollState.value.updateScrollX(delta)
            onScrollChanged?.invoke(scrollState.value.scrollX)
        }
        // 返回实际消耗的滚动距离
        scrollState.value.scrollX - oldScrollX
    }

    // 处理自动滚动
    var isAutoScrolling by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
    ) {
        TimeArea(
            scrollX = scrollState.value.scrollX,
            onScroll = { delta ->
                scope.launch {
                    scrollState.value = scrollState.value.updateScrollX(delta)
                    onScrollChanged?.invoke(scrollState.value.scrollX)
                }
            }
        )

        EditArea(
            scrollX = scrollState.value.scrollX,
            onScroll = { delta ->
                Log.d("Timeline", "EditArea onScroll: $delta")
                scope.launch {
                    scrollState.value = scrollState.value.updateScrollX(delta)
                    onScrollChanged?.invoke(scrollState.value.scrollX)
                }
            }
        )
    }
}

// 子组件示例
@Composable
fun TimeArea(
    scrollX: Float,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    // 将 dp 转换为 pixels
    val itemWidthPx = with(density) { 100.dp.toPx() }

    LaunchedEffect(scrollX) {
        val itemWidth = itemWidthPx // 每个项目的宽度，单位是 dp
        val itemIndex = (scrollX / itemWidth).toInt()
        val offset = (scrollX % itemWidth).toInt()

        lazyListState.scrollToItem(
            index = itemIndex,
            scrollOffset = offset
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onScroll(dragAmount)
                }
            }
    ) {
        LazyRow(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize(),
        ) {
            // 时间刻度
            items(100) { index ->
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(if (index % 2 == 0) Color.White else Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index * 100}ms")
                }
            }
        }

    }
}


@Composable
fun EditArea(
    scrollX: Float,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 将 dp 转换为 pixels
    val itemWidthPx = with(density) { 100.dp.toPx() }

    // 同步滚动位置
    LaunchedEffect(scrollX) {
        val itemWidth = itemWidthPx // 每个项目的宽度，单位是 dp
        val itemIndex = (scrollX / itemWidth).toInt()
        val offset = (scrollX % itemWidth).toInt()

        lazyListState.scrollToItem(
            index = itemIndex,
            scrollOffset = offset
        )

    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    Log.d("EditArea", "Drag detected: $dragAmount")
                    onScroll(dragAmount)
                }
            }
    ) {
        LazyRow(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize(),
            userScrollEnabled = false
        ) {
            // 示例内容：创建一些项目来测试滚动
            items(100) { index ->
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .background(
                            if (index % 2 == 0) Color.LightGray else Color.Gray
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Item $index")
                }
            }
        }
    }


}

// 自定义滚动修饰符
fun Modifier.onScroll(
    onScroll: (Float) -> Unit
) = this.then(
    Modifier.pointerInput(Unit) {
        var totalDelta = 0f

        detectHorizontalDragGestures(
            onDragStart = {
                totalDelta = 0f
            },
            onDragEnd = {
                // 处理惯性滚动
                if (abs(totalDelta) > 0) {
                    onScroll(totalDelta * 0.1f) // 添加一些惯性效果
                }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDelta += dragAmount
                onScroll(dragAmount)
            }
        )
    }
)