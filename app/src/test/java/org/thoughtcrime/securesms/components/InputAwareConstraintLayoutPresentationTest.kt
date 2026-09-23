/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.app.Application
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InputAwareConstraintLayoutPresentationTest {

  @Test
  fun `creators default to the keyboard-height container and can select full screen`() {
    val defaultCreator = creator(id = 1)
    val fullScreenCreator = creator(id = 2, presentation = InputAwareConstraintLayout.Presentation.FULL_SCREEN)

    assertThat(defaultCreator.presentation).isEqualTo(InputAwareConstraintLayout.Presentation.KEYBOARD_HEIGHT)
    assertThat(defaultCreator.presentation.containerId).isEqualTo(R.id.input_container)
    assertThat(fullScreenCreator.presentation.containerId).isEqualTo(R.id.full_screen_input_container)
  }

  @Test
  fun `full-screen inputs switch and hide in the active container`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val root = InputAwareConstraintLayout(activity)
    val keyboardContainer = FrameLayout(activity).apply { id = R.id.input_container }
    val fullScreenContainer = FrameLayout(activity).apply {
      id = R.id.full_screen_input_container
      visibility = View.GONE
    }
    root.addView(keyboardContainer)
    root.addView(fullScreenContainer)
    activity.setContentView(root)
    root.fragmentManager = activity.supportFragmentManager

    val events = mutableListOf<String>()
    root.addInputListener(
      object : InputAwareConstraintLayout.Listener {
        override fun onInputShown(fragmentCreatorId: Int) {
          events += "shown:$fragmentCreatorId"
        }

        override fun onInputHidden() {
          events += "hidden"
        }
      }
    )

    val first = creator(id = 10, presentation = InputAwareConstraintLayout.Presentation.FULL_SCREEN)
    val second = creator(id = 11, presentation = InputAwareConstraintLayout.Presentation.FULL_SCREEN)
    val imeTarget = EditText(activity)

    root.toggleInput(first, imeTarget, showSoftKeyOnHide = false)
    activity.supportFragmentManager.executePendingTransactions()
    assertThat(root.isInputShowing).isTrue()
    assertThat(fullScreenContainer.visibility == View.VISIBLE).isTrue()
    assertThat(activity.supportFragmentManager.findFragmentById(R.id.full_screen_input_container)).isEqualTo(first.created)
    assertThat(activity.supportFragmentManager.findFragmentById(R.id.input_container)).isNull()

    root.toggleInput(second, imeTarget, showSoftKeyOnHide = false)
    activity.supportFragmentManager.executePendingTransactions()
    assertThat(activity.supportFragmentManager.findFragmentById(R.id.full_screen_input_container)).isEqualTo(second.created)
    assertThat(events).containsExactly("shown:10", "hidden", "shown:11")

    root.hideInput()
    activity.supportFragmentManager.executePendingTransactions()
    assertThat(root.isInputShowing).isFalse()
    assertThat(fullScreenContainer.visibility == View.GONE).isTrue()
    assertThat(keyboardContainer.visibility == View.VISIBLE).isTrue()
    assertThat(activity.supportFragmentManager.findFragmentById(R.id.full_screen_input_container)).isNull()
    assertThat(events).containsExactly("shown:10", "hidden", "shown:11", "hidden")
  }

  private fun creator(
    id: Int,
    presentation: InputAwareConstraintLayout.Presentation? = null
  ): TestCreator {
    return TestCreator(id, presentation)
  }

  private class TestCreator(
    override val id: Int,
    private val requestedPresentation: InputAwareConstraintLayout.Presentation?
  ) : InputAwareConstraintLayout.FragmentCreator {
    val created = Fragment()

    override val presentation: InputAwareConstraintLayout.Presentation
      get() = requestedPresentation ?: super.presentation

    override fun create(): Fragment = created
  }
}
