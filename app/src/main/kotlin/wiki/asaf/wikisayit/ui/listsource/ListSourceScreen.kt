package wiki.asaf.wikisayit.ui.listsource

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.wikidata.CannedSparqlQuery
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsSegmentedControl
import wiki.asaf.wikisayit.ui.components.WsTable
import wiki.asaf.wikisayit.ui.session.CategoryDepth
import wiki.asaf.wikisayit.ui.session.DisambiguationCandidate
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.ListBuildStage
import wiki.asaf.wikisayit.ui.session.ListSourceType
import wiki.asaf.wikisayit.ui.session.MatchAs
import wiki.asaf.wikisayit.ui.session.RecordingFlowUiState
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.MonospaceEvidence
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing

/**
 * Step 3+4 of 4 (`1a` pick-then-form, plus the fresh/checking/checked/empty sub-states and the
 * `2d` empty outcome) — all on one route, matching the design's single conceptual "list" step.
 */
@Composable
fun ListSourceScreen(
    viewModel: RecordingFlowViewModel,
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.listBuildStage) {
        ListBuildStage.PICK_SOURCE ->
            SourcePickContent(onPick = viewModel::selectSourceType, modifier = modifier)
        ListBuildStage.SOURCE_FORM ->
            SourceFormContent(
                uiState = uiState,
                onTextChange = viewModel::updateSourceText,
                onMatchAsChange = viewModel::updateMatchAs,
                onDepthChange = viewModel::updateCategoryDepth,
                onCannedQueryPick = viewModel::applyCannedQuery,
                onBack = viewModel::backToSourcePick,
                onBuildList = viewModel::buildList,
                modifier = modifier,
            )
        ListBuildStage.RESOLVING ->
            ResolvingContent(uiState = uiState, modifier = modifier)
        ListBuildStage.DISAMBIGUATING ->
            DisambiguationContent(
                uiState = uiState,
                onPick = viewModel::resolveDisambiguation,
                onRecordAll = viewModel::resolveDisambiguationRecordAll,
                onBack = viewModel::backFromDisambiguation,
                modifier = modifier,
            )
        ListBuildStage.FRESH, ListBuildStage.CHECKING, ListBuildStage.CHECKED ->
            ListCheckContent(
                uiState = uiState,
                onStartCheck = viewModel::startCheck,
                onSkipCheck = viewModel::skipCheck,
                onBack = viewModel::backToSourceForm,
                onStart = {
                    viewModel.startSession()
                    onStartRecording()
                },
                modifier = modifier,
            )
        ListBuildStage.EMPTY ->
            EmptyOutcomeContent(
                uiState = uiState,
                onPickDifferentList = viewModel::pickDifferentList,
                onRecordAnyway = {
                    viewModel.recordAsSecondTakes()
                    viewModel.startSession()
                    onStartRecording()
                },
                modifier = modifier,
            )
    }
}

@Composable
private fun ScreenHeader(
    kicker: String,
    title: String,
    explainer: String? = null,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    Column(
        modifier =
            Modifier.padding(
                horizontal = WikiSayItSpacing.screenHorizontal,
                vertical = WikiSayItSpacing.screenTop,
            ),
    ) {
        WsKicker(text = kicker)
        Text(text = title, style = typography.h2)
        if (explainer != null) {
            Text(
                text = explainer,
                style = typography.secondary,
                color = colors.neutral700,
                modifier = Modifier.padding(top = WikiSayItSpacing.space1),
            )
        }
    }
}

@Composable
private fun SourcePickContent(
    onPick: (ListSourceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            kicker = stringResource(R.string.list_source_step_kicker),
            title = stringResource(R.string.list_source_title),
            explainer = stringResource(R.string.list_source_explainer),
        )
        Column(
            modifier = Modifier.padding(horizontal = WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space4),
        ) {
            SourceCard(
                kicker = stringResource(R.string.source_paste_kicker),
                title = stringResource(R.string.source_paste_title),
                body = stringResource(R.string.source_paste_body),
                onClick = { onPick(ListSourceType.PASTE) },
            )
            SourceCard(
                kicker = stringResource(R.string.source_query_kicker),
                title = stringResource(R.string.source_query_title),
                body = stringResource(R.string.source_query_body),
                onClick = { onPick(ListSourceType.QUERY) },
            )
            SourceCard(
                kicker = stringResource(R.string.source_category_kicker),
                title = stringResource(R.string.source_category_title),
                body = stringResource(R.string.source_category_body),
                onClick = { onPick(ListSourceType.CATEGORY) },
            )
        }
    }
}

