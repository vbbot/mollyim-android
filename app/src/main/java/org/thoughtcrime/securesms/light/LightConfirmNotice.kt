/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import org.thoughtcrime.securesms.R
import java.util.Locale

/** The centered-panel gutter the Light Phone uses for a block of status text. */
private const val CENTERED_PANEL_PADDING_UNITS = 3f

/** Title to body copy. The LP3's own title/subtitle rhythm. */
private const val BODY_TOP_UNITS = 0.75f

/** Optical centering: centered text reads high, so bias it down. The LP3's own value. */
private const val OPTICAL_CENTERING_BOTTOM_UNITS = 4f

/** Copy to the first action, and between actions. */
private const val ACTION_TOP_UNITS = 1f

/** The busy label. `LightBottomBar` has no spinner, and neither does the reference client. */
private const val BUSY_LABEL = "…"

/**
 * One row of a [LightConfirmNotice], in the form a View-land caller can construct.
 *
 * The action is a [Runnable] rather than a Kotlin lambda because most callers of this surface are
 * Java fragments and dialogs, and `javac` cannot coerce a `void`-returning method reference to
 * `Function0<Unit>`; a `Runnable` is the one shape both languages accept without ceremony. It
 * matches [LightTextAction]'s own parameter for the same reason.
 */
data class LightConfirmAction(val label: String, val onSelected: Runnable)

const val LIGHT_CONFIRM_NOTICE_TAG = "light-confirm-notice"
const val LIGHT_CONFIRM_NOTICE_ACTION_TAG = "light-confirm-notice-action"

/**
 * A full-screen message with one or more actions: the Light Phone III's confirmation surface.
 *
 * This is what a `MaterialAlertDialog` becomes. It began life as a private composable inside
 * `LightSvrPinView`, where it stood in for the one alert the confirm-PIN screen raised, and it is
 * here now because three unrelated screens need the same shape: the PIN restore flow's notices, the
 * opt-out confirmation, and the registration-lock enable/disable confirmations. Each of those was a
 * `MaterialAlertDialogBuilder` -- a rounded grey sheet with Roboto in it, which is the one thing this
 * design language has no version of.
 *
 * Two things are deliberate:
 *
 * - **It is opaque and it fills the screen.** A half-height card over live content is a Signal
 *   dialog; this is a Light notice. It also means there is no "tap outside to dismiss" gesture to
 *   offer, because there is no outside. Cancellation, when a caller wants one, is an explicit row --
 *   which is what [LightConfirmDialog] sets up -- and back is the host's business.
 * - **It consumes every pointer event in its area.** A `background(Color.Black)` consumes nothing,
 *   so without this a tap beside the text reaches whatever is underneath: on the confirm-PIN screen,
 *   the credential field.
 */
@Composable
fun LightConfirmNotice(
  title: String,
  message: String,
  actions: List<LightConfirmAction>,
  modifier: Modifier = Modifier,
  busy: Boolean = false
) {
  val colors = LightThemeTokens.colors

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(colors.background)
      .pointerInput(Unit) { detectTapGestures { } }
      .testTag(LIGHT_CONFIRM_NOTICE_TAG)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = CENTERED_PANEL_PADDING_UNITS.gridUnitsAsDp())
        .padding(bottom = OPTICAL_CENTERING_BOTTOM_UNITS.verticalGridUnitsAsDp()),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // An empty title renders nothing rather than an empty line. One caller -- the PIN restore
      // screen's "you have one attempt remaining" notice -- has a body and no heading upstream, and
      // inventing a heading for it would be inventing copy.
      if (title.isNotEmpty()) {
        LightText(
          text = title,
          variant = LightTextVariant.Title,
          align = TextAlign.Center
        )
      }

      if (message.isNotEmpty()) {
        LightText(
          text = message,
          variant = LightTextVariant.Paragraph,
          lighten = true,
          align = TextAlign.Center,
          modifier = Modifier.padding(top = BODY_TOP_UNITS.gridUnitsAsDp())
        )
      }

      Spacer(modifier = Modifier.height(ACTION_TOP_UNITS.verticalGridUnitsAsDp()))

      actions.forEach { action ->
        LightTextAction(
          label = action.label,
          onClick = action.onSelected,
          enabled = !busy,
          busy = busy,
          align = Alignment.Center
        )
      }
    }
  }
}

