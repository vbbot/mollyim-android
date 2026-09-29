/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.contacts.paged.light.LightContactCoverageFailure
import org.thoughtcrime.securesms.contacts.paged.light.LightContactSearch
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme

@Composable
fun MultiselectForwardScreen(
  isSplitPane: Boolean,
  args: MultiselectForwardFragmentArgs,
  contactSearchViewModel: ContactSearchViewModel,
  callback: MultiselectForwardFragment.Callback,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  searchConfigurationProvider: SearchConfigurationProvider?,
  contactSearchCallbacks: ContactSearchCallbacks,
  bottomContentPadding: Dp = 0.dp
) {
  MollyLightTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      if (args.isWrappedInBottomSheet) {
        LightTopBar(
          center = LightTopBarCenter.Text(stringResource(args.title)),
          modifier = Modifier.fillMaxWidth()
        )
      } else if (args.isToolbarVisible) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = callback::navigateUp,
            contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
          ),
          center = if (!isSplitPane) LightTopBarCenter.Text(stringResource(args.title)) else null,
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
        )
      }

      ForwardContent(
        isSplitPane = isSplitPane,
        args = args,
        contactSearchViewModel = contactSearchViewModel,
        callback = callback,
        mapStateToConfiguration = mapStateToConfiguration,
        searchConfigurationProvider = searchConfigurationProvider,
        contactSearchCallbacks = contactSearchCallbacks,
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = bottomContentPadding)
      )
    }
  }
}

@Composable
private fun ForwardContent(
  isSplitPane: Boolean,
  args: MultiselectForwardFragmentArgs,
  contactSearchViewModel: ContactSearchViewModel,
  callback: MultiselectForwardFragment.Callback,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  searchConfigurationProvider: SearchConfigurationProvider?,
  contactSearchCallbacks: ContactSearchCallbacks,
  modifier: Modifier = Modifier
) {
  // Remembered so that the identity does not change on every recomposition. The Light list reads it
  // through `rememberUpdatedState` rather than as a `LaunchedEffect` key, so this is about not
  // re-projecting the whole row list for nothing rather than about restarting a collector.
  val displayOptions = remember(args.selectSingleRecipient) {
    ContactSearchAdapter.DisplayOptions(
      displayCheckBox = !args.selectSingleRecipient,
      displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.NEVER,
      // Kept for parity with the option bag the Material list took: the Light row draws a story's
      // viewer count rather than an avatar ring, so nothing reads it. Left set rather than cleared
      // so that the two presentations of this screen agree on what they asked for.
      displayStoryRing = true
    )
  }

  Row(modifier = modifier) {
    if (isSplitPane) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxSize()
          .padding(horizontal = 1.5f.gridUnitsAsDp()),
        contentAlignment = Alignment.TopStart
      ) {
        LightText(
          text = stringResource(args.title),
          variant = LightTextVariant.Title
        )
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxSize()
    ) {
      if (args.isSearchEnabled) {
        val query by contactSearchViewModel.query.collectAsStateWithLifecycle()
        LightSearchField(
          query = query.orEmpty(),
          // Only the query goes through: `LightSearchField` reads and writes the field itself, so a
          // caller that also held it would rebuild everything below on every keystroke.
          onQueryChange = contactSearchViewModel::setQuery,
          onFocusChanged = { focused -> if (focused) callback.onSearchInputFocused() },
          modifier = Modifier.fillMaxWidth()
        )
      }

      val provider = searchConfigurationProvider
      LightContactSearch(
        viewModel = contactSearchViewModel,
        mapStateToConfiguration = mapStateToConfiguration,
        displayOptions = displayOptions,
        callbacks = contactSearchCallbacks,
        supportedArbitraryTypes = provider?.getLightArbitraryRowTypes().orEmpty(),
        customContent = { arbitrary ->
          // A provider is not guaranteed to exist, and a null one must not leave a blank row where
          // the row's content was: this names the type instead.
          if (provider != null) {
            provider.LightArbitraryRow(arbitrary, Modifier.fillMaxWidth())
          } else {
            LightContactCoverageFailure(arbitrary.type, Modifier.fillMaxWidth())
          }
        },
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
      )
    }
  }
}
