package com.iwatchme.jetpackstarter.gallery

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGallery(
    modifier: Modifier = Modifier,
    images: List<Image>
) {
    var selectedImage by remember { mutableStateOf<Uri?>(null) }


    Box(
        contentAlignment = Alignment.Center
    ) {
        LazyVerticalGrid(
            modifier = modifier,
            columns = GridCells.Fixed(2)
        ) {


            items(images) { image ->
                GalleryImage(
                    modifier = Modifier
                        .clickable {
                            selectedImage = image.uri
                        }
                        .height(150.dp)
                        .fillMaxSize(),
                    uri = image.uri,
                    scaleType = ContentScale.Crop
                )

            }

        }
        selectedImage?.let {
            GalleryPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        selectedImage = null
                    },
                selectImage = it
            )
        }
    }


}