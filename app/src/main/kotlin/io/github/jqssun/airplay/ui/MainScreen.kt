package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.buttons.NextButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.PreviousButton
import androidx.media3.ui.compose.material3.indicator.DurationText
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.ui.gestures.BrightnessState
import io.github.jqssun.airplay.ui.gestures.DoubleTapIndicator
import io.github.jqssun.airplay.ui.gestures.GestureInfoText
import io.github.jqssun.airplay.ui.gestures.SeekGestureState
import io.github.jqssun.airplay.ui.gestures.TapGestureState
import io.github.jqssun.airplay.ui.gestures.VerticalGesture
import io.github.jqssun.airplay.ui.gestures.VerticalProgressIndicator
import io.github.jqssun.airplay.ui.gestures.VideoContentScale
import io.github.jqssun.airplay.ui.gestures.VideoPlayerGestures
import io.github.jqssun.airplay.ui.gestures.VolumeAndBrightnessGestureState
import io.github.jqssun.airplay.ui.gestures.VolumeState
import io.github.jqssun.airplay.ui.gestures.ZoomState
import io.github.jqssun.airplay.viewmodel.DebugInfo
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    OVERVIEW(R.string.tab_overview, Icons.Default.Cast),
    LOGS(R.string.tab_logs, Icons.AutoMirrored.Filled.Article),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isInPip: Boolean = false,
    onSurfaceAvailable: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: (android.view.Surface) -> Unit,
    onPip: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    var fullscreen by remember { mutableStateOf(false) }
    val pin by viewModel.pinCode.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val audioOnly by viewModel.audioOnly.collectAsState()
    val videoPlaybackActive by viewModel.videoPlaybackActive.collectAsState()
    val videoSessionPending by viewModel.videoSessionPending.collectAsState()
    val mirroringActive by viewModel.mirroringActive.collectAsState()
    val autoFullscreen by viewModel.autoFullscreen.collectAsState()

    // don't use movableContentOf: moving AndroidView across subcomposition boundaries makes it crash on reparent
    val video: @Composable () -> Unit = {
        val aspect by viewModel.videoAspect.collectAsState()
        VideoSurfaceView(
            onSurfaceAvailable = onSurfaceAvailable,
            onSurfaceDestroyed = onSurfaceDestroyed,
            aspectRatio = aspect
        )
    }

    // fullscreen only once mirroring reports a size: connections rise before the kind is known
    var prevMirroringActive by remember { mutableStateOf(false) }
    LaunchedEffect(mirroringActive, audioOnly, videoPlaybackActive, pin) {
        val justStarted = !prevMirroringActive && mirroringActive
        prevMirroringActive = mirroringActive
        if (justStarted && !audioOnly && !videoPlaybackActive && autoFullscreen && pin == null) {
            fullscreen = true
        }
    }

    // manual fullscreen over the idle preview is deliberate; only undo on disconnect
    LaunchedEffect(connections) {
        if (connections == 0) fullscreen = false
    }

    // leaving video playback must not fall through to a stale mirroring fullscreen
    LaunchedEffect(videoPlaybackActive) {
        if (videoPlaybackActive) fullscreen = false
    }

    val activity = LocalContext.current as? Activity
    val tv = isTv()
    val serverState by viewModel.serverState.collectAsState()
    // Screensaver: covers the whole window, nav bar included, after an idle delay.
    // Only while audio is actually playing -- there is no burn-in risk on an idle
    // app the user is looking at, and a screensaver over the setup UI would be
    // obstructive. Any key dismisses it and restarts the timer.
    val vizEnabled by viewModel.visualizerEnabled.collectAsState()
    val vizDelayS by viewModel.visualizerDelayS.collectAsState()
    var vizWakeTick by remember { mutableStateOf(0L) }
    var vizActive by remember { mutableStateOf(false) }
    val vizEligible = vizEnabled && vizDelayS > 0 && audioOnly &&
        serverState == ServerState.RUNNING && connections > 0
    LaunchedEffect(vizWakeTick, vizEligible) {
        vizActive = false
        if (!vizEligible) return@LaunchedEffect
        delay(vizDelayS * 1000L)
        vizActive = true
    }
    var vizLevel by remember { mutableStateOf(0f) }
    var vizProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(vizActive) {
        if (!vizActive) return@LaunchedEffect
        while (true) {
            vizLevel = viewModel.audioLevel()
            val p = viewModel.dacpPlayer
            val d = p?.duration ?: 0L
            if (p != null && d > 0) {
                vizProgress = (p.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            }
            delay(VIZ_LEVEL_POLL_MS)
        }
    }

    val serverToggleFocus = remember { FocusRequester() }
    // bumped when the nav bar wants to hand focus up into the media controls
    var mediaWakeTick by remember { mutableStateOf(0L) }
    // land D-pad focus on the server toggle so the remote has a sensible start
    LaunchedEffect(tv) {
        if (tv) serverToggleFocus.requestFocusUntilLanded()
    }
    val videoScreen = videoPlaybackActive || videoSessionPending
    LaunchedEffect(fullscreen, videoScreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen || videoScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (videoScreen) {
        val videoPlaybackAspect by viewModel.videoPlaybackAspect.collectAsState()
        val playback: @Composable () -> Unit = {
            VideoSurfaceView(
                onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
                onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
                aspectRatio = videoPlaybackAspect
            )
        }
        if (isInPip) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                playback()
            }
            return
        }
        val overlayTick by viewModel.videoOverlayTick.collectAsState()
        val playing by viewModel.videoPlaying.collectAsState()
        val positionMs by viewModel.videoPositionMs.collectAsState()
        val durationMs by viewModel.videoDurationMs.collectAsState()
        val scrubPositionMs by viewModel.videoScrubPositionMs.collectAsState()
        val downloadProgress by viewModel.videoDownloadProgress.collectAsState()
        val scrubbing = scrubPositionMs != null
        val downloading = downloadProgress != null
        var overlayVisible by remember { mutableStateOf(false) }
        LaunchedEffect(overlayTick, playing, scrubbing, downloading, videoPlaybackActive) {
            // tick 0 = fresh session; a pending session or a running download pins the overlay
            if (!videoPlaybackActive) {
                overlayVisible = true
            } else if (overlayTick == 0L) {
                overlayVisible = false
            } else {
                overlayVisible = true
                if (playing && !scrubbing && !downloading) {
                    delay(VIDEO_OVERLAY_HIDE_MS)
                    overlayVisible = false
                }
            }
        }
        val gestureScope = rememberCoroutineScope()
        val tapGestureState = remember(viewModel) { TapGestureState(viewModel, gestureScope) }
        val seekGestureState = remember(viewModel) { SeekGestureState(viewModel) }
        val context = LocalContext.current
        val volumeState = remember { VolumeState(context) }
        val brightnessState = remember(activity) { activity?.window?.let { BrightnessState(it) } }
        val volumeAndBrightnessGestureState = remember(volumeState, brightnessState) {
            VolumeAndBrightnessGestureState(volumeState, brightnessState, gestureScope)
        }
        val zoomState = remember(viewModel) { ZoomState(viewModel, gestureScope) }
        var controlsLocked by remember { mutableStateOf(false) }
        DisposableEffect(volumeState) { volumeState.handleLifecycle(this) }
        LaunchedEffect(overlayTick) {
            if (overlayTick == 0L) {
                zoomState.reset()
                controlsLocked = false
            }
        }
        DisposableEffect(brightnessState) {
            onDispose { brightnessState?.clearOverride() }
        }
        // orientation follows the video aspect; manual rotate holds until the next video
        LaunchedEffect(videoPlaybackAspect) {
            activity?.requestedOrientation = if (videoPlaybackAspect < 1f) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        DisposableEffect(activity) {
            onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        val videoPlaybackSize by viewModel.videoPlaybackSize.collectAsState()
        val buffering by viewModel.videoBuffering.collectAsState()
        val videoTitle by viewModel.videoTitle.collectAsState()
        val videoLocation by viewModel.videoLocation.collectAsState()
        val speed by viewModel.videoSpeed.collectAsState()
        val isPipSupported = remember {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }
        var showSpeedSelector by remember { mutableStateOf(false) }
        BackHandler { viewModel.stopVideoPlayback() }

        val rootFocusRequester = remember { FocusRequester() }
        val playPauseFocusRequester = remember { FocusRequester() }
        val unlockFocusRequester = remember { FocusRequester() }
        var isPlayPauseFocused by remember { mutableStateOf(false) }
        var isUnlockFocused by remember { mutableStateOf(false) }
        LaunchedEffect(overlayVisible, controlsLocked, showSpeedSelector) {
            if (showSpeedSelector) return@LaunchedEffect
            if (!overlayVisible) {
                runCatching { rootFocusRequester.requestFocus() }
                return@LaunchedEffect
            }
            val locked = controlsLocked
            val target = if (locked) unlockFocusRequester else playPauseFocusRequester
            target.requestFocusUntilLanded(attempts = 20) { if (locked) isUnlockFocused else isPlayPauseFocused }
        }

        // dpad seeking (controls hidden): accumulate the skipped amount and briefly show it
        var dpadSeekOffsetMs by remember { mutableLongStateOf(0L) }
        var dpadSeekTargetMs by remember { mutableLongStateOf(0L) }
        var dpadSeekActive by remember { mutableStateOf(false) }
        var dpadSeekTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(dpadSeekTick) {
            if (!dpadSeekActive) return@LaunchedEffect
            delay(1000)
            dpadSeekActive = false
        }
        val showDpadSeekFeedback: (Long) -> Unit = { deltaMs ->
            if (!dpadSeekActive) dpadSeekOffsetMs = 0L
            dpadSeekOffsetMs += deltaMs
            dpadSeekTargetMs = (dpadSeekTargetMs.takeIf { dpadSeekActive } ?: positionMs).plus(deltaMs)
                .coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
            dpadSeekActive = true
            dpadSeekTick++
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (showSpeedSelector) {
                        false
                    } else {
                        handlePlayerKeyEvent(
                            keyEvent = keyEvent,
                            controlsVisible = overlayVisible,
                            controlsLocked = controlsLocked,
                            isPlayPauseFocused = isPlayPauseFocused,
                            seekIncrementMs = TapGestureState.SEEK_INCREMENT_MS,
                            viewModel = viewModel,
                            showControls = { viewModel.showVideoOverlay() },
                            unlockControls = {
                                controlsLocked = false
                                viewModel.showVideoOverlay()
                            },
                            onDpadSeek = showDpadSeekFeedback,
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            VideoContentFrame(
                aspect = videoPlaybackAspect,
                videoSizePx = videoPlaybackSize,
                contentScale = zoomState.contentScale,
                zoom = zoomState.zoom
            ) { sizeModifier ->
                VideoSurfaceView(
                    onSurfaceAvailable = { viewModel.onVideoPlaybackSurfaceAvailable(it) },
                    onSurfaceDestroyed = { viewModel.onVideoPlaybackSurfaceDestroyed(it) },
                    applyAspectRatio = false,
                    modifier = sizeModifier
                )
            }
            VideoPlayerGestures(
                enabled = videoPlaybackActive,
                locked = controlsLocked,
                onTap = {
                    // a pending session pins the overlay; taps must not unpin it
                    if (!videoPlaybackActive) return@VideoPlayerGestures
                    if (overlayVisible) overlayVisible = false else viewModel.showVideoOverlay()
                },
                tapGestureState = tapGestureState,
                seekGestureState = seekGestureState,
                volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                zoomState = zoomState
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = overlayVisible && videoPlaybackActive && !controlsLocked,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            if (buffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(72.dp))
            }
            DoubleTapIndicator(tapGestureState = tapGestureState)
            val seekAmountMs = seekGestureState.seekAmountMs
            when {
                seekAmountMs != null -> GestureInfoText(
                    info = "${if (seekAmountMs < 0) "-" else "+"}${formatVideoTime(abs(seekAmountMs))}\n" +
                        "[${formatVideoTime(seekGestureState.targetPositionMs ?: 0)}]",
                    modifier = Modifier.align(Alignment.Center)
                )
                zoomState.isZooming -> GestureInfoText(
                    info = "${(zoomState.zoom * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.Center)
                )
                zoomState.showContentScaleIndicator -> GestureInfoText(
                    info = stringResource(zoomState.contentScale.nameRes()),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible && videoPlaybackActive && !controlsLocked,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    PlayPauseButton(
                        playing = playing,
                        onClick = { viewModel.toggleVideoPlayPause() },
                        modifier = Modifier
                            .focusRequester(playPauseFocusRequester)
                            .onFocusChanged { isPlayPauseFocused = it.hasFocus }
                    )
                }
            }
            DpadSeekIndicator(
                visible = dpadSeekActive && dpadSeekOffsetMs != 0L,
                offsetMs = dpadSeekOffsetMs,
                positionMs = dpadSeekTargetMs
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(24.dp)
            ) {
                VerticalProgressIndicator(value = volumeState.percentage, icon = Icons.AutoMirrored.Rounded.VolumeUp)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp)
            ) {
                VerticalProgressIndicator(value = brightnessState?.percentage ?: 0, icon = Icons.Rounded.BrightnessHigh)
            }
            if (controlsLocked) {
                if (overlayVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(top = 24.dp)
                    ) {
                        UnlockButton(
                            onClick = {
                                controlsLocked = false
                                viewModel.showVideoOverlay()
                            },
                            modifier = Modifier
                                .focusRequester(unlockFocusRequester)
                                .onFocusChanged { isUnlockFocused = it.hasFocus }
                        )
                    }
                }
            } else {
                androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    VideoControlsTop(
                        title = videoTitle,
                        videoUrl = videoLocation,
                        downloadProgress = downloadProgress,
                        showDownload = durationMs > 0,
                        onBackClick = { viewModel.stopVideoPlayback() },
                        onSpeedClick = {
                            overlayVisible = false
                            showSpeedSelector = true
                        },
                        onDownloadClick = { viewModel.toggleVideoDownload() }
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    VideoControlsBottom(
                        positionMs = scrubPositionMs ?: positionMs,
                        durationMs = durationMs,
                        contentScale = zoomState.contentScale,
                        isPipSupported = isPipSupported,
                        onRotateClick = {
                            activity?.let {
                                it.requestedOrientation =
                                    if (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                            }
                        },
                        onLockClick = {
                            viewModel.showVideoOverlay()
                            controlsLocked = true
                        },
                        onContentScaleClick = {
                            viewModel.showVideoOverlay()
                            zoomState.switchToNextContentScale()
                        },
                        onPipClick = onPip,
                        onSeek = { seekGestureState.onSeek(it) },
                        onSeekEnd = { seekGestureState.onSeekEnd() },
                        seekBarModifier = Modifier
                            .focusProperties { up = playPauseFocusRequester }
                            .dpadAdjust(
                                onLeft = {
                                    viewModel.seekVideoBy(-TapGestureState.SEEK_INCREMENT_MS)
                                    viewModel.showVideoOverlay()
                                },
                                onRight = {
                                    viewModel.seekVideoBy(TapGestureState.SEEK_INCREMENT_MS)
                                    viewModel.showVideoOverlay()
                                }
                            )
                    )
                }
            }
            val skipSilence by viewModel.videoSkipSilence.collectAsState()
            PlaybackSpeedSelector(
                show = showSpeedSelector,
                speed = speed,
                skipSilence = skipSilence,
                onSpeedChange = { viewModel.setVideoSpeed(it) },
                onSkipSilenceChange = { viewModel.setVideoSkipSilence(it) },
                onDismiss = { showSpeedSelector = false }
            )
        }
        return
    }

    // pip mode: show only the video surface
    if (isInPip) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            video()
        }
        return
    }

    // restore system bars when exiting pip back to non-fullscreen
    LaunchedEffect(isInPip) {
        if (!isInPip && !fullscreen) {
            val window = activity?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // exit fullscreen while a pin is being shown so the dialog isn't covered
    LaunchedEffect(pin) {
        if (pin != null) fullscreen = false
    }

    // Root box so the screensaver can paint over everything, nav bar included.
    // onPreviewKeyEvent sits here (not on the overlay) because the overlay must
    // not take focus -- stealing it would disturb the focus position the user
    // returns to. Instead the root sees every key first and, while the saver is
    // up, swallows exactly one to dismiss it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                vizWakeTick++
                // swallow the key that dismisses, so it does not also actuate
                vizActive
            }
    ) {
    if (fullscreen) {
        BackHandler { fullscreen = false }
        FullscreenVideo(
            viewModel = viewModel,
            video = video,
            onExitFullscreen = { fullscreen = false },
            onPip = onPip
        )
    } else {
        Scaffold(
            bottomBar = {
                Column {
                    AudioMiniController(
                        viewModel,
                        visible = audioOnly && connections > 0 && tab != Tab.OVERVIEW,
                        onClick = { tab = Tab.OVERVIEW }
                    )
                    NavigationBar(
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            // hand off to the media chrome, which grabs focus once shown
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp &&
                                tv && tab == Tab.OVERVIEW && audioOnly && connections > 0
                            ) {
                                mediaWakeTick++
                                true
                            } else false
                        }
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(stringResource(t.labelRes)) },
                                modifier = Modifier.dpadFocus()
                            )
                        }
                        if (tv) {
                            NavigationBarItem(
                                selected = false,
                                onClick = {
                                    if (serverState == ServerState.RUNNING) viewModel.stopServer()
                                    else viewModel.startServer()
                                },
                                icon = {
                                    Icon(
                                        if (serverState == ServerState.RUNNING) Icons.Default.Stop
                                        else Icons.Default.PlayArrow,
                                        null
                                    )
                                },
                                label = {
                                    Text(
                                        if (serverState == ServerState.RUNNING) stringResource(R.string.btn_stop)
                                        else stringResource(R.string.btn_start)
                                    )
                                },
                                modifier = Modifier.dpadFocus().focusRequester(serverToggleFocus)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                TabContent(
                    tab, viewModel, video,
                    onFullscreen = { fullscreen = true }, onPip = onPip, showAudioMode = audioOnly,
                    mediaWakeTick = mediaWakeTick
                )
            }
        }
    }

    // Screensaver on top of everything. Fades so the handover is not jarring.
    AnimatedVisibility(
        visible = vizActive,
        enter = fadeIn(animationSpec = tween(VIZ_FADE_IN_MS)),
        exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MS)),
    ) {
        AudioVisualizer(level = vizLevel, progress = vizProgress)
    }
    }

    // Back dismisses the screensaver before anything else acts on it.
    if (vizActive) {
        BackHandler { vizWakeTick++ }
    }

    // pin dialog
    if (pin != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPin() },
            title = { Text(stringResource(R.string.dialog_pin_title)) },
            text = {
                Text(
                    text = pin!!,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPin() }) { Text(stringResource(R.string.btn_ok)) }
            }
        )
    }

}

