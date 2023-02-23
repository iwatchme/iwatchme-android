package com.iwatchme.jetpackstarter.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun SettingsScreen() {
    val viewModel = SettingsViewModel()

    MaterialTheme {
        val state = viewModel.state.collectAsState().value

        SettingList(
            modifier = Modifier, state = state,
            toggleNotification = viewModel::toggleNotifications,
            toggleHintSettings = viewModel::toggleHintSetting,
            selectMarketOptions = viewModel::selectMaketOption,
            selectTheme = viewModel::selectThemeOption
        )


    }

}


@Composable
fun SettingList(
    modifier: Modifier,
    state: SettingState,
    toggleNotification: () -> Unit,
    toggleHintSettings: () -> Unit,
    selectMarketOptions: (MarketOptions) -> Unit,
    selectTheme: (Theme) -> Unit
) {

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {

        TopAppBar(
            backgroundColor = MaterialTheme.colors.surface,
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Setting", fontSize = 18.sp,
                color = MaterialTheme.colors.onSurface,
            )

        }

        NotificationSettings(
            modifier = Modifier.fillMaxWidth(),
            title = "Enable Notifications",
            state.notificationEnabled
        ) {
            toggleNotification()
        }

        Divider(startIndent = 16.dp)


        HintSettings(
            modifier = Modifier.fillMaxWidth(),
            title = "Hint Settings",
            checked = state.hintsEnabled
        ) {
            toggleHintSettings()
        }

        Divider()

        ManageSubscriptionSettings(
            modifier = Modifier.fillMaxWidth(),
            title = "Manage Subscription"
        ) {
        }

        SectionSpacer(modifier = Modifier.fillMaxWidth())

        MarketSettings(
            title = "Receive marketing emails?",
            modifier = Modifier.fillMaxWidth(),
            selected = state.marketOptions) {
            selectMarketOptions(it)
        }

        Divider()

        ThemeSettings(
            modifier = Modifier.fillMaxWidth(),
            title = "Theme", selectedTheme = state.theme
        ) {
            selectTheme(it)

        }

        SectionSpacer(modifier = Modifier.fillMaxWidth())
    }


}

@Composable
fun SettingItem(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        content()
    }
}


@Composable
fun NotificationSettings(
    modifier: Modifier,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    SettingItem(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .testTag(Tags.TAG_TOGGLE_ITEM)
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }


    }

}


@Composable
fun HintSettings(
    modifier: Modifier,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    SettingItem(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Checkbox(checked = checked, onCheckedChange = null)
        }


    }

}

@Composable
fun ManageSubscriptionSettings(
    modifier: Modifier,
    title: String,
    onSettingClick: () -> Unit
) {

    SettingItem(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSettingClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "Click")
        }


    }

}

@Composable
fun SectionSpacer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                MaterialTheme.colors.onSurface.copy(
                    alpha = 0.12f
                )
            )
    )
}


@Composable
fun MarketSettings(
    title: String,
    modifier: Modifier,
    selected: MarketOptions,
    onClick: (MarketOptions) -> Unit
) {
    val options = mutableListOf("opt-in for marketing emails", "Don't send me emails")

    SettingItem(modifier = modifier) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(text = title, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            options.forEachIndexed { index, s ->
                Row(
                    modifier = Modifier
                        .testTag(Tags.TAG_MARKETING_OPTION + index)
                        .fillMaxWidth()
                        .selectable(
                            selected = selected.id == index,
                            role = Role.RadioButton
                        ) {
                            if (index == MarketOptions.ALLOWED.id) {
                                onClick(MarketOptions.ALLOWED)
                            } else {
                                onClick(MarketOptions.NOT_ALLOWED)
                            }
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected.id == index,
                        onClick = null
                    )
                    Text(text = s, modifier = Modifier.padding(start = 18.dp))
                }


            }
        }

    }


}


@Composable
fun ThemeSettings(
    modifier: Modifier,
    title: String,
    selectedTheme: Theme,
    onThemeSelected: (Theme) -> Unit

) {
    var expanded by remember {
        mutableStateOf(false)
    }
    SettingItem(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    onClick = {
                        expanded = !expanded
                    }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Text(text = stringResource(id = selectedTheme.label))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(16.dp, 0.dp)
        ) {
            Theme.values().forEach {
                DropdownMenuItem(onClick = {
                    onThemeSelected(it)
                    expanded = false
                }) {
                    Text(text = stringResource(id = it.label))
                }
            }
        }
    }
}

