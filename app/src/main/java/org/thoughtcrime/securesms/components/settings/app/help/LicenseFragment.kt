/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.res.stringResource
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.io.InputStream

class LicenseFragment : ComposeFragment() {
  private val TAG = Log.tag(LicenseFragment::class.java)

  @Composable
  override fun FragmentContent() {
    val textState: State<List<String>> = Single
      .fromCallable {
        requireContext().resources.openRawResource(R.raw.third_party_licenses).readToLines() +
          requireContext().assets.open("acknowledgments/libsignal.md").readToLines() +
          requireContext().assets.open("acknowledgments/ringrtc.md").readToLines()
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeAsState(initial = emptyList())

    LicenseScreen(
      licenseTextLines = textState.value,
      onBack = findNavController()::popBackStack
    )
  }
}

@Composable
fun LicenseScreen(
  licenseTextLines: List<String>,
  onBack: () -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.HelpSettingsFragment__licenses),
      onBack = onBack
    ) {
      licenseTextLines.forEach { line ->
        LightSettingsRow(
          title = line.ifEmpty { " " },
          onClick = null
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
fun LicenseFragmentPreview() {
  Previews.Preview {
    LicenseScreen(listOf("Lorem ipsum", "Delor"))
  }
}

private fun InputStream.readToLines(): List<String> {
  return this.bufferedReader().use { it.readText().split("\n") }
}
