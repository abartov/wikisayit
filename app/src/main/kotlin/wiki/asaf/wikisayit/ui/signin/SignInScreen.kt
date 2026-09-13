package wiki.asaf.wikisayit.ui.signin

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.theme.BarlowCondensed
import wiki.asaf.wikisayit.ui.theme.HeadingWeight
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.MonospaceEvidence
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme

/**
 * The app's first screen for a new install (`2a`, app-UI half only — the OAuth consent sheet
 * itself is a Custom Tab on meta.wikimedia.org, not app UI).
 */
@Composable
fun SignInScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.authorizeUrl) {
        val url = uiState.authorizeUrl ?: return@LaunchedEffect
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
        viewModel.onAuthorizeUrlConsumed()
    }
    LaunchedEffect(uiState.signedIn) {
        if (uiState.signedIn) onSignIn()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    SignInScreenContent(
        uiState = uiState,
        onSignInClicked = viewModel::onSignInClicked,
        modifier = modifier,
    )
}

@Composable
private fun SignInScreenContent(
    uiState: SignInUiState,
    onSignInClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WikiSayItSpacing.screenHorizontal),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 46.sp),
        )
        Text(
            text = stringResource(R.string.sign_in_kicker),
            style = TextStyle(fontFamily = MonospaceEvidence, fontSize = 11.sp, letterSpacing = 0.9.sp),
            color = colors.accent700,
            modifier = Modifier.padding(top = WikiSayItSpacing.space3),
        )
        Text(
            text = stringResource(R.string.sign_in_promise),
            style = typography.body,
            modifier = Modifier.padding(top = WikiSayItSpacing.space6),
        )
        BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space6)) {
            Column(
                modifier = Modifier.padding(WikiSayItSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
            ) {
                WsKicker(text = stringResource(R.string.sign_in_you_will_need_kicker))
                Text(text = stringResource(R.string.sign_in_you_will_need_body), style = typography.secondary)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space8),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsPrimaryButton(
                text =
                    if (uiState.isSigningIn) {
                        stringResource(R.string.sign_in_primary_cta_in_progress)
                    } else {
                        stringResource(R.string.sign_in_primary_cta)
                    },
                onClick = onSignInClicked,
                enabled = !uiState.isSigningIn,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 54.dp,
                fontSize = 16.sp,
            )
            Text(
                text = uiState.errorMessage ?: stringResource(R.string.sign_in_note),
                style = typography.caption,
                color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.error else colors.neutral700,
            )
            WsGhostButton(
                text = stringResource(R.string.sign_in_no_account),
                onClick = { uriHandler.openUri("https://meta.wikimedia.org/wiki/Special:CreateAccount") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    WikiSayItTheme {
        SignInScreenContent(uiState = SignInUiState(), onSignInClicked = {})
    }
}
