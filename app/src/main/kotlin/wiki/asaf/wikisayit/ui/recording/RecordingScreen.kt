package wiki.asaf.wikisayit.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.components.BlueprintBox
import wiki.asaf.wikisayit.ui.components.WsGhostButton
import wiki.asaf.wikisayit.ui.components.WsKicker
import wiki.asaf.wikisayit.ui.components.WsPrimaryButton
import wiki.asaf.wikisayit.ui.components.WsSecondaryButton
import wiki.asaf.wikisayit.ui.session.FlowScreen
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.RecordingBlocker
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.RecordingPhase
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItColors
import wiki.asaf.wikisayit.ui.theme.LocalWikiSayItTypography
import wiki.asaf.wikisayit.ui.theme.WikiSayItSpacing
import kotlin.random.Random

/** The recording session (`1a` ring). Also covers the mic-permission-blocked and
 * mid-word-interrupted states from `2f`. */
@Composable
fun RecordingScreen(
    viewModel: RecordingFlowViewModel,
    onNavigateReview: () -> Unit,
    onNavigateSummary: () -> Unit,
    onReturnToSummary: () -> Unit,
    onNavigateListSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.clearRecordingBlocker() else viewModel.setMicPermissionDenied()
        }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.clearRecordingBlocker() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(uiState.autoNavigateTo) {
        when (uiState.autoNavigateTo) {
            FlowScreen.REVIEW -> onNavigateReview()
            FlowScreen.SUMMARY -> onNavigateSummary()
            FlowScreen.SUMMARY_RETURN -> onReturnToSummary()
            FlowScreen.LIST_SOURCE -> onNavigateListSource()
            else -> Unit
        }
    }

    when (uiState.recordingBlocker) {
        RecordingBlocker.MicPermissionDenied ->
            MicPermissionBlockedContent(
                onOpenSettings = {
                    context.startActivity(
                        android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onBack = onNavigateListSource,
                modifier = modifier,
            )
        RecordingBlocker.Interrupted ->
            InterruptedContent(
                word = uiState.currentRecordingEntry?.label.orEmpty(),
                takesCount = uiState.recordingIndex,
                onCarryOn = viewModel::redoCurrentWord,
                onStopAndReview = viewModel::stopSession,
                modifier = modifier,
            )
        null -> {
            val entry = uiState.currentRecordingEntry
            if (entry != null) {
                RecordingRingContent(
                    entry = entry,
                    index = uiState.recordingIndex,
                    total = uiState.recordingQueue.size,
                    pass = uiState.recordingPass,
                    phase = uiState.recordingPhase,
                    silenceRemainingSeconds = uiState.silenceRemainingSeconds,
                    silenceThresholdSeconds = uiState.settings.silenceThresholdSeconds,
                    manualMode = uiState.manualMode,
                    manualRecordingActive = uiState.manualRecordingActive,
                    onToggleManualMode = { viewModel.setManualMode(!uiState.manualMode) },
                    onManualRecord = viewModel::startManualRecording,
                    onManualStop = viewModel::stopManualRecording,
                    onManualNext = viewModel::manualNext,
                    onRedo = viewModel::redoCurrentWord,
                    onSkip = viewModel::skipCurrentWord,
                    onStop = viewModel::stopSession,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun RecordingRingContent(
    entry: QueueEntry,
    index: Int,
    total: Int,
    pass: Int,
    phase: RecordingPhase,
    silenceRemainingSeconds: Float,
    silenceThresholdSeconds: Float,
    manualMode: Boolean,
    manualRecordingActive: Boolean,
    onToggleManualMode: () -> Unit,
    onManualRecord: () -> Unit,
    onManualStop: () -> Unit,
    onManualNext: () -> Unit,
    onRedo: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current

    val hint =
        when {
            manualMode && manualRecordingActive -> stringResource(R.string.recording_hint_manual_recording)
            manualMode && entry.audioFile != null -> stringResource(R.string.recording_hint_manual_recorded)
            manualMode -> stringResource(R.string.recording_hint_manual_idle)
            pass > 1 -> stringResource(R.string.recording_hint_redo_pass)
            phase == RecordingPhase.SILENCE -> stringResource(R.string.recording_hint_silence, silenceRemainingSeconds)
            else -> stringResource(R.string.recording_hint_speak_when_ready)
        }
    val stateText =
        when {
            manualMode && !manualRecordingActive -> stringResource(R.string.recording_state_manual_idle)
            phase == RecordingPhase.READY -> stringResource(R.string.recording_state_listening)
            phase == RecordingPhase.SPEAKING -> stringResource(R.string.recording_state_recording)
            else -> stringResource(R.string.recording_state_stopping, silenceRemainingSeconds)
        }
    val withinWordFraction =
        when (phase) {
            RecordingPhase.READY -> 0f
            RecordingPhase.SPEAKING -> 0.4f
            RecordingPhase.SILENCE ->
                0.4f + 0.6f * (1f - (silenceRemainingSeconds / silenceThresholdSeconds).coerceIn(0f, 1f))
        }
    val progress = ((index + withinWordFraction) / total.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.recording_counter, index + 1, total),
                style = typography.evidence,
                color = colors.neutral700,
            )
            Text(text = hint, style = typography.evidence, color = colors.neutral700)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = WikiSayItSpacing.space2)
                    .height(3.dp)
                    .background(colors.neutral300),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(colors.accent))
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = entry.displayText,
                style = typography.displayWord,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = WikiSayItSpacing.space2),
            )
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "${entry.evidenceId} · ${entry.detail}",
                    style = typography.evidence,
                    color = colors.neutral600,
                    modifier = Modifier.padding(top = WikiSayItSpacing.space3),
                )
            }
            MicRing(
                isLive = phase != RecordingPhase.SILENCE,
                isSpeaking = phase == RecordingPhase.SPEAKING,
                modifier = Modifier.padding(top = WikiSayItSpacing.space6),
            )
            LevelBars(phase = phase, modifier = Modifier.padding(top = WikiSayItSpacing.space4))
            Text(
                text = stateText.uppercase(),
                style = typography.stateLine,
                color = if (phase == RecordingPhase.SPEAKING) colors.accent900 else colors.accent700,
                modifier = Modifier.padding(top = WikiSayItSpacing.space3),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = WikiSayItSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsGhostButton(
                text =
                    if (manualMode) {
                        stringResource(R.string.recording_manual_mode_off)
                    } else {
                        stringResource(R.string.recording_manual_mode_on)
                    },
                onClick = onToggleManualMode,
                modifier = Modifier.fillMaxWidth(),
            )
            if (manualMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    WsSecondaryButton(
                        text = stringResource(R.string.recording_manual_record),
                        onClick = onManualRecord,
                        enabled = !manualRecordingActive,
                        modifier = Modifier.weight(1f),
                        minHeight = 52.dp,
                    )
                    WsSecondaryButton(
                        text = stringResource(R.string.recording_manual_stop),
                        onClick = onManualStop,
                        enabled = manualRecordingActive,
                        modifier = Modifier.weight(1f),
                        minHeight = 52.dp,
                    )
                }
                WsPrimaryButton(
                    text = stringResource(R.string.recording_manual_next),
                    onClick = onManualNext,
                    enabled = !manualRecordingActive && entry.audioFile != null,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52.dp,
                )
                WsGhostButton(
                    text = stringResource(R.string.action_skip),
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    WsSecondaryButton(
                        text = stringResource(R.string.action_redo),
                        onClick = onRedo,
                        modifier = Modifier.weight(1f),
                        minHeight = 52.dp,
                    )
                    WsSecondaryButton(
                        text = stringResource(R.string.action_skip),
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        minHeight = 52.dp,
                    )
                }
            }
            WsGhostButton(
                text = stringResource(R.string.action_stop_session),
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MicRing(
    isLive: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val transition = rememberInfiniteTransition(label = "mic-ring-pulse")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = "mic-ring-t",
    )
    Box(modifier = modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (isLive) {
            PulseRing(t = t, color = colors.accent)
            PulseRing(t = (t + 0.5f) % 1f, color = colors.accent)
        }
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .background(
                        if (isSpeaking) colors.accent700 else colors.accent,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun PulseRing(
    t: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    val scale = 1f + 0.55f * t
    val alpha = 0.55f * (1f - t)
    Canvas(
        modifier =
            Modifier
                .size(132.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    compositingStrategy = CompositingStrategy.Offscreen
                },
    ) {
        drawCircle(color = color, radius = size.minDimension / 2f, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun LevelBars(
    phase: RecordingPhase,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    var heights by remember { mutableStateOf(List(24) { 4.dp }) }
    LaunchedEffect(phase) {
        while (true) {
            val range =
                when (phase) {
                    RecordingPhase.SPEAKING -> 4..34
                    RecordingPhase.READY -> 4..10
                    RecordingPhase.SILENCE -> 4..6
                }
            heights = List(24) { Random.nextInt(range.first, range.last + 1).dp }
            delay(120)
        }
    }
    Row(
        modifier = modifier.height(34.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heights.forEach { barHeight ->
            Box(Modifier.width(3.dp).height(barHeight).background(colors.accent500))
        }
    }
}

@Composable
private fun MicPermissionBlockedContent(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxSize().padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(96.dp).padding(bottom = WikiSayItSpacing.space4),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic_off),
                    contentDescription = null,
                    tint = colors.neutral600,
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(text = stringResource(R.string.mic_blocked_title), style = typography.h2)
            Text(
                text = stringResource(R.string.mic_blocked_body),
                style = typography.body,
                modifier = Modifier.padding(top = WikiSayItSpacing.space3),
            )
            BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space4)) {
                Column(modifier = Modifier.padding(WikiSayItSpacing.space3)) {
                    WsKicker(text = stringResource(R.string.mic_blocked_kicker))
                    Text(
                        text = stringResource(R.string.mic_blocked_instructions),
                        style = typography.secondary,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = WikiSayItSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsPrimaryButton(
                text = stringResource(R.string.mic_blocked_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 50.dp,
            )
            WsGhostButton(
                text = stringResource(R.string.mic_blocked_back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InterruptedContent(
    word: String,
    takesCount: Int,
    onCarryOn: () -> Unit,
    onStopAndReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWikiSayItColors.current
    val typography = LocalWikiSayItTypography.current
    Column(modifier = modifier.fillMaxSize().padding(horizontal = WikiSayItSpacing.screenHorizontal)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = word, style = typography.displayWord, color = colors.neutral600, textAlign = TextAlign.Center)
            Text(
                text = stringResource(R.string.interrupted_nothing_kept),
                style = typography.evidence,
                color = colors.neutral600,
                modifier = Modifier.padding(top = WikiSayItSpacing.space2),
            )
            Box(
                modifier =
                    Modifier
                        .padding(top = WikiSayItSpacing.space6)
                        .size(132.dp)
                        .background(androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(132.dp)) {
                    drawCircle(
                        color = colors.neutral500,
                        radius = size.minDimension / 2f,
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect =
                                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 8f),
                                    ),
                            ),
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = null,
                    tint = colors.neutral500,
                    modifier = Modifier.size(40.dp),
                )
            }
            BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space6)) {
                Column(modifier = Modifier.padding(WikiSayItSpacing.space3)) {
                    WsKicker(text = stringResource(R.string.interrupted_why_kicker))
                    Text(
                        text = stringResource(R.string.interrupted_why_body),
                        style = typography.body,
                        modifier = Modifier.padding(top = WikiSayItSpacing.space1),
                    )
                }
            }
            BlueprintBox(modifier = Modifier.fillMaxWidth().padding(top = WikiSayItSpacing.space2)) {
                Row(modifier = Modifier.padding(WikiSayItSpacing.space3)) {
                    Text(
                        text = "!",
                        color = colors.accent700,
                        modifier = Modifier.padding(end = WikiSayItSpacing.space2),
                    )
                    Text(text = stringResource(R.string.interrupted_noise_body), style = typography.secondary)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = WikiSayItSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(WikiSayItSpacing.space2),
        ) {
            WsPrimaryButton(
                text = stringResource(R.string.interrupted_carry_on, word),
                onClick = onCarryOn,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp,
            )
            WsGhostButton(
                text = pluralStringResource(R.plurals.interrupted_stop_review, takesCount, takesCount),
                onClick = onStopAndReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
