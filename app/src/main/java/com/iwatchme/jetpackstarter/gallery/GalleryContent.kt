package com.fy.kotlindemo.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.iwatchme.jetpackstarter.gallery.Image
import com.iwatchme.jetpackstarter.gallery.ImageGallery
import com.iwatchme.jetpackstarter.gallery.PermissionExplainer

@ExperimentalFoundationApi
@ExperimentalPermissionsApi
@Composable
fun GalleryContent(
    modifier: Modifier = Modifier,
    media: List<Image>? = null,
    permissionState: PermissionState,
) {

    when {
        !permissionState.permissionRequested -> {
            PermissionExplainer(modifier = Modifier.fillMaxSize()) {
                permissionState.launchPermissionRequest()
            }
        }

        permissionState.hasPermission -> {
            if (media == null) {
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                ImageGallery(
                    modifier = modifier,
                    images = media
                )
            }
        }

    }


}