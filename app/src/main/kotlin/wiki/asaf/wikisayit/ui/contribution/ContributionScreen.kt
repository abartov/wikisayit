package wiki.asaf.wikisayit.ui.contribution

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.session.FlowScreen
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.commonsFilename
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Contribution progress (`1a` happy path). Failure handling (`2e`) is a separate follow-up
 * (s-1s5.1..4 haven't landed, so there's nothing real yet to drive a failure state from). */
@Composable
fun ContributionScreen(
    viewModel: RecordingFlowViewModel,
    onNavigateDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.autoNavigateTo) {
        if (uiState.autoNavigateTo == FlowScreen.DONE) onNavigateDone()
    }

    val approved = uiState.approved
    val total = approved.size
    val done = uiState.uploadIndex.coerceAtMost(total)
    val current = approved.getOrNull(uiState.uploadIndex.coerceAtMost(total - 1).coerceAtLeast(0))
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp)) {
        WsKicker(text = stringResource(R.string.contribution_kicker))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = done.toString(), style = typography.h2.copy(fontSize = 44.sp))
            Text(
                text = stringResource(R.string.contribution_of_total, total),
                style = typography.secondary,
                color = colors.neutral700,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).background(colors.accent200)) {
            val progress = if (total > 0) done.toFloat() / total else 0f
            Box(Modifier.fillMaxWidth(progress).height(8.dp).background(colors.accent))
        }
        if (current != null) {
            BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WsKicker(text = stringResource(R.string.contribution_now_uploading))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = current.commonsFilename(uiState.language?.isoCode.orEmpty(), uiState.username),
                            style = typography.evidence,
                            color = colors.ink,
                        )
                    }
                    val step1 = uiState.uploadStepsDone > 0
                    val step2 = uiState.uploadStepsDone > 1
                    val step3 = uiState.uploadStepsDone > 2
                    UploadStepRow(done = step1, label = stringResource(R.string.contribution_step_commons))
                    UploadStepRow(done = step2, label = stringResource(R.string.contribution_step_categories))
                    UploadStepRow(
                        done = step3,
                        label = stringResource(R.string.contribution_step_p443, current.evidenceId),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.contribution_leaving_note),
            style = typography.caption,
            color = colors.neutral700,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun UploadStepRow(
    done: Boolean,
    label: String,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = if (done) "✓" else "·", color = colors.accent, style = typography.secondary)
        Text(text = label, style = typography.secondary)
    }
}
