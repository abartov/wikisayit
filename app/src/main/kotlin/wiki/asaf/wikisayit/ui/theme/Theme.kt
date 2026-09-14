package wiki.asaf.wikisayit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

// Square corners everywhere — cards, buttons, inputs, dialogs. Deliberate per the
// Industry design system; do not apply Material's default rounding.
private val WikiSayItShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ColorAccent,
        onPrimary = ColorGround,
        secondary = ColorAccent2,
        onSecondary = ColorGround,
        background = ColorGround,
        onBackground = ColorInk,
        surface = ColorSurface,
        onSurface = ColorInk,
        outline = ColorDivider,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = ColorAccent,
        onPrimary = ColorGround,
        secondary = ColorAccent2,
        onSecondary = ColorGround,
        background = Neutral900,
        onBackground = ColorGround,
        surface = Neutral800,
        onSurface = ColorGround,
        outline = ColorDivider,
    )

/**
 * The Industry brand is a fixed identity — steel accent on a technical ground — so unlike
 * the Compose template this replaces, there is no `dynamicColor` escape hatch: Material's
 * per-device wallpaper theming would override the brand the design system is built around.
 */
@Composable
fun WikiSayItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val wikiSayItColors = wikiSayItColors(darkTheme)
    val wikiSayItTypography = WikiSayItTypography()

    CompositionLocalProvider(
        LocalWikiSayItColors provides wikiSayItColors,
        LocalWikiSayItTypography provides wikiSayItTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WikiSayItShapes,
            content = content,
        )
    }
}
