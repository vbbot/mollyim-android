/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeDialogFragment
import org.signal.core.ui.rememberIsSplitPane
import org.signal.core.util.BreakIteratorCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsViewModel
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId

internal const val EDIT_CALL_LINK_NAME_SCREEN_TAG = "call-link:edit-name"
internal const val EDIT_CALL_LINK_NAME_FIELD_TAG = "call-link:edit-name:field"
internal const val MAX_CALL_LINK_NAME_GRAPHEMES = 32

@Immutable
data class EditCallLinkNameLightState(
  val value: TextFieldValue,
  val showNavigationIcon: Boolean = true
)

interface EditCallLinkNameCallbacks {
  fun onNameChanged(value: TextFieldValue) = Unit
  fun onSaveClicked() = Unit
  fun onNavigationClicked() = Unit

  object Empty : EditCallLinkNameCallbacks
}

class EditCallLinkNameDialogFragment : ComposeDialogFragment() {

  companion object {
    const val RESULT_KEY = "edit_call_link_name"
    const val ARG_NAME = "name"
  }

  private val argName: String
    get() = requireArguments().getString(ARG_NAME)!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return dialog
  }

  @Preview
  @Composable
  override fun DialogContent() {
    EditCallLinkNameRoute(
      initialName = argName,
      onSave = {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_KEY to it))
        dismiss()
      },
      onNavigation = { dismiss() }
    )
  }
}

@Composable
fun EditCallLinkNameScreen(
  roomId: CallLinkRoomId,
  initialName: String
) {
  val viewModel: CallLinkDetailsViewModel = viewModel {
    CallLinkDetailsViewModel(roomId)
  }
  val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
  val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

  EditCallLinkNameRoute(
    initialName = initialName,
    onSave = {
      lifecycleScope.launch {
        viewModel.setName(it)
        backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
      }
    },
    onNavigation = {
      backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
    },
    showNavigationIcon = !LocalResources.current.rememberIsSplitPane()
  )
}

/** Stateful route adapter. The actual screen below remains an immutable state/callback seam. */
@Composable
private fun EditCallLinkNameRoute(
  initialName: String,
  onSave: (String) -> Unit,
  onNavigation: () -> Unit,
  showNavigationIcon: Boolean = true
) {
  var value by rememberSaveable(initialName, stateSaver = TextFieldValue.Saver) {
    mutableStateOf(
      TextFieldValue(
        text = initialName,
        selection = TextRange(initialName.length)
      )
    )
  }

  val callbacks = object : EditCallLinkNameCallbacks {
    override fun onNameChanged(newValue: TextFieldValue) {
      value = limitCallLinkName(newValue)
    }

    override fun onSaveClicked() {
      onSave(value.text)
    }

    override fun onNavigationClicked() {
      onNavigation()
    }
  }

  MollyLightTheme {
    EditCallLinkNameContent(
      state = EditCallLinkNameLightState(
        value = value,
        showNavigationIcon = showNavigationIcon
      ),
      callbacks = callbacks
    )
  }
}

/** Pure Light name editor. The LP3's system IME talks directly to this BasicTextField. */
@Composable
fun EditCallLinkNameContent(
  state: EditCallLinkNameLightState,
  callbacks: EditCallLinkNameCallbacks,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  val colors = LightThemeTokens.colors
  val copyStyle = LightThemeTokens.typography.copy.copy(
    color = colors.content,
    fontSize = 30f.designVerticalPxToSp(),
    lineHeight = 45f.designVerticalPxToSp()
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(colors.background)
      .testTag(EDIT_CALL_LINK_NAME_SCREEN_TAG)
  ) {
    LightTopBar(
      leftButton = if (state.showNavigationIcon) {
        LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = callbacks::onNavigationClicked,
          contentDescription = stringResource(R.string.ConversationFragment__content_description_back_button)
        )
      } else {
        null
      },
      center = LightTopBarCenter.Text(
        stringResource(R.string.EditCallLinkNameDialogFragment__edit_call_name)
      ),
      rightButton = LightBarButton.Text(
        text = stringResource(R.string.EditCallLinkNameDialogFragment__save),
        onClick = callbacks::onSaveClicked
      )
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp())
    ) {
      LightText(
        text = stringResource(R.string.EditCallLinkNameDialogFragment__call_name),
        variant = LightTextVariant.Detail,
        modifier = Modifier.padding(top = 1f.gridUnitsAsDp())
      )

      BasicTextField(
        value = state.value,
        onValueChange = { callbacks.onNameChanged(limitCallLinkName(it)) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 0.25f.gridUnitsAsDp())
          .focusRequester(focusRequester)
          .testTag(EDIT_CALL_LINK_NAME_FIELD_TAG),
        textStyle = copyStyle,
        cursorBrush = SolidColor(colors.content),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          capitalization = KeyboardCapitalization.Words,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { callbacks.onSaveClicked() })
      )

      Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
      Box(
        modifier = Modifier
          .fillMaxWidth(0.8f)
          .height(3f.designVerticalPxToDp())
          .background(colors.content)
      )
    }
  }

  LaunchedEffect(focusRequester) {
    focusRequester.requestFocus()
  }
}

internal fun truncateCallLinkName(name: String): String {
  return BreakIteratorCompat.getInstance()
    .apply { setText(name) }
    .take(MAX_CALL_LINK_NAME_GRAPHEMES)
    .toString()
}

internal fun limitCallLinkName(value: TextFieldValue): TextFieldValue {
  val truncated = truncateCallLinkName(value.text)
  if (truncated == value.text) return value

  return value.copy(
    text = truncated,
    selection = TextRange(
      start = value.selection.start.coerceAtMost(truncated.length),
      end = value.selection.end.coerceAtMost(truncated.length)
    ),
    composition = null
  )
}

@Preview(widthDp = 360, heightDp = 413, showBackground = true)
@Composable
private fun EditCallLinkNameContentPreview() {
  MollyLightTheme {
    EditCallLinkNameContent(
      state = EditCallLinkNameLightState(
        value = TextFieldValue("Call Name", selection = TextRange(9))
      ),
      callbacks = EditCallLinkNameCallbacks.Empty
    )
  }
}
