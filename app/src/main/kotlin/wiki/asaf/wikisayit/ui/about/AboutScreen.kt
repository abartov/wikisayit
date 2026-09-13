package wiki.asaf.wikisayit.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.BuildConfig
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.MonospaceEvidence

/** About (`1a`): version/license line, purpose paragraph, the CREDITS file verbatim, and the
 * CC0/no-ads closing note. */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val context = LocalContext.current
    val credits =
        remember {
            context.resources.openRawResource(R.raw.credits).bufferedReader().use { it.readText() }.trim()
        }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.weight(
                    1f,
                ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Text(text = stringResource(R.string.app_name), style = typography.h2)
            Text(
                text = stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME),
                style = typography.evidence,
                color = colors.neutral600,
            )
            Text(
                text = stringResource(R.string.about_purpose),
                style = typography.body,
                modifier = Modifier.padding(top = 16.dp),
            )
            WsHairlineDivider(Modifier.padding(vertical = 16.dp))
            WsKicker(text = stringResource(R.string.about_credits_heading), color = colors.ink)
            Text(
                text = credits,
                style =
                    typography.evidence.copy(
                        fontFamily = MonospaceEvidence,
                        lineHeight = typography.evidence.fontSize * 1.6,
                    ),
                color = colors.ink.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
            WsHairlineDivider(Modifier.padding(vertical = 16.dp))
            Text(
                text = stringResource(R.string.about_closing_note),
                style = typography.caption,
                color = colors.neutral700,
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            WsSecondaryButton(
                text = stringResource(R.string.settings_back_to_session),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
