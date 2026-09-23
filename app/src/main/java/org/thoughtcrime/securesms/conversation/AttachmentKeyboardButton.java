package org.thoughtcrime.securesms.conversation;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum AttachmentKeyboardButton {

  GALLERY(R.string.AttachmentKeyboard_gallery, R.drawable.symbol_album_tilt_24),
  /**
   * LIGHT PHONE: records a voice note.
   *
   * The Light thread's bottom bar is call, attach and compose, so the hold-to-record microphone that
   * used to sit inside the compose bubble is gone. Voice notes are an attachment like any other now,
   * and this is where the user reaches them. It starts recording straight away, hands-free -- see
   * {@code MicrophoneRecorderView.startLockedRecording()}.
   */
  VOICE_NOTE(R.string.AttachmentKeyboard_voice_note, R.drawable.ic_mic_24),
  FILE(R.string.AttachmentKeyboard_file, R.drawable.symbol_file_24),
  CONTACT(R.string.AttachmentKeyboard_contact, org.signal.core.ui.R.drawable.symbol_person_circle_24),
  LOCATION(R.string.AttachmentKeyboard_location, R.drawable.symbol_location_circle_24),
  POLL(R.string.AttachmentKeyboard_poll, R.drawable.symbol_poll_24);

  private final int titleRes;
  private final int iconRes;

  AttachmentKeyboardButton(@StringRes int titleRes, @DrawableRes int iconRes) {
    this.titleRes = titleRes;
    this.iconRes = iconRes;
  }

  public @StringRes int getTitleRes() {
    return titleRes;
  }

  public @DrawableRes int getIconRes() {
    return iconRes;
  }
}
