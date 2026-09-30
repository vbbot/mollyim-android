package org.thoughtcrime.securesms.components.settings.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gates the enabled state of the account/settings rows in AppSettingsFragment.
 *
 * This is deliberately a plain JVM test rather than a Robolectric one: it exercises pure
 * presentation state, so it needs no Context, no graphics and no database. Every constructor
 * argument is passed explicitly so the `RemoteConfig`/`BuildConfig` defaults are never evaluated.
 */
class AppSettingsFragmentLightTest {

  private fun state(
    isPrimaryDevice: Boolean = true,
    unreadPaymentsCount: Int = 0,
    userUnregistered: Boolean = false,
    isLinkedDevice: Boolean = false,
    clientDeprecated: Boolean = false,
    showInternalPreferences: Boolean = false,
    showAppUpdates: Boolean = false,
    backupFailureState: BackupFailureState = BackupFailureState.NONE
  ) = AppSettingsState(
    isPrimaryDevice = isPrimaryDevice,
    unreadPaymentsCount = unreadPaymentsCount,
    userUnregistered = userUnregistered,
    isLinkedDevice = isLinkedDevice,
    clientDeprecated = clientDeprecated,
    showInternalPreferences = showInternalPreferences,
    showAppUpdates = showAppUpdates,
    backupFailureState = backupFailureState
  )

  @Test
  fun `a registered current client is up to date`() {
    assertTrue(state().isRegisteredAndUpToDate())
  }

  @Test
  fun `an unregistered user is not up to date`() {
    assertFalse(state(userUnregistered = true).isRegisteredAndUpToDate())
  }

  @Test
  fun `a deprecated client is not up to date`() {
    assertFalse(state(clientDeprecated = true).isRegisteredAndUpToDate())
  }

  @Test
  fun `a linked device that is current is still up to date`() {
    // Linking alone is not a reason to disable the account rows.
    assertTrue(state(isPrimaryDevice = false, isLinkedDevice = true).isRegisteredAndUpToDate())
  }

  @Test
  fun `link state does not mask an unregistered user`() {
    assertFalse(state(isLinkedDevice = true, userUnregistered = true).isRegisteredAndUpToDate())
  }
}
