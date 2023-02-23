package com.iwatchme.jetpackstarter.emailbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iwatchme.jetpackstarter.R


@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    inboxEventListener: (event: InboxEvent) -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.message_content_error))


        Spacer(modifier = Modifier.height(12.dp))


        Button(onClick = {
            inboxEventListener(InboxEvent.RefreshContent)

        }) {
            Text(text = stringResource(id = R.string.label_try_again))
        }


    }
}