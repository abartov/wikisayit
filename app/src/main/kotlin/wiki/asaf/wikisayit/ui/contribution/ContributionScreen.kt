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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsTag
import wiki.asaf.wikisayit.ui.components.WsTagVariant
import wiki.asaf.wikisayit.ui.session.FlowScreen
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.UploadEntryState
import wiki.asaf.wikisayit.ui.session.UploadFailureStatus
import wiki.asaf.wikisayit.ui.session.UploadStepFailure
import wiki.asaf.wikisayit.ui.session.commonsFilename
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Contribution progress (`1a` happy path) plus the `2e` failure states: a connectivity-dropped
 * banner, a "Needs attention" list naming which step failed per entry, inline rename/discard
 * actions for name conflicts, and "Retry all now" / "Leave them for later" footer actions.
 * Everything here is driven by [wiki.asaf.wikisayit.ui.session.RecordingFlowUiState]'s real
 * per-entry upload state — [RecordingFlowViewModel.contribute] calls the real
 * [wiki.asaf.wikisayit.data.commons.CommonsUploader] and
 * [wiki.asaf.wikisayit.data.wikidata.P443StatementWriter], nothing here is simulated. */
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
    val done = uiState.uploadDoneCount
    val current = uiState.activeUploadEntry
    val currentState = uiState.activeUploadState
    val needsAttention = uiState.uploadNeedsAttention
    val isoCode = uiState.language?.isoCode.orEmpty()
    val username = uiState.username
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
        if (uiState.connectivityLost) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp).background(colors.accent100).padding(10.dp)) {
                Text(
                    text = stringResource(R.string.contribution_connectivity_banner),
                    style = typography.secondary,
                    color = colors.accent800,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (current != null && currentState != null) {
                BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WsKicker(text = stringResource(R.string.contribution_now_uploading))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = current.commonsFilename(isoCode, username),
                                style = typography.evidence,
                                color = colors.ink,
                            )
                        }
                        UploadStepRow(
                            done = currentState.commonsDone,
                            label = stringResource(R.string.contribution_step_commons),
                        )
                        UploadStepRow(
                            done = currentState.categoriesDone,
                            label = stringResource(R.string.contribution_step_categories),
                        )
                        UploadStepRow(
                            done = currentState.p443Done,
                            label = stringResource(R.string.contribution_step_p443, current.evidenceId),
                        )
                    }
                }
            }
            if (needsAttention.isNotEmpty()) {
                WsKicker(
                    text = stringResource(R.string.contribution_needs_attention_kicker),
                    modifier = Modifier.padding(top = 22.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    needsAttention.forEach { index ->
                        val entry = approved.getOrNull(index)
                        val entryState = uiState.uploadStates.getOrNull(index)
                        if (entry != null && entryState != null) {
                            NeedsAttentionRow(
                                entry = entry,
                                state = entryState,
                                filename = entry.commonsFilename(isoCode, username),
                                onRename = { viewModel.renameAndRetryFailedEntry(index) },
                                onDiscard = { viewModel.discardFailedEntry(index) },
                            )
                        }
                    }
                }
            }
        }
        if (needsAttention.isNotEmpty() && uiState.activeUploadIndex == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WsPrimaryButton(
                    text =
                        pluralStringResource(
                            R.plurals.contribution_retry_all_now,
                            needsAttention.size,
                            needsAttention.size,
                        ),
                    onClick = viewModel::retryFailedUploads,
                    modifier = Modifier.fillMaxWidth(),
                )
                WsGhostButton(
                    text = stringResource(R.string.action_leave_for_later),
                    onClick = viewModel::leaveFailuresForLater,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.contribution_leaving_note),
                style = typography.caption,
                color = colors.neutral700,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
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

@Composable
private fun NeedsAttentionRow(
    entry: QueueEntry,
    state: UploadEntryState,
    filename: String,
    onRename: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    BlueprintBox(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = entry.label, style = typography.cardTitle, modifier = Modifier.weight(1f))
                WsTag(text = stringResource(state.failureStatus.labelRes()), variant = WsTagVariant.OUTLINE)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(text = filename, style = typography.evidence, color = colors.neutral700)
            }
            Text(
                text =
                    stringResource(
                        if (state.failedStep == UploadStepFailure.COMMONS) {
                            R.string.contribution_failed_step_commons
                        } else {
                            R.string.contribution_failed_step_p443
                        },
                        state.errorMessage.orEmpty(),
                    ),
                style = typography.secondary,
                color = colors.neutral700,
            )
            if (state.failureStatus == UploadFailureStatus.NAME_TAKEN) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WsSecondaryButton(
                        text = stringResource(R.string.action_rename_and_upload),
                        onClick = onRename,
                        modifier = Modifier.weight(1f),
                    )
                    WsGhostButton(text = stringResource(R.string.action_discard_this_one), onClick = onDiscard)
                }
            }
        }
    }
}

private fun UploadFailureStatus?.labelRes(): Int =
    when (this) {
        UploadFailureStatus.NAME_TAKEN -> R.string.contribution_status_name_taken
        UploadFailureStatus.WAITING -> R.string.contribution_status_waiting
        UploadFailureStatus.RETRYING, null -> R.string.contribution_status_retrying
    }
