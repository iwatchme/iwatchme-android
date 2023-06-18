package com.iwatchme.jetpackstarter.blog.ui

import Post
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.MotionScene
import androidx.constraintlayout.compose.Visibility
import coil.compose.AsyncImage


private const val KEY_HEADER = "header"
private const val KEY_TITLE = "title"
private const val KEY_TITLE_BACKGROUND = "title_background"
private const val KEY_UP = "up"
private const val KEY_AVATAR = "avatar"
private const val KEY_AUTHOR = "author"
private const val KEY_EXCERPT = "excerpt"
private const val KEY_TINT = "tint"

@ExperimentalComposeUiApi
@ExperimentalMotionApi
@Composable
fun PostDetail(
    modifier: Modifier = Modifier,
    post: Post,
    handleNavigateUp: () -> Unit
) {
    val scrollState = rememberScrollState()
    MotionLayout(
        modifier = modifier,
        motionScene = motionScene(),
        progress = if (scrollState.maxValue > 0 &&
            LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE
        ) {
            scrollState.value / scrollState.maxValue.toFloat()
        } else 1f
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .layoutId(KEY_HEADER),
            model = post.image,
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        Box(
            modifier = Modifier
                .layoutId(KEY_TITLE_BACKGROUND)
                .background(Color.Black.copy(alpha = 0.4f))
                .fillMaxWidth()
        )

        Text(
            modifier = Modifier
                .layoutId(KEY_TITLE)
                .padding(vertical = 16.dp),
            text = post.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        IconButton(
            modifier = Modifier
                .layoutId(KEY_UP),
            onClick = handleNavigateUp
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "",
                tint = Color.White
            )
        }
        Icon(
            modifier = Modifier
                .size(40.dp)
                .layoutId(KEY_AVATAR),
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            tint = motionProperties(id = KEY_AVATAR).value.color(KEY_TINT)
        )

        Text(
            modifier = Modifier
                .layoutId(KEY_AUTHOR)
                .width(200.dp),
            text = post.author,
            textAlign = TextAlign.Center
        )

        Text(
            modifier = Modifier
                .layoutId(KEY_EXCERPT)
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, bottom = 78.dp),
            text = post.excerpt
        )
    }
}

@ExperimentalMotionApi
@Composable
fun motionScene() = MotionScene {
    val sceneStart = constraintSet {
        val header = createRefFor(KEY_HEADER)
        val title = createRefFor(KEY_TITLE)
        val up = createRefFor(KEY_UP)
        val titleBackground = createRefFor(KEY_TITLE_BACKGROUND)
        val avatar = createRefFor(KEY_AVATAR)
        val author = createRefFor(KEY_AUTHOR)
        val excerpt = createRefFor(KEY_EXCERPT)

        constrain(header) {
            start.linkTo(parent.start)
            top.linkTo(parent.top)
            end.linkTo(parent.end)
            height = Dimension.value(280.dp)
        }
        constrain(title) {
            start.linkTo(parent.start)
            bottom.linkTo(header.bottom)
            end.linkTo(parent.end)
        }
        constrain(titleBackground) {
            start.linkTo(parent.start)
            bottom.linkTo(header.bottom)
            end.linkTo(parent.end)
            height = Dimension.value(60.dp)
        }
        constrain(up) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(header.bottom)
            visibility = Visibility.Gone
        }
        constrain(avatar) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(header.bottom, 16.dp)
            customColor(KEY_TINT, Color.Black)
        }
        constrain(author) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(avatar.bottom)
            visibility = Visibility.Visible
        }
        constrain(excerpt) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(author.bottom, 16.dp)
        }
    }
    val sceneEnd = constraintSet {
        val header = createRefFor(KEY_HEADER)
        val title = createRefFor(KEY_TITLE)
        val up = createRefFor(KEY_UP)
        val titleBackground = createRefFor(KEY_TITLE_BACKGROUND)
        val avatar = createRefFor(KEY_AVATAR)
        val author = createRefFor(KEY_AUTHOR)
        val excerpt = createRefFor(KEY_EXCERPT)

        constrain(header) {
            start.linkTo(parent.start)
            top.linkTo(parent.top)
            end.linkTo(parent.end)
            height = Dimension.value(60.dp)
        }
        constrain(title) {
            start.linkTo(up.end)
            bottom.linkTo(header.bottom)
        }
        constrain(titleBackground) {
            start.linkTo(parent.start)
            bottom.linkTo(header.bottom)
            end.linkTo(parent.end)
            height = Dimension.value(60.dp)
        }
        constrain(up) {
            start.linkTo(parent.start)
            top.linkTo(header.top)
            bottom.linkTo(header.bottom)
            visibility = Visibility.Visible
        }
        constrain(avatar) {
            top.linkTo(titleBackground.top)
            bottom.linkTo(titleBackground.bottom)
            end.linkTo(parent.end, 8.dp)
            customColor(KEY_TINT, Color.White)
        }
        constrain(author) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(avatar.bottom)
            visibility = Visibility.Gone
        }
        constrain(excerpt) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            top.linkTo(titleBackground.bottom, 4.dp)
        }
    }
    defaultTransition(
        from = sceneStart,
        to = sceneEnd
    ) {}
}
