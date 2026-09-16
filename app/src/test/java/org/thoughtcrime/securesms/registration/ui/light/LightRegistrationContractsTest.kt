/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.light

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class LightRegistrationContractsTest {

  @Test
  fun `every welcome action requests permission before selecting a destination`() {
    LightRegistrationContracts.WelcomeAction.entries.forEach { action ->
      assertThat(LightRegistrationContracts.welcomeRoute(action, hasPermissions = false))
        .isEqualTo(LightRegistrationContracts.WelcomeRoute.Permissions(action))
    }
  }

  @Test
  fun `welcome actions select their existing destinations after permission`() {
    val routes = LightRegistrationContracts.WelcomeAction.entries.associateWith { action ->
      LightRegistrationContracts.welcomeRoute(action, hasPermissions = true)
    }

    assertThat(routes).isEqualTo(
      mapOf(
        LightRegistrationContracts.WelcomeAction.CONTINUE to LightRegistrationContracts.WelcomeRoute.PhoneNumber,
        LightRegistrationContracts.WelcomeAction.LINK_DEVICE to LightRegistrationContracts.WelcomeRoute.LinkDeviceQr,
        LightRegistrationContracts.WelcomeAction.RESTORE_WITH_OLD_PHONE to LightRegistrationContracts.WelcomeRoute.RestoreViaQr,
        LightRegistrationContracts.WelcomeAction.RESTORE_WITHOUT_OLD_PHONE to LightRegistrationContracts.WelcomeRoute.SelectRestoreMethod
      )
    )
  }

  @Test
  fun `each route dispatches exactly one direct callback`() {
    val routes = listOf(
      LightRegistrationContracts.WelcomeRoute.Permissions(LightRegistrationContracts.WelcomeAction.CONTINUE),
      LightRegistrationContracts.WelcomeRoute.PhoneNumber,
      LightRegistrationContracts.WelcomeRoute.LinkDeviceQr,
      LightRegistrationContracts.WelcomeRoute.RestoreViaQr,
      LightRegistrationContracts.WelcomeRoute.SelectRestoreMethod
    )

    routes.forEachIndexed { expectedIndex, route ->
      val calls = MutableList(routes.size) { 0 }
      val callbacks = LightRegistrationContracts.WelcomeRouteCallbacks(
        requestPermissions = { calls[0]++ },
        showPhoneNumber = { calls[1]++ },
        showLinkDeviceQr = { calls[2]++ },
        showRestoreViaQr = { calls[3]++ },
        showRestoreMethods = { calls[4]++ }
      )

      LightRegistrationContracts.dispatch(route, callbacks)

      assertThat(calls).isEqualTo(List(routes.size) { index -> if (index == expectedIndex) 1 else 0 })
    }
  }

  @Test
  fun `action state disables submission while progress is visible`() {
    assertThat(LightRegistrationContracts.actionState(inputValid = true, inProgress = false))
      .isEqualTo(LightRegistrationContracts.ActionState(enabled = true, showProgress = false))
    assertThat(LightRegistrationContracts.actionState(inputValid = true, inProgress = true))
      .isEqualTo(LightRegistrationContracts.ActionState(enabled = false, showProgress = true))
    assertThat(LightRegistrationContracts.actionState(inputValid = false, inProgress = false))
      .isEqualTo(LightRegistrationContracts.ActionState(enabled = false, showProgress = false))
  }

  @Test
  fun `country code sanitation retains only digits`() {
    assertThat(LightRegistrationContracts.sanitizeCountryCode(" +1 (44)a ")).isEqualTo("144")
    assertThat(LightRegistrationContracts.sanitizeCountryCode("")).isEqualTo("")
  }

  @Test
  fun `verification code completeness requires the exact number of digits`() {
    assertThat(LightRegistrationContracts.isCompleteVerificationCode("123456", length = 6)).isTrue()
    assertThat(LightRegistrationContracts.isCompleteVerificationCode("12345", length = 6)).isFalse()
    assertThat(LightRegistrationContracts.isCompleteVerificationCode("1234567", length = 6)).isFalse()
    assertThat(LightRegistrationContracts.isCompleteVerificationCode("12345a", length = 6)).isFalse()
  }

  @Test
  fun `pin policies preserve their distinct whitespace semantics`() {
    val value = " 12 34 "

    assertThat(
      LightRegistrationContracts.pinLength(value, LightRegistrationContracts.PinWhitespace.TRIM_EDGES)
    ).isEqualTo(5)
    assertThat(
      LightRegistrationContracts.pinLength(value, LightRegistrationContracts.PinWhitespace.REMOVE_SPACES)
    ).isEqualTo(4)
  }

  @Test
  fun `LP3 geometry derives actions gutters and touch targets from 27 columns`() {
    val width = LightRegistrationGeometry.LP3_WIDTH_DP

    assertThat(LightRegistrationGeometry.LP3_HEIGHT_DP).isEqualTo(413f)
    assertThat(LightRegistrationGeometry.gridUnitDp(width)).isCloseTo(13.333333f, 0.0001f)
    assertThat(LightRegistrationGeometry.horizontalGutterDp(width)).isCloseTo(26.666666f, 0.0001f)
    assertThat(LightRegistrationGeometry.contentWidthDp(width)).isCloseTo(306.66666f, 0.0001f)
    assertThat(LightRegistrationGeometry.actionHeightDp(width)).isCloseTo(53.333332f, 0.0001f)
    assertThat(LightRegistrationGeometry.minimumTouchDp(width)).isCloseTo(44f, 0.0001f)
  }
}
