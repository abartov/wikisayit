package wiki.asaf.wikisayit.ui.settings

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.data.language.InterfaceLanguages
import wiki.asaf.wikisayit.data.local.settings.AppSettings
import wiki.asaf.wikisayit.ui.components.WsCheckboxRow
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsSegmentedControl
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

private val SILENCE_THRESHOLD_OPTIONS = listOf(1.0f, 1.5f, 2.5f)

/** Settings (`1a`): auto-use-last-profile, auto-trim, interface language, silence threshold and account. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val signedInUsername by viewModel.signedInUsername.collectAsStateWithLifecycle()
    val skippedCount by viewModel.skippedEntryCount.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    SettingsContent(
        settings = settings,
        signedInUsername = signedInUsername,
        skippedCount = skippedCount,
        onForgetSkipped = viewModel::forgetSkippedEntries,
        onToggleAutoUseLastProfile = { viewModel.setAutoUseLastProfile(!settings.autoUseLastProfile) },
        onToggleTrimSilence = { viewModel.setTrimSilenceAutomatically(!settings.trimSilenceAutomatically) },
        onInterfaceLanguageSelected = { tag ->
            viewModel.setInterfaceLanguageTag(tag)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val localeManager = activity?.getSystemService(android.app.LocaleManager::class.java)
                localeManager?.applicationLocales = android.os.LocaleList.forLanguageTags(tag)
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                activity?.recreate()
            }
        },
        onSilenceThresholdSelected = { viewModel.setSilenceThresholdSeconds(SILENCE_THRESHOLD_OPTIONS[it]) },
        onMaxListSizeChanged = viewModel::setMaxListSize,
        onBack = onBack,
        onLogOut = {
            scope.launch {
                viewModel.logOut()
                onLoggedOut()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    signedInUsername: String?,
    skippedCount: Int,
    onForgetSkipped: () -> Unit,
    onToggleAutoUseLastProfile: () -> Unit,
    onToggleTrimSilence: () -> Unit,
    onInterfaceLanguageSelected: (String) -> Unit,
    onSilenceThresholdSelected: (Int) -> Unit,
    onMaxListSizeChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
            Text(text = stringResource(R.string.settings_title), style = typography.h2)
            Column(modifier = Modifier.padding(top = 16.dp)) {
                WsCheckboxRow(
                    checked = settings.autoUseLastProfile,
                    title = stringResource(R.string.settings_auto_use_last_profile_title),
                    explainer = stringResource(R.string.settings_auto_use_last_profile_explainer),
                    onToggle = onToggleAutoUseLastProfile,
                )
                WsCheckboxRow(
                    checked = settings.trimSilenceAutomatically,
                    title = stringResource(R.string.settings_trim_silence_title),
                    explainer = stringResource(R.string.settings_trim_silence_explainer),
                    onToggle = onToggleTrimSilence,
                )
            }
            Column(modifier = Modifier.padding(top = 18.dp)) {
                Text(text = stringResource(R.string.settings_interface_language_label), style = typography.caption)
                InterfaceLanguageDropdown(
                    selectedTag = settings.interfaceLanguageTag,
                    onSelect = onInterfaceLanguageSelected,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.settings_interface_language_note),
                    style = typography.caption,
                    color = LocalWikiSayItColors.current.neutral700,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 18.dp)) {
                Text(text = stringResource(R.string.settings_silence_threshold_label), style = typography.caption)
                WsSegmentedControl(
                    options = SILENCE_THRESHOLD_OPTIONS.map { stringResource(R.string.settings_seconds_format, it) },
                    selectedIndex =
                        SILENCE_THRESHOLD_OPTIONS.indexOf(
                            settings.silenceThresholdSeconds,
                        ).coerceAtLeast(0),
                    onSelect = onSilenceThresholdSelected,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 18.dp)) {
                Text(text = stringResource(R.string.settings_max_list_size_label), style = typography.caption)
                MaxListSizeField(
                    maxListSize = settings.maxListSize,
                    onMaxListSizeChanged = onMaxListSizeChanged,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.settings_max_list_size_note),
                    style = typography.caption,
                    color = LocalWikiSayItColors.current.neutral700,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (skippedCount > 0) {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Text(text = stringResource(R.string.settings_skipped_label), style = typography.caption)
                    WsSecondaryButton(
                        text =
                            pluralStringResource(
                                R.plurals.settings_forget_skipped_button,
                                skippedCount,
                                skippedCount,
                            ),
                        onClick = onForgetSkipped,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_skipped_note),
                        style = typography.caption,
                        color = LocalWikiSayItColors.current.neutral700,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (signedInUsername != null) {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Text(text = stringResource(R.string.settings_account_label), style = typography.caption)
                    WsSecondaryButton(
                        text = stringResource(R.string.settings_log_out_button, signedInUsername),
                        onClick = onLogOut,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterfaceLanguageDropdown(
    selectedTag: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = InterfaceLanguages.options
    val selected = options.firstOrNull { it.tag == selectedTag } ?: options.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelect(option.tag)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MaxListSizeField(
    maxListSize: Int,
    onMaxListSizeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(maxListSize) { mutableStateOf(maxListSize.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digitsOnly = input.filter { it.isDigit() }
            text = digitsOnly
            digitsOnly.toIntOrNull()?.takeIf { it > 0 }?.let(onMaxListSizeChanged)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}
