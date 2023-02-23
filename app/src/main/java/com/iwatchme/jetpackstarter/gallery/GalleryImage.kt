package com.iwatchme.jetpackstarter.gallery

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberImagePainter
import com.iwatchme.jetpackstarter.R

@Composable
fun GalleryImage(
    modifier: Modifier,
    uri: Uri,
    scaleType: ContentScale
) {
    Image(
        modifier = modifier,
        painter = rememberImagePainter(
            data = uri,
            builder = {
                placeholder(R.drawable.ic_baseline_image_24)
                crossfade(true)
                error(R.drawable.ic_baseline_error_24)
            }
        ),
        contentScale = scaleType,
        contentDescription = null
    )

}