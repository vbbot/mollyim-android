package org.thoughtcrime.securesms.components.settings.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.util.TestThemeRule
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSettingsFragmentLightTest {

  @get:Rule
  val themeRule = TestThemeRule()

  @Test
  fun `dummy test to verify we can add tests`() {
    assert(true)
  }
}
