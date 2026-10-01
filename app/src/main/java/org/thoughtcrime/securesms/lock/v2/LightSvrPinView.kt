/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.lock.v2

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.NoAutofillEditText
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.light.LightActionPanelDialog
import org.thoughtcrime.securesms.light.LightAppTopBar
import org.thoughtcrime.securesms.light.LightBarAction
import org.thoughtcrime.securesms.light.LightConfirmAction
import org.thoughtcrime.securesms.light.LightConfirmNotice
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.LightPinFieldStyle
import org.thoughtcrime.securesms.light.LightTextAction
import org.thoughtcrime.securesms.light.LightTopBarView
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.Locale

/** Screen-edge content padding -- the same gutter the registration screens use. */
private const val SCREEN_PADDING_UNITS = 2f

/** Title to body copy. The LP3's own title/subtitle rhythm. */
private const val BODY_TOP_UNITS = 0.75f

/** Rule thickness, in the SDK's design-space vertical pixels. Straight from `LightTextField`. */
private const val UNDERLINE_THICKNESS_PX = 3f

/** `LightTextField`'s rule runs at 80% of the field's width. */
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/** One entry in a PIN screen's overflow panel, in the form a View-land caller can construct. */
data class LightSvrPinMenuItem(val label: String, val action: Runnable)

/**
 * The Light Phone III presentation of the Signal PIN create/confirm screen.
 *
 * A View-land host rather than a composable the fragment drives directly, for the reason the rest of
 * Molly's converted screens are: these fragments drive themselves through listeners and view models,
 * and a Compose tree over a view-hosted screen buys nothing and costs the accessibility nodes those
 * controls already have. The fragment assigns fields; this draws them.
 *
 * ## The PIN is entered on the system IME, on a real `EditText`
 *
 * There is no app-drawn keypad here, and that is a decision rather than an omission. The Light Phone
 * III's keyboard is a *system* IME (`app.lightphonekeyboard/.LightImeService`, the device's default),
 * so an ordinary editable field raises the Light keypad exactly as it would on any other Android
 * device -- which is what `LightSearchField` already relies on. A second, app-drawn keypad would be a
 * keyboard inside a keyboard, and the SDK has no keypad component to build one from.
 *
 * Two further things make it the wrong call regardless:
 *
 * - A Signal PIN may be **alphanumeric** (`PinKeyboardType.ALPHA_NUMERIC`). A ten-key pad cannot
 *   enter one, and the numeric/alphanumeric toggle is a feature rather than a leftover.
 * - `LightTextField` is display-only, so an app-drawn keypad would have to reimplement masking, the
 *   incognito keyboard, autofill suppression, IME actions and the weak-PIN shake from scratch, on
 *   the one control where a mistake costs someone their account.
 *
 * So [input] stays a real, focusable, autofill-suppressed [NoAutofillEditText] with
 * `PasswordTransformationMethod`, hosted through [AndroidView]. Everything around the credential is
 * Light components sized in grid units; the credential itself is not restyled past its ink.
 */
