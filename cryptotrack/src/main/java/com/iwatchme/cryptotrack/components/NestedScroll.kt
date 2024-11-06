import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedScrollDemo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background
) {
    val AppBarHeight = 56.dp
    val Purple40 = Color(0xFF6650a4)
    val Contents = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "www", "333", "FFFF")
    val appBarMaxHeightPx = with(LocalDensity.current) { AppBarHeight.roundToPx() }
    val connection = remember(appBarMaxHeightPx) {
        CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
    }
    val density = LocalDensity.current
    val spaceHeight by remember(density) {
        derivedStateOf {
            with(density) {
                (appBarMaxHeightPx + connection.appBarOffset).toDp()
            }
        }
    }

    Box(Modifier.nestedScroll(connection)) {
        Column {
            Spacer(
                Modifier
                    .padding(4.dp)
                    .height(spaceHeight)
            )
            LazyColumn {
                items(Contents.size) { index ->
                    Text(text = Contents[index], modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }

        TopAppBar(
            modifier = Modifier.offset { IntOffset(0, connection.appBarOffset) },
            title = { Text(text = "Jetpack Compose") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Purple40,
                titleContentColor = Color.White
            )
        )
    }
}

private class CollapsingAppBarNestedScrollConnection(
    val appBarMaxHeight: Int
) : NestedScrollConnection {

    var appBarOffset: Int by mutableIntStateOf(0)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        Log.e("Frank", "available.y: ${available.y}")
        val delta = available.y.toInt()
        val newOffset = appBarOffset + delta
        val previousOffset = appBarOffset
        appBarOffset = newOffset.coerceIn(-appBarMaxHeight, 0)
        val consumed = appBarOffset - previousOffset
        Log.e("Frank", "consumed: $consumed")
        return Offset(0f, consumed.toFloat())
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {

        Log.e("Frank", "after consumed: $consumed")
        Log.e("Frank", "after available: $available")
        return super.onPostScroll(consumed, available, source)
    }
}