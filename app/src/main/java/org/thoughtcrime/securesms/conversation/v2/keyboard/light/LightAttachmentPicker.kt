/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.keyboard.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.models.media.Media
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.concurrent.TimeUnit

/** The explicit product order. It deliberately does not follow the enum declaration order. */
val LIGHT_ATTACHMENT_ACTIONS: List<AttachmentKeyboardButton> = listOf(
  AttachmentKeyboardButton.GALLERY,
  AttachmentKeyboardButton.VOICE_NOTE,
  AttachmentKeyboardButton.FILE,
  AttachmentKeyboardButton.POLL,
  AttachmentKeyboardButton.CONTACT,
  AttachmentKeyboardButton.LOCATION
)

data class LightAttachmentPickerState(
  val actions: List<AttachmentKeyboardButton> = LIGHT_ATTACHMENT_ACTIONS,
  val mediaAccess: LightAttachmentMediaAccess = LightAttachmentMediaAccess.NoAccess
) {
  companion object {
    /**
     * Projects platform permission facts into presentation state. Selected-media access is checked
     * first because Android also reports it as "any" access, and the legacy picker gave it priority.
     */
    fun map(
      actions: List<AttachmentKeyboardButton>,
      media: List<Media>,
      canOnlyReadSelectedMedia: Boolean,
      canReadAnyMedia: Boolean
    ): LightAttachmentPickerState {
      val mediaSnapshot = media.toList()
      val access = when {
        canOnlyReadSelectedMedia && mediaSnapshot.isEmpty() -> LightAttachmentMediaAccess.LimitedEmpty
        canOnlyReadSelectedMedia -> LightAttachmentMediaAccess.Limited(mediaSnapshot)
        canReadAnyMedia -> LightAttachmentMediaAccess.Full(mediaSnapshot)
        else -> LightAttachmentMediaAccess.NoAccess
      }

      return LightAttachmentPickerState(actions = actions.toList(), mediaAccess = access)
    }
  }
}

sealed interface LightAttachmentMediaAccess {
  data object LimitedEmpty : LightAttachmentMediaAccess
  data class Limited(val media: List<Media>) : LightAttachmentMediaAccess
  data class Full(val media: List<Media>) : LightAttachmentMediaAccess
  data object NoAccess : LightAttachmentMediaAccess
}

/**
 * The full-screen Light attachment chooser. It renders immutable state and reports user intent; the
 * fragment remains the owner of permissions, context menus, recent-media loading and result bundles.
 */
@Composable
fun LightAttachmentPicker(
  state: LightAttachmentPickerState,
  onBack: () -> Unit,
  onActionSelected: (AttachmentKeyboardButton) -> Unit,
  onMediaSelected: (Media) -> Unit,
  onPermissionsRequested: () -> Unit,
  onManageRequested: (showAtStart: Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      // The thread remains laid out underneath this container. Consume every pointer pass so an
      // unhandled tap can never select a message or activate thread chrome below the picker.
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      }
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.ConversationFragment__content_description_back_button)
      ),
      center = LightTopBarCenter.Text(stringResource(R.string.ConversationActivity_add_attachment))
    )

    LightScrollView(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      RecentMediaAccessBlock(
        access = state.mediaAccess,
        onMediaSelected = onMediaSelected,
        onPermissionsRequested = onPermissionsRequested,
        onManageRequested = onManageRequested
      )

      state.actions.forEach { action ->
        AttachmentActionRow(
          action = action,
          onClick = { onActionSelected(action) }
        )
      }
    }
  }
}

@Composable
private fun RecentMediaAccessBlock(
  access: LightAttachmentMediaAccess,
  onMediaSelected: (Media) -> Unit,
  onPermissionsRequested: () -> Unit,
  onManageRequested: (showAtStart: Boolean) -> Unit
) {
  when (access) {
    LightAttachmentMediaAccess.LimitedEmpty -> {
      AccessExplanation(stringResource(R.string.AttachmentKeyboard_no_photos_found))
      AccessActionRow(
        label = stringResource(R.string.AttachmentKeyboard_manage),
        onClick = { onManageRequested(true) }
      )
    }

    is LightAttachmentMediaAccess.Limited -> {
      RecentMediaRail(media = access.media, onMediaSelected = onMediaSelected)
      AccessExplanation(stringResource(R.string.AttachmentKeyboard_signal_has_limited_access))
      AccessActionRow(
        label = stringResource(R.string.AttachmentKeyboard_manage),
        onClick = { onManageRequested(false) }
      )
    }

    is LightAttachmentMediaAccess.Full -> {
      if (access.media.isNotEmpty()) {
        RecentMediaRail(media = access.media, onMediaSelected = onMediaSelected)
      }
    }

    LightAttachmentMediaAccess.NoAccess -> {
      AccessExplanation(stringResource(R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos))
      AccessActionRow(
        label = stringResource(R.string.AttachmentKeyboard_allow_access),
        onClick = onPermissionsRequested
      )
    }
  }
}

@Composable
private fun RecentMediaRail(
  media: List<Media>,
  onMediaSelected: (Media) -> Unit
) {
  val thumbnailSize = MEDIA_THUMBNAIL_SIZE_UNITS.gridUnitsAsDp()

  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .height(thumbnailSize)
      .testTag(RECENT_MEDIA_RAIL_TAG),
    contentPadding = PaddingValues(horizontal = 2f.gridUnitsAsDp()),
    horizontalArrangement = Arrangement.spacedBy(0.75f.gridUnitsAsDp())
  ) {
    items(media, key = { it.uri.toString() }) { item ->
      MediaThumbnail(
        media = item,
        size = thumbnailSize,
        onClick = { onMediaSelected(item) }
      )
    }
  }
}

@Composable
private fun MediaThumbnail(
  media: Media,
  size: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit
) {
  BoxWithConstraints(
    modifier = Modifier
      .size(size)
      .testTag(mediaTestTag(media))
      .lightClickable(
        onClickLabel = media.fileName ?: media.uri.lastPathSegment,
        onClick = onClick
      )
  ) {
    GlideImage(
      model = media.uri,
      imageSize = DpSize(maxWidth, maxHeight),
      scaleType = GlideImageScaleType.CENTER_CROP,
      modifier = Modifier.fillMaxSize()
    )

    when {
      media.duration > 0 -> {
        LightText(
          text = formatVideoDuration(media.duration),
          variant = LightTextVariant.Superfine,
          color = Color.White,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 0.3f.gridUnitsAsDp(), vertical = 0.15f.gridUnitsAsDp())
        )
      }

      MediaUtil.isVideoType(media.contentType) -> {
        Image(
          painter = painterResource(R.drawable.triangle_right),
          contentDescription = null,
          colorFilter = ColorFilter.tint(Color.White),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(2f.gridUnitsAsDp())
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(0.5f.gridUnitsAsDp())
            .testTag(VIDEO_INDICATOR_TAG)
        )
      }
    }
  }
}

@Composable
private fun AccessExplanation(text: String) {
  LightText(
    text = text,
    variant = LightTextVariant.Detail,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp())
  )
}

@Composable
private fun AccessActionRow(label: String, onClick: () -> Unit) {
  LightText(
    text = label,
    variant = LightTextVariant.Heading,
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(onClick = onClick)
      .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.2f.gridUnitsAsDp())
  )
}

@Composable
private fun AttachmentActionRow(action: AttachmentKeyboardButton, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(actionTestTag(action))
      .lightClickable(onClick = onClick)
      .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.2f.gridUnitsAsDp())
  ) {
    LightText(
      text = stringResource(action.titleRes),
      variant = LightTextVariant.Heading,
      modifier = Modifier.testTag(actionLabelTestTag(action))
    )
  }
}

internal fun formatVideoDuration(timeMillis: Long): String {
  var remaining = timeMillis
  val hours = TimeUnit.MILLISECONDS.toHours(remaining)
  remaining -= TimeUnit.HOURS.toMillis(hours)
  val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
  remaining -= TimeUnit.MINUTES.toMillis(minutes)
  val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)

  return if (hours > 0) {
    "${zeroPad(hours)}:${zeroPad(minutes)}:${zeroPad(seconds)}"
  } else {
    "${zeroPad(minutes)}:${zeroPad(seconds)}"
  }
}

private fun zeroPad(value: Long): String = if (value < 10) "0$value" else value.toString()

internal fun actionTestTag(action: AttachmentKeyboardButton): String = "attachment-action-${action.name}"
internal fun actionLabelTestTag(action: AttachmentKeyboardButton): String = "attachment-action-label-${action.name}"
internal fun mediaTestTag(media: Media): String = "attachment-media-${media.uri}"
internal const val RECENT_MEDIA_RAIL_TAG = "attachment-recent-media"
internal const val VIDEO_INDICATOR_TAG = "attachment-video-indicator"
private const val MEDIA_THUMBNAIL_SIZE_UNITS = 6f

/** View-land host used by [org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment]. */
class LightAttachmentPickerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  private var state by mutableStateOf(LightAttachmentPickerState())

  var onBack: (() -> Unit)? = null
  var onActionSelected: ((AttachmentKeyboardButton) -> Unit)? = null
  var onMediaSelected: ((Media) -> Unit)? = null
  var onPermissionsRequested: (() -> Unit)? = null
  var onManageRequested: ((showAtStart: Boolean) -> Unit)? = null

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun submit(state: LightAttachmentPickerState) {
    this.state = state
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightAttachmentPicker(
        state = state,
        onBack = { onBack?.invoke() },
        onActionSelected = { onActionSelected?.invoke(it) },
        onMediaSelected = { onMediaSelected?.invoke(it) },
        onPermissionsRequested = { onPermissionsRequested?.invoke() },
        onManageRequested = { onManageRequested?.invoke(it) },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}
