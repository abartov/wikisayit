package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** The `seg` control — a row of mutually-exclusive, equal-width square options. */
@Composable
fun WsSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.dp, colors.divider)
                .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.divider))
            }
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .background(if (selected) colors.accent else Color.Transparent)
                        .selectable(selected = selected, onClick = { onSelect(index) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = typography.secondary,
                    color = if (selected) colors.ground else colors.ink,
                )
            }
        }
    }
}
