/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.ActionMenuView
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.views.DarkOverflowToolbar

/**
 * The conversation thread's Material `Toolbar` is no longer drawn: `LightConversationTopBar` is
 * painted over it, and the toolbar is kept only to host the menu. Two things therefore have to hold,
 * neither of which the compiler can check and both of which fail *silently* on the device -- as an
 * action item that cannot be seen or tapped, or as an ellipses that opens nothing.
 *
 * 1. No item renders as an action button, so the only child the toolbar's `ActionMenuView` lays out
 *    is the overflow button. `conversation_callable_secure` in particular asks for
 *    `showAsAction="always"` on both the voice and the video call.
 * 2. `Toolbar.showOverflowMenu()` -- what the Light bar's ellipses calls -- actually has an overflow
 *    menu to show.
 *
 * `R.menu.conversation` itself is deliberately not inflated here: its search item names an
 * `actionViewClass` of Molly's own `SearchView`, and inflating that reaches `EmojiTextView`, whose
 * constructor blocks forever without the app's dependency graph. The menus used instead are the ones
 * that actually carry `showAsAction` flags.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class ConversationOptionsMenuOverflowTest {

  @Test
  fun `the call actions are demoted out of the toolbar and into the overflow`() {
    val toolbar = callableToolbar()

    val actionMenuView = toolbar.firstChildOfType<ActionMenuView>()
    assertThat(actionMenuView).isNotNull()

    // Only the overflow button. Were the calls still action items there would be three children.
    assertThat(actionMenuView!!.childCount).isEqualTo(1)
    assertThat(actionMenuView.getChildAt(0)::class.java.simpleName).contains("Overflow")
  }

  @Test
  fun `the overflow menu the Light bar's ellipses opens is actually there`() {
    val toolbar = callableToolbar()

    // The exact call ConversationFragment wires the ellipses to. It returns false when there is no
    // overflow button, no menu view, or nothing left to put in the popup.
    assertThat(toolbar.showOverflowMenu()).isTrue()
  }

  /**
   * The search item keeps its `collapseActionView` flag: that flag is what lets
   * `MenuItem.expandActionView()` put the `SearchView` into the toolbar when "Search in conversation"
   * is chosen. Demoting it along with everything else would silently break in-conversation search.
   */
  @Test
  fun `search keeps the flag that lets it expand while its neighbours lose theirs`() {
    val toolbar = toolbar()
    val menu = toolbar.menu

    val search = menu.add(Menu.NONE, R.id.menu_search, 0, "Search").apply {
      actionView = View(toolbar.context)
      setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
    }
    val media = menu.add(Menu.NONE, R.id.menu_view_media, 1, "Media").apply {
      actionView = View(toolbar.context)
      setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
    }
    layOut(toolbar)

    ConversationOptionsMenu.forceIntoOverflow(menu)

    assertThat(media.expandActionView()).isFalse()
    assertThat(search.expandActionView()).isTrue()
  }

  private fun callableToolbar(): DarkOverflowToolbar {
    val toolbar = toolbar()
    toolbar.inflateMenu(R.menu.conversation_callable_secure)
    toolbar.inflateMenu(R.menu.conversation_unmuted)

    ConversationOptionsMenu.forceIntoOverflow(toolbar.menu)
    layOut(toolbar)

    return toolbar
  }

  private fun toolbar(): DarkOverflowToolbar {
    val themed = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Signal_DayNight)
    return DarkOverflowToolbar(themed)
  }

  private fun layOut(view: View) {
    val width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
    val height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(width, height)
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
  }

  private inline fun <reified T : View> ViewGroup.firstChildOfType(): T? {
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      if (child is T) return child
    }
    return null
  }
}
