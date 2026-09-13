package wiki.asaf.wikisayit.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.stats.StatsRepository
import java.time.Clock
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class MonthRow(val yearMonth: String, val label: String, val items: Int, val forms: Int, val total: Int)

data class YearRow(val year: String, val total: Int)

data class StatsUiState(
    val totalRecordings: Int = 0,
    val totalItems: Int = 0,
    val totalForms: Int = 0,
    val currentYear: String = "",
    val currentYearRows: List<MonthRow> = emptyList(),
    val earlierYears: List<YearRow> = emptyList(),
)

@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        statsRepository: StatsRepository,
        clock: Clock = Clock.systemDefaultZone(),
    ) : ViewModel() {
        private val currentYear = YearMonth.now(clock).year

        val uiState: StateFlow<StatsUiState> =
            combine(
                statsRepository.observeTotalsByType(),
                statsRepository.observeMonthlyTotalsByType(),
            ) { totals, monthly ->
                val items = totals.firstOrNull { it.entryType == RecordingEntryType.WIKIDATA_ITEM }?.count ?: 0
                val forms = totals.firstOrNull { it.entryType == RecordingEntryType.LEXEME_FORM }?.count ?: 0

                val currentYearRows =
                    monthly
                        .groupBy { it.yearMonth }
                        .filterKeys { it.startsWith("$currentYear-") }
                        .map { (yearMonth, counts) ->
                            val monthItems =
                                counts.firstOrNull { it.entryType == RecordingEntryType.WIKIDATA_ITEM }?.count ?: 0
                            val monthForms =
                                counts.firstOrNull { it.entryType == RecordingEntryType.LEXEME_FORM }?.count ?: 0
                            MonthRow(
                                yearMonth = yearMonth,
                                label = monthLabel(yearMonth),
                                items = monthItems,
                                forms = monthForms,
                                total = monthItems + monthForms,
                            )
                        }
                        .sortedByDescending { it.yearMonth }

                val earlierYears =
                    monthly
                        .filterNot { it.yearMonth.startsWith("$currentYear-") }
                        .groupBy { it.yearMonth.take(4) }
                        .map { (year, counts) -> YearRow(year = year, total = counts.sumOf { it.count }) }
                        .sortedByDescending { it.year }

                StatsUiState(
                    totalRecordings = items + forms,
                    totalItems = items,
                    totalForms = forms,
                    currentYear = currentYear.toString(),
                    currentYearRows = currentYearRows,
                    earlierYears = earlierYears,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

        private fun monthLabel(yearMonth: String): String =
            YearMonth.parse(yearMonth).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
