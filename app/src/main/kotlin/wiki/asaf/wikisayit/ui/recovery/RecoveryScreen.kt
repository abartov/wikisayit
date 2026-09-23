package wiki.asaf.wikisayit.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.recovery.MAX_RECOVERY_UPLOADS
import wiki.asaf.wikisayit.data.recovery.RecoveryFinding
import wiki.asaf.wikisayit.data.recovery.RecoveryOutcome
import wiki.asaf.wikisayit.data.recovery.RecoveryProgress
import wiki.asaf.wikisayit.data.recovery.RecoveryReport
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsTable
import wiki.asaf.wikisayit.ui.components.WsTag
import wiki.asaf.wikisayit.ui.components.WsTagVariant
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing

/** Recovery mode (s-7f3), reached from the list-source picker instead of building a new list. */
@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecoveryContent(
        uiState = uiState,
        onLimitChange = viewModel::updateLimitText,
        onStart = viewModel::start,
        onRunAgain = viewModel::reset,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun RecoveryContent(
    uiState: RecoveryUiState,
    onLimitChange: (String) -> Unit,
    onStart: () -> Unit,
    onRunAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val title =
        when (uiState.stage) {
            RecoveryStage.SETUP -> stringResource(R.string.recovery_title)
            RecoveryStage.RUNNING -> stringResource(R.string.recovery_running_title)
            RecoveryStage.DONE -> stringResource(R.string.recovery_done_title)
            RecoveryStage.FAILED -> stringResource(R.string.recovery_failed_title)
        }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WikiSayItSpacing.screenHorizontal, vertical = WikiSayItSpacing.screenTop),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space4),
        ) {
            Column {
                WsKicker(text = stringResource(R.string.recovery_kicker))
                Text(text = title, style = typography.h2)
                if (uiState.stage == RecoveryStage.SETUP) {
                    Text(
                        text = stringResource(R.string.recovery_explainer),
                        style = typography.secondary,
                        color = colors.neutral700,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
            when (uiState.stage) {
                RecoveryStage.SETUP ->
                    OutlinedTextField(
                        value = uiState.limitText,
                        onValueChange = onLimitChange,
                        label = { Text(stringResource(R.string.recovery_limit_label, MAX_RECOVERY_UPLOADS)) },
                        isError = uiState.limit == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                RecoveryStage.RUNNING -> ProgressSection(uiState.progress)
                RecoveryStage.DONE -> uiState.report?.let { ReportSection(it) }
                RecoveryStage.FAILED ->
                    Text(text = uiState.errorMessage.orEmpty(), style = typography.secondary, color = colors.accent800)
            }
        }
        WsHairlineDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            if (uiState.stage != RecoveryStage.RUNNING) {
                WsSecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
            when (uiState.stage) {
                RecoveryStage.SETUP ->
                    WsPrimaryButton(
                        text = stringResource(R.string.recovery_start_button),
                        onClick = onStart,
                        enabled = uiState.limit != null,
                        modifier = Modifier.weight(1f),
                    )
                RecoveryStage.DONE, RecoveryStage.FAILED ->
                    WsPrimaryButton(
                        text = stringResource(R.string.recovery_run_again_button),
                        onClick = onRunAgain,
                        modifier = Modifier.weight(1f),
                    )
                RecoveryStage.RUNNING -> Unit
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: RecoveryProgress?) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val (label, counter, fraction) =
        when (progress) {
            is RecoveryProgress.Listing ->
                Triple(
                    stringResource(R.string.recovery_progress_listing),
                    "${progress.fetched}/${progress.limit}",
                    progress.fetched.toFloat() / progress.limit,
                )
            is RecoveryProgress.Linking ->
                Triple(
                    stringResource(R.string.recovery_progress_linking),
                    "${progress.done}/${progress.total}",
                    if (progress.total > 0) progress.done.toFloat() / progress.total else 0f,
                )
            RecoveryProgress.CheckingWikidata, null ->
                Triple(stringResource(R.string.recovery_progress_checking), "", null)
        }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = typography.evidence)
            Text(text = counter, style = typography.evidence)
        }
        val progressModifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space2)
        if (fraction == null) {
            LinearProgressIndicator(modifier = progressModifier, color = colors.accent, trackColor = colors.accent200)
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = progressModifier,
                color = colors.accent,
                trackColor = colors.accent200,
            )
        }
    }
}

@Composable
private fun ReportSection(report: RecoveryReport) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val countRows =
        listOf(
            report.scannedCount to stringResource(R.string.recovery_row_scanned),
            report.findings.size to stringResource(R.string.recovery_row_recognized),
            report.count(RecoveryOutcome.ALREADY_LINKED) to stringResource(R.string.recovery_row_already_linked),
            report.count(RecoveryOutcome.LINK_ADDED) to stringResource(R.string.recovery_row_link_added),
            report.count(RecoveryOutcome.LINK_FAILED) to stringResource(R.string.recovery_row_link_failed),
            report.count(RecoveryOutcome.UNRESOLVED) to stringResource(R.string.recovery_row_unresolved),
        )
    WsTable(
        rows =
            countRows.map { (count, label) ->
                listOf(
                    { Text(text = count.toString(), style = typography.evidence) },
                    { Text(text = label, style = typography.secondary) },
                )
            },
    )
    if (report.findings.isEmpty()) {
        Text(
            text = stringResource(R.string.recovery_no_recordings),
            style = typography.secondary,
            color = colors.neutral700,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2)) {
        WsKicker(text = stringResource(R.string.recovery_findings_kicker))
        // What recovery mode changed, or couldn't, first; the files that were already fine last.
        report.findings.sortedBy { it.outcome.displayOrder }.forEach { FindingRow(it) }
    }
}

private val RecoveryOutcome.displayOrder: Int
    get() =
        when (this) {
            RecoveryOutcome.LINK_ADDED -> 0
            RecoveryOutcome.LINK_FAILED -> 1
            RecoveryOutcome.UNRESOLVED -> 2
            RecoveryOutcome.ALREADY_LINKED -> 3
        }

@Composable
private fun FindingRow(finding: RecoveryFinding) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val (tagText, tagVariant) =
        when (finding.outcome) {
            RecoveryOutcome.LINK_ADDED -> stringResource(R.string.recovery_tag_link_added) to WsTagVariant.ACCENT
            RecoveryOutcome.LINK_FAILED -> stringResource(R.string.recovery_tag_link_failed) to WsTagVariant.OUTLINE
            RecoveryOutcome.UNRESOLVED -> stringResource(R.string.recovery_tag_unresolved) to WsTagVariant.OUTLINE
            RecoveryOutcome.ALREADY_LINKED -> stringResource(R.string.recovery_tag_already_linked) to WsTagVariant.NEUTRAL
        }
    BlueprintBox(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = finding.targetId, style = typography.evidence, color = colors.neutral700)
                WsTag(text = tagText, variant = tagVariant)
            }
            Text(text = finding.filename, style = typography.secondary)
            finding.message?.let { Text(text = it, style = typography.caption, color = colors.neutral700) }
        }
    }
}
