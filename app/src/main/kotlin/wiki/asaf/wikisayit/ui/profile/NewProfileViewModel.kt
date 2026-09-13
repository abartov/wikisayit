package wiki.asaf.wikisayit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.profile.ProfileLanguageInput
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import javax.inject.Inject

data class NewProfileUiState(
    val username: String = "",
    val languages: List<ProfileLanguageInput> = emptyList(),
    val isAddSheetOpen: Boolean = false,
    val draftLanguageName: String = "",
    val draftIsoCode: String = "",
    val draftProficiency: LanguageProficiency = LanguageProficiency.NATIVE,
    val draftDialect: String = "",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = username.isNotBlank() && languages.isNotEmpty()
    val canAddDraft: Boolean get() = draftLanguageName.isNotBlank() && draftIsoCode.isNotBlank()
}

/**
 * Backs the new-profile form (`2b`). The username field stands in for the OAuth identity
 * until s-fx6.1 lands — it's a plain editable field here rather than the design's locked
 * "verified" card, since faking that badge without real OAuth would be dishonest.
 */
@HiltViewModel
class NewProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NewProfileUiState())
        val uiState: StateFlow<NewProfileUiState> = _uiState.asStateFlow()

        fun updateUsername(value: String) = _uiState.update { it.copy(username = value) }

        fun openAddLanguageSheet() =
            _uiState.update {
                it.copy(
                    isAddSheetOpen = true,
                    draftLanguageName = "",
                    draftIsoCode = "",
                    draftProficiency = LanguageProficiency.NATIVE,
                    draftDialect = "",
                )
            }

        fun closeAddLanguageSheet() = _uiState.update { it.copy(isAddSheetOpen = false) }

        fun updateDraftLanguageName(value: String) = _uiState.update { it.copy(draftLanguageName = value) }

        fun updateDraftIsoCode(value: String) = _uiState.update { it.copy(draftIsoCode = value) }

        fun updateDraftProficiency(value: LanguageProficiency) = _uiState.update { it.copy(draftProficiency = value) }

        fun updateDraftDialect(value: String) = _uiState.update { it.copy(draftDialect = value) }

        fun confirmAddLanguage() {
            val state = _uiState.value
            if (!state.canAddDraft) return
            val entry =
                ProfileLanguageInput(
                    isoCode = state.draftIsoCode.trim().lowercase(),
                    languageName = state.draftLanguageName.trim(),
                    proficiency = state.draftProficiency,
                    dialect = state.draftDialect.trim(),
                )
            _uiState.update { it.copy(languages = it.languages + entry, isAddSheetOpen = false) }
        }

        fun removeLanguageAt(index: Int) {
            _uiState.update { it.copy(languages = it.languages.filterIndexed { i, _ -> i != index }) }
        }

        fun save() {
            val state = _uiState.value
            if (!state.canSave) return
            viewModelScope.launch {
                profileRepository.saveProfile(wikimediaUsername = state.username.trim(), languages = state.languages)
                _uiState.update { it.copy(saved = true) }
            }
        }
    }
