/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.captcha

import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.Navigation
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.thelightphone.sdk.ui.LightIcons
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.light.LightBarAction
import org.thoughtcrime.securesms.light.LightTopBarView

/**
 * The captcha step's Light chrome. The WebView and its token handling are not this test's business
 * (and are unchanged); what is pinned is the bar that replaced the centred headline -- a BACK icon
 * that does what system back already did there, and a short title -- and that the Material surface
 * the step used to sit on is gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class CaptchaFragmentTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun `top bar BACK navigates up and says what it leaves`() {
    val (fragment, navController) = launch()
    val bar = fragment.requireView().findViewById<LightTopBarView>(R.id.light_top_bar)

    val back = bar.leftAction
    assertThat(back).isNotNull().isInstanceOf(LightBarAction.Sdk::class)
    back as LightBarAction.Sdk
    assertThat(back.icon).isEqualTo(LightIcons.BACK)
    assertThat(back.contentDescription).isEqualTo(context.getString(R.string.CaptchaFragment__back_without_verifying))

    back.onClick()

    verify(exactly = 1) { navController.navigateUp() }
  }

  @Test
  fun `top bar carries the short title and nothing on the right`() {
    val (fragment, _) = launch()
    val bar = fragment.requireView().findViewById<LightTopBarView>(R.id.light_top_bar)

    assertThat(bar.title).isEqualTo(context.getString(R.string.CaptchaFragment__verification))
    assertThat(bar.rightAction).isNull()
  }

  @Test
  fun `the step sits on the Light background, not a Material surface`() {
    val (fragment, _) = launch()
    val root = fragment.requireView()

    assertThat((root.background as ColorDrawable).color).isEqualTo(LightItemStyle.backgroundColor(context))
    // The explanation line and the WebView draw no surface of their own over it.
    assertThat(root.findViewById<View>(R.id.registration_captcha_explanation).background).isNull()
    assertThat(root.findViewById<WebView>(R.id.registration_captcha_web_view)).isNotNull()
  }

  private fun launch(): Pair<TestCaptchaFragment, NavController> {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val fragment = TestCaptchaFragment()
    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

    // findNavController() falls back to the controller set on the fragment's view.
    val navController = mockk<NavController>(relaxed = true)
    Navigation.setViewNavController(fragment.requireView(), navController)
    return fragment to navController
  }
}

/** [CaptchaFragment] is abstract; the token sink is irrelevant to the chrome under test. */
class TestCaptchaFragment : CaptchaFragment() {
  override fun handleCaptchaToken(token: String) = Unit
}
