package wiki.asaf.wikisayit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.auth.TokenStore
import wiki.asaf.wikisayit.data.language.LanguageCatalog
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.profile.ProfileLanguageInput
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import javax.inject.Inject

data class NewProfileUiState(
    val username: String = "",
    val speakerName: String = "",
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
 * Backs the new-profile form (`2b`). The username is the signed-in OAuth identity from
 * [TokenStore] — locked, not editable, matching the design's "verified" card.
 */
@HiltViewModel
class NewProfileViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val tokenStore: TokenStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(NewProfileUiState())
        val uiState: StateFlow<NewProfileUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val username = tokenStore.tokens.first()?.username.orEmpty()
                _uiState.update { it.copy(username = username) }
            }
        }

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

        fun updateSpeakerName(value: String) = _uiState.update { it.copy(speakerName = value) }

        fun updateDraftLanguageName(value: String) =
            _uiState.update { it.copy(draftLanguageName = value, draftIsoCode = "") }

        fun selectDraftLanguage(option: LanguageCatalog.LanguageOption) =
            _uiState.update { it.copy(draftLanguageName = option.displayName, draftIsoCode = option.isoCode) }

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
                profileRepository.saveProfile(
                    wikimediaUsername = state.username.trim(),
                    languages = state.languages,
                    speakerName = state.speakerName.trim(),
                )
                _uiState.update { it.copy(saved = true) }
            }
        }
    }
