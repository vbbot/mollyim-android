/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme

private const val COUNTRY_ROW_HEIGHT_UNITS = 4.5f

/** Light country search/selection presentation; state and result navigation remain fragment-owned. */
@Composable
fun CountryCodeSelectScreen(
  state: CountryCodeState,
  title: String,
  onSearch: (String) -> Unit = {},
  onDismissed: () -> Unit = {},
  onClick: (Country) -> Unit = {}
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
          onClick = onDismissed,
          contentDescription = stringResource(R.string.Material3SearchToolbar__close)
        ),
        center = LightTopBarCenter.Text(title)
      )

      LightSearchField(
        query = state.query,
        onQueryChange = onSearch,
        hint = stringResource(R.string.CountryCodeFragment__search_by)
      )

      val listState = rememberLazyListState()
      val visibleCountries = when {
        state.countryList.isEmpty() -> emptyList()
        state.query.isNotEmpty() -> state.filteredList
        else -> state.commonCountryList + state.countryList
      }

      if (state.countryList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          LightText(
            text = "…",
            variant = LightTextVariant.Copy,
            align = TextAlign.Center
          )
        }
      } else {
        LightLazyScrollView(
          listState = listState,
          uniformItemHeightGridUnits = COUNTRY_ROW_HEIGHT_UNITS,
          scrollBarPosition = LightScrollBarPosition.Outside,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          items(visibleCountries) { country ->
            CountryItem(country = country, onClick = onClick)
          }
        }
      }

      LaunchedEffect(state.startingIndex, state.query, visibleCountries.size) {
        if (state.query.isEmpty() && visibleCountries.isNotEmpty()) {
          val target = state.startingIndex.coerceIn(0, visibleCountries.lastIndex)
          listState.scrollToItem(target)
        }
      }
    }
  }
}

@Composable
private fun CountryItem(
  country: Country,
  onClick: (Country) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(COUNTRY_ROW_HEIGHT_UNITS.gridUnitsAsDp())
      .lightClickable { onClick(country) }
      .padding(horizontal = 1f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = country.emoji,
      color = LightThemeTokens.colors.content,
      modifier = Modifier.padding(end = 1f.gridUnitsAsDp())
    )
    LightText(
      text = country.name.ifEmpty { stringResource(R.string.CountryCodeFragment__unknown_country) },
      variant = LightTextVariant.Copy,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )
    LightText(
      text = "+${country.countryCode}",
      variant = LightTextVariant.Detail,
      lighten = true,
      modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp())
    )
  }
}

@DayNightPreviews
@Composable
private fun ScreenPreview() {
  Previews.Preview {
    CountryCodeSelectScreen(
      state = CountryCodeState(
        countryList = mutableListOf(
          Country("🇺🇸", "United States", 1, "US"),
          Country("🇨🇦", "Canada", 1, "CA"),
          Country("🇲🇽", "Mexico", 52, "MX")
        ),
        commonCountryList = mutableListOf(Country("🇺🇸", "United States", 1, "US"))
      ),
      title = "Your country"
    )
  }
}
