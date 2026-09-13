package wiki.asaf.wikisayit.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsTag
import wiki.asaf.wikisayit.ui.components.WsTagVariant
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Session summary (`1a`): the approved-list confirmation before Contribute. */
@Composable
fun SessionSummaryScreen(
    viewModel: RecordingFlowViewModel,
    onContribute: () -> Unit,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionSummaryContent(
        approved = uiState.approved,
        showAbandonDialog = uiState.showAbandonDialog,
        replayingEntryId = uiState.replayingEntryId,
        onReplay = viewModel::replayEntry,
        onContribute = {
            viewModel.contribute()
            onContribute()
        },
        onAskAbandon = viewModel::askAbandon,
        onCancelAbandon = viewModel::cancelAbandon,
        onConfirmAbandon = {
            viewModel.doAbandon()
            onBackToStart()
        },
        onBackToStart = onBackToStart,
        modifier = modifier,
    )
}

@Composable
private fun SessionSummaryContent(
    approved: List<QueueEntry>,
    showAbandonDialog: Boolean,
    replayingEntryId: String?,
    onReplay: (QueueEntry) -> Unit,
    onContribute: () -> Unit,
    onAskAbandon: () -> Unit,
    onCancelAbandon: () -> Unit,
    onConfirmAbandon: () -> Unit,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val hasApproved = approved.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
            WsKicker(text = stringResource(R.string.summary_kicker))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = approved.size.toString(), style = typography.displayWord.copy(fontSize = 64.sp))
                Text(
                    text =
                        if (hasApproved) {
                            pluralStringResource(R.plurals.summary_headline_ready, approved.size)
                        } else {
                            pluralStringResource(R.plurals.summary_headline_kept, approved.size)
                        },
                    style = typography.h3,
                )
            }
            Text(
                text =
                    if (hasApproved) {
                        stringResource(R.string.summary_blurb_ready)
                    } else {
                        stringResource(R.string.summary_blurb_empty)
                    },
                style = typography.secondary,
                color = colors.neutral700,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!hasApproved) {
                BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        WsKicker(text = stringResource(R.string.summary_nothing_to_send_kicker))
                        Text(
                            text = stringResource(R.string.summary_nothing_to_send_body),
                            style = typography.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            items(approved, key = { it.evidenceId }) { entry ->
                ApprovedRow(
                    entry = entry,
                    isReplaying = entry.evidenceId == replayingEntryId,
                    onReplay = { onReplay(entry) },
                )
            }
        }
        WsHairlineDivider()
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasApproved) {
                WsPrimaryButton(
                    text = stringResource(R.string.summary_contribute_button),
                    onClick = onContribute,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52.dp,
                )
                Text(
                    text = stringResource(R.string.summary_cc0_note),
                    style = typography.caption,
                    color = colors.neutral700,
                )
                WsGhostButton(
                    text = stringResource(R.string.summary_abandon_button),
                    onClick = onAskAbandon,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                WsPrimaryButton(
                    text = stringResource(R.string.summary_back_to_start_button),
                    onClick = onBackToStart,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52.dp,
                )
            }
        }
    }

    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = onCancelAbandon,
            title = { Text(stringResource(R.string.summary_abandon_dialog_title)) },
            text = { Text(pluralStringResource(R.plurals.summary_abandon_dialog_body, approved.size, approved.size)) },
            confirmButton = {
                TextButton(onClick = onConfirmAbandon) { Text(stringResource(R.string.summary_abandon_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelAbandon) { Text(stringResource(R.string.summary_abandon_cancel)) }
            },
        )
    }
}

@Composable
private fun ApprovedRow(
    entry: QueueEntry,
    isReplaying: Boolean,
    onReplay: () -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onReplay, enabled = !isReplaying) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.summary_replay_action),
                tint = if (isReplaying) colors.neutral400 else colors.accent700,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = entry.label, style = typography.cardTitle, modifier = Modifier.weight(1f))
        WsTag(
            text =
                if (entry.kind == EntryKind.FORM) {
                    stringResource(
                        R.string.tag_form,
                    )
                } else {
                    stringResource(R.string.tag_item)
                },
            variant = WsTagVariant.OUTLINE,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(text = entry.evidenceId, style = typography.evidence, color = colors.neutral600)
        }
    }
    WsHairlineDivider()
}