/**
 * One full-width text action, in the Light Phone's bottom-action grammar.
 *
 * A text action rather than an SDK bar item: the bar's grammar is three icon slots with no room for
 * a stack of full-width labels, and the screens that need this have exactly the actions Signal put
 * in buttons. Disabled means visibly inert and still readable -- `#BBBBBB` on black, per the design
 * language -- never hidden and never a dropped action.
 */
@Composable
fun LightTextAction(
  label: String,
  onClick: Runnable?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  busy: Boolean = false,
  align: Alignment = Alignment.CenterStart
) {
  val live = enabled && !busy && onClick != null

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(LightPinFieldStyle.MINIMUM_TOUCH_UNITS.verticalGridUnitsAsDp())
      .semantics {
        role = Role.Button
        if (!live) disabled()
        if (busy) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
      }
      .lightClickable(enabled = live) { onClick?.run() }
      .testTag(LIGHT_CONFIRM_NOTICE_ACTION_TAG),
    contentAlignment = align
  ) {
    LightText(
      text = if (busy) BUSY_LABEL else label.uppercase(Locale.getDefault()),
      variant = LightTextVariant.Button,
      lighten = !enabled,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * [LightConfirmNotice] in its own window, for callers that have no screen to draw it on.
 *
 * The PIN screens raise their notice in place, because a `Dialog` window would let the fragment's
 * back stack through while it was up. A caller with nothing of its own -- the opt-out confirmation,
 * the registration-lock toggles -- has no such constraint, and this keeps the presentation identical
 * to the in-place one rather than growing a second dialog shape.
 *
 * The window is the whole surface, not a themed body inside a Material container: black to the
 * edges, no rounded corners. A Light body inside a grey sheet would be worse than either alone.
 *
 * Shaped like `MaterialAlertDialogBuilder` -- construct, add actions, then show -- for one concrete
 * reason rather than for symmetry: an action almost always has to dismiss the window it lives in,
 * which in Java means the action's closure refers to a variable that has to be final, so the dialog
 * has to exist before the actions that mention it.
 */
class LightConfirmDialog private constructor(
  context: Context,
  private val title: String,
  private val message: String,
  cancelable: Boolean
) {

  private val view = LightConfirmDialogView(context)

  private val dialog = Dialog(context).apply {
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    setCancelable(cancelable)
    setCanceledOnTouchOutside(false)
    setContentView(view)
  }

  /**
   * Sets what the notice offers, replacing anything already there.
   *
   * Before [show], always: the window never appears with its body and no way out of it.
   */
  fun setActions(actions: List<LightConfirmAction>) {
    view.update(title = title, message = message, actions = actions)
  }

  /** Runs on dismissal, whatever dismissed it -- a back press, or [dismiss]. */
  fun setOnDismissListener(listener: Runnable?) {
    dialog.setOnDismissListener { listener?.run() }
  }

  /**
   * Whether the notice's actions are in flight.
   *
   * A busy notice shows an ellipsis in place of every label and ignores taps, which is the Light
   * grammar's only progress affordance. It is set rather than inferred because the work is usually
   * on another thread and has no composable state for the notice to read.
   */
  fun setBusy(busy: Boolean) {
    view.updateBusy(busy)
  }

  fun show() {
    dialog.show()

    // Only honoured once the window is up, and only for full bleed; `setLayout` before `show` is
    // ignored on most versions.
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  }

  fun dismiss() {
    dialog.dismiss()
  }

  companion object {
    /** Builds the notice. Nothing is on screen until [show]. */
    @JvmStatic
    @JvmOverloads
    fun create(
      context: Context,
      title: String,
      message: String,
      cancelable: Boolean = true
    ): LightConfirmDialog = LightConfirmDialog(context, title, message, cancelable)
  }
}

/** The notice as a view, for a host that wants to place one itself rather than open a window. */
private class LightConfirmDialogView(context: Context) : AbstractComposeView(context) {

  private var title: String by mutableStateOf("")
  private var message: String by mutableStateOf("")
  private var actions: List<LightConfirmAction> by mutableStateOf(emptyList())
  private var busy: Boolean by mutableStateOf(false)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setBackgroundColor(AndroidColor.BLACK)
  }

  fun update(title: String, message: String, actions: List<LightConfirmAction>) {
    this.title = title
    this.message = message
    this.actions = actions
  }

  fun updateBusy(busy: Boolean) {
    this.busy = busy
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConfirmNotice(
        title = title,
        message = message,
        actions = actions,
        busy = busy
      )
    }
  }
}
