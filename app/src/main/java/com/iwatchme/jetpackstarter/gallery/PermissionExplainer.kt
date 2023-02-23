package com.iwatchme.jetpackstarter.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iwatchme.jetpackstarter.R.*

@Composable
fun PermissionExplainer(
    modifier: Modifier = Modifier,
    requestPermission: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = string.permission_explainer_message),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                requestPermission()
            }
        ) {
            Text(stringResource(id = string.permission_explainer_action))
        }
    }
}