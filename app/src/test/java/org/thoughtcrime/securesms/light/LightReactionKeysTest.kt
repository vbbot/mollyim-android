/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule

/**
 * The context window's second level -- the reaction keys -- and the level change that reaches it.
 *
 * The key set is asserted literally because it is a wire format as much as a layout: these are the
 * emoji this client can send, captured from the Light Phone's own emoji panel, and they replace both
 * Signal's scrubber and the "any emoji" picker on the end of it. Changing one silently changes what
 * lands on other people's screens.
 *
 * The level tests go through [LightActionPanelView] rather than the composables, because the level
 * lives there: the rows come from outside the panel, so the row that descends can only do it by
 * calling back into the host.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightReactionKeysTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `the keys are the reference client's three rows of eight`() {
    assertThat(LIGHT_REACTION_KEYS[0]).containsExactly("😅", "😊", "🙄", "😍", "😜", "😂", "😭", "😎")
    assertThat(LIGHT_REACTION_KEYS[1]).containsExactly("👏", "👍", "👎", "🤞", "✌️", "👌", "👋", "🙏")
    assertThat(LIGHT_REACTION_KEYS[2]).containsExactly("✨", "🔥", "❤️", "💔", "🏆", "🎯", "👑", "👀")
    assertThat(LIGHT_REACTION_KEYS.size).isEqualTo(3)
  }

  @Test
  fun `every key is rendered`() {
    setKeys()

    LIGHT_REACTION_KEYS.flatten().forEach { key ->
      composeTestRule.onNodeWithText(key).assertIsDisplayed()
    }
  }

  @Test
  fun `tapping a key reports that key`() {
    var chosen: String? = null
    setKeys(onKeySelected = { chosen = it })

    composeTestRule.onNodeWithText("🔥").performClick()

    assertThat(chosen).isEqualTo("🔥")
  }

  @Test
  fun `the chevron dismisses from the keys too`() {
    var dismissed = false
    setKeys(onDismiss = { dismissed = true })

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(dismissed).isTrue()
  }

  /**
   * Three 46dp rows sit at the top of a panel that is half of 413dp, which leaves the chevron its
   * zone on the bottom edge. Asserted rather than assumed: a key sharing pixels with the chevron is
   * a tap that dismisses instead of reacting.
   */
  @Test
  fun `the bottom row of keys clears the chevron`() {
    setKeys()

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val bottomRowKey = composeTestRule.onNodeWithText("👀").getUnclippedBoundsInRoot()
    val chevronTop = root.bottom - CHEVRON_ZONE

    assertThat(bottomRowKey.bottom.value).isLessThan(chevronTop.value)
  }

  @Test
  fun `the panel opens on the action rows and a row descends to the keys`() {
    val panel = setPanelView()

    composeTestRule.onNodeWithText("REACT").assertIsDisplayed()

    composeTestRule.onNodeWithText("REACT").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("👍").assertIsDisplayed()
    assertThat(panel.isOpen).isTrue()
  }

  /**
   * Reopening always lands on the action rows. The reference client gets this by declaring the level
   * below its null check, so that closing drops it; this view outlives any one opening, so it has to
   * be reset on the way in -- and a panel that came back up on the keys would have hidden every
   * action behind a grid.
   */
  @Test
  fun `reopening the panel returns to the action rows`() {
    val panel = setPanelView()

    composeTestRule.onNodeWithText("REACT").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("👍").assertIsDisplayed()

    panel.close()
    composeTestRule.waitForIdle()
    panel.show(listOf(LightPanelAction("REACT") { panel.showReactionKeys() })) { }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("REACT").assertIsDisplayed()
  }

  /** The call menu has no second level, and nothing it can do should be able to open one. */
  @Test
  fun `a panel opened without a reaction level cannot descend to one`() {
    val panel = setPanelView(withReactionKeys = false)

    composeTestRule.onNodeWithText("REACT").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("REACT").assertIsDisplayed()
  }

  private fun setKeys(onKeySelected: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LightReactionKeys(
            onKeySelected = onKeySelected,
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }
  }

  private fun setPanelView(withReactionKeys: Boolean = true): LightActionPanelView {
    lateinit var panel: LightActionPanelView

    composeTestRule.setContent {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          LightActionPanelView(context).also {
            panel = it
            it.show(
              listOf(LightPanelAction("REACT") { it.showReactionKeys() }),
              if (withReactionKeys) { _ -> Unit } else null
            )
          }
        }
      )
    }

    composeTestRule.waitForIdle()
    return panel
  }

  companion object {
    /** Mirrors `CHEVRON_ZONE` in LightActionPanel.kt, which is private to it. */
    private val CHEVRON_ZONE = 38.dp
  }
}
