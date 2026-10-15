package cx.aswin.boxlore.feature.onboarding

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.delay

/** A show card's clip: a real episode, heard from about 40% in. */
internal data class EchoClip(val showId: Long, val url: String, val startMs: Long)

internal data class EchoPreviewStatus(
    val showId: Long? = null,
    /** The listener asked to hear it; true while buffering too. */
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    /** How far through the clip, 0 to 1. */
    val progress: Float = 0f,
)

/**
 * A clip is a short listen from the card's start point. It fades in whenever it starts or resumes
 * and fades out at the end, so it never cuts in or out mid-word, then rewinds for another listen.
 */
internal object EchoClipTiming {
    const val LengthMillis = 45_000L
    const val FadeInMillis = 600L
    const val FadeOutMillis = 1_500L

    fun volume(elapsed: Long, resumedAt: Long): Float = minOf(
        ((elapsed - resumedAt).toFloat() / FadeInMillis).coerceIn(0f, 1f),
        ((LengthMillis - elapsed).toFloat() / FadeOutMillis).coerceIn(0f, 1f),
    )

    fun progress(elapsed: Long): Float = (elapsed.toFloat() / LengthMillis).coerceIn(0f, 1f)

    fun finished(elapsed: Long): Boolean = elapsed >= LengthMillis
}

/** Plays one show card's clip at a time. Reacting to a card never starts the next card's clip. */
internal interface EchoShowPreview {
    val status: State<EchoPreviewStatus>

    /** Puts [clip] on top, paused and ready to play from its start; null pauses everything. */
    fun load(clip: EchoClip?)

    fun toggle()

    fun pause()

    /** Runs while playing: fades, progress and the clip's end. */
    fun tick()

    /** Frees the players; the pool can be used again afterwards. */
    fun release()
}

/** Tests swap the player for a fake; the app plays clips from an [EchoClipPool]. */
internal val LocalEchoShowPreviewFactory = staticCompositionLocalOf<(Context) -> EchoShowPreview> { { context -> EchoClipPool(context) } }

/**
 * The deck's clip player. [shared] is the view model's pool, whose clips were buffered before the
 * deck opened; it is paused, not released, when the deck closes. Without one, a pool of its own
 * is made and released with the deck.
 */
@Composable
internal fun rememberEchoShowPreview(shared: EchoShowPreview? = null): EchoShowPreview {
    val context = LocalContext.current.applicationContext
    val factory = LocalEchoShowPreviewFactory.current
    val preview = shared ?: remember { factory(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(preview, lifecycle) {
        // Leaving the app pauses the clip; closing the deck stops it.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) preview.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (shared == null) preview.release() else preview.load(null)
        }
    }
    val playing = preview.status.value.playing
    LaunchedEffect(preview, playing) {
        while (playing) {
            preview.tick()
            delay(EchoClipTickMillis)
        }
    }
    return preview
}

private const val EchoClipTickMillis = 80L

/**
 * One small player per clip, apart from the listening queue. [prefetch] prepares each at its start
 * point as soon as the cards arrive, so most clips play at once; one still loading finishes on tap.
 * The card on top plays and the rest wait, paused. Players take audio focus like any player, so a playing episode pauses while a
 * clip plays.
 */
@OptIn(UnstableApi::class)
internal class EchoClipPool(private val context: Context) : EchoShowPreview {
    private val players = LinkedHashMap<Long, Pair<EchoClip, ExoPlayer>>()
    private val state: MutableState<EchoPreviewStatus> = mutableStateOf(EchoPreviewStatus())
    override val status: State<EchoPreviewStatus> = state
    private var active: Pair<EchoClip, ExoPlayer>? = null
    private var resumedAt = 0L

    /** Starts buffering every clip from its start, in the background; the deck never waits for it. */
    fun prefetch(clips: List<EchoClip>) {
        clips.forEach { player(it) }
    }

    override fun load(clip: EchoClip?) {
        if (clip != null && clip == active?.first) return
        active?.second?.let { it.playWhenReady = false }
        active = clip?.let { player(it) }
        resumedAt = 0L
        state.value = EchoPreviewStatus(showId = clip?.showId, failed = active?.second?.playerError != null)
        active?.let { (start, player) -> player.seekTo(start.startMs) }
    }

    override fun toggle() {
        val player = active?.second ?: return
        if (player.playWhenReady) pause() else play()
    }

    private fun play() {
        val (clip, player) = active ?: return
        if (state.value.failed) return
        if (EchoClipTiming.finished(elapsed()) || player.playbackState == Player.STATE_ENDED) player.seekTo(clip.startMs)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        resumedAt = elapsed()
        player.volume = 0f
        player.playWhenReady = true
        publish()
    }

    override fun pause() {
        active?.second?.playWhenReady = false
        publish()
    }

    override fun tick() {
        val (clip, player) = active ?: return
        val elapsed = elapsed()
        if (EchoClipTiming.finished(elapsed)) {
            player.playWhenReady = false
            player.seekTo(clip.startMs)
        } else {
            player.volume = EchoClipTiming.volume(elapsed, resumedAt)
        }
        publish()
    }

    override fun release() {
        players.values.forEach { (_, player) -> player.release() }
        players.clear()
        active = null
        state.value = EchoPreviewStatus()
    }

    private fun player(clip: EchoClip): Pair<EchoClip, ExoPlayer> =
        players.getOrPut(clip.showId) {
            clip to newPlayer().apply {
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) = publishIfActive(clip)

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publishIfActive(clip)

                        override fun onPlayerError(error: PlaybackException) {
                            playWhenReady = false
                            if (active?.first == clip) state.value = state.value.copy(playing = false, buffering = false, failed = true)
                        }
                    },
                )
                setMediaItem(MediaItem.fromUri(clip.url), clip.startMs)
                volume = 0f
                playWhenReady = false
                prepare()
            }
        }

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(Util.getUserAgent(context, "BoxLore"))
                        .setAllowCrossProtocolRedirects(true),
                ),
                // Many podcast MP3s have no seek table; without this the clip would start at 0:00.
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
            ),
        )
        // A clip needs a little audio to start and never more than its own length.
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 45_000, 1_000, 2_000).build())
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private fun publishIfActive(clip: EchoClip) {
        if (active?.first == clip) publish()
    }

    private fun elapsed(): Long = active?.let { (clip, player) -> (player.currentPosition - clip.startMs).coerceAtLeast(0L) } ?: 0L

    private fun publish() {
        if (state.value.failed) return
        val player = active?.second ?: return
        val wanted = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        state.value = state.value.copy(
            playing = wanted,
            buffering = wanted && player.playbackState == Player.STATE_BUFFERING,
            progress = EchoClipTiming.progress(elapsed()),
        )
    }
}