@Composable
private fun SourceCard(
    kicker: String,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    BlueprintBox(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsKicker(text = kicker)
            Text(text = title, style = typography.cardTitle)
            Text(text = body, style = typography.secondary)
        }
    }
}

@Composable
private fun SourceFormContent(
    uiState: RecordingFlowUiState,
    onTextChange: (String) -> Unit,
    onMatchAsChange: (MatchAs) -> Unit,
    onDepthChange: (CategoryDepth) -> Unit,
    onCannedQueryPick: (CannedSparqlQuery) -> Unit,
    onBack: () -> Unit,
    onBuildList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val sourceType = uiState.listSourceType ?: ListSourceType.PASTE
    val sourceName =
        when (sourceType) {
            ListSourceType.PASTE -> stringResource(R.string.source_name_paste)
            ListSourceType.QUERY -> stringResource(R.string.source_name_query)
            ListSourceType.CATEGORY -> stringResource(R.string.source_name_category)
        }
    val formTitle =
        when (sourceType) {
            ListSourceType.PASTE -> stringResource(R.string.form_title_paste)
            ListSourceType.QUERY -> stringResource(R.string.form_title_query)
            ListSourceType.CATEGORY -> stringResource(R.string.form_title_category)
        }
    val formLabel =
        when (sourceType) {
            ListSourceType.PASTE -> stringResource(R.string.form_label_paste)
            ListSourceType.QUERY -> stringResource(R.string.form_label_query)
            ListSourceType.CATEGORY ->
                stringResource(R.string.form_label_category, uiState.language?.isoCode?.ifBlank { "en" } ?: "en")
        }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            kicker = stringResource(R.string.form_step_kicker, sourceName),
            title = formTitle,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space4),
        ) {
            if (sourceType == ListSourceType.QUERY) {
                CannedQueryPicker(onPick = onCannedQueryPick)
            }
            OutlinedTextField(
                value = uiState.sourceText,
                onValueChange = onTextChange,
                label = { Text(formLabel) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                textStyle = TextStyle(fontFamily = MonospaceEvidence, fontSize = 12.5.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
            if (sourceType == ListSourceType.PASTE) {
                Column {
                    Text(
                        text = stringResource(R.string.match_as_label),
                        style = LocalWikiSayItTypography.current.caption,
                    )
                    WsSegmentedControl(
                        options =
                            listOf(
                                stringResource(R.string.match_as_items),
                                stringResource(R.string.match_as_lexemes),
                            ),
                        selectedIndex = if (uiState.matchAs == MatchAs.ITEMS) 0 else 1,
                        onSelect = { onMatchAsChange(if (it == 0) MatchAs.ITEMS else MatchAs.LEXEMES) },
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                    Text(
                        text = stringResource(R.string.match_as_note),
                        style = LocalWikiSayItTypography.current.caption,
                        color = colors.neutral700,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
            if (sourceType == ListSourceType.CATEGORY) {
                Column {
                    Text(text = stringResource(R.string.depth_label), style = LocalWikiSayItTypography.current.caption)
                    WsSegmentedControl(
                        options =
                            listOf(
                                stringResource(R.string.depth_none),
                                stringResource(R.string.depth_two),
                                stringResource(R.string.depth_five),
                            ),
                        selectedIndex = CategoryDepth.entries.indexOf(uiState.categoryDepth),
                        onSelect = { onDepthChange(CategoryDepth.entries[it]) },
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                    Text(
                        text = stringResource(R.string.depth_note),
                        style = LocalWikiSayItTypography.current.caption,
                        color = colors.neutral700,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
        }
        WsHairlineDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsSecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            WsPrimaryButton(
                text = stringResource(R.string.list_source_build_button),
                onClick = onBuildList,
                enabled = uiState.sourceText.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CannedQueryPicker(onPick: (CannedSparqlQuery) -> Unit) {
    Column {
        Text(text = stringResource(R.string.canned_query_label), style = LocalWikiSayItTypography.current.caption)
        FlowRow(
            modifier = Modifier.padding(top = WikiSayItSpacing.space1),
            horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
        ) {
            CannedQueryChip(CannedSparqlQuery.ALL_LEXEMES, stringResource(R.string.canned_query_all_lexemes), onPick)
            CannedQueryChip(CannedSparqlQuery.NOUNS, stringResource(R.string.canned_query_nouns), onPick)
            CannedQueryChip(CannedSparqlQuery.VERBS, stringResource(R.string.canned_query_verbs), onPick)
            CannedQueryChip(CannedSparqlQuery.ADJECTIVES, stringResource(R.string.canned_query_adjectives), onPick)
            CannedQueryChip(CannedSparqlQuery.ADVERBS, stringResource(R.string.canned_query_adverbs), onPick)
            CannedQueryChip(CannedSparqlQuery.PHRASES, stringResource(R.string.canned_query_phrases), onPick)
        }
    }
}

@Composable
private fun CannedQueryChip(
    query: CannedSparqlQuery,
    label: String,
    onPick: (CannedSparqlQuery) -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Text(
        text = label,
        style = typography.caption,
        color = colors.accent700,
        modifier =
            Modifier
                .clickable(onClick = { onPick(query) })
                .border(1.dp, colors.accent)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ResolvingContent(
    uiState: RecordingFlowUiState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val showLineProgress = uiState.listSourceType == ListSourceType.PASTE
    val title =
        when (uiState.listSourceType) {
            ListSourceType.QUERY -> stringResource(R.string.resolving_title_query)
            ListSourceType.CATEGORY -> stringResource(R.string.resolving_title_category)
            else -> stringResource(R.string.resolving_title)
        }
    val explainer =
        when (uiState.listSourceType) {
            ListSourceType.QUERY -> stringResource(R.string.resolving_explainer_query)
            ListSourceType.CATEGORY -> stringResource(R.string.resolving_explainer_category)
            else -> stringResource(R.string.resolving_explainer)
        }
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            kicker = stringResource(R.string.list_step_kicker),
            title = title,
            explainer = explainer,
        )
        Column(modifier = Modifier.padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
            if (!showLineProgress) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.accent,
                    trackColor = colors.accent200,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.resolving_label),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    )
                    Text(
                        text = "${uiState.resolveDone}/${uiState.rawCount}",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    )
                }
                val progress = if (uiState.rawCount > 0) uiState.resolveDone.toFloat() / uiState.rawCount else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space2),
                    color = colors.accent,
                    trackColor = colors.accent200,
                )
            }
        }
    }
}

@Composable
private fun DisambiguationContent(
    uiState: RecordingFlowUiState,
    onPick: (DisambiguationCandidate) -> Unit,
    onRecordAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val case = uiState.currentDisambiguation ?: return
    val explainer =
        if (case.kind == EntryKind.ITEM) {
            stringResource(R.string.disambiguation_explainer_items, case.candidates.size)
        } else {
            stringResource(R.string.disambiguation_explainer_lexemes, case.candidates.size)
        }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                kicker =
                    stringResource(
                        R.string.disambiguation_kicker,
                        uiState.disambiguationIndex + 1,
                        uiState.disambiguationQueue.size,
                    ),
                title = case.originalLabel,
                explainer = explainer,
            )
            Column(
                modifier = Modifier.padding(horizontal = WikiSayItSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
            ) {
                case.candidates.forEach { candidate ->
                    DisambiguationCandidateCard(candidate = candidate, onClick = { onPick(candidate) })
                }
                WsGhostButton(
                    text = stringResource(R.string.disambiguation_record_all_button, case.candidates.size),
                    onClick = onRecordAll,
                    modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space1),
                )
            }
        }
        WsHairlineDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal)) {
            WsSecondaryButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DisambiguationCandidateCard(
    candidate: DisambiguationCandidate,
    onClick: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    BlueprintBox(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
        ) {
            Text(text = candidate.label, style = typography.cardTitle)
            if (!candidate.description.isNullOrBlank()) {
                Text(text = candidate.description, style = typography.secondary)
            }
            Text(
                text = candidate.id,
                style = TextStyle(fontFamily = MonospaceEvidence, fontSize = 10.5.sp),
                color = colors.neutral700,
            )
        }
    }
}

@Composable
private fun ListCheckContent(
    uiState: RecordingFlowUiState,
    onStartCheck: () -> Unit,
    onSkipCheck: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val blurb =
        when (uiState.listSourceType) {
            ListSourceType.QUERY -> stringResource(R.string.list_blurb_query)
            ListSourceType.CATEGORY -> stringResource(R.string.list_blurb_category)
            else -> stringResource(R.string.list_blurb_paste)
        }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                kicker = stringResource(R.string.list_step_kicker),
                title = pluralStringResource(R.plurals.list_entries_found_count, uiState.rawCount, uiState.rawCount),
                explainer = blurb,
            )
            Column(modifier = Modifier.padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
                if (uiState.listBuildHadError) {
                    Text(
                        text = stringResource(R.string.list_build_partial_error_banner),
                        style = typography.caption,
                        color = colors.accent800,
                        modifier = Modifier.padding(bottom = WikiSayItSpacing.space3),
                    )
                }
                BlueprintBox(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
                        verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
                    ) {
                        WsKicker(text = stringResource(R.string.worth_checking_kicker))
                        Text(text = stringResource(R.string.worth_checking_body), style = typography.secondary)
                    }
                }
                if (uiState.listBuildStage == ListBuildStage.CHECKING) {
                    Column(modifier = Modifier.padding(top = WikiSayItSpacing.space4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.checking_label),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            )
                            Text(
                                text = "${uiState.checkDone}/${uiState.rawCount}",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            )
                        }
                        val progress = if (uiState.rawCount > 0) uiState.checkDone.toFloat() / uiState.rawCount else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space2),
                            color = colors.accent,
                            trackColor = colors.accent200,
                        )
                    }
                }
                if (uiState.listBuildStage == ListBuildStage.CHECKED) {
                    val finalCount = uiState.rawCount - uiState.excludedCount + uiState.formsAddedCount
                    WsTable(
                        modifier = Modifier.padding(top = WikiSayItSpacing.space4),
                        rows =
                            listOf(
                                listOf(
                                    { EvidenceCell(uiState.rawCount.toString()) },
                                    { Text(stringResource(R.string.check_row_entries), style = typography.secondary) },
                                ),
                                listOf(
                                    { EvidenceCell("−${uiState.excludedCount}", color = colors.neutral600) },
                                    {
                                        Text(
                                            stringResource(R.string.check_row_excluded),
                                            style = typography.secondary,
                                            color = colors.neutral600,
                                        )
                                    },
                                ),
                                listOf(
                                    { EvidenceCell("+${uiState.formsAddedCount}", color = colors.accent700) },
                                    {
                                        Text(
                                            stringResource(R.string.check_row_forms_added),
                                            style = typography.secondary,
                                            color = colors.accent700,
                                        )
                                    },
                                ),
                                listOf(
                                    { EvidenceCell(finalCount.toString(), emphasized = true) },
                                    { Text(stringResource(R.string.check_row_final), style = typography.secondary) },
                                ),
                            ),
                    )
                }
            }
        }
        WsHairlineDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            if (uiState.listBuildStage != ListBuildStage.CHECKING) {
                WsSecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (uiState.listBuildStage == ListBuildStage.FRESH) {
                WsPrimaryButton(
                    text = stringResource(R.string.check_button),
                    onClick = onStartCheck,
                    modifier = Modifier.fillMaxWidth(),
                )
                WsGhostButton(
                    text = stringResource(R.string.skip_check_button),
                    onClick = onSkipCheck,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (uiState.listBuildStage == ListBuildStage.CHECKED) {
                WsPrimaryButton(
                    text = stringResource(R.string.start_button),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52.dp,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun EvidenceCell(
    text: String,
    color: Color = LocalWikiSayItColors.current.ink,
    emphasized: Boolean = false,
) {
    val typography = LocalWikiSayItTypography.current
    Text(
        text = text,
        style = if (emphasized) typography.evidence.copy(fontSize = 17.sp) else typography.evidence,
        color = color,
    )
}

@Composable
private fun EmptyOutcomeContent(
    uiState: RecordingFlowUiState,
    onPickDifferentList: () -> Unit,
    onRecordAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val hadError = uiState.listBuildHadError
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                kicker =
                    stringResource(
                        if (hadError) R.string.list_build_error_kicker else R.string.empty_check_complete_kicker,
                    ),
                title =
                    if (hadError) {
                        stringResource(R.string.list_build_error_title)
                    } else {
                        pluralStringResource(R.plurals.empty_title, uiState.rawCount, uiState.rawCount)
                    },
                explainer = stringResource(if (hadError) R.string.list_build_error_body else R.string.empty_body),
            )
            Column(modifier = Modifier.padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
                if (!hadError) {
                    WsTable(
                        rows =
                            listOf(
                                listOf(
                                    { EvidenceCell(uiState.rawCount.toString()) },
                                    { Text(stringResource(R.string.check_row_entries), style = typography.secondary) },
                                ),
                                listOf(
                                    { EvidenceCell("0", emphasized = true) },
                                    { Text(stringResource(R.string.check_row_final), style = typography.secondary) },
                                ),
                            ),
                    )
                    BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space4)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
                        ) {
                            WsKicker(text = stringResource(R.string.empty_where_gaps_kicker))
                            Text(text = stringResource(R.string.empty_where_gaps_body), style = typography.secondary)
                        }
                    }
                }
            }
        }
        WsHairlineDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsPrimaryButton(
                text = stringResource(R.string.empty_pick_different_button),
                onClick = onPickDifferentList,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!hadError) {
                WsGhostButton(
                    text = stringResource(R.string.empty_record_anyway_button),
                    onClick = onRecordAnyway,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
