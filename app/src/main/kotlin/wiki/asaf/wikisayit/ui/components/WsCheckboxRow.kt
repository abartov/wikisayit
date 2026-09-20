package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** The blueprint-style checkbox row used by Settings and by the list-source form: a square that
 * fills with the accent colour when ticked, a title, and an optional explainer under it. The
 * whole row is the tap target. */
@Composable
fun WsCheckboxRow(
    checked: Boolean,
    title: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    explainer: String? = null,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .border(1.5.dp, colors.accent)
                    .background(if (checked) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(text = "✓", color = colors.onAccent, style = typography.caption)
            }
        }
        Column {
            Text(text = title, style = typography.body)
            if (explainer != null) {
                Text(text = explainer, style = typography.caption, color = colors.neutral700)
            }
        }
    }
}
