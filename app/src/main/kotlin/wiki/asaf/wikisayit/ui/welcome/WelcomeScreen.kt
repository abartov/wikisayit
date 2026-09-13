package wiki.asaf.wikisayit.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme

@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WelcomeScreenContent(
        uiState = uiState,
        onGetStartedClicked = viewModel::onGetStartedClicked,
        modifier = modifier,
    )
}

@Composable
private fun WelcomeScreenContent(
    uiState: WelcomeUiState,
    onGetStartedClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.app_name))
                Button(onClick = onGetStartedClicked) {
                    Text(text = stringResource(R.string.get_started))
                }
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.get_started_tap_count,
                            uiState.getStartedTapCount,
                            uiState.getStartedTapCount,
                        ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    WikiSayItTheme {
        WelcomeScreenContent(uiState = WelcomeUiState(getStartedTapCount = 2), onGetStartedClicked = {})
    }
}