class LightSvrPinView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * The credential field.
   *
   * Public and stable so the fragment can attach its text watcher, IME action, enabled state and
   * shake animation to it directly, exactly as it did when this was a View found by id.
   */
  val input: NoAutofillEditText = NoAutofillEditText(context)

  /** Screen title. */
  var title: String by mutableStateOf("")
    private set

  /** The explanatory paragraph beneath [title]. */
  var description: String by mutableStateOf("")
    private set

  /** Whether a "learn more" affordance follows [description]. */
  var learnMoreVisible: Boolean by mutableStateOf(false)
    private set

  /** The status line beneath the field: the length restriction, "PINs don't match", and so on. */
  var label: CharSequence by mutableStateOf("")
    private set

  /**
   * Whether [label] reports a failure.
   *
   * The Light Phone has no colour, so this is carried by size rather than by a red: a failing line
   * is [LightTextVariant.Copy] in white where a neutral one is `Detail` in `#BBBBBB`.
   */
  var labelIsError: Boolean by mutableStateOf(false)
    private set

  /** The numeric/alphanumeric toggle's label, or null when the screen does not offer one. */
  var keyboardToggleLabel: String? by mutableStateOf(null)
    private set

  /** The confirm action's label. */
  var confirmLabel: String by mutableStateOf("")
    private set

  /** Whether the confirm action is live. Mirrors the view model's entry-length check. */
  var confirmEnabled: Boolean by mutableStateOf(false)
    private set

  /** Whether the confirm action is in flight, in which case it shows progress and is inert. */
  var busy: Boolean by mutableStateOf(false)
    private set

  /** Whether the field is on screen at all. The confirm screen hides it while the PIN is saved. */
  var inputVisible: Boolean by mutableStateOf(true)
    private set

  /** Whether the rule under the field is drawn live or inert. The field's own `isEnabled` is the
   *  fragment's to set. */
  var inputEnabled: Boolean by mutableStateOf(true)
    private set

  /** Back. Null on a root screen, which renders the SDK's spacer rather than an inert chevron. */
  var onBack: Runnable? by mutableStateOf(null)

  /** The "learn more" affordance under the description. */
  var onLearnMore: Runnable? by mutableStateOf(null)

  /** Switches the field between the numeric and alphanumeric keyboards. */
  var onKeyboardToggle: Runnable? by mutableStateOf(null)

  /**
   * An extra offer above the confirm action, or null when the screen has none to make.
   *
   * The PIN restore screen's "need help": it appears only once a guess has been spent, and it opens
   * a notice offering a new PIN or support. Null rather than a hidden flag, so a screen that never
   * sets it does not reserve the row.
   */
  var supportLabel: String? by mutableStateOf(null)
    private set

  /** Runs the extra offer. Ignored while [supportLabel] is null, so the two cannot disagree. */
  var onSupport: Runnable? by mutableStateOf(null)

  /** Sets the extra offer, or removes it with a null [label]. */
  fun updateSupport(label: String?, onSelect: Runnable? = null) {
    this.supportLabel = label
    this.onSupport = onSelect
  }

  /** Commits the entered PIN to the view model. */
  var onConfirm: Runnable? by mutableStateOf(null)

  /**
   * The blocking notice's title, or null when there is nothing to report.
   *
   * A title, because the notice is the screen's only message: the user pressed confirm, the service
   * refused, and there is no PIN afterwards.
   */
  var failureTitle: String? by mutableStateOf(null)
    private set

  /** The blocking notice's body. */
  var failureMessage: String by mutableStateOf("")
    private set

  /**
   * What the blocking notice offers.
   *
   * A list because the two screens that raise one offer different things: the confirm screen
   * acknowledges, the PIN restore screen offers "create a new PIN" and "contact support" alongside
   * its own cancellation. Each action carries its own handler, so there is no separate dismissal
   * callback that could be left pointing at a notice that has already gone.
   */
  var failureActions: List<LightConfirmAction> by mutableStateOf(emptyList())
    private set

  /**
   * What the overflow affordance offers, in panel order. Empty renders no affordance at all.
   *
   * This is the former `R.menu.pin_skip` options menu, and the gating that decided whether it had
   * any items has not moved: it lives with the `SignalStore` reads that made it, in the fragment.
   */
  var overflowItems: List<LightSvrPinMenuItem> by mutableStateOf(emptyList())
    private set

  /** What the back icon is *for*, spoken by a screen reader. Never defaulted. */
  var backContentDescription: String by mutableStateOf("")
    private set

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    LightPinFieldStyle.apply(input)
    setBackgroundColor(LightItemStyle.backgroundColor(context))
  }

  // -------------------------------------------------------------------------------------------
  // Field updates. Each of these is the whole update, so a host never leaves two of them telling
  // different stories -- a stale enabled flag next to a fresh label is how a PIN screen ends up
  // offering an action the view model will refuse.
  // -------------------------------------------------------------------------------------------

  /** Sets the header block: title, body copy, and whether a "learn more" follows it. */
  fun updateHeader(title: String, description: String, learnMoreVisible: Boolean) {
    this.title = title
    this.description = description
    this.learnMoreVisible = learnMoreVisible
  }

  /** Sets the status line beneath the field. */
  fun updateLabel(label: CharSequence, isError: Boolean) {
    this.label = label
    this.labelIsError = isError
  }

  /** Sets the confirm action's label, liveness and in-flight state together. */
  fun updateConfirm(label: String, enabled: Boolean, busy: Boolean) {
    this.confirmLabel = label
    this.confirmEnabled = enabled
    this.busy = busy
  }

  /** Sets the numeric/alphanumeric toggle, or null to remove it. */
  fun updateKeyboardToggle(label: String?) {
    this.keyboardToggleLabel = label
  }

  /** Sets the field's presentation. Whether it accepts input is the host's call, on [input]. */
  fun updateInputState(visible: Boolean, enabled: Boolean) {
    this.inputVisible = visible
    this.inputEnabled = enabled
  }

  /** Raises the blocking notice, or clears it with a null [title]. */
  fun updateNotice(title: String?, message: String, actions: List<LightConfirmAction>) {
    this.failureTitle = title
    this.failureMessage = message
    this.failureActions = actions
  }

  /**
   * Empties the credential field, for the paths that have just rejected what was in it.
   *
   * `EditText.getText()` is declared `@Nullable`, though the platform has always handed back a live
   * `Editable`. `text?.clear()` would compile and quietly leave the rejected PIN sitting on screen
   * if that ever stopped being true, and on this screen that is the one failure that must be loud:
   * clearing the field is the entire point of those branches. So the invariant is asserted here,
   * once, rather than papered over at each call site.
   */
  fun clearInput() {
    input.text!!.clear()
  }

  /** Replaces the overflow panel's contents. */
  fun updateOverflowItems(items: List<LightSvrPinMenuItem>) {
    this.overflowItems = items
  }

  /** Names the back icon's destination for a screen reader. */
  fun updateBackContentDescription(contentDescription: String) {
    this.backContentDescription = contentDescription
  }

  @Composable
  override fun Content() {
    val colors = LightThemeTokens.colors
    val scrollState = rememberScrollState()
    var overflowOpen by remember { mutableStateOf(false) }

    val actions = remember(overflowItems) {
      overflowItems.map { item -> LightPanelAction(item.label) { item.action.run() } }
    }

    val back = onBack
    val learnMore = onLearnMore
    val keyboardToggle = onKeyboardToggle
    val confirm = onConfirm
    val support = onSupport
    val failure = failureTitle

    MollyLightTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(colors.background)
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(colors.background)
              .statusBarsPadding()
          ) {
            LightAppTopBar(
              title = null,
              leftAction = back?.let {
                LightTopBarView.backAction(backContentDescription, it::run)
              },
              rightAction = if (actions.isEmpty()) {
                null
              } else {
                LightBarAction.Sdk(
                  contentDescription = stringResource(R.string.MainToolbar__more_options_content_description),
                  icon = LightIcons.ELLIPSES,
                  onClick = { overflowOpen = true }
                )
              }
            )
          }

          LightScrollView(
            scrollState = scrollState,
            scrollBarPosition = LightScrollBarPosition.Outside,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp())
            ) {
              LightText(
                text = title,
                variant = LightTextVariant.Title,
                modifier = Modifier.fillMaxWidth()
              )

              LightText(
                text = description,
                variant = LightTextVariant.Paragraph,
                lighten = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = BODY_TOP_UNITS.gridUnitsAsDp())
              )

              if (learnMoreVisible && learnMore != null) {
                Box(
                  modifier = Modifier
                    .lightClickable(role = Role.Button, onClick = learnMore::run)
                    .padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp())
                ) {
                  LightText(
                    text = stringResource(R.string.LearnMoreTextView_learn_more)
                      .uppercase(Locale.getDefault()),
                    variant = LightTextVariant.Button
                  )
                }
              }

              PinEntryField(
                editText = input,
                visible = inputVisible,
                enabled = inputEnabled
              )

              if (label.isNotEmpty()) {
                LightText(
                  text = label.toString(),
                  variant = if (labelIsError) LightTextVariant.Copy else LightTextVariant.Detail,
                  lighten = !labelIsError,
                  align = TextAlign.Center,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.5f.gridUnitsAsDp())
                )
              }

              Spacer(modifier = Modifier.height(1f.verticalGridUnitsAsDp()))
            }
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(colors.background)
              .navigationBarsPadding()
              .padding(
                horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp(),
                vertical = 0.75f.verticalGridUnitsAsDp()
              )
          ) {
            keyboardToggleLabel?.let { toggleLabel ->
              LightTextAction(label = toggleLabel, onClick = keyboardToggle)
            }

            // Above the confirm action, and hidden until the screen has something to ask: the PIN
            // restore screen only offers this once a guess has been spent, and an action that is
            // always there is an action that is always in the way.
            supportLabel?.let { label ->
              LightTextAction(label = label, onClick = support, align = Alignment.Center)
            }

            LightTextAction(
              label = confirmLabel,
              enabled = confirmEnabled,
              busy = busy,
              align = Alignment.CenterEnd,
              onClick = confirm
            )
          }
        }

        // Last, opaque and pointer-eating: a tap in the empty area around the notice would otherwise
        // land on the PIN field underneath. The notice is not cancelable, exactly as the Material
        // dialog it replaces was `setCancelable(false)`; the back gesture is swallowed by the host
        // for the same reason.
        if (failure != null) {
          LightConfirmNotice(
            title = failure,
            message = failureMessage,
            actions = failureActions
          )
        }
      }

      if (overflowOpen && actions.isNotEmpty()) {
        LightActionPanelDialog(
          actions = actions,
          onDismiss = { overflowOpen = false }
        )
      }
    }
  }
}

