package wiki.asaf.wikisayit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors

/**
 * The "blueprint object" every card, figure and primary button in the Industry design
 * system is built from: a hairline border plus four `+` registration marks inset at the
 * corners. Built once here per the design handoff so no screen hand-places marks.
 */
@Composable
fun BlueprintBox(
    modifier: Modifier = Modifier,
    fillColor: Color = Color.Transparent,
    borderColor: Color = LocalWikiSayItColors.current.divider,
    showCornerMarks: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val markColor = LocalWikiSayItColors.current.cornerMark
    Box(
        modifier =
            modifier.drawBehind {
                drawRect(color = fillColor)
                drawRect(color = borderColor, style = Stroke(width = 1.dp.toPx()))
                if (showCornerMarks) {
                    val armLength = 5.5.dp.toPx()
                    val strokeWidth = 1.dp.toPx()
                    val corners =
                        listOf(
                            Offset(0f, 0f),
                            Offset(size.width, 0f),
                            Offset(0f, size.height),
                            Offset(size.width, size.height),
                        )
                    for (corner in corners) {
                        drawLine(
                            color = markColor,
                            start = Offset(corner.x - armLength, corner.y),
                            end = Offset(corner.x + armLength, corner.y),
                            strokeWidth = strokeWidth,
                        )
                        drawLine(
                            color = markColor,
                            start = Offset(corner.x, corner.y - armLength),
                            end = Offset(corner.x, corner.y + armLength),
                            strokeWidth = strokeWidth,
                        )
                    }
                }
            },
        content = content,
    )
}
