package com.iwatchme.jetpackstarter.emailbox

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EmailList(
    modifier: Modifier = Modifier,
    emails: List<Email>,
    onEmailDeleted: (id: String) -> Unit
) {

    LazyColumn(modifier = modifier) {
        items(items = emails, key = { item: Email -> item.id }) { email ->
            var isEmailItemDismissed by remember {
                mutableStateOf(false)
            }
            val dismissState = rememberDismissState {
                if (it == DismissValue.DismissedToEnd) {
                    isEmailItemDismissed = true
                }
                true
            }

            val emailHeightAnimation by animateDpAsState(
                targetValue = if (isEmailItemDismissed) 0.dp else 120.dp,
                animationSpec = tween(delayMillis = 300),
                finishedListener = {
                    onEmailDeleted(email.id)
                }
            )
            SwipeToDismiss(
                directions = setOf(DismissDirection.StartToEnd),
                dismissThresholds = { FractionalThreshold(0.15f) },
                dismissContent = {
                    EmailItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(emailHeightAnimation),
                        email = email,
                        dismissState.dismissDirection
                    )
                },
                background = {
                    EmailItemBackground(
                        modifier = Modifier
                            .fillMaxWidth(
                            )
                            .height(emailHeightAnimation),
                        targetValue = dismissState.targetValue
                    )
                },
                state = dismissState
            )
        }
    }

}