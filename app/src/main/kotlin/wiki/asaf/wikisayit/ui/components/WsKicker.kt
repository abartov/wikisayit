package wiki.asaf.wikisayit.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/**
 * The uppercase, letter-spaced `card-kicker` / `h6` label — step counters ("Step 1 of 4"),
 * card kickers ("you will need", "now uploading"), and plain section headers ("Languages you
 * can record"). Defaults to accent (the common case); pass [color] for the plain-ink h6 rows.
 */
@Composable
fun WsKicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalWikiSayItColors.current.accent,
) {
    Text(
        text = text.uppercase(),
        style = LocalWikiSayItTypography.current.kicker,
        color = color,
        modifier = modifier,
    )
}
