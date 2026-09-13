package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Minimum comfortable touch target across the design's buttons; screens raise it up to 54dp. */
private val DefaultButtonMinHeight = 48.dp

@Composable
private fun WsButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    fillColor: Color,
    borderColor: Color,
    showCornerMarks: Boolean,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    BlueprintBox(
        modifier =
            modifier
                .heightIn(min = minHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        fillColor = fillColor,
        borderColor = borderColor,
        showCornerMarks = showCornerMarks,
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The solid accent button — the only filled object on screen. Set [blueprint] = false inside
 * dialogs/sheets that already carry their own blueprint frame. */
@Composable
fun WsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = DefaultButtonMinHeight,
    blueprint: Boolean = true,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    WsButtonSurface(
        onClick = onClick,
        modifier = modifier,
        minHeight = minHeight,
        fillColor = colors.accent,
        borderColor = colors.accent,
        showCornerMarks = blueprint,
        enabled = enabled,
    ) {
        val style =
            if (fontSize == TextUnit.Unspecified) {
                typography.buttonLabel
            } else {
                typography.buttonLabel.copy(fontSize = fontSize)
            }
        Text(text = text, style = style, color = colors.ground)
    }
}

@Composable
fun WsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = DefaultButtonMinHeight,
    enabled: Boolean = true,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    WsButtonSurface(
        onClick = onClick,
        modifier = modifier,
        minHeight = minHeight,
        fillColor = Color.Transparent,
        borderColor = colors.divider,
        showCornerMarks = false,
        enabled = enabled,
    ) {
        Text(text = text, style = typography.buttonLabel, color = colors.ink)
    }
}

/** Row content variant of [WsSecondaryButton], for rows that need a leading icon or a
 * trailing glyph (e.g. "Your Commons uploads ↗"). */
@Composable
fun WsSecondaryButtonRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = DefaultButtonMinHeight,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalWikiSayItColors.current
    WsButtonSurface(
        onClick = onClick,
        modifier = modifier,
        minHeight = minHeight,
        fillColor = Color.Transparent,
        borderColor = colors.divider,
        showCornerMarks = false,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun WsGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    enabled: Boolean = true,
    contentColor: Color = LocalWikiSayItColors.current.accent700,
) {
    val typography = LocalWikiSayItTypography.current
    Box(
        modifier =
            modifier
                .heightIn(min = minHeight)
                .alpha(if (enabled) 1f else 0.45f)
                .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
                .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = typography.secondary, color = contentColor)
    }
}

/** Square icon-only tap target (the hamburger, play/pause glyphs, dialog close, etc.). The
 * icon's own `contentDescription` carries accessibility text, matching Compose's [Icon] API. */
@Composable
fun WsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = size)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
