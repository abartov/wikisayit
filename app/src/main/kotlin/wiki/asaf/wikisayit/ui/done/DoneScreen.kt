package wiki.asaf.wikisayit.ui.done

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButtonRow
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Done (`1a`): the closing screen after every approved take has been contributed. */
@Composable
fun DoneScreen(
    viewModel: RecordingFlowViewModel,
    onRecordAnotherList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val username = uiState.username

    DoneContent(
        liveCount = uiState.uploadDoneCount,
        pendingCount = uiState.approved.size - uiState.uploadDoneCount,
        onOpenCommonsContributions = {
            uriHandler.openUri("https://commons.wikimedia.org/wiki/Special:Contributions/$username")
        },
        onOpenWikidataContributions = {
            uriHandler.openUri("https://www.wikidata.org/wiki/Special:Contributions/$username")
        },
        onRecordAnotherList = {
            viewModel.startOver()
            onRecordAnotherList()
        },
        modifier = modifier,
    )
}

@Composable
private fun DoneContent(
    liveCount: Int,
    pendingCount: Int,
    onOpenCommonsContributions: () -> Unit,
    onOpenWikidataContributions: () -> Unit,
    onRecordAnotherList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 22.dp)) {
            BlueprintBox(modifier = Modifier.size(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.accent700,
                    modifier = Modifier.size(28.dp).align(Alignment.Center),
                )
            }
            Text(
                text = pluralStringResource(R.plurals.done_headline, liveCount, liveCount),
                style = typography.h2,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text =
                    if (pendingCount > 0) {
                        pluralStringResource(R.plurals.done_explainer_partial, pendingCount, pendingCount)
                    } else {
                        stringResource(R.string.done_explainer)
                    },
                style = typography.secondary,
                color = colors.neutral700,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                WsSecondaryButtonRow(
                    onClick = onOpenCommonsContributions,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                ) {
                    Text(
                        text = stringResource(R.string.done_commons_uploads),
                        style = typography.buttonLabel,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "↗", style = typography.evidence)
                }
                WsSecondaryButtonRow(
                    onClick = onOpenWikidataContributions,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 48.dp,
                ) {
                    Text(
                        text = stringResource(R.string.done_wikidata_edits),
                        style = typography.buttonLabel,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "↗", style = typography.evidence)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            WsPrimaryButton(
                text = stringResource(R.string.done_record_another_list),
                onClick = onRecordAnotherList,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp,
            )
        }
    }
}
