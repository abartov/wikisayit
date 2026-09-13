package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import wiki.asaf.wikisayit.data.language.LanguageCatalog

/** Typing combobox for picking a language name from [LanguageCatalog]; selection fills in the ISO code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WsLanguageAutocompleteField(
    query: String,
    isoCode: String,
    onQueryChange: (String) -> Unit,
    onLanguageSelected: (LanguageCatalog.LanguageOption) -> Unit,
    label: @Composable () -> Unit,
    isoLabel: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(query) { LanguageCatalog.search(query) }
    val menuVisible = expanded && options.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = menuVisible,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            label = label,
            supportingText = { if (isoCode.isNotBlank()) Text("$isoLabel: ${isoCode.uppercase()}") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuVisible) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
        )
        ExposedDropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.displayName} (${option.isoCode.uppercase()})") },
                    onClick = {
                        onLanguageSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
