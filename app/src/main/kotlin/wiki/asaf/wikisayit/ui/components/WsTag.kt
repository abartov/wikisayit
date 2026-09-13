package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

enum class WsTagVariant { ACCENT, NEUTRAL, OUTLINE }

/** A `tag` chip — language readings, statuses (`retrying`, `redo`), item/form kind. */
@Composable
fun WsTag(
    text: String,
    modifier: Modifier = Modifier,
    variant: WsTagVariant = WsTagVariant.ACCENT,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    val background: Color
    val foreground: Color
    val borderColor: Color?
    when (variant) {
        WsTagVariant.ACCENT -> {
            background = colors.accent100
            foreground = colors.accent800
            borderColor = null
        }
        WsTagVariant.NEUTRAL -> {
            background = colors.neutral100
            foreground = colors.neutral800
            borderColor = null
        }
        WsTagVariant.OUTLINE -> {
            background = Color.Transparent
            foreground = colors.accent
            borderColor = colors.accent
        }
    }
    Text(
        text = text,
        style = typography.caption,
        color = foreground,
        modifier =
            modifier
                .let { if (borderColor != null) it.border(1.dp, borderColor) else it }
                .background(background)
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
