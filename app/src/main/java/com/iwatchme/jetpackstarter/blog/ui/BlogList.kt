package com.iwatchme.jetpackstarter.blog.ui

import Post
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocalMap
import androidx.compose.ui.unit.dp


@Composable
fun BlogList(
    modifier: Modifier = Modifier,
    posts: List<Post>,
    onPostSelected: (index: Int) -> Unit
) {

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
            Post(
                modifier = modifier.clickable {
                    onPostSelected(index)
                },
                post = post
            )
        }

    }

}