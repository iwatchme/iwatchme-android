package com.iwatchme.jetpackstarter.gallery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.iwatchme.jetpackstarter.gallery.GalleryImage


@Composable
fun GalleryPreview(
    modifier: Modifier,
    selectImage: Uri
) {
    Box(modifier = modifier.background(Color.Black)) {
        GalleryImage(
            modifier = Modifier.fillMaxWidth(),
            uri = selectImage,
            scaleType = ContentScale.None
        )
    }


}