package wiki.asaf.wikisayit.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Industry design system tokens — ported verbatim from
// design_handoff_wikisayit_flow/design/industry-styles.css. Do not retune here;
// retune the token sheet and re-port.

val ColorGround = Color(0xFFF2F2F3)
val ColorSurface = Color(0xFFE9E9EA)
val ColorInk = Color(0xFF1D1F20)
val ColorDivider = ColorInk.copy(alpha = 0.16f)

val ColorAccent = Color(0xFF5980A6)
val ColorAccent2 = Color(0xFF728FAB)

val Accent100 = Color(0xFFEEF6FF)
val Accent200 = Color(0xFFD6EBFF)
val Accent300 = Color(0xFFB5D9FD)
val Accent400 = Color(0xFF94BCE3)
val Accent500 = Color(0xFF749DC4)
val Accent600 = Color(0xFF597EA3)
val Accent700 = Color(0xFF416180)
val Accent800 = Color(0xFF2C455D)
val Accent900 = Color(0xFF1D2D3D)

val Neutral100 = Color(0xFFF5F5F8)
val Neutral200 = Color(0xFFE7E7EA)
val Neutral300 = Color(0xFFD4D4D7)
val Neutral400 = Color(0xFFB7B7BA)
val Neutral500 = Color(0xFF98989B)
val Neutral600 = Color(0xFF7A7A7D)
val Neutral700 = Color(0xFF5D5D60)
val Neutral800 = Color(0xFF424244)
val Neutral900 = Color(0xFF2B2B2D)

/**
 * The full Industry token ramp, exposed as a CompositionLocal because Material3's
 * [androidx.compose.material3.ColorScheme] only has room for a handful of semantic
 * roles — the blueprint primitives (corner marks, tags, tables) need direct access
 * to specific ramp steps the way the CSS does.
 */
data class WikiSayItColors(
    val ground: Color = ColorGround,
    val surface: Color = ColorSurface,
    val ink: Color = ColorInk,
    val divider: Color = ColorDivider,
    val accent: Color = ColorAccent,
    val accent2: Color = ColorAccent2,
    val accent100: Color = Accent100,
    val accent200: Color = Accent200,
    val accent300: Color = Accent300,
    val accent400: Color = Accent400,
    val accent500: Color = Accent500,
    val accent600: Color = Accent600,
    val accent700: Color = Accent700,
    val accent800: Color = Accent800,
    val accent900: Color = Accent900,
    val neutral100: Color = Neutral100,
    val neutral200: Color = Neutral200,
    val neutral300: Color = Neutral300,
    val neutral400: Color = Neutral400,
    val neutral500: Color = Neutral500,
    val neutral600: Color = Neutral600,
    val neutral700: Color = Neutral700,
    val neutral800: Color = Neutral800,
    val neutral900: Color = Neutral900,
    /** Corner registration marks: ink at 55% alpha. */
    val cornerMark: Color = ColorInk.copy(alpha = 0.55f),
)

val LocalWikiSayItColors = staticCompositionLocalOf { WikiSayItColors() }
