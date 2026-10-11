/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.signal.core.ui.compose.ProvideIncognitoKeyboard
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.Locale

/** The LightTextField underline: 3 design px, 0.5 gu below the value, 80% wide. */
private const val UNDERLINE_THICKNESS_PX = 3f
private const val UNDERLINE_GAP_GRID_UNITS = 0.5f
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/**
 * What the passphrase form shows for one [ChangePassphraseDialogFragment] mode. The fragment keeps
 * every decision about what a submit does; this only names the copy and which fields exist.
 *
 * [hidesFieldsWhileInProgress] carries over the old layouts: the two that set a new passphrase had a
 * progress container that replaced the fields during key generation, the disable layout had none and
 * only disabled its field.
 */
data class ChangePassphraseForm(
  @StringRes val title: Int,
  @StringRes val submitLabel: Int,
  @StringRes val explanation: Int?,
  @StringRes val currentPassphraseLabel: Int?,
  val asksForNewPassphrase: Boolean,
) {
  val hidesFieldsWhileInProgress: Boolean
    get() = asksForNewPassphrase

  companion object {
    @JvmStatic
    fun forMode(mode: Int): ChangePassphraseForm = when (mode) {
      ChangePassphraseDialogFragment.MODE_ENABLE -> ChangePassphraseForm(
        title = R.string.AndroidManifest__create_passphrase,
        submitLabel = android.R.string.ok,
        explanation = R.string.ChangePassphraseDialogFragment_please_choose_a_passphrase_that_will_be_used_to_locally_encrypt_your_data_this_should_be_a_strong_passphrase,
        currentPassphraseLabel = null,
        asksForNewPassphrase = true,
      )
      ChangePassphraseDialogFragment.MODE_DISABLE -> ChangePassphraseForm(
        title = R.string.ApplicationPreferencesActivity_disable_passphrase,
        submitLabel = R.string.ApplicationPreferencesActivity_disable,
        explanation = R.string.ApplicationPreferencesActivity_this_will_permanently_unlock_signal_and_message_notifications,
        currentPassphraseLabel = R.string.ChangePassphraseDialogFragment_passphrase,
        asksForNewPassphrase = false,
      )
      else -> ChangePassphraseForm(
        title = R.string.AndroidManifest__change_passphrase,
        submitLabel = android.R.string.ok,
        explanation = null,
        currentPassphraseLabel = R.string.change_passphrase_activity__old_passphrase,
        asksForNewPassphrase = true,
      )
    }
  }
}

/** The first problem with a new passphrase and its repeat, in the order the old dialog checked them. */
enum class NewPassphraseEntry {
  VALID,
  EMPTY,
  MISMATCH;

  companion object {
    @JvmStatic
    fun check(newPassphrase: CharArray, repeatPassphrase: CharArray): NewPassphraseEntry = when {
      newPassphrase.isEmpty() -> EMPTY
      !newPassphrase.contentEquals(repeatPassphrase) -> MISMATCH
      else -> VALID
    }
  }
}

/**
 * The passphrase form's state, owned by [ChangePassphraseDialogFragment] for the life of the fragment.
 *
 * The fields are deliberately not saveable: a passphrase never goes into a saved-state Bundle. Errors
 * are already-resolved strings because the weak passphrase message is built from the validator's own
 * text.
 */
class ChangePassphraseFormState {
  val currentPassphrase = TextFieldState()
  val newPassphrase = TextFieldState()
  val repeatPassphrase = TextFieldState()

  var currentPassphraseError: String? by mutableStateOf(null)
  var newPassphraseError: String? by mutableStateOf(null)
  var repeatPassphraseError: String? by mutableStateOf(null)

  /** Key generation or re-encryption is running: actions are inert and back is unavailable. */
  var inProgress: Boolean by mutableStateOf(false)

  fun clearErrors() {
    currentPassphraseError = null
    newPassphraseError = null
    repeatPassphraseError = null
  }

  companion object {
    /** Copies [field]'s text out char by char, so that no String of the passphrase is made here. */
    @JvmStatic
    fun copyOf(field: TextFieldState): CharArray {
      val text = field.text
      return CharArray(text.length) { text[it] }
    }
  }
}

/**
 * A full-screen Light form in the chats field-editor grammar: BACK on the left, the mode as the title,
 * the commit as a text action on the right, and the fields stacked under it with the LP3 system
 * keyboard below. Every field is a [BasicSecureTextField] -- password keyboard, no autocorrect, no
 * copy or cut, the text obfuscated unless the user asks to see it -- under an incognito keyboard, so
 * the IME is told not to learn from it whatever the app-wide setting.
 */
@Composable
fun ChangePassphraseScreen(
  form: ChangePassphraseForm,
  state: ChangePassphraseFormState,
  onBack: () -> Unit,
  onSubmit: () -> Unit,
) {
  MollyLightTheme {
    ProvideIncognitoKeyboard(enabled = true) {
      val colors = LightThemeTokens.colors
      val focusManager = LocalFocusManager.current
      val keyboardController = LocalSoftwareKeyboardController.current
      val inProgress = state.inProgress
      val submit = {
        keyboardController?.hide()
        onSubmit()
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(colors.background)
          .navigationBarsPadding()
          .imePadding(),
      ) {
        LightTopBar(
          // Hidden rather than inert while keys are generated, as the old Cancel button was: the work
          // cannot be abandoned once it has started.
          leftButton = if (inProgress) null else LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = onBack,
            contentDescription = stringResource(R.string.ChangePassphraseDialogFragment__close_without_changing),
          ),
          center = LightTopBarCenter.Text(
            text = stringResource(if (inProgress) R.string.please_wait else form.title),
          ),
          rightButton = LightBarButton.Text(
            text = if (inProgress) "…" else stringResource(form.submitLabel).uppercase(Locale.getDefault()),
            onClick = if (inProgress) null else submit,
          ),
          modifier = Modifier.statusBarsPadding(),
        )

        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Outside,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        ) {
          // Start padding only: the Outside scroll bar already reserves a 2 grid unit gutter at the end.
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
          ) {
            if (inProgress && form.hidesFieldsWhileInProgress) {
              LightText(
                text = stringResource(R.string.ChangePassphraseDialogFragment_generating_keys_this_may_take_a_while),
                variant = LightTextVariant.Copy,
              )
            } else {
              FormFields(
                form = form,
                state = state,
                enabled = !inProgress,
                focusManager = focusManager,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FormFields(
  form: ChangePassphraseForm,
  state: ChangePassphraseFormState,
  enabled: Boolean,
  focusManager: FocusManager,
) {
  if (form.explanation != null) {
    LightText(
      text = stringResource(form.explanation),
      variant = LightTextVariant.Copy,
      modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
    )
  }

  if (form.currentPassphraseLabel != null) {
    PassphraseField(
      label = stringResource(form.currentPassphraseLabel),
      field = state.currentPassphrase,
      error = state.currentPassphraseError,
      enabled = enabled,
      imeAction = if (form.asksForNewPassphrase) ImeAction.Next else ImeAction.Done,
      onEdited = { state.currentPassphraseError = null },
    )
  }

  if (form.asksForNewPassphrase) {
    PassphraseField(
      label = stringResource(R.string.change_passphrase_activity__new_passphrase),
      field = state.newPassphrase,
      error = state.newPassphraseError,
      enabled = enabled,
      imeAction = ImeAction.Next,
      onEdited = { state.newPassphraseError = null },
    )
    PassphraseField(
      label = stringResource(R.string.change_passphrase_activity__repeat_new_passphrase),
      field = state.repeatPassphrase,
      error = state.repeatPassphraseError,
      enabled = enabled,
      imeAction = ImeAction.Done,
      onEdited = { state.repeatPassphraseError = null },
    )
  }

  // A disabled field must not keep focus, and with it the keyboard, while the work runs.
  LaunchedEffect(enabled) {
    if (!enabled) focusManager.clearFocus(force = true)
  }
}

/**
 * One passphrase field in LightTextField's geometry -- Detail label, Copy value, 3px underline at 80% --
 * with a Detail error line under it and a SHOW/HIDE action in place of Material's eye toggle.
 */
@Composable
private fun PassphraseField(
  label: String,
  field: TextFieldState,
  error: String?,
  enabled: Boolean,
  imeAction: ImeAction,
  onEdited: () -> Unit,
) {
  val context = LocalContext.current
  val colors = LightThemeTokens.colors
  var revealed by remember { mutableStateOf(false) }

  // An error is about the text it was raised for; editing that text clears it, as the old
  // TextInputLayout did. Compared by content -- so a cursor move is not an edit -- and without
  // building a String of the passphrase.
  LaunchedEffect(field) {
    snapshotFlow { field.text }
      .distinctUntilChanged { old, new -> old.contentEquals(new) }
      .drop(1)
      .collect { onEdited() }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 0.5f.gridUnitsAsDp()),
  ) {
    LightText(
      text = label,
      variant = LightTextVariant.Detail,
      lighten = !enabled,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicSecureTextField(
        state = field,
        enabled = enabled,
        textStyle = LightItemStyle.composeStyle(context, LightTextVariant.Copy)
          .copy(color = if (enabled) colors.content else colors.contentSecondary),
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          autoCorrectEnabled = false,
          imeAction = imeAction,
        ),
        cursorBrush = SolidColor(colors.content),
        textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        modifier = Modifier
          .weight(1f)
          .padding(top = 0.25f.gridUnitsAsDp()),
      )
      val toggleDescription = stringResource(
        if (revealed) R.string.ChangePassphraseDialogFragment__hide_passphrase else R.string.ChangePassphraseDialogFragment__show_passphrase
      )
      Box(
        modifier = Modifier
          .semantics { contentDescription = toggleDescription }
          .lightClickable(enabled = enabled, role = Role.Button, onClick = { revealed = !revealed })
          .padding(start = 0.5f.gridUnitsAsDp(), top = 0.25f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
      ) {
        LightText(
          text = stringResource(
            if (revealed) R.string.ChangePassphraseDialogFragment__hide else R.string.ChangePassphraseDialogFragment__show
          ).uppercase(Locale.getDefault()),
          variant = LightTextVariant.Detail,
          lighten = !enabled,
          maxLines = 1,
        )
      }
    }
    Spacer(modifier = Modifier.height(UNDERLINE_GAP_GRID_UNITS.gridUnitsAsDp()))
    Spacer(
      modifier = Modifier
        .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
        .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
        .background(if (enabled) colors.content else colors.contentSecondary),
    )
    if (error != null) {
      LightText(
        text = error,
        variant = LightTextVariant.Detail,
        modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
      )
    }
  }
}

/** Java entry point: a [ComposeView] hosting [ChangePassphraseScreen], for the fragment's onCreateView. */
object ChangePassphraseContent {
  @JvmStatic
  fun create(
    context: Context,
    form: ChangePassphraseForm,
    state: ChangePassphraseFormState,
    onBack: Runnable,
    onSubmit: Runnable,
  ): ComposeView {
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      // As the old layouts' importantForAutofill="noExcludeDescendants": Compose marks its own view
      // important for autofill, and BasicSecureTextField tags itself a password, but a parent that
      // excludes its descendants keeps both out of the autofill structure.
      importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
      setContent {
        ChangePassphraseScreen(
          form = form,
          state = state,
          onBack = onBack::run,
          onSubmit = onSubmit::run,
        )
      }
    }
  }
}
