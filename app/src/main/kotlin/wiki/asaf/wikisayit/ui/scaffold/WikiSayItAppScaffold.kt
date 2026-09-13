package wiki.asaf.wikisayit.ui.scaffold

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import wiki.asaf.wikisayit.R

/**
 * Shared chrome for every screen: an app-name top bar with the hamburger menu that opens
 * Settings/Stats/About, wrapping whatever destination content is currently navigated to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiSayItAppScaffold(
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.nav_menu_open),
                        )
                    }
                    DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_destination_settings)) },
                            onClick = {
                                isMenuExpanded = false
                                onSettingsClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_destination_stats)) },
                            onClick = {
                                isMenuExpanded = false
                                onStatsClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_destination_about)) },
                            onClick = {
                                isMenuExpanded = false
                                onAboutClick()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
