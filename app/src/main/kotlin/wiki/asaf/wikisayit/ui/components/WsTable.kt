package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing

/** A hairline row divider, the `table td { border-bottom }` rule. */
@Composable
fun WsHairlineDivider(modifier: Modifier = Modifier) {
    val colors = LocalWikiSayItColors.current
    Box(modifier.fillMaxWidth().height(1.dp).background(colors.divider))
}

/**
 * The `table` primitive — column headers are optional (the breakdown tables on the list-check
 * and empty-state screens have none), rows are lists of pre-built cells so callers keep full
 * control of per-cell alignment, color and font (right-aligned monospace counts, bold totals).
 */
@Composable
fun WsTable(
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    headers: List<String>? = null,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxWidth()) {
        headers?.let { headerLabels ->
            Row(Modifier.fillMaxWidth().padding(vertical = WikiSayItSpacing.space2)) {
                headerLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = typography.caption.copy(letterSpacing = 0.9.sp),
                        color = colors.neutral700,
                        textAlign = TextAlign.End,
                    )
                }
            }
            WsHairlineDivider()
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = WikiSayItSpacing.space2),
            ) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f)) { cell() }
                }
            }
            if (index != rows.lastIndex) WsHairlineDivider()
        }
    }
}
