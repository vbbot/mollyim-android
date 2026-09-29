/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.thoughtcrime.securesms.components.settings.models.AsyncSwitch
import org.thoughtcrime.securesms.components.settings.models.Button
import org.thoughtcrime.securesms.components.settings.models.Space
import org.thoughtcrime.securesms.components.settings.models.Text

class LightDSLSettingsAdapterTest {

  @Test
  fun `every standard concrete DSL model has an explicit Light factory`() {
    val mappedClasses = LightDSLSettingsAdapter().itemTypes.keys

    assertThat(mappedClasses).isEqualTo(
      setOf(
        ClickPreference::class.java,
        LongClickPreference::class.java,
        TextPreference::class.java,
        LearnMoreTextPreference::class.java,
        RadioListPreference::class.java,
        MultiSelectListPreference::class.java,
        ExternalLinkPreference::class.java,
        DividerPreference::class.java,
        SectionHeaderPreference::class.java,
        SwitchPreference::class.java,
        RadioPreference::class.java,
        Text.Model::class.java,
        Space.Model::class.java,
        Button.Model.Primary::class.java,
        Button.Model.PrimaryWrapped::class.java,
        Button.Model.Tonal::class.java,
        Button.Model.TonalWrapped::class.java,
        Button.Model.SecondaryNoOutline::class.java,
        AsyncSwitch.Model::class.java
      )
    )
  }
}
