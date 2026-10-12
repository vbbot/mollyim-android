/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.lock.v2

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.util.Locale
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
import org.thoughtcrime.securesms.light.withLightNoExtractUi
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.text.AfterTextChanged

/** Screen-edge content padding -- the same gutter the registration screens use. */
private const val SCREEN_PADDING_UNITS = 2f

/** Title to body copy. The LP3's own title/subtitle rhythm. */
private const val BODY_TOP_UNITS = 0.75f

/** Top bar to the first line of body copy, matching the registration screens' content inset. */
private const val CONTENT_TOP_UNITS = 1f

/** Rule thickness, in the SDK's design-space vertical pixels. Straight from `LightTextField`. */
private const val UNDERLINE_THICKNESS_PX = 3f

/** `LightTextField`'s rule runs at 80% of the field's width. */
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/** `LightTextField`'s gap between the value and its rule. */
private const val UNDERLINE_GAP_UNITS = 0.5f

/** Centred status text sits this far above true centre, as `LightConfirmNotice`'s does. */
private const val OPTICAL_CENTERING_BOTTOM_UNITS = 4f

/**
 * The weak-PIN nudge, in the terms the platform `TranslateAnimation` it replaces used: 30px to the
 * right over 50ms, eight times (one run and seven repeats), snapping back between runs.
 */
private const val SHAKE_DISTANCE_PX = 30f
private const val SHAKE_STEP_MS = 50
private const val SHAKE_RUNS = 8

/** One mask glyph per entered character on the form. The editor masks with the field's own method. */
private const val MASK_GLYPH = '•'

/** One entry in a PIN screen's overflow panel, in the form a View-land caller can construct. */
data class LightSvrPinMenuItem(val label: String, val action: Runnable)

/**
 * The Light Phone III presentation of the Signal PIN screens: create, confirm, restore entry,
 * registration lock and re-register.
 *
 * A View-land host rather than a composable the fragment drives directly, for the reason the rest of
 * Molly's converted screens are: these fragments drive themselves through listeners and view models,
 * and a Compose tree over a view-hosted screen buys nothing and costs the accessibility nodes those
 * controls already have. The fragment assigns fields; this draws them.
 *
 * ## A form, and a full-screen editor over it
 *
 * The same grammar as the phone number step. The form is the top bar, the body copy, a display-only
 * `LightTextField` row standing for the PIN (one mask glyph per character, or a placeholder), the
 * status line and the screen's actions -- with no keyboard up, so the confirm action is never under
 * it. Tapping the row opens the editor: opaque, over the form, with BACK (put back what was there
 * when it opened), the title, DONE (keep it), and the field just under the bar so the keyboard can
 * have the bottom of the screen. The keyboard's own Done is DONE. Neither submits the step; only the
 * form's confirm action does.
 *
 * Over both, while the host says so: a full-screen progress state for a check or save in flight
 * ([busy]), a full-screen success state ([showSuccess]), and the blocking notice.
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
 * `PasswordTransformationMethod`, hosted through [AndroidView] -- in the editor, and only there. It
 * is the *same* instance whether the editor is open or not, so the text never leaves it: the form
 * reads nothing from it but its length, nothing about the PIN is Compose state, and nothing about it
 * is saved state. The one copy this class takes is the entry as it stood when the editor opened, in
 * a plain field, so BACK can put it back; it is dropped as soon as the editor closes.
 */