@Composable
private fun TabContent(
    tab: Tab,
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    showAudioMode: Boolean,
    mediaWakeTick: Long = 0L
) {
    when (tab) {
        Tab.OVERVIEW -> OverviewContent(
            viewModel, video,
            onFullscreen = onFullscreen, onPip = onPip, showAudioMode = showAudioMode,
            mediaWakeTick = mediaWakeTick
        )
        Tab.LOGS -> LogsScreen(viewModel)
        Tab.SETTINGS -> SettingsScreen(viewModel)
    }
}

@Composable
private fun OverviewContent(
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onFullscreen: () -> Unit,
    onPip: () -> Unit,
    showAudioMode: Boolean = false,
    mediaWakeTick: Long = 0L
) {
    val state by viewModel.serverState.collectAsState()
    val connections by viewModel.connectionCount.collectAsState()
    val serverName by viewModel.serverName.collectAsState()
    val videoResolution by viewModel.videoResolution.collectAsState()
    val idlePreview by viewModel.idlePreview.collectAsState()
    val mirroringActive by viewModel.mirroringActive.collectAsState()
    val videoPlaybackActive by viewModel.videoPlaybackActive.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val tv = isTv()
    val startFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        // content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .thenIf(!tv) { padding(16.dp).clip(RoundedCornerShape(16.dp)) }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val connecting = state == ServerState.RUNNING && connections > 0 &&
                !mirroringActive && !videoPlaybackActive
            if (showAudioMode && state == ServerState.RUNNING && connections > 0) {
                NowPlayingContent(viewModel, mediaWakeTick)
            } else {
                if (state == ServerState.RUNNING && (mirroringActive || idlePreview)) {
                    video()
                }
                if (state != ServerState.RUNNING || connections == 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (connections > 0) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(if (tv) 96.dp else 64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(if (tv) 20.dp else 12.dp))
                        Text(
                            text = when (state) {
                                ServerState.STOPPED -> stringResource(R.string.server_stopped)
                                ServerState.RUNNING -> stringResource(R.string.waiting_for_connection)
                                ServerState.ERROR -> stringResource(R.string.error_starting_server)
                            },
                            style = if (tv) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else if (connecting) {
                    Text(
                        text = stringResource(R.string.waiting_for_playback),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (state == ServerState.RUNNING && mirroringActive) {
                    Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        IconButton(onClick = onPip, modifier = Modifier.dpadFocus()) {
                            Icon(
                                painterResource(R.drawable.ic_pip), contentDescription = stringResource(R.string.cd_pip),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = onFullscreen, modifier = Modifier.dpadFocus()) {
                            Icon(
                                Icons.Default.Fullscreen, contentDescription = stringResource(R.string.cd_fullscreen),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            if (debugEnabled && connections > 0) {
                DebugOverlay(debugInfo, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            var showRes by remember { mutableStateOf(false) }
            LaunchedEffect(videoResolution) {
                if (videoResolution.isNotEmpty() && !showAudioMode) {
                    showRes = true
                    delay(5000)
                    showRes = false
                }
            }
            if (!showAudioMode) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showRes && connections > 0,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = videoResolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // controls (phone only: on TV the toggle lives in the bottom bar)
        if (!tv) Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        val statusColor by animateColorAsState(
                            when (state) {
                                ServerState.RUNNING -> MaterialTheme.colorScheme.primary
                                ServerState.ERROR -> MaterialTheme.colorScheme.error
                                ServerState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }, label = "status"
                        )
                        Text(
                            text = when (state) {
                                ServerState.RUNNING -> stringResource(R.string.connected_count, connections)
                                ServerState.ERROR -> stringResource(R.string.error_label)
                                ServerState.STOPPED -> stringResource(R.string.stopped_label)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (state == ServerState.RUNNING) viewModel.stopServer()
                            else viewModel.startServer()
                        },
                        modifier = Modifier.dpadFocus().focusRequester(startFocus)
                    ) {
                        Icon(
                            imageVector = if (state == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (state == ServerState.RUNNING) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenVideo(
    viewModel: MainViewModel,
    video: @Composable () -> Unit,
    onExitFullscreen: () -> Unit,
    onPip: () -> Unit
) {
    val videoResolution by viewModel.videoResolution.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var tapTick by remember { mutableStateOf(0) }
    LaunchedEffect(tapTick) {
        controlsVisible = true
        delay(8000)
        controlsVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { tapTick++ } },
        contentAlignment = Alignment.Center
    ) {
        video()
        androidx.compose.animation.AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Row(modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onPip, modifier = Modifier.dpadFocus()) {
                    Icon(
                        painterResource(R.drawable.ic_pip), contentDescription = stringResource(R.string.cd_pip),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onExitFullscreen, modifier = Modifier.dpadFocus()) {
                    Icon(
                        Icons.Default.FullscreenExit, contentDescription = stringResource(R.string.cd_exit_fullscreen),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        if (debugEnabled) {
            DebugOverlay(debugInfo, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        var showRes by remember { mutableStateOf(false) }
        LaunchedEffect(videoResolution) {
            if (videoResolution.isNotEmpty()) {
                showRes = true
                delay(5000)
                showRes = false
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showRes,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        ) {
            Text(
                text = videoResolution,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// Thin progress line for the hidden-chrome state: position only, no text and no
// interaction. Not shown while the visualizer is up -- that draws its own progress.
@Composable
private fun SlimProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/*
 * Full-screen screensaver. Covers everything including the nav bar, because a
 * static nav bar is the worst burn-in case on the panel -- it never moves at all.
 *
 * Bitmap-based rather than vector strokes: the goal is that EVERY pixel changes
 * colour continuously, which sparse outlines cannot do. A small field of pixels
 * is generated per frame and scaled up to fill the display, so the per-frame cost
 * is bounded by the field size (a few thousand pixels) and not by screen area.
 *
 * The field is an interference pattern of moving wave sources. It is driven by
 * the audio level, so it still reacts to sound the way the previous version did.
 * Chapter progress rides along as a travelling bright band -- position encodes
 * progress, but it sweeps continuously so no pixel holds a fixed value.
 */
@Composable
private fun AudioVisualizer(
    level: Float,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // Smooth the level so the field pulses rather than jitters.
    val smoothed by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(VIZ_LEVEL_SMOOTH_MS, easing = LinearEasing),
        label = "level"
    )
    // Free-running clock: withFrameNanos keeps the field advancing every frame
    // even when the audio is silent, so pixels never stall.
    var timeS by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeS = (now - start) / 1_000_000_000f }
        }
    }

    // Created via the Android API rather than the ImageBitmap() factory: that
    // factory gives no mutability guarantee, and setPixels on an immutable bitmap
    // throws at runtime. createBitmap is mutable by contract.
    val androidBitmap = remember {
        android.graphics.Bitmap.createBitmap(
            VIZ_FIELD_W, VIZ_FIELD_H, android.graphics.Bitmap.Config.ARGB_8888
        )
    }
    val bitmap = remember(androidBitmap) { androidBitmap.asImageBitmap() }
    val pixels = remember { IntArray(VIZ_FIELD_W * VIZ_FIELD_H) }

    // Palette hue rotates slowly so the same pixel cycles through colours over
    // time rather than resting on one, which is what actually ages an OLED.
    val paletteShift = (timeS / VIZ_HUE_CYCLE_S) % 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        renderVisualizerField(pixels, timeS, smoothed, progress, paletteShift)
        androidBitmap.setPixels(pixels, 0, VIZ_FIELD_W, 0, 0, VIZ_FIELD_W, VIZ_FIELD_H)
        drawImage(
            image = bitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

/*
 * Fills `out` with one frame of the field. Pure arithmetic, no allocation: this
 * runs every frame, so it must stay cheap.
 *
 * Three moving sine sources interfere; the audio level scales their amplitude,
 * and a progress band adds brightness at the row matching chapter position.
 */
private fun renderVisualizerField(
    out: IntArray,
    timeS: Float,
    level: Float,
    progress: Float,
    paletteShift: Float,
) {
    val amp = VIZ_AMP_BASE + level * VIZ_AMP_LEVEL
    // Source positions orbit at unrelated rates so the pattern never repeats
    // exactly, which would otherwise reintroduce a static image.
    val ax = cos(timeS * 0.31f) * 0.6f
    val ay = sin(timeS * 0.23f) * 0.6f
    val bx = cos(timeS * -0.17f) * 0.7f
    val by = sin(timeS * 0.29f) * 0.7f
    val cx = cos(timeS * 0.13f) * 0.5f
    val cy = sin(timeS * -0.19f) * 0.5f

    // Progress is shown as a colour-split boundary sweeping left to right: the
    // "played" side runs on a shifted palette, so the split is readable across
    // the whole screen instead of as a thin line lost in the ripples. The
    // boundary itself is soft-edged and the palette keeps moving, so nothing
    // holds a hard static edge.
    val boundaryX = progress.coerceIn(0f, 1f) * 2f - 1f

    var i = 0
    for (py in 0 until VIZ_FIELD_H) {
        val y = py * 2f / VIZ_FIELD_H - 1f
        for (px in 0 until VIZ_FIELD_W) {
            val x = px * 2f / VIZ_FIELD_W - 1f
            val d1 = (x - ax) * (x - ax) + (y - ay) * (y - ay)
            val d2 = (x - bx) * (x - bx) + (y - by) * (y - by)
            val d3 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            val v = sin(d1 * VIZ_FREQ1 - timeS * 1.7f) +
                sin(d2 * VIZ_FREQ2 + timeS * 1.1f) +
                sin(d3 * VIZ_FREQ3 - timeS * 0.7f)
            // v is -3..3 -> 0..1
            var inten = (v / 6f + 0.5f) * amp

            // Soft transition across the progress boundary. Played side is
            // brighter and hue-shifted; the seam is a gradient, never a hard line.
            val fromBoundary = (x - boundaryX) / VIZ_BOUNDARY_SOFTNESS
            val played = 1f - (fromBoundary.coerceIn(-1f, 1f) * 0.5f + 0.5f)
            inten += played * VIZ_PLAYED_GAIN
            // A bright crest rides exactly on the boundary so the current
            // position is pinpointable, not just approximate.
            val edge = fromBoundary * fromBoundary
            inten += VIZ_EDGE_GAIN / (1f + edge * VIZ_EDGE_TIGHTNESS)

            if (inten < 0f) inten = 0f
            if (inten > 1f) inten = 1f
            // Hue varies across the field as well as with intensity, so the
            // frame is genuinely multicoloured instead of one tinted mass.
            val hue = paletteShift + inten * VIZ_HUE_SPREAD +
                (x + y) * VIZ_HUE_SPATIAL + d1 * 0.05f + played * VIZ_PLAYED_HUE
            // Floor the brightness: a black pixel is an unexercised pixel, which
            // is the whole thing we are trying to avoid.
            val lit = VIZ_MIN_BRIGHTNESS + inten * (1f - VIZ_MIN_BRIGHTNESS)
            out[i++] = visualizerColor(lit, hue)
        }
    }
}

// Cheap hue ramp. Kept dim on purpose: peak output stays well under full white,
// since sustained maximum drive is what wears OLED subpixels fastest.
private fun visualizerColor(intensity: Float, hue: Float): Int {
    val h = (hue % 1f + 1f) % 1f * 6f
    val sector = h.toInt()
    val f = h - sector
    val v = intensity * VIZ_MAX_BRIGHTNESS
    // Desaturated: every pixel drives all three subpixels to some degree, rather
    // than resting one at zero while another runs at maximum.
    val p = v * (1f - VIZ_SATURATION)
    val q = v * (1f - VIZ_SATURATION * f)
    val t = v * (1f - VIZ_SATURATION * (1f - f))
    val r: Float; val g: Float; val b: Float
    when (sector) {
        0 -> { r = v; g = t; b = p }
        1 -> { r = q; g = v; b = p }
        2 -> { r = p; g = v; b = t }
        3 -> { r = p; g = q; b = v }
        4 -> { r = t; g = p; b = v }
        else -> { r = v; g = p; b = q }
    }
    val ri = (r * 255f).toInt().coerceIn(0, 255)
    val gi = (g * 255f).toInt().coerceIn(0, 255)
    val bi = (b * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
}

// dacp scanning is press-and-hold: beginff/beginrew while held, playresume on release
@Composable
private fun HoldScanButton(
    icon: ImageVector,
    contentDescription: String,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A D-pad hold arrives as repeating key-downs then one key-up, so scanning
    // is driven off that edge pair rather than a click, matching the pointer
    // press/release path. Guarded so the auto-repeat does not re-issue begin.
    var scanning by remember { mutableStateOf(false) }
    // Releasing while unfocused (or leaving mid-hold) would otherwise strand the
    // player in scan mode forever, since the key-up never arrives here.
    DisposableEffect(Unit) {
        onDispose { if (scanning) onEnd() }
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .dpadFocus(CircleShape)
            .onKeyEvent { e ->
                when {
                    e.key != Key.DirectionCenter && e.key != Key.Enter &&
                        e.key != Key.NumPadEnter -> false
                    e.type == KeyEventType.KeyDown -> {
                        if (!scanning) {
                            scanning = true
                            onBegin()
                        }
                        true
                    }
                    e.type == KeyEventType.KeyUp -> {
                        if (scanning) {
                            scanning = false
                            onEnd()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onBegin()
                    try {
                        awaitRelease()
                    } finally {
                        onEnd()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
@AndroidxOptIn(UnstableApi::class)
private fun AudioMiniController(viewModel: MainViewModel, visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    val player = viewModel.dacpPlayer ?: return
    val context = LocalContext.current
    MiniController(
        player,
        bitmapLoader = remember { DataSourceBitmapLoader(context) },
        onClick = onClick
    )
}

@Composable
@AndroidxOptIn(UnstableApi::class)
private fun NowPlayingContent(viewModel: MainViewModel, mediaWakeTick: Long = 0L) {
    val track by viewModel.trackInfo.collectAsState()
    val tv = isTv()

    // the art is the show; chrome appears on interaction and fades back out
    var controlsTick by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    val fillArt by viewModel.artFill.collectAsState()
    // While the controls are hidden they leave composition, taking focus with
    // them. This host keeps focus in the media area so the next key press can
    // still be seen and bring them back, instead of locking the user out.
    val artFocus = remember { FocusRequester() }
    // Waking the chrome must also land focus on a real button, or the user sees
    // controls with nothing selected and has to press OK to get in.
    val playPauseFocus = remember { FocusRequester() }
    // Whether play/pause truly holds focus. requestFocus() can report success
    // against a requester whose node is not composed yet, so the retry loop
    // needs this to know the focus actually landed rather than stopping early.
    var isPlayPauseFocused by remember { mutableStateOf(false) }
    // Whether focus is anywhere in the media area. Declared before the timer
    // because the timer reads it: chrome is only allowed to be up while we own
    // focus, so walking down to the nav bar hides it no matter what the last
    // key press did on its way out.
    var mediaHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controlsTick, mediaHasFocus) {
        if (!mediaHasFocus) {
            controlsVisible = false
            return@LaunchedEffect
        }
        controlsVisible = true
        delay(AUDIO_CONTROLS_HIDE_MS)
        controlsVisible = false
    }
    val wake = { controlsTick++ }

    // Nav bar handed focus up to us. Focus is still down there, so mediaHasFocus
    // is false and the timer would refuse to show anything -- claim the art host
    // first. That flips mediaHasFocus, which lets the timer raise the chrome;
    // the transition effect below then lands focus on play/pause.
    LaunchedEffect(mediaWakeTick) {
        if (mediaWakeTick <= 0L) return@LaunchedEffect
        artFocus.requestFocusUntilLanded { mediaHasFocus }
        wake()
    }

    // Focus is landed only on the hidden -> visible edge, never on an ordinary
    // wake. Bumping focus on every key press would fight the user: pressing
    // left off play/pause would immediately snap the highlight back onto it.
    // Keyed on controlsVisible because that is the signal the buttons are
    // actually composed and can therefore accept focus.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            // The chrome fades in, so the button may not be focusable for a few
            // frames yet; retry until it genuinely takes the focus.
            if (mediaHasFocus) playPauseFocus.requestFocusUntilLanded(attempts = 30) { isPlayPauseFocused }
        } else if (mediaHasFocus) {
            // Chrome going away takes the buttons -- and focus -- with them.
            // Park focus on the art host so the next key press is still seen,
            // but only while focus is ours: if the user walked down to the nav
            // bar, grabbing it back would yank them out of the menu.
            runCatching { artFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { st ->
                // Authoritative: the timer keys off this, so leaving for the nav
                // bar drops the chrome even if the departing key press woke it.
                mediaHasFocus = st.hasFocus
            }
            .focusRequester(artFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Down out of the media area: let it through untouched. Waking
                // here would re-show the chrome we are in the middle of leaving.
                if (e.key == Key.DirectionDown) return@onPreviewKeyEvent false
                // hidden: any key just wakes the chrome and is swallowed, so the
                // press that reveals the controls doesn't also actuate one
                val wasHidden = !controlsVisible
                wake()
                wasHidden
            },
        contentAlignment = Alignment.Center
    ) {
        // chapter progress, shared by the slim bar and the visualizer
        val player = viewModel.dacpPlayer
        var progressFraction by remember { mutableStateOf(0f) }
        LaunchedEffect(player) {
            if (player == null) return@LaunchedEffect
            while (true) {
                val d = player.duration
                progressFraction = if (d > 0) {
                    (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                } else 0f
                delay(SLIM_BAR_POLL_MS)
            }
        }

        // cover art fills the whole media area
        if (track.coverArt != null) {
            Image(
                bitmap = track.coverArt!!.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_cover_art),
                modifier = Modifier.fillMaxSize(),
                // Crop fills the area; Fit shows the whole cover, letterboxed
                contentScale = if (fillArt) ContentScale.Crop else ContentScale.Fit
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(if (tv) 96.dp else 64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }

        // Thin always-on progress line, shown only while the chrome is hidden so
        // the two never stack. Position alone, no text -- just enough to see how
        // far along the track is at a glance.
        AnimatedVisibility(
            visible = !controlsVisible,
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MS)),
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MS)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SlimProgressBar(progressFraction)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            // The default fade is slow enough to read as lag when the chrome is
            // meant to appear the instant a key is pressed.
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MS)),
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MS)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.65f),
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // track info
                Text(
                    text = track.title.ifEmpty { stringResource(R.string.unknown_track) },
                    style = if (tv) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.artist.isNotEmpty()) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (track.album.isNotEmpty()) {
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (player != null) {
                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PositionText(player, Modifier.padding(end = 8.dp))
                        Box(modifier = Modifier.weight(1f)) { ProgressSlider(player) }
                        DurationText(player, Modifier.padding(start = 8.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HoldScanButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.cd_rewind),
                            onBegin = { wake(); viewModel.audioScanBegin(false) },
                            onEnd = { viewModel.audioScanEnd() }
                        )
                        PreviousButton(player, modifier = Modifier.dpadFocus())
                        PlayPauseButton(
                            player,
                            modifier = Modifier
                                .size(63.dp)
                                .focusRequester(playPauseFocus)
                                .onFocusChanged { isPlayPauseFocused = it.isFocused }
                                .dpadFocus(CircleShape),
                            iconSize = 40.dp
                        )
                        NextButton(player, modifier = Modifier.dpadFocus())
                        HoldScanButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.cd_fast_forward),
                            onBegin = { wake(); viewModel.audioScanBegin(true) },
                            onEnd = { viewModel.audioScanEnd() }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { wake(); viewModel.audioVolumeDown() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeDown, stringResource(R.string.cd_volume_down))
                        }
                        IconButton(onClick = { wake(); viewModel.audioMuteToggle() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeOff, stringResource(R.string.cd_mute))
                        }
                        IconButton(onClick = { wake(); viewModel.audioVolumeUp() }, modifier = Modifier.dpadFocus()) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.cd_volume_up))
                        }
                        IconButton(
                            onClick = { wake(); viewModel.setArtFill(!fillArt) },
                            modifier = Modifier.dpadFocus()
                        ) {
                            Icon(
                                if (fillArt) Icons.Default.FitScreen else Icons.Default.Crop,
                                stringResource(
                                    if (fillArt) R.string.cd_art_fit else R.string.cd_art_fill
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val VIDEO_OVERLAY_HIDE_MS = 4000L
private const val AUDIO_CONTROLS_HIDE_MS = 3000L
// Snappy enough that the chrome feels like it responds to the keypress itself.
private const val CONTROLS_FADE_MS = 90
private const val SLIM_BAR_POLL_MS = 1000L
// Visualizer. Field is deliberately low-res: it is scaled to full screen, so cost
// depends on field size, not panel size. 96x54 keeps a 16:9 shape.
private const val VIZ_FIELD_W = 96
private const val VIZ_FIELD_H = 54
private const val VIZ_LEVEL_SMOOTH_MS = 90
private const val VIZ_LEVEL_POLL_MS = 33L      // ~30fps
private const val VIZ_FREQ1 = 11f
private const val VIZ_FREQ2 = 17f
private const val VIZ_FREQ3 = 7f
private const val VIZ_AMP_BASE = 0.55f         // motion continues through silence
private const val VIZ_AMP_LEVEL = 0.45f        // audio-driven part
private const val VIZ_HUE_CYCLE_S = 90f        // full palette rotation
private const val VIZ_HUE_SPREAD = 0.35f       // hue variation across intensity
private const val VIZ_HUE_SPATIAL = 0.22f      // hue variation across the field
private const val VIZ_SATURATION = 0.75f       // <1 keeps all subpixels lit
private const val VIZ_MIN_BRIGHTNESS = 0.22f   // no pixel ever sits fully black
private const val VIZ_PLAYED_GAIN = 0.22f      // played side brightened
private const val VIZ_PLAYED_HUE = 0.42f       // played side hue offset: the readable cue
private const val VIZ_BOUNDARY_SOFTNESS = 0.30f // width of the gradient seam
private const val VIZ_EDGE_GAIN = 0.45f        // crest marking exact position
private const val VIZ_EDGE_TIGHTNESS = 90f     // higher = tighter crest
private const val VIZ_MAX_BRIGHTNESS = 0.62f   // never drive pixels to full white
private const val VIZ_FADE_IN_MS = 700         // gentle arrival; dismissal stays snappy

@Composable
private fun DebugOverlay(info: DebugInfo, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val style = MaterialTheme.typography.labelSmall
        val headingStyle = style.copy(fontWeight = FontWeight.Bold)
        val color = Color.White.copy(alpha = 0.9f)
        val headingColor = Color(0xFF80D8FF) // cyan
        val context = LocalContext.current
        debugOverlaySections(context, info).forEachIndexed { i, section ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            Text(section.title.uppercase(), style = headingStyle, color = headingColor)
            section.lines.forEach { Text(it, style = style, color = color) }
        }
    }
}

private data class DebugSection(val title: String, val lines: List<String>)

private fun debugOverlaySections(context: Context, info: DebugInfo): List<DebugSection> = buildList {
    buildList {
        if (info.videoCodec.isNotEmpty()) {
            add(context.getString(R.string.debug_video, info.videoCodec, info.videoRes))
            add(context.getString(R.string.debug_fps_bitrate, info.videoFps, info.bitrateStr))
            add(context.getString(R.string.debug_frames_drops, info.videoFrames, info.droppedFrames))
            add(context.getString(R.string.debug_jitter, info.jitterStr))
        }
    }.takeIf { it.isNotEmpty() }?.let { add(DebugSection(context.getString(R.string.debug_section_video), it)) }

    buildList {
        if (info.audioCodec.isNotEmpty()) add(context.getString(R.string.debug_audio, info.audioCodec, info.audioVolume))
        info.audio?.let { a ->
            add(context.getString(R.string.debug_audio_buffer, a.backlogMs, a.tunedCushionMs))
            add(context.getString(R.string.debug_audio_glitch, a.trims, a.drops, a.silences, a.underruns, a.xrun))
            add(context.getString(R.string.debug_audio_decode, formatDecode(a.decodeMeanUs, a.decodeMaxUs, a.decodeHeld, a.decodeErrors)))
        }
    }.takeIf { it.isNotEmpty() }?.let { add(DebugSection(context.getString(R.string.debug_section_audio), it)) }

    add(DebugSection(context.getString(R.string.debug_section_network), listOf(context.getString(R.string.debug_clients, info.connections))))
}

// "mean/max ms held=N", or "held=N" before first latency window lands
private fun formatDecode(meanUs: Int, maxUs: Int, held: Int, errors: Int): String =
    (if (meanUs == 0) "held=$held"
    else "%.1f/%.1f ms held=%d".format(meanUs / 1000.0, maxUs / 1000.0, held)) + " errs=$errors"
