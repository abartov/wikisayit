package wiki.asaf.wikisayit.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.session.FlowScreen
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.commonsFilename
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography

/** Review carousel (`1a`): plays each take back in order, then a 1.5s window to Redo/Drop
 * before auto-approving and advancing. */
@Composable
fun ReviewScreen(
    viewModel: RecordingFlowViewModel,
    onNavigateRecording: () -> Unit,
    onNavigateSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.autoNavigateTo) {
        when (uiState.autoNavigateTo) {
            FlowScreen.RECORDING -> onNavigateRecording()
            FlowScreen.SUMMARY -> onNavigateSummary()
            else -> Unit
        }
    }

    val entry = uiState.currentReviewEntry ?: return
    ReviewContent(
        entry = entry,
        index = uiState.reviewIndex,
        total = uiState.reviewSession.size,
        isPlaying = uiState.reviewPlaying,
        decisionRemainingSeconds = uiState.reviewDecisionRemainingSeconds,
        isoCode = uiState.language?.isoCode.orEmpty(),
        username = uiState.username,
        onRedo = viewModel::reviewRedo,
        onDrop = viewModel::reviewDrop,
        onReplay = { viewModel.replayEntry(entry) },
        onBack = viewModel::backToRecordingFromReview,
        modifier = modifier,
    )
}

@Composable
private fun ReviewContent(
    entry: QueueEntry,
    index: Int,
    total: Int,
    isPlaying: Boolean,
    decisionRemainingSeconds: Float,
    isoCode: String,
    username: String,
    onRedo: () -> Unit,
    onDrop: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current

    val takeDurationSeconds = entry.durationSeconds ?: 0f
    val scrubber = remember(index) { Animatable(0f) }
    LaunchedEffect(index, isPlaying) {
        if (isPlaying) {
            scrubber.snapTo(0f)
            val durationMillis = (takeDurationSeconds * 1000).toInt().coerceAtLeast(1)
            scrubber.animateTo(1f, animationSpec = tween(durationMillis, easing = LinearEasing))
        }
    }
    val elapsedSeconds = scrubber.value * takeDurationSeconds

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.review_counter, index + 1, total),
                style = typography.evidence,
                color = colors.neutral700,
            )
            Text(
                text = stringResource(R.string.review_listen_back),
                style = typography.evidence,
                color = colors.neutral700,
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = entry.displayText, style = typography.h2.copy(fontSize = 48.sp), textAlign = TextAlign.Center)
            BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BlueprintBox(modifier = Modifier.size(40.dp)) {
                            IconButton(onClick = onReplay, modifier = Modifier.align(Alignment.Center)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = stringResource(R.string.review_listen_back),
                                    tint = colors.accent700,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(5.dp).background(colors.neutral300),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(scrubber.value).height(5.dp).background(colors.accent),
                                )
                            }
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "%.2f s".format(elapsedSeconds),
                                        style = typography.evidence,
                                        color = colors.neutral600,
                                    )
                                    Text(
                                        text = "%.2f s".format(takeDurationSeconds),
                                        style = typography.evidence,
                                        color = colors.neutral600,
                                    )
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr,
                    ) {
                        Text(
                            text = entry.commonsFilename(isoCode, username),
                            style = typography.evidence,
                            color = colors.neutral600,
                        )
                    }
                }
            }
            Text(
                text =
                    if (isPlaying) {
                        stringResource(R.string.review_playing_back)
                    } else {
                        stringResource(R.string.review_decision_prompt, decisionRemainingSeconds)
                    },
                style = typography.stateLine,
                color = colors.accent700,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WsSecondaryButton(
                text = stringResource(R.string.action_redo),
                onClick = onRedo,
                modifier = Modifier.weight(1f),
                minHeight = 52.dp,
            )
            WsSecondaryButton(
                text = stringResource(R.string.action_drop),
                onClick = onDrop,
                modifier = Modifier.weight(1f),
                minHeight = 52.dp,
            )
        }
        WsGhostButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
        )
    }
}
