package wiki.asaf.wikisayit.ui.scaffold

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsIconButton
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/**
 * Shared chrome for the main flow: a hamburger + wordmark + monospace context chip top bar,
 * and the Settings/Stats/About drawer, both drawn straight from the design handoff (the top
 * bar is unconditional across every screen in `1a`, from Profile through Done). [showTopBar]
 * is false for Sign-in (no chrome at all) and New profile (its own back-chevron bar instead).
 */
@Composable
fun WikiSayItAppScaffold(
    showTopBar: Boolean,
    chipText: String,
    menuFooterText: String,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = showTopBar,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RectangleShape,
                drawerContainerColor = colors.ground,
                modifier = Modifier.width(262.dp),
            ) {
                Column(modifier = Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = typography.cardTitle,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 14.dp),
                    )
                    WsHairlineDivider()
                    DrawerRow(stringResource(R.string.nav_destination_settings)) {
                        scope.launch { drawerState.close() }
                        onSettingsClick()
                    }
                    WsHairlineDivider()
                    DrawerRow(stringResource(R.string.nav_destination_stats)) {
                        scope.launch { drawerState.close() }
                        onStatsClick()
                    }
                    WsHairlineDivider()
                    DrawerRow(stringResource(R.string.nav_destination_about)) {
                        scope.launch { drawerState.close() }
                        onAboutClick()
                    }
                    WsHairlineDivider()
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = menuFooterText,
                        style = typography.caption,
                        color = colors.neutral700,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        },
        content = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                if (showTopBar) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WsIconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = stringResource(R.string.nav_menu_open),
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            style = typography.cardTitle,
                            modifier =
                                Modifier.clickable(
                                    onClickLabel = stringResource(R.string.top_bar_reset_action),
                                    onClick = onTitleClick,
                                ),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = chipText, style = typography.evidence, color = colors.neutral700)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    WsHairlineDivider()
                }
                content(Modifier.fillMaxSize())
            }
        },
    )
}

@Composable
private fun DrawerRow(
    text: String,
    onClick: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(text = text, style = typography.buttonLabel)
    }
}
