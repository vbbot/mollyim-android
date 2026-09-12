package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import androidx.annotation.ColorRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.R as MaterialR
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper

/**
 * Scroll helper to manage the color state of the top bar and status bar.
 */
class ConversationToolbarOnScrollHelper(
  activity: FragmentActivity,
  toolbarBackground: View,
  private val wallpaperProvider: () -> ChatWallpaper?,
  private val releaseNotesProvider: () -> Boolean,
  lifecycleOwner: LifecycleOwner,
  private val incognito: Boolean = false
) : Material3OnScrollHelper(
  activity = activity,
  views = listOf(toolbarBackground),
  lifecycleOwner = lifecycleOwner,
  setStatusBarColor = {}
) {
  override val activeColorSet: ColorSet
    = when {
      incognito -> ColorSet.from(activity, R.color.conversation_toolbar_color_incognito)
      releaseNotesProvider() -> ColorSet.from(activity, R.color.release_notes_toolbar_scrolled)
      else -> ColorSet.from(activity, getActiveToolbarColor(wallpaperProvider() != null))
    }

  override val inactiveColorSet: ColorSet
    = when {
      incognito -> ColorSet.from(activity, R.color.conversation_toolbar_color_incognito)
      releaseNotesProvider() -> ColorSet.from(activity, R.color.release_notes_toolbar_transparent)
      else -> ColorSet.from(activity, getInactiveToolbarColor(wallpaperProvider() != null))
    }

  /**
   * LIGHT PHONE: the same colour as the unscrolled state.
   *
   * This used to raise the strip behind the status bar to `colorSurfaceContainer` once the thread
   * was scrolled, to imply elevation. On the black palette that reads as a faint grey rectangle
   * hanging over an otherwise pure-black screen, and the Light design has no elevation scrim to
   * imply in the first place. The wallpaper and release-notes cases keep their own treatment.
   */
  @ColorRes
  private fun getActiveToolbarColor(hasWallpaper: Boolean): Int {
    return if (hasWallpaper) R.color.conversation_toolbar_color_wallpaper_scrolled else MaterialR.attr.colorSurface
  }

  @ColorRes
  private fun getInactiveToolbarColor(hasWallpaper: Boolean): Int {
    return if (hasWallpaper) R.color.conversation_toolbar_color_wallpaper else MaterialR.attr.colorSurface
  }
}