/**
 * The Light Phone III presentation of the PIN migration splash.
 *
 * The migration activity's root screen, so it has no back affordance: there is nothing behind it to
 * go back to, and the fragment swallows the system back gesture to say the same thing.
 */
class LightSvrSplashView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var title: String by mutableStateOf("")
    private set

  var description: String by mutableStateOf("")
    private set

  var primaryLabel: String by mutableStateOf("")
    private set

  var secondaryLabel: String by mutableStateOf("")
    private set

  var onPrimary: Runnable? by mutableStateOf(null)
  var onSecondary: Runnable? by mutableStateOf(null)

  var overflowItems: List<LightSvrPinMenuItem> by mutableStateOf(emptyList())
    private set

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setBackgroundColor(LightItemStyle.backgroundColor(context))
  }

  fun updateHeader(title: String, description: String, primaryLabel: String, secondaryLabel: String) {
    this.title = title
    this.description = description
    this.primaryLabel = primaryLabel
    this.secondaryLabel = secondaryLabel
  }

  fun updateOverflowItems(items: List<LightSvrPinMenuItem>) {
    this.overflowItems = items
  }

  @Composable
  override fun Content() {
    val colors = LightThemeTokens.colors
    var overflowOpen by remember { mutableStateOf(false) }

    val actions = remember(overflowItems) {
      overflowItems.map { item -> LightPanelAction(item.label) { item.action.run() } }
    }

    val primary = onPrimary
    val secondary = onSecondary

    MollyLightTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(colors.background)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(colors.background)
          ) {
            LightAppTopBar(
              title = null,
              leftAction = null,
              rightAction = if (actions.isEmpty()) {
                null
              } else {
                LightBarAction.Sdk(
                  contentDescription = stringResource(R.string.MainToolbar__more_options_content_description),
                  icon = LightIcons.ELLIPSES,
                  onClick = { overflowOpen = true }
                )
              }
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            LightText(
              text = title,
              variant = LightTextVariant.Title,
              align = TextAlign.Center
            )

            LightText(
              text = description,
              variant = LightTextVariant.Paragraph,
              lighten = true,
              align = TextAlign.Center,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = BODY_TOP_UNITS.gridUnitsAsDp())
            )
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(colors.background)
              .navigationBarsPadding()
              .padding(
                horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp(),
                vertical = 0.75f.verticalGridUnitsAsDp()
              )
          ) {
            LightTextAction(label = primaryLabel, onClick = primary, align = Alignment.Center)
            LightTextAction(label = secondaryLabel, onClick = secondary, align = Alignment.Center)
          }
        }

        if (overflowOpen && actions.isNotEmpty()) {
          LightActionPanelDialog(
            actions = actions,
            onDismiss = { overflowOpen = false }
          )
        }
      }
    }
  }
}

/**
 * The PIN field: the real [android.widget.EditText] with the SDK's text-entry rule beneath it.
 *
 * The rule is what makes this read as somewhere to type. It runs at 80% of the field's width, as
 * `LightTextField` does, rather than full bleed as `LightSearchField` does, because this field is
 * centred in a form rather than spanning a list.
 */
@Composable
private fun PinEntryField(
  editText: NoAutofillEditText,
  visible: Boolean,
  enabled: Boolean
) {
  val colors = LightThemeTokens.colors

  if (!visible) return

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 1f.gridUnitsAsDp())
  ) {
    // `update` is a no-op on purpose. Compose owns the layout; the text, the input type, the
    // transformation method, the IME options and the text watcher all belong to the credential
    // flow, and nothing in this composable may overwrite them.
    AndroidView(
      factory = { editText },
      update = { },
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = LightPinFieldStyle.MINIMUM_TOUCH_UNITS.verticalGridUnitsAsDp())
    )

    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

    Box(
      modifier = Modifier
        .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
        .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
        .background(if (enabled) colors.content else colors.contentSecondary)
    )
  }
}
