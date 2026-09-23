/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * Pins [LightMessageColumn] -- the fractions the thread's message rows lay their text column out
 * against -- to the Light reference chat client's `MessageRow`, which is the design authority.
 *
 * Molly's rows are `ConstraintLayout`s rather than Compose, because a message body has to stay an
 * `EmojiTextView` for its spans to survive (see `LightTextOnlyViewHolder`). That makes this the one
 * place the two can be compared directly: the reference's row is reproduced verbatim below, measured,
 * and its message column's edges are checked against the fractions our guidelines are driven from.
 * Anything that moves one without moving the other fails here.
 *
 * At LP3 geometry (360dp wide, 27 grid units, so 1 gu = 13.333dp) the column runs:
 *
 * | row | left edge | right edge |
 * |---|---|---|
 * | incoming | 1.5 gu | 22.5 gu |
 * | outgoing | 4.375 gu | 24.5 gu |
 *
 * Robolectric stubs glyph widths, so only layout-determined edges are trustworthy -- which is exactly
 * what this measures. The column is sized by [Modifier.fillMaxWidth], never by its text.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightMessageRowGeometryTest {

  companion object {
    private const val COLUMN_TAG = "message-column"

    /** A twentieth of a grid unit -- well under a device pixel at LP3 density. */
    private const val TOLERANCE = 0.05f
  }

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `an incoming row's column starts and ends where the reference's does`() {
    val (start, end) = referenceColumnFractions(isMine = false)

    assertThat(start).isCloseTo(LightMessageColumn.startFraction(isIncoming = true), TOLERANCE / 27f)
    assertThat(end).isCloseTo(LightMessageColumn.endFraction(isIncoming = true), TOLERANCE / 27f)
  }

  @Test
  fun `an outgoing row's column starts and ends where the reference's does`() {
    val (start, end) = referenceColumnFractions(isMine = true)

    assertThat(start).isCloseTo(LightMessageColumn.startFraction(isIncoming = false), TOLERANCE / 27f)
    assertThat(end).isCloseTo(LightMessageColumn.endFraction(isIncoming = false), TOLERANCE / 27f)
  }

  /**
   * The buffer between a message and the edge it hangs off: 1.5 grid units for incoming on the left,
   * but 2.5 for outgoing on the right, because that edge also carries the thread scrollbar. This is
   * the whole reason the two sides are not mirror images, and it is stated on its own so that
   * collapsing the two gutters back to one value fails loudly rather than shifting both columns and
   * still matching a re-derived expectation.
   *
   * Note this is the *near* edge. The far edge of either row sits further in again, by the 12.5% the
   * width cap leaves uncovered.
   */
  @Test
  fun `outgoing rows keep a wider buffer on their own edge than incoming rows do`() {
    val incomingGutter = LightMessageColumn.startFraction(isIncoming = true) * 27f
    val outgoingGutter = (1f - LightMessageColumn.endFraction(isIncoming = false)) * 27f

    assertThat(incomingGutter).isCloseTo(LightMessageColumn.GUTTER_UNITS, TOLERANCE)
    assertThat(outgoingGutter).isCloseTo(LightMessageColumn.OUTGOING_END_GUTTER_UNITS, TOLERANCE)
    assertThat(outgoingGutter - incomingGutter).isCloseTo(1f, TOLERANCE)
  }

  /** Lays out [ReferenceMessageRow] and returns its message column's edges as fractions of the row. */
  private fun referenceColumnFractions(isMine: Boolean): Pair<Float, Float> {
    composeTestRule.setContent {
      MollyLightTheme {
        ReferenceMessageRow(isMine = isMine)
      }
    }

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val column = composeTestRule.onNodeWithTag(COLUMN_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val width = (root.right - root.left).value
    val start = (column.left - root.left).value / width
    val end = (column.right - root.left).value / width

    return Pair(start, end)
  }

  /**
   * `MessageRow` from the reference client's `ThreadScreen.kt`, reduced to the parts that decide
   * horizontal geometry. The reference cannot be compiled into Molly, so it is reproduced here; the
   * padding, the width fraction and the alignment are copied verbatim.
   */
  @Composable
  private fun ReferenceMessageRow(isMine: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = 1.5f.gridUnitsAsDp(),
            end = if (isMine) 2.5f.gridUnitsAsDp() else 1.5f.gridUnitsAsDp(),
            top = 8.dp,
            bottom = 8.dp
          )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth(0.875f)
            .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
            .testTag(COLUMN_TAG),
          horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
          LightText(text = "8:14 AM", variant = LightTextVariant.Superfine)
          LightText(text = "Sure, see you then.", variant = LightTextVariant.Paragraph)
        }
      }
    }
  }
}
