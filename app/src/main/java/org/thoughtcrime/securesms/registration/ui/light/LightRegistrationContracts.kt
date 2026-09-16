/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.light

/** Pure presentation decisions shared by the legacy registration Views and their unit tests. */
object LightRegistrationContracts {

  enum class WelcomeAction {
    CONTINUE,
    LINK_DEVICE,
    RESTORE_WITH_OLD_PHONE,
    RESTORE_WITHOUT_OLD_PHONE
  }

  sealed interface WelcomeRoute {
    data class Permissions(val action: WelcomeAction) : WelcomeRoute
    data object PhoneNumber : WelcomeRoute
    data object LinkDeviceQr : WelcomeRoute
    data object RestoreViaQr : WelcomeRoute
    data object SelectRestoreMethod : WelcomeRoute
  }

  fun welcomeRoute(action: WelcomeAction, hasPermissions: Boolean): WelcomeRoute {
    if (!hasPermissions) return WelcomeRoute.Permissions(action)

    return when (action) {
      WelcomeAction.CONTINUE -> WelcomeRoute.PhoneNumber
      WelcomeAction.LINK_DEVICE -> WelcomeRoute.LinkDeviceQr
      WelcomeAction.RESTORE_WITH_OLD_PHONE -> WelcomeRoute.RestoreViaQr
      WelcomeAction.RESTORE_WITHOUT_OLD_PHONE -> WelcomeRoute.SelectRestoreMethod
    }
  }

  data class WelcomeRouteCallbacks(
    val requestPermissions: (WelcomeAction) -> Unit,
    val showPhoneNumber: () -> Unit,
    val showLinkDeviceQr: () -> Unit,
    val showRestoreViaQr: () -> Unit,
    val showRestoreMethods: () -> Unit
  )

  /** Dispatches one route to one visible destination callback. */
  fun dispatch(route: WelcomeRoute, callbacks: WelcomeRouteCallbacks) {
    when (route) {
      is WelcomeRoute.Permissions -> callbacks.requestPermissions(route.action)
      WelcomeRoute.PhoneNumber -> callbacks.showPhoneNumber()
      WelcomeRoute.LinkDeviceQr -> callbacks.showLinkDeviceQr()
      WelcomeRoute.RestoreViaQr -> callbacks.showRestoreViaQr()
      WelcomeRoute.SelectRestoreMethod -> callbacks.showRestoreMethods()
    }
  }

  enum class PinWhitespace {
    TRIM_EDGES,
    REMOVE_SPACES
  }

  fun sanitizeCountryCode(value: CharSequence): String = value.filter(Char::isDigit).toString()

  fun isCompleteVerificationCode(value: String, length: Int): Boolean =
    value.length == length && value.all(Char::isDigit)

  fun pinLength(value: String, whitespace: PinWhitespace): Int = when (whitespace) {
    PinWhitespace.TRIM_EDGES -> value.trim().length
    PinWhitespace.REMOVE_SPACES -> value.replace(" ", "").length
  }

  data class ActionState(val enabled: Boolean, val showProgress: Boolean)

  fun actionState(inputValid: Boolean, inProgress: Boolean): ActionState = ActionState(
    enabled = inputValid && !inProgress,
    showProgress = inProgress
  )
}

/** LP3 reference geometry. Runtime dimensions are derived from the same 27-column grid. */
object LightRegistrationGeometry {
  const val LP3_WIDTH_DP = 360f
  const val LP3_HEIGHT_DP = 413f
  const val GRID_COLUMNS = 27f
  const val HORIZONTAL_GUTTER_UNITS = 2f
  const val ACTION_HEIGHT_UNITS = 4f
  const val MINIMUM_TOUCH_UNITS = 3.3f

  fun gridUnitDp(widthDp: Float): Float = widthDp / GRID_COLUMNS

  fun horizontalGutterDp(widthDp: Float): Float = gridUnitDp(widthDp) * HORIZONTAL_GUTTER_UNITS

  fun contentWidthDp(widthDp: Float): Float = widthDp - horizontalGutterDp(widthDp) * 2f

  fun actionHeightDp(widthDp: Float): Float = gridUnitDp(widthDp) * ACTION_HEIGHT_UNITS

  fun minimumTouchDp(widthDp: Float): Float = gridUnitDp(widthDp) * MINIMUM_TOUCH_UNITS
}
