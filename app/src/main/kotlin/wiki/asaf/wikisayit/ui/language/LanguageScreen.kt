package wiki.asaf.wikisayit.ui.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.ProfileLanguageEntity
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.SelectedLanguage
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme

/**
 * Step 2 of 4 (`1a`): pick which of the profile's languages to record in. Tapping goes straight
 * on. Skipped automatically when the profile only has one language to offer.
 */
@Composable
fun LanguageScreen(
    viewModel: RecordingFlowViewModel,
    onLanguagePicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = uiState.activeProfile ?: return

    LaunchedEffect(profile) {
        val onlyLanguage = profile.languages.singleOrNull() ?: return@LaunchedEffect
        viewModel.selectLanguage(
            SelectedLanguage(
                isoCode = onlyLanguage.isoCode,
                name = onlyLanguage.languageName,
                proficiency = onlyLanguage.proficiency,
                dialect = onlyLanguage.dialect,
            ),
        )
        onLanguagePicked()
    }

    LanguageScreenContent(
        username = profile.profile.wikimediaUsername,
        languages = profile.languages,
        onPick = { language ->
            viewModel.selectLanguage(
                SelectedLanguage(
                    isoCode = language.isoCode,
                    name = language.languageName,
                    proficiency = language.proficiency,
                    dialect = language.dialect,
                ),
            )
            onLanguagePicked()
        },
        modifier = modifier,
    )
}

@Composable
private fun LanguageScreenContent(
    username: String,
    languages: List<ProfileLanguageEntity>,
    onPick: (ProfileLanguageEntity) -> Unit,
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
            WsKicker(text = stringResource(R.string.language_step_kicker))
            Text(text = stringResource(R.string.language_title), style = typography.h2)
            Text(
                text = stringResource(R.string.language_explainer, username),
                style = typography.secondary,
                color = colors.neutral700,
                modifier = Modifier.padding(top = WikiSayItSpacing.space1),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = WikiSayItSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space3),
        ) {
            items(languages, key = { it.id }) { language ->
                LanguageRow(language = language, onClick = { onPick(language) })
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: ProfileLanguageEntity,
    onClick: () -> Unit,
) {
    val typography = LocalWikiSayItTypography.current
    val colors = LocalWikiSayItColors.current
    val proficiencyLabel =
        if (language.proficiency == LanguageProficiency.NATIVE) {
            stringResource(R.string.proficiency_native_titlecase)
        } else {
            stringResource(R.string.proficiency_proficient_titlecase)
        }
    val dialectLabel = language.dialect.ifBlank { stringResource(R.string.dialect_standard) }

    BlueprintBox(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WikiSayItSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space1),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
            ) {
                Text(
                    text = language.isoCode,
                    style = typography.evidence,
                    color = colors.accent700,
                )
                Text(text = language.languageName, style = typography.cardTitle)
            }
            Text(text = "$proficiencyLabel · $dialectLabel", style = typography.caption, color = colors.neutral700)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    WikiSayItTheme {
        LanguageScreenContent(
            username = "Ijon",
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
                        isoCode = "yi",
                        languageName = "Yiddish",
                        proficiency = LanguageProficiency.PROFICIENT,
                        dialect = "Litvish",
                    ),
                ),
            onPick = {},
        )
    }
}
