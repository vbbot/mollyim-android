package org.thoughtcrime.securesms.registration.fragments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightTextAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

/**
 * Helpful screen displayed during registration when the user enters the wrong verification code too many times.
 *
 * This was a Material bottom sheet whose two ways onward were link spans inside a sentence. On the
 * Light Phone it is a full-screen help page in its own window -- black to the edges, the title in the
 * top bar beside BACK -- and the links are the page's two text actions, so each is a whole-width
 * target rather than a few words of a paragraph. The class keeps its name and [showSafely] because
 * both verification-code screens (registration and change number) hold one and raise it by that name.
 *
 * Neither action closes the page, as neither link closed the sheet: a user who comes back from the
 * troubleshooting steps can still contact support. BACK and system back close it.
 */
class ContactSupportBottomSheetFragment : DialogFragment() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_TITLE, 0)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        HavingTroubleScreen(
          onBack = { dismissAllowingStateLoss() },
          onTroubleshoot = { openTroubleshootingSteps() },
          onContactSupport = { sendEmailToSupport() }
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()

    // Full bleed, and black under the content: the default dialog window is a padded, rounded card.
    // Only honoured once the window is up, so here rather than in onCreateDialog.
    dialog?.window?.apply {
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }
  }

  private fun openTroubleshootingSteps() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.support_center_url))
  }

  private fun sendEmailToSupport() {
    val body = SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      R.string.RegistrationActivity_code_support_subject,
      null,
      null
    )
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(R.string.RegistrationActivity_code_support_subject),
      body
    )
  }

  fun showSafely(fm: FragmentManager, tag: String) {
    if (!isAdded && !fm.isStateSaved) {
      show(fm, tag)
    }
  }
}

/**
 * The page itself, apart from the fragment so that its actions can be exercised without a window.
 *
 * The sheet's bold heading ("Having trouble registering?") becomes the top bar title, shortened to
 * fit it; its suggestions are prose, so `Paragraph`; its closing sentence ("please follow these
 * troubleshooting steps or Contact Support") is what the two actions now say.
 */
@Composable
fun HavingTroubleScreen(
  onBack: () -> Unit,
  onTroubleshoot: () -> Unit,
  onContactSupport: () -> Unit
) {
  MollyLightTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onBack,
          contentDescription = stringResource(R.string.EnterCodeFragment__back_to_verification_code)
        ),
        center = LightTopBarCenter.Text(text = stringResource(R.string.ContactSupportBottomSheetFragment__having_trouble)),
        modifier = Modifier.statusBarsPadding()
      )

      LightScrollView(
        scrollBarPosition = LightScrollBarPosition.Outside,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .navigationBarsPadding()
      ) {
        // Start padding only: the Outside scroll bar already reserves a 2 grid unit gutter at the end.
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
        ) {
          LightText(
            text = stringResource(R.string.RegistrationActivity_support_bottom_sheet_body_suggestions),
            variant = LightTextVariant.Paragraph
          )

          Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))

          LightTextAction(
            label = stringResource(R.string.ContactSupportBottomSheetFragment__troubleshoot),
            onClick = Runnable { onTroubleshoot() }
          )
          LightTextAction(
            label = stringResource(R.string.RegistrationActivity_support_bottom_sheet_cta_contact_support_substring),
            onClick = Runnable { onContactSupport() }
          )
        }
      }
    }
  }
}
