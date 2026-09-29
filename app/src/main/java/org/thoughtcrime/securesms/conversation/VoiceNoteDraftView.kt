package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.Observer
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.audio.AudioWaveForms
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.conversation.v2.light.LightVoiceNoteDraftCallbacks
import org.thoughtcrime.securesms.conversation.v2.light.LightVoiceNoteDraftHostView
import org.thoughtcrime.securesms.conversation.v2.light.LightVoiceNoteDraftState
import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.mms.AudioSlide
import java.util.concurrent.TimeUnit

/**
 * View-land owner for the recorded-draft review host.
 *
 * DraftViewModel still owns the blob and VoiceNoteMediaController still owns playback. This class only
 * translates their URI/state callbacks into immutable Light presentation state.
 */
class VoiceNoteDraftView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr) {

  var listener: Listener? = null

  var draft: DraftTable.Draft? = null
    private set

  private val lightView: LightVoiceNoteDraftHostView
  private var audioSlide: AudioSlide? = null
  private var audioUri: Uri? = null
  private var metadataDisposable: Disposable = Disposable.disposed()

  val playbackStateObserver: Observer<VoiceNotePlaybackState> = Observer(::onPlaybackState)

  init {
    orientation = VERTICAL
    inflate(context, R.layout.voice_note_draft_view, this)
    lightView = findViewById(R.id.light_voice_note_draft)
    lightView.callbacks = LightVoiceNoteDraftCallbacks(
      onDelete = {
        audioUri?.let { listener?.onVoiceNoteDraftDelete(it) }
      },
      onTogglePlayback = {
        audioUri?.let { uri ->
          if (lightView.state.isPlaying) {
            listener?.onVoiceNoteDraftPause(uri)
          } else {
            listener?.onVoiceNoteDraftPlay(uri, lightView.state.progress.toDouble())
          }
        }
      },
      onSeek = { progress ->
        audioUri?.let { listener?.onVoiceNoteDraftSeekTo(it, progress) }
      },
      onSend = {
        audioUri?.let { listener?.onVoiceNoteDraftSend(it) }
      }
    )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (lightView.state.durationMillis == 0L) {
      loadDuration()
    }
  }

  override fun onDetachedFromWindow() {
    metadataDisposable.dispose()
    super.onDetachedFromWindow()
  }

  fun clearDraft() {
    metadataDisposable.dispose()
    audioSlide = null
    audioUri = null
    draft = null
    lightView.state = LightVoiceNoteDraftState()
  }

  fun setDraft(draft: DraftTable.Draft) {
    val nextAudioSlide = AudioSlide.createFromVoiceNoteDraft(draft)
    val nextUri = nextAudioSlide.uri

    if (nextUri != audioUri) {
      lightView.state = LightVoiceNoteDraftState()
    }

    audioSlide = nextAudioSlide
    audioUri = nextUri
    this.draft = draft
    loadDuration()
  }

  private fun loadDuration() {
    val slide = audioSlide ?: return
    metadataDisposable.dispose()
    metadataDisposable = AudioWaveForms
      .getWaveForm(context, slide.asAttachment())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { info ->
          lightView.state = lightView.state.copy(
            durationMillis = info.getDuration(TimeUnit.MILLISECONDS)
          )
        },
        { /* The draft remains playable even if waveform metadata cannot be read. */ }
      )
  }

  private fun onPlaybackState(playbackState: VoiceNotePlaybackState) {
    val targetUri = audioUri ?: return
    val current = lightView.state

    lightView.state = if (playbackState.uri == targetUri) {
      current.copy(
        positionMillis = if (playbackState.isAutoReset) 0L else playbackState.playheadPositionMillis,
        durationMillis = playbackState.trackDuration.takeIf { it > 0L } ?: current.durationMillis,
        isPlaying = playbackState.isPlaying
      )
    } else if (current.isPlaying) {
      current.copy(isPlaying = false)
    } else {
      current
    }
  }

  interface Listener {
    fun onVoiceNoteDraftPlay(audioUri: Uri, progress: Double)
    fun onVoiceNoteDraftPause(audioUri: Uri)
    fun onVoiceNoteDraftSeekTo(audioUri: Uri, progress: Double)
    fun onVoiceNoteDraftDelete(audioUri: Uri)
    fun onVoiceNoteDraftSend(audioUri: Uri)
  }
}
