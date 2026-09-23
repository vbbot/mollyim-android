/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.app.Application
import androidx.annotation.VisibleForTesting

object CoreUiDependencies {

  private lateinit var _application: Application
  private lateinit var _provider: Provider

  fun init(application: Application, provider: Provider) {
    if (this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
  }

  /**
   * MOLLY: [init] is first-writer-wins, which is fine in production but not under Robolectric,
   * where test classes sharing a sandbox classloader also share this object. Whichever class ran
   * first leaves the real provider installed, and the next one's fake is silently dropped -- its
   * [provideForceSplitPane] then reads an uninitialised SignalStore and throws. Tests must be able
   * to overwrite unconditionally.
   */
  @VisibleForTesting
  fun initForTests(application: Application, provider: Provider) {
    _application = application
    _provider = provider
  }

  val application: Application
    get() = _application

  val backupBaseDirName: String
    get() = _provider.provideBackupBaseDirName()

  val isIncognitoKeyboardEnabled: Boolean
    get() = _provider.provideIsIncognitoKeyboardEnabled()

  val isScreenSecurityEnabled: Boolean
    get() = _provider.provideIsScreenSecurityEnabled()

  val forceSplitPane: Boolean
    get() = _provider.provideForceSplitPane()

  interface Provider {
    fun provideBackupBaseDirName(): String
    fun provideIsIncognitoKeyboardEnabled(): Boolean
    fun provideIsScreenSecurityEnabled(): Boolean
    fun provideForceSplitPane(): Boolean
  }
}
