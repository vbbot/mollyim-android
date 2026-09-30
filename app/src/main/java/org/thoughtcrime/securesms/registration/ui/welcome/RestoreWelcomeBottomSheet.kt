/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.core.os.bundleOf
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightActionPanelOverlay
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.Locale

/**
 * Restore flow starting panel that allows a user to progress through quick restore or manual
 * restore flows from the Welcome screen.
 *
 * This was a Material bottom sheet: a `Surface` in Signal's `colorSurface1` with 18dp rounded
 * corners, a drag handle, and two rounded cards whose icons were tinted with `colorScheme.primary`
 * -- an accent colour, on a device that has none. It is now [LightActionPanelOverlay], the same
 * half-screen black panel with a dismiss chevron that every other panel in the app uses, so the
 * sheet has the same shape as the panels it sits among.
 *
 * The result plumbing is unchanged: this is shown on the Welcome fragment's child fragment manager,
 * so [setFragmentResult] lands on the manager WelcomeFragment is already listening on, and the
 * `CONTINUE` default still means "dismissed without choosing".
 */
class RestoreWelcomeBottomSheet : DialogFragment() {

  private var result: WelcomeUserSelection = WelcomeUserSelection.CONTINUE

  companion object {
    const val REQUEST_KEY = "RestoreWelcomeBottomSheet"
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    // The overlay paints the whole window itself and dims whatever is behind it, so the platform
    // window must not also contribute a background or a title.
    return (super.onCreateDialog(savedInstanceState) as Dialog).apply {
      requestWindowFeature(Window.FEATURE_NO_TITLE)
      window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
  }

  override fun onStart() {
    super.onStart()
    // Applied here rather than in onCreateDialog: the window's layout params are re-applied as the
    // dialog shows, which would otherwise put back the default wrap-content width and leave the
    // black background a narrow strip.
    dialog?.window?.setLayout(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MollyLightTheme {
          RestorePanel(
            onHasOldPhone = {
              result = WelcomeUserSelection.RESTORE_WITH_OLD_PHONE
              dismissAllowingStateLoss()
            },
            onNoPhone = {
              result = WelcomeUserSelection.RESTORE_WITH_NO_PHONE
              dismissAllowingStateLoss()
            },
            onDismiss = { dismissAllowingStateLoss() }
          )
        }
      }
    }
  }

  override fun onDismiss(dialog: android.content.DialogInterface) {
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to result))

    super.onDismiss(dialog)
  }
}

/**
 * The two restore choices, in the Light Phone's panel grammar.
 *
 * Upper case because that is how [org.thoughtcrime.securesms.light.LightPanelAction] labels are
 * written everywhere else -- `LightPanelActions`, the main bottom bar's overflow, the contact
 * search panel all pass them through `uppercase()`. The two subtitles this row used to carry are
 * gone with the cards: the panel has one label per row, and both labels already say which is which
 * without them.
 */
@Composable
private fun RestorePanel(
  onHasOldPhone: () -> Unit,
  onNoPhone: () -> Unit,
  onDismiss: () -> Unit
) {
  LightActionPanelOverlay(
    actions = listOf(
      LightPanelAction(
        label = stringResource(R.string.WelcomeFragment_restore_action_i_have_my_old_phone).uppercase(Locale.getDefault()),
        onSelected = onHasOldPhone
      ),
      LightPanelAction(
        label = stringResource(R.string.WelcomeFragment_restore_action_i_dont_have_my_old_phone).uppercase(Locale.getDefault()),
        onSelected = onNoPhone
      )
    ),
    onDismiss = onDismiss
  )
}