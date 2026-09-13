package wiki.asaf.wikisayit.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.components.WsTable
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Stats (`1a`): total tiles plus monthly/yearly breakdown tables, backed by real Room data. */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(uiState = uiState, onBack = onBack, modifier = modifier)
}

@Composable
private fun StatsContent(
    uiState: StatsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Text(text = stringResource(R.string.stats_title), style = typography.h2)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    value = uiState.totalRecordings,
                    label = stringResource(R.string.stats_recordings),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = uiState.totalItems,
                    label = stringResource(R.string.stats_items),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = uiState.totalForms,
                    label = stringResource(R.string.stats_forms),
                    modifier = Modifier.weight(1f),
                )
            }
            if (uiState.currentYearRows.isNotEmpty()) {
                Text(text = uiState.currentYear, style = typography.stateLine, modifier = Modifier.padding(top = 20.dp))
                WsTable(
                    modifier = Modifier.padding(top = 4.dp),
                    headers =
                        listOf(
                            stringResource(R.string.stats_col_month),
                            stringResource(R.string.stats_col_items),
                            stringResource(R.string.stats_col_forms),
                            stringResource(R.string.stats_col_all),
                        ),
                    rows =
                        uiState.currentYearRows.map { row ->
                            listOf(
                                { Text(text = row.label, style = typography.secondary) },
                                { NumberCell(row.items) },
                                { NumberCell(row.forms) },
                                { NumberCell(row.total) },
                            )
                        },
                )
            }
            if (uiState.earlierYears.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.stats_earlier_years),
                    style = typography.stateLine,
                    modifier = Modifier.padding(top = 22.dp),
                )
                WsTable(
                    modifier = Modifier.padding(top = 4.dp),
                    rows =
                        uiState.earlierYears.map { row ->
                            listOf(
                                { Text(text = row.year, style = typography.secondary) },
                                { NumberCell(row.total) },
                            )
                        },
                )
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

@Composable
private fun NumberCell(value: Int) {
    val typography = LocalWikiSayItTypography.current
    Text(
        text = value.toString(),
        style = typography.evidence,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatTile(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    BlueprintBox(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = value.toString(), style = typography.h3.copy(fontSize = 34.sp))
            Text(text = label.uppercase(), style = typography.caption, color = colors.neutral700)
        }
    }
}
