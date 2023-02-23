package com.iwatchme.jetpackstarter.emailbox

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iwatchme.jetpackstarter.R

@Composable
fun Inbox() {
    val viewModel: InboxViewModel = viewModel()
    MaterialTheme {
        EmailBox(
            modifier = Modifier.fillMaxWidth(),
            inboxState = viewModel.uiState.collectAsState().value,
            inboxEventListener = viewModel::handleEvent
        )
    }
    LaunchedEffect(Unit) {
        viewModel.handleEvent(InboxEvent.RefreshContent)
    }
}


@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun EmailBox(
    modifier: Modifier = Modifier,
    inboxState: InboxState,
    inboxEventListener: (inboxEvent: InboxEvent) -> Unit
) {

    Scaffold(modifier = modifier,
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 0.dp,
                title = {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        text = stringResource(id = R.string.title_inbox, inboxState.emails.count())
                    )
                }
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {

            if (inboxState.status == InboxStatus.LOADING) {
                Loading()
            } else if (inboxState.status == InboxStatus.ERROR) {
                ErrorState(inboxEventListener = {
                    inboxEventListener(it)
                })
            } else if (inboxState.status == InboxStatus.SUCCESS) {
                EmailList(
                    modifier = Modifier.fillMaxSize(),
                    emails = inboxState.emails
                ) {
                    inboxEventListener(InboxEvent.DeleteContent(it))
                }
            } else{
                EmptyState(inboxEventListener = {
                    inboxEventListener(it)
                })
            }


        }


    }
}

