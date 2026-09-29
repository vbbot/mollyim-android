/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import java.util.Locale
import java.util.function.Consumer
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme

/** Light top/search shell for the two legacy activity-based contact selectors. */
class LightContactSelectionHeader @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  enum class NavigationMode { NONE, BACK, DONE }

  private var title by mutableStateOf(context.getString(R.string.NewConversationActivity__new_message))
  private var query by mutableStateOf("")
  private var navigationMode by mutableStateOf(NavigationMode.NONE)
  private var requestFocus by mutableStateOf(false)
  private var navigationAction by mutableStateOf<() -> Unit>({})
  private var queryChanged: (String) -> Unit = {}

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun setTitle(title: CharSequence) {
    this.title = title.toString()
  }

  @JvmSynthetic
  fun setNavigation(mode: NavigationMode, action: () -> Unit) {
    navigationAction = action
    navigationMode = mode
  }

  fun setNavigation(mode: NavigationMode, action: Runnable) {
    setNavigation(mode) { action.run() }
  }

  @JvmSynthetic
  fun setOnQueryChanged(listener: (String) -> Unit) {
    queryChanged = listener
  }

  fun setOnQueryChanged(listener: Consumer<String>) {
    setOnQueryChanged { listener.accept(it) }
  }

  fun focusAndShowKeyboard() {
    requestFocus = true
  }

  @Composable
  override fun Content() {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
      if (requestFocus) {
        focusRequester.requestFocus()
        requestFocus = false
      }
    }

    MollyLightTheme {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(LightThemeTokens.colors.background)
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        LightTopBar(
          leftButton = when (navigationMode) {
            NavigationMode.NONE -> null
            NavigationMode.BACK -> LightBarButton.LightIcon(
              icon = LightIcons.BACK,
              onClick = navigationAction,
              contentDescription = context.getString(R.string.DefaultTopAppBar__navigate_up_content_description)
            )
            NavigationMode.DONE -> LightBarButton.Text(
              text = context.getString(R.string.AddMembersActivity__done).uppercase(Locale.getDefault()),
              onClick = navigationAction,
              contentDescription = context.getString(R.string.AddMembersActivity__done)
            )
          },
          center = LightTopBarCenter.Text(title)
        )

        LightSearchField(
          query = query,
          onQueryChange = {
            query = it
            queryChanged(it)
          },
          focusRequester = focusRequester
        )
      }
    }
  }
}
