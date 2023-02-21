package com.iwatchme.jetpackstarter.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwatchme.jetpackstarter.R

@Composable
fun ColumnScope.DrawContent(
    modifier: Modifier = Modifier,
    onNavigationSelected: (destination: Destination) -> Unit,
    logout: () -> Unit

) {

    Text(
        text = stringResource(id = R.string.app_name),
        modifier = Modifier.padding(16.dp),
        fontSize = 20.sp
    )

    Spacer(modifier = Modifier.height(8.dp))


    Text(
        text = stringResource(id = R.string.app_name),
        modifier = Modifier.padding(16.dp),
        fontSize = 16.sp
    )


    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )


    DrawItem(
        Modifier.fillMaxWidth(),
        label = Destination.Settings.path,
    ) {
        onNavigationSelected(Destination.Settings)
    }

    Spacer(modifier = Modifier.height(8.dp))

    DrawItem(
        Modifier.fillMaxWidth(),
        label = Destination.Upgrade.path,
    ) {
        onNavigationSelected(Destination.Upgrade)
    }

    Spacer(modifier = Modifier.weight(1f))

    DrawItem(
        Modifier.fillMaxWidth(),
        label = "Log out",
    ) {
        logout()
    }

    Spacer(modifier = Modifier.height(8.dp))


}

@Composable
fun DrawItem(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {

    Text(text = label.replaceFirstChar {
        it.titlecase()
    },
        modifier = modifier
            .clickable {
                onClick()
            }
            .padding(16.dp)

    )

}