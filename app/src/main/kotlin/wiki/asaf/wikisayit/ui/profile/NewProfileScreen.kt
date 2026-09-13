package wiki.asaf.wikisayit.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.language.LanguageCatalog
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.profile.ProfileLanguageInput
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsIconButton
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsLanguageAutocompleteField
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsSegmentedControl
import wiki.asaf.wikisayit.ui.components.WsTag
import wiki.asaf.wikisayit.ui.components.WsTagVariant
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing

/** New speaker profile (`2b`): the signed-in OAuth username, verified, plus the language sets from the spec. */
@Composable
fun NewProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    NewProfileScreenContent(
        uiState = uiState,
        onBack = onBack,
        onAddLanguage = viewModel::openAddLanguageSheet,
        onRemoveLanguage = viewModel::removeLanguageAt,
        onSave = viewModel::save,
        onDismissSheet = viewModel::closeAddLanguageSheet,
        onDraftNameChange = viewModel::updateDraftLanguageName,
        onLanguageSelected = viewModel::selectDraftLanguage,
        onDraftProficiencyChange = viewModel::updateDraftProficiency,
        onDraftDialectChange = viewModel::updateDraftDialect,
        onConfirmAddLanguage = viewModel::confirmAddLanguage,
        onSpeakerNameChange = viewModel::updateSpeakerName,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProfileScreenContent(
    uiState: NewProfileUiState,
    onBack: () -> Unit,
    onAddLanguage: () -> Unit,
    onRemoveLanguage: (Int) -> Unit,
    onSave: () -> Unit,
    onDismissSheet: () -> Unit,
    onDraftNameChange: (String) -> Unit,
    onLanguageSelected: (LanguageCatalog.LanguageOption) -> Unit,
    onDraftProficiencyChange: (LanguageProficiency) -> Unit,
    onDraftDialectChange: (String) -> Unit,
    onConfirmAddLanguage: () -> Unit,
    onSpeakerNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(WikiSayItSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WsIconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Spacer(modifier = Modifier.width(WikiSayItSpacing.space2))
            Text(text = stringResource(R.string.new_profile_title), style = typography.cardTitle)
        }
        WsHairlineDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
        ) {
            item {
                BlueprintBox(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            WsKicker(text = stringResource(R.string.new_profile_username_label))
                            Text(
                                text = uiState.username,
                                style = typography.cardTitle,
                                modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                            )
                        }
                        WsTag(text = stringResource(R.string.new_profile_verified_tag), variant = WsTagVariant.ACCENT)
                    }
                }
            }
            item {
                Column {
                    OutlinedTextField(
                        value = uiState.speakerName,
                        onValueChange = onSpeakerNameChange,
                        label = { Text(stringResource(R.string.new_profile_speaker_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(R.string.new_profile_speaker_name_explainer),
                        style = typography.caption,
                        color = colors.neutral700,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
            item {
                Column {
                    WsKicker(text = stringResource(R.string.new_profile_languages_heading), color = colors.ink)
                    Text(
                        text = stringResource(R.string.new_profile_languages_explainer),
                        style = typography.caption,
                        color = colors.neutral700,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
            itemsIndexed(uiState.languages) { index, language ->
                LanguageRow(language = language, onRemove = { onRemoveLanguage(index) })
            }
            item {
                WsSecondaryButton(
                    text = stringResource(R.string.new_profile_add_language_button),
                    onClick = onAddLanguage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.new_profile_footer_note),
                    style = typography.caption,
                    color = colors.neutral700,
                )
            }
        }

        WsHairlineDivider()
        WsPrimaryButton(
            text = stringResource(R.string.new_profile_save_button),
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.screenHorizontal),
            minHeight = 50.dp,
        )
    }

    if (uiState.isAddSheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = onDismissSheet, sheetState = sheetState) {
            AddLanguageSheetContent(
                uiState = uiState,
                onDraftNameChange = onDraftNameChange,
                onLanguageSelected = onLanguageSelected,
                onDraftProficiencyChange = onDraftProficiencyChange,
                onDraftDialectChange = onDraftDialectChange,
                onCancel = onDismissSheet,
                onConfirm = onConfirmAddLanguage,
            )
        }
    }
}

@Composable
private fun LanguageRow(
    language: ProfileLanguageInput,
    onRemove: () -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val proficiencyLabel =
        if (language.proficiency == LanguageProficiency.NATIVE) {
            stringResource(R.string.proficiency_native_titlecase)
        } else {
            stringResource(R.string.proficiency_proficient_titlecase)
        }
    val dialectLabel = language.dialect.ifBlank { stringResource(R.string.dialect_standard) }

    BlueprintBox(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
        ) {
            Text(
                text = language.isoCode,
                style = typography.evidence,
                color = colors.accent700,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = language.languageName, style = typography.cardTitle)
                Text(
                    text = "$proficiencyLabel · $dialectLabel",
                    style = typography.caption,
                    color = colors.neutral700,
                )
            }
            WsIconButton(onClick = onRemove, size = 36.dp) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.new_profile_remove_language, language.languageName),
                    tint = colors.neutral600,
                )
            }
        }
    }
}

@Composable
private fun AddLanguageSheetContent(
    uiState: NewProfileUiState,
    onDraftNameChange: (String) -> Unit,
    onLanguageSelected: (LanguageCatalog.LanguageOption) -> Unit,
    onDraftProficiencyChange: (LanguageProficiency) -> Unit,
    onDraftDialectChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
    ) {
        Text(text = stringResource(R.string.add_language_title), style = typography.h3)
        WsLanguageAutocompleteField(
            query = uiState.draftLanguageName,
            isoCode = uiState.draftIsoCode,
            onQueryChange = onDraftNameChange,
            onLanguageSelected = onLanguageSelected,
            label = { Text(stringResource(R.string.add_language_name_label)) },
            isoLabel = stringResource(R.string.add_language_iso_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Column {
            Text(text = stringResource(R.string.add_language_proficiency_label), style = typography.caption)
            Spacer(modifier = Modifier.padding(top = WikiSayItSpacing.space1))
            WsSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.proficiency_native_titlecase),
                        stringResource(R.string.proficiency_proficient_titlecase),
                    ),
                selectedIndex = if (uiState.draftProficiency == LanguageProficiency.NATIVE) 0 else 1,
                onSelect = {
                    onDraftProficiencyChange(
                        if (it == 0) LanguageProficiency.NATIVE else LanguageProficiency.PROFICIENT,
                    )
                },
            )
        }
        OutlinedTextField(
            value = uiState.draftDialect,
            onValueChange = onDraftDialectChange,
            label = { Text(stringResource(R.string.add_language_dialect_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2), modifier = Modifier.fillMaxWidth()) {
            WsSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            WsPrimaryButton(
                text = stringResource(R.string.action_add),
                onClick = onConfirm,
                enabled = uiState.canAddDraft,
                blueprint = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
