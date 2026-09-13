package wiki.asaf.wikisayit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import wiki.asaf.wikisayit.R

val Barlow =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

val BarlowCondensed =
    FontFamily(
        Font(R.font.barlow_condensed_regular, FontWeight.Normal),
        Font(R.font.barlow_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    )

/** Evidence text — Wikidata ids, filenames, counters — is always fixed-width system monospace. */
val MonospaceEvidence = FontFamily.Monospace

private const val HEADING_WEIGHT_VALUE = 600
val HeadingWeight = FontWeight(HEADING_WEIGHT_VALUE)

/**
 * Text styles that don't map cleanly onto Material3's type roles — this design's scale
 * (display word 54sp, h2 29sp, h3 23sp, card title 17sp, body 15sp/1.55, secondary 13sp,
 * caption 11.5sp, monospace evidence 10.5–12sp) is bespoke to the Industry system, so it's
 * exposed as its own CompositionLocal alongside Material3's [Typography] rather than forced
 * into displayLarge/headlineMedium/etc.
 */
data class WikiSayItTypography(
    val displayWord: TextStyle =
        TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 54.sp, lineHeight = 60.sp),
    val h2: TextStyle =
        TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 29.sp, lineHeight = 33.sp),
    val h3: TextStyle =
        TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 23.sp, lineHeight = 27.sp),
    val cardTitle: TextStyle =
        TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 17.sp, lineHeight = 20.sp),
    val buttonLabel: TextStyle =
        TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 15.sp, lineHeight = 18.sp),
    val stateLine: TextStyle =
        TextStyle(
            fontFamily = BarlowCondensed,
            fontWeight = HeadingWeight,
            fontSize = 13.sp,
            letterSpacing = 1.3.sp,
        ),
    val kicker: TextStyle =
        TextStyle(
            fontFamily = Barlow,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
        ),
    val body: TextStyle =
        TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    val secondary: TextStyle =
        TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    val caption: TextStyle =
        TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 16.sp),
    val evidence: TextStyle =
        TextStyle(fontFamily = MonospaceEvidence, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

val LocalWikiSayItTypography = staticCompositionLocalOf { WikiSayItTypography() }

/** Material3 roles used by stock components (TopAppBar, DropdownMenu, dialogs). */
val Typography =
    Typography(
        headlineMedium =
            TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 29.sp, lineHeight = 33.sp),
        titleLarge =
            TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 19.sp, lineHeight = 24.sp),
        titleMedium =
            TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 17.sp, lineHeight = 20.sp),
        bodyLarge =
            TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium =
            TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge =
            TextStyle(fontFamily = BarlowCondensed, fontWeight = HeadingWeight, fontSize = 15.sp, lineHeight = 18.sp),
    )