class LightSvrPinView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * The credential field.
   *
   * Public and stable so the fragment can attach its text watcher, enabled state and keyboard type
   * to it directly, exactly as it did when this was a View found by id.
   *
   * The IME action is this view's, not the host's: it is Done, and Done closes the editor (see
   * [commitEditor]). A host must not replace the editor-action listener, or the keyboard's Done
   * would stop closing the editor.
   *
   * The keyboard's no-extract-UI flags are added as the input connection is created rather than to
   * `imeOptions`, so an `imeOptions` assignment made after this view is built cannot drop them. The
   * action and every other flag are kept.
   */
  val input: NoAutofillEditText = object : NoAutofillEditText(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
      return super.onCreateInputConnection(outAttrs).also {
        outAttrs.imeOptions = withLightNoExtractUi(outAttrs.imeOptions)
      }
    }
  }

  /** Screen title. */
  var title: String by mutableStateOf("")
    private set

  /** The explanatory paragraph beneath [title]. */
  var description: String by mutableStateOf("")
    private set

  /** Whether a "learn more" affordance follows [description]. */
  var learnMoreVisible: Boolean by mutableStateOf(false)
    private set

  /** The PIN row's label on the form. */
  var fieldLabel: String by mutableStateOf(context.getString(R.string.LightSvrPinView__pin))
    private set

  /** What the PIN row says while nothing has been entered. */
  var fieldPlaceholder: String by mutableStateOf(context.getString(R.string.RegistrationLockFragment__enter_your_pin))
    private set

  /**
   * How many characters are in [input]. The form draws one mask glyph per character from this and
   * from nothing else: it is the only thing about the PIN that leaves the field.
   */
  var entryLength: Int by mutableIntStateOf(0)
    private set

  /** Whether the full-screen editor is open over the form. */
  var editorOpen: Boolean by mutableStateOf(false)
    private set

  /** The status line beneath the field: the length restriction, "PINs don't match", and so on. */
  var label: CharSequence by mutableStateOf("")
    private set

  /**
   * Whether [label] reports a failure.
   *
   * The Light Phone has no colour, and the status line is `Detail` either way, so this is carried
   * by ink: a failing line is white where a neutral one is `#BBBBBB`.
   */
  var labelIsError: Boolean by mutableStateOf(false)
    private set

  /** The numeric/alphanumeric toggle's label, or null when the screen does not offer one. */
  var keyboardToggleLabel: String? by mutableStateOf(null)
    private set

  /**
   * Whether the toggle also has a row on the form. It is always in the editor, where it is used; a
   * screen whose form keeps it in the overflow panel instead says so with false.
   */
  var keyboardToggleOnForm: Boolean by mutableStateOf(true)
    private set

  /** The confirm action's label. */
  var confirmLabel: String by mutableStateOf("")
    private set

  /** Whether the confirm action is live. Mirrors the view model's entry-length check. */
  var confirmEnabled: Boolean by mutableStateOf(false)
    private set

  /**
   * Whether the confirm action is in flight. The screen is then the full-screen progress state, with
   * [busyHeading] centred under the title, and nothing on it can be pressed.
   */
  var busy: Boolean by mutableStateOf(false)
    private set

  /** What the progress state says while [busy]. */
  var busyHeading: String by mutableStateOf(context.getString(R.string.LightSvrPinView__checking_pin))
    private set

  /** The success state's heading, or null when there is no success to report. */
  var successTitle: String? by mutableStateOf(null)
    private set

  /** The success state's explanation, or null for a heading on its own. */
  var successMessage: String? by mutableStateOf(null)
    private set

  /** Whether the PIN row is on the form at all. The confirm screen hides it while the PIN is saved. */
  var inputVisible: Boolean by mutableStateOf(true)
    private set

  /** Whether the PIN row opens the editor. The field's own `isEnabled` is the fragment's to set. */
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

  /**
   * The entry as it stood when the editor opened, so BACK can put it back. A plain field -- not
   * Compose state, not saved state -- and null whenever the editor is closed.
   */
  private var entryAtOpen: String? = null

  /** Bumped to start a shake of the PIN; see [shakeEntry]. */
  private var shakeRequest by mutableIntStateOf(0)

  /** What runs when a shake ends or is cut short, and which request it belongs to. */
  private var afterShake: Pair<Int, Runnable>? = null

  /**
   * System back while the editor is up: the same as its BACK. Switched on and off by [openEditor]
   * and [closeEditor] themselves rather than by a Compose `BackHandler`, which only follows the
   * editor on the next recomposition and so can be a frame behind it -- long enough to take a back
   * press that belongs to a notice raised in the same frame the editor closed.
   */
  private val editorBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = discardEditor()
  }

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(view: View) {
        findBackDispatcher()?.addCallback(editorBack)
      }

      override fun onViewDetachedFromWindow(view: View) {
        editorBack.remove()
      }
    })

    // Before the styling, which ORs the no-extract-UI flags into whatever action is already here.
    input.imeOptions = EditorInfo.IME_ACTION_DONE

    // Start-aligned, at Heading: the field has the editor to itself, as the phone number does.
    LightPinFieldStyle.apply(input, variant = LightTextVariant.Heading)
    setBackgroundColor(LightItemStyle.backgroundColor(context))

    input.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        commitEditor()
        true
      } else {
        false
      }
    }

    // Length only. The host attaches its own watcher for the entry itself.
    input.addTextChangedListener(AfterTextChanged { entryLength = it.length })

    // Never part of a saved hierarchy, whoever hosts it: `AndroidView` saves its view's state, and a
    // PIN must not outlive the screen in a bundle. It has no id, which already keeps it out; this
    // keeps it out even if it is given one.
    input.isSaveEnabled = false
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

  /** Sets what the PIN row says while it is empty. */
  fun updateFieldPlaceholder(placeholder: String) {
    this.fieldPlaceholder = placeholder
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
    if (busy) {
      closeEditor()
    }
  }

  /** Sets what the progress state says while the confirm action is in flight. */
  fun updateBusyHeading(heading: String) {
    this.busyHeading = heading
  }

  /**
   * Sets the numeric/alphanumeric toggle, or null to remove it. [onForm] false keeps it off the
   * form, for a screen that offers it in its overflow panel there; it is in the editor either way.
   */
  @JvmOverloads
  fun updateKeyboardToggle(label: String?, onForm: Boolean = true) {
    this.keyboardToggleLabel = label
    this.keyboardToggleOnForm = onForm
  }

  /** Sets the field's presentation. Whether it accepts input is the host's call, on [input]. */
  fun updateInputState(visible: Boolean, enabled: Boolean) {
    this.inputVisible = visible
    this.inputEnabled = enabled
    if (!visible || !enabled) {
      closeEditor()
    }
  }

  /**
   * Raises the blocking notice, or clears it with a null [title].
   *
   * Raising one closes the editor first, keeping what was typed: the notice is about the screen, and
   * when it is dismissed the user is back on the form, which is where its consequences are shown.
   */
  fun updateNotice(title: String?, message: String, actions: List<LightConfirmAction>) {
    if (title != null) {
      commitEditor()
    }
    this.failureTitle = title
    this.failureMessage = message
    this.failureActions = actions
  }

  /**
   * Replaces the screen with the success state: [title] at Heading, and [message] beneath it when
   * there is one. There is no way back from it; the host leaves the screen.
   */
  @JvmOverloads
  fun showSuccess(title: String, message: String? = null) {
    closeEditor()
    this.successTitle = title
    this.successMessage = message
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

  /**
   * Nudges the PIN -- the field in the editor, or its row on the form -- then runs [afterwards].
   *
   * The weak-PIN rejection: the entry is only cleared in [afterwards], so it is not wiped out from
   * under a half-finished shake. [afterwards] always runs, exactly once: at the end of the shake, as
   * soon as it is cut short (a second rejection, or the screen going away), or straight away when
   * this view is not on screen to shake at all.
   */
  fun shakeEntry(afterwards: Runnable) {
    afterShake?.let { (_, pending) ->
      afterShake = null
      pending.run()
    }

    if (!isAttachedToWindow) {
      afterwards.run()
      return
    }

    afterShake = (shakeRequest + 1) to afterwards
    shakeRequest++
  }

  /** Replaces the overflow panel's contents. */
  fun updateOverflowItems(items: List<LightSvrPinMenuItem>) {
    this.overflowItems = items
  }

  /** Names the back icon's destination for a screen reader. */
  fun updateBackContentDescription(contentDescription: String) {
    this.backContentDescription = contentDescription
  }

  // -------------------------------------------------------------------------------------------
  // The editor.
  // -------------------------------------------------------------------------------------------

  /** Whether the PIN row may open the editor: the field is on screen, live, and nothing covers it. */
  private val canEdit: Boolean
    get() = inputVisible && inputEnabled && input.isEnabled && !busy && successTitle == null && failureTitle == null

  /**
   * Opens the editor over the form, with the field focused and the keyboard up. A no-op while the
   * field is hidden or inert, a check or save is in flight, or a notice holds the screen -- so a host
   * can ask for it on arrival without first working out whether anything else got there first.
   */
  fun openEditor() {
    if (editorOpen || !canEdit) return
    entryAtOpen = input.text?.toString().orEmpty()
    editorOpen = true
    editorBack.isEnabled = true
  }

  /** DONE, and the keyboard's Done: keeps the entry and goes back to the form. */
  fun commitEditor() {
    closeEditor()
  }

  /** BACK, and system back: puts back the entry as it was when the editor opened. */
  fun discardEditor() {
    val previous = entryAtOpen
    if (editorOpen && previous != null && input.text?.toString() != previous) {
      // Through the field, so the host's watcher re-derives the confirm action from it.
      input.setText(previous)
    }
    closeEditor()
  }

  /** The toggle clears the field when the input class changes, so BACK has nothing to put back. */
  private fun toggleKeyboardInEditor() {
    if (editorOpen) {
      entryAtOpen = ""
    }
    onKeyboardToggle?.run()
  }

  /** The view tree's dispatcher, or the activity's when nothing set one on the tree. */
  private fun findBackDispatcher(): OnBackPressedDispatcher? {
    findViewTreeOnBackPressedDispatcherOwner()?.let { return it.onBackPressedDispatcher }
    var current: Context? = context
    while (current is ContextWrapper) {
      if (current is OnBackPressedDispatcherOwner) return current.onBackPressedDispatcher
      current = current.baseContext
    }
    return null
  }

  private fun closeEditor() {
    if (!editorOpen) return
    entryAtOpen = null
    // The field's own window token is gone once the editor stops hosting it; this view's is not.
    context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    input.clearFocus()
    editorOpen = false
    editorBack.isEnabled = false
  }

  @Composable
  override fun Content() {
    val colors = LightThemeTokens.colors
    val scrollState = rememberScrollState()
    var overflowOpen by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    val actions = remember(overflowItems) {
      overflowItems.map { item -> LightPanelAction(item.label) { item.action.run() } }
    }

    val back = onBack
    val learnMore = onLearnMore
    val keyboardToggle = onKeyboardToggle
    val confirm = onConfirm
    val support = onSupport
    val failure = failureTitle
    val success = successTitle

    val request = shakeRequest
    LaunchedEffect(request) {
      if (request == 0) return@LaunchedEffect
      try {
        repeat(SHAKE_RUNS) {
          shake.snapTo(0f)
          shake.animateTo(SHAKE_DISTANCE_PX, tween(durationMillis = SHAKE_STEP_MS, easing = LinearEasing))
        }
        shake.snapTo(0f)
      } finally {
        // Only this request's clean-up: a newer request has already run this one's, and owns its own.
        afterShake?.takeIf { it.first == request }?.let { (_, pending) ->
          afterShake = null
          pending.run()
        }
      }
    }

    val shakeOffset = Modifier.offset { IntOffset(shake.value.toInt(), 0) }

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
              title = title,
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
              // The title is in the top bar; the body copy leads the content.
              LightText(
                text = description,
                variant = LightTextVariant.Paragraph,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = CONTENT_TOP_UNITS.gridUnitsAsDp())
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

              if (inputVisible) {
                // Display-only, as the phone number's row is: the PIN itself is only ever in the
                // editor's field. One mask glyph per character is all the form knows about it.
                LightTextField(
                  label = fieldLabel,
                  value = MASK_GLYPH.toString().repeat(entryLength),
                  placeholder = fieldPlaceholder,
                  onClick = { openEditor() },
                  modifier = shakeOffset
                )
              }

              StatusLine(label = label, isError = labelIsError)

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
            val toggleLabel = keyboardToggleLabel
            if (toggleLabel != null && keyboardToggleOnForm) {
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

        if (editorOpen) {
          PinEditor(
            title = title,
            editText = input,
            label = label,
            labelIsError = labelIsError,
            keyboardToggleLabel = keyboardToggleLabel,
            fieldModifier = shakeOffset,
            onBack = ::discardEditor,
            onDone = ::commitEditor,
            onKeyboardToggle = ::toggleKeyboardInEditor
          )
        }

        if (busy) {
          StatusScreen(title = title, heading = busyHeading, message = null)
        } else if (success != null) {
          StatusScreen(title = title, heading = success, message = successMessage)
        }

        // Last, opaque and pointer-eating: a tap in the empty area around the notice would otherwise
        // land on the screen underneath. The notice is not cancelable, exactly as the Material
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
            // Centred status text, as the reference client draws it: Heading, not Title.
            LightText(
              text = title,
              variant = LightTextVariant.Heading,
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
 * The full-screen PIN editor, in the manner of the phone number's: drawn over the form, opaque and
 * touch-swallowing, with BACK (discard), the title, DONE (keep), and the field just under the bar so
 * the system keyboard can take the bottom of the screen. Under the field, the status line and -- on
 * a screen that has one -- the keyboard toggle, so the keyboard can be switched while typing.
 *
 * Touches are swallowed by a layer *behind* the content rather than by the editor's own root. The
 * field is a View hosted through [AndroidView], and Compose hands a hosted View its touches in the
 * final pass: a root that consumed every touch, as the phone number's editor does over a Compose
 * field, would leave this one unable to be tapped to bring the keyboard back.
 */
@Composable
private fun PinEditor(
  title: String,
  editText: NoAutofillEditText,
  label: CharSequence,
  labelIsError: Boolean,
  keyboardToggleLabel: String?,
  fieldModifier: Modifier,
  onBack: () -> Unit,
  onDone: () -> Unit,
  onKeyboardToggle: () -> Unit
) {
  val colors = LightThemeTokens.colors


  LaunchedEffect(Unit) {
    // The cursor goes after what is already there -- at the start of an empty field.
    editText.setSelection(editText.length())
    // Only once the field is in the window: this effect can run before `AndroidView` has attached
    // it, and a focus request on a detached view is silently dropped -- the editor would open with
    // no focus and no keyboard. Posted so the request lands after the field's first layout.
    editText.doOnAttach { attached -> attached.post { ViewUtil.focusAndShowKeyboard(editText) } }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
  ) {
    Spacer(
      modifier = Modifier
        .matchParentSize()
        .swallowTouches()
    )

    Column(modifier = Modifier.fillMaxSize()) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onBack,
          contentDescription = stringResource(R.string.LightSvrPinView__close_without_saving)
        ),
        center = title.takeIf { it.isNotBlank() }?.let { LightTopBarCenter.Text(text = it) },
        rightButton = LightBarButton.Text(
          text = stringResource(R.string.LightSvrPinView__done).uppercase(Locale.getDefault()),
          onClick = onDone
        ),
        modifier = Modifier.statusBarsPadding()
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp())
          .padding(top = CONTENT_TOP_UNITS.gridUnitsAsDp())
      ) {
        Column(modifier = fieldModifier.fillMaxWidth()) {
          // `update` is a no-op on purpose. Compose owns the layout; the text, the input type, the
          // transformation method, the IME options and the text watcher all belong to the credential
          // flow, and nothing in this composable may overwrite them. The field is the screen's one
          // instance, so it is taken off whatever hosted it last before it is hosted here.
          AndroidView(
            factory = {
              (editText.parent as? ViewGroup)?.removeView(editText)
              editText
            },
            update = { },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = LightPinFieldStyle.MINIMUM_TOUCH_UNITS.verticalGridUnitsAsDp())
          )

          Spacer(modifier = Modifier.height(UNDERLINE_GAP_UNITS.gridUnitsAsDp()))

          Box(
            modifier = Modifier
              .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
              .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
              .background(colors.content)
          )
        }

        StatusLine(label = label, isError = labelIsError)

        keyboardToggleLabel?.let { toggleLabel ->
          LightTextAction(
            label = toggleLabel,
            onClick = Runnable { onKeyboardToggle() },
            modifier = Modifier.padding(top = 0.5f.verticalGridUnitsAsDp())
          )
        }
      }
    }
  }
}

