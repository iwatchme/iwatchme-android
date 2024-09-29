package com.iwatchme.jetpackstarter.gallery

import android.annotation.TargetApi
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fy.kotlindemo.gallery.GalleryContent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun Gallery() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retrievedMedia by remember { mutableStateOf<List<Image>?>(null) }


    val permissionState =
        rememberPermissionState(
            permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
               android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )

    LaunchedEffect(key1 = permissionState.hasPermission) {
        if (permissionState.hasPermission) {
            scope.launch(Dispatchers.IO) {
                val retrieveMedia = retrieveMedia(context)
                withContext(Dispatchers.Main) {
                    retrievedMedia = retrieveMedia
                }
            }
        }
    }

    MaterialTheme {
        GalleryContent(
            modifier = Modifier.fillMaxSize(),
            permissionState = permissionState,
            media = retrievedMedia,
        )
    }

}