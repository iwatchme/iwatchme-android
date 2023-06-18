package com.iwatchme.jetpackstarter.blog.ui

import Post
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter


@Composable
fun Post(
    modifier: Modifier = Modifier,
    post: Post?
) {
    post?.let {
        Card(
            modifier = modifier.heightIn(265.dp)
        ) {
            ConstraintLayout(
                Modifier
                    .fillMaxWidth()
            ) {
                val (header, excerpt, author, title, date) = createRefs()

                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .constrainAs(header) {
                            start.linkTo(parent.start)
                            top.linkTo(parent.top)
                        },
                    model = post.image,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    onState = { state ->
                        Log.e("Frank", "${state}")
                        when (state) {
                            is AsyncImagePainter.State.Error -> {
                                Log.e("Frank", "${state.result.throwable}")
                            }
                            else -> {

                            }
                        }
                    }
                )

                Text(
                    modifier = Modifier
                        .constrainAs(title) {
                            bottom.linkTo(header.bottom)
                        }
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    text = post.title,
                    textAlign = TextAlign.Start,
                    color = Color.White
                )

                Text(
                    modifier = Modifier
                        .constrainAs(excerpt) {
                            top.linkTo(header.bottom, margin = 12.dp)
                            start.linkTo(title.start)
                            end.linkTo(title.end)
                        }
                        .padding(horizontal = 12.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    text = post.excerpt
                )

                Text(
                    modifier = Modifier
                        .padding(end = 12.dp, bottom = 12.dp)
                        .constrainAs(date) {
                            end.linkTo(parent.end)
                            bottom.linkTo(parent.bottom)
                        },
                    text = post.publishDate,
                    fontSize = 14.sp
                )

                Text(
                    modifier = Modifier
                        .padding(12.dp)
                        .constrainAs(author) {
                            bottom.linkTo(parent.bottom)
                            end.linkTo(date.start)
                        },
                    text = post.author,
                    fontSize = 14.sp
                )
                createHorizontalChain(author, date, chainStyle = ChainStyle.SpreadInside)
            }
        }
    }
}