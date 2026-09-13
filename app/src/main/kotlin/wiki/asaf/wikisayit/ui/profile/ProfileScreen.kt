package wiki.asaf.wikisayit.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.ProfileLanguageEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsHairlineDivider
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsTag
import wiki.asaf.wikisayit.ui.components.WsTagVariant
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme

/** Step 1 of 4 (`1a`): pick a stored speaker profile, or start a new one. */
@Composable
fun ProfileScreen(
    viewModel: RecordingFlowViewModel,
    onProfileSelected: () -> Unit,
    onNewProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.settings.autoUseLastProfile, uiState.profiles, uiState.activeProfile) {
        if (uiState.activeProfile == null && uiState.settings.autoUseLastProfile) {
            val last = uiState.profiles.firstOrNull { it.profile.id == uiState.settings.lastUsedProfileId }
            if (last != null) {
                viewModel.selectProfile(last)
                onProfileSelected()
            }
        }
    }

    ProfileScreenContent(
        profiles = uiState.profiles,
        recordingCountFor = viewModel::recordingCountFor,
        onPick = { profile ->
            viewModel.selectProfile(profile)
            onProfileSelected()
        },
        onNewProfile = onNewProfile,
        modifier = modifier,
    )
}

@Composable
private fun ProfileScreenContent(
    profiles: List<SpeakerProfileWithLanguages>,
    recordingCountFor: (Long) -> Flow<Int>,
    onPick: (SpeakerProfileWithLanguages) -> Unit,
    onNewProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = WikiSayItSpacing.screenHorizontal,
                    vertical = WikiSayItSpacing.screenTop,
                ),
        ) {
            WsKicker(text = stringResource(R.string.profile_step_kicker))
            Text(text = stringResource(R.string.profile_title), style = typography.h2)
            Text(
                text = stringResource(R.string.profile_explainer),
                style = typography.secondary,
                color = colors.neutral700,
                modifier = Modifier.padding(top = WikiSayItSpacing.space1),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space4),
        ) {
            items(profiles, key = { it.profile.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    recordingsFlow = recordingCountFor(profile.profile.id),
                    onClick = { onPick(profile) },
                )
            }
            item {
                WsSecondaryButton(
                    text = stringResource(R.string.profile_new_button),
                    onClick = onNewProfile,
                    modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space1),
                )
            }
        }
        WsHairlineDivider()
        Text(
            text = stringResource(R.string.profile_footer_note),
            style = typography.caption,
            color = colors.neutral700,
            modifier = Modifier.padding(WikiSayItSpacing.screenHorizontal),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    profile: SpeakerProfileWithLanguages,
    recordingsFlow: Flow<Int>,
    onClick: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val recordingsCount by recordingsFlow.collectAsStateWithLifecycle(initialValue = 0)
    val proficiencyLabels =
        mapOf(
            LanguageProficiency.NATIVE to stringResource(R.string.proficiency_native),
            LanguageProficiency.PROFICIENT to stringResource(R.string.proficiency_proficient),
        )
    BlueprintBox(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
            ) {
                Text(text = profile.profile.wikimediaUsername, style = typography.cardTitle)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = stringResource(R.string.profile_home_wikis),
                        style = typography.evidence,
                        color = colors.neutral600,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
                verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
            ) {
                profile.languages.forEach { language ->
                    val proficiency = proficiencyLabels[language.proficiency].orEmpty()
                    WsTag(text = "${language.languageName} — $proficiency", variant = WsTagVariant.ACCENT)
                }
            }
            Text(
                text = pluralStringResource(R.plurals.profile_recordings_count, recordingsCount, recordingsCount),
                style = typography.caption,
                color = colors.neutral600,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    val sampleProfiles =
        listOf(
            SpeakerProfileWithLanguages(
                profile = SpeakerProfileEntity(id = 1, wikimediaUsername = "Ijon"),
                languages =
                    listOf(
                        ProfileLanguageEntity(
                            profileId = 1,
                            isoCode = "he",
                            languageName = "Hebrew",
                            proficiency = LanguageProficiency.NATIVE,
                        ),
                        ProfileLanguageEntity(
                            profileId = 1,
                            isoCode = "en",
                            languageName = "English",
                            proficiency = LanguageProficiency.NATIVE,
                        ),
                    ),
            ),
        )
    WikiSayItTheme {
        ProfileScreenContent(
            profiles = sampleProfiles,
            recordingCountFor = { flowOf(418) },
            onPick = {},
            onNewProfile = {},
        )
    }
}