/**
 * The status line under the PIN: the length rule, "re-enter your PIN", a rejection. `Detail` either
 * way; a failure is white where a neutral line is `#BBBBBB`.
 */
@Composable
private fun StatusLine(label: CharSequence, isError: Boolean) {
  if (label.isEmpty()) return

  LightText(
    text = label.toString(),
    variant = LightTextVariant.Detail,
    lighten = !isError,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 0.5f.gridUnitsAsDp())
  )
}

/**
 * A full-screen status in place of the form: a check or save in flight, or a save that has landed.
 *
 * The title stays in the top bar so the screen still says where the user is; there is no back
 * affordance and no overflow, because there is nothing to do here but wait. Opaque and
 * touch-swallowing, so nothing on the form underneath can be pressed through it.
 */
@Composable
private fun StatusScreen(title: String, heading: String, message: String?) {
  val colors = LightThemeTokens.colors

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background)
      .swallowTouches()
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
    ) {
      LightAppTopBar(title = title, leftAction = null, rightAction = null)
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = SCREEN_PADDING_UNITS.gridUnitsAsDp())
        .padding(bottom = OPTICAL_CENTERING_BOTTOM_UNITS.verticalGridUnitsAsDp()),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LightText(
        text = heading,
        variant = LightTextVariant.Heading,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      if (!message.isNullOrBlank()) {
        LightText(
          text = message,
          variant = LightTextVariant.Paragraph,
          align = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = BODY_TOP_UNITS.gridUnitsAsDp())
        )
      }
    }
  }
}

/** Consumes every touch that reaches it, so a full-screen layer is not pressed through. */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false).consume()
    while (true) {
      val event = awaitPointerEvent()
      event.changes.forEach { it.consume() }
      if (event.changes.none { it.pressed }) break
    }
  }
}
