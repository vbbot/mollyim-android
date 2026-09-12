/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Observable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * The thread's Light bottom bar shows a call button only when the thread actually offers a call, and
 * it learns that from the options menu rather than from a second copy of the rules
 * ([ConversationOptionsMenu.Provider.onCreateMenu] reports what it just decided). These tests drive
 * the real provider and assert on what it reports, so the bar and the overflow can never disagree
 * about which threads are callable.
 *
 * They run the pre-first-render menu, which is the one that carries the call items and applies the
 * group and push gating to them, and which stops short of the `SignalStore` read further down
 * `createMenu` that a plain Robolectric test has nothing to answer with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LightCallActionAvailabilityTest {

  private var reportedVoice: Boolean? = null
  private var reportedVideo: Boolean? = null

  @Before
  fun setUp() {
    reportedVoice = null
    reportedVideo = null
  }

  @Test
  fun `a one-to-one push contact offers both calls`() {
    buildMenu(recipient = individual(), isPushAvailable = true)

    assertThat(reportedVoice!!).isTrue()
    assertThat(reportedVideo!!).isTrue()
  }

  /** A group call is a video call. There is no voice-only group call to offer. */
  @Test
  fun `an active group offers video only`() {
    buildMenu(recipient = group(), isPushAvailable = true, isActiveV2Group = true)

    assertThat(reportedVoice!!).isFalse()
    assertThat(reportedVideo!!).isTrue()
  }

  @Test
  fun `an inactive group offers neither, so the bar drops the call button`() {
    buildMenu(recipient = group(), isPushAvailable = true, isActiveV2Group = false)

    assertThat(reportedVoice!!).isFalse()
    assertThat(reportedVideo!!).isFalse()
  }

  @Test
  fun `an SMS or otherwise non-push contact offers neither`() {
    buildMenu(recipient = individual(), isPushAvailable = false)

    assertThat(reportedVoice!!).isFalse()
    assertThat(reportedVideo!!).isFalse()
  }

  @Test
  fun `Note to Self offers neither`() {
    buildMenu(recipient = individual(isSelf = true), isPushAvailable = true)

    assertThat(reportedVoice!!).isFalse()
    assertThat(reportedVideo!!).isFalse()
  }

  /** A pending message request has no menu beyond accept/block, and so no call to put on the bar. */
  @Test
  fun `a pending message request offers neither`() {
    buildMenu(
      recipient = individual(),
      isPushAvailable = true,
      messageRequestState = MessageRequestState(MessageRequestState.State.INDIVIDUAL)
    )

    assertThat(reportedVoice!!).isFalse()
    assertThat(reportedVideo!!).isFalse()
  }

  private fun buildMenu(
    recipient: Recipient,
    isPushAvailable: Boolean,
    isActiveV2Group: Boolean = false,
    messageRequestState: MessageRequestState = MessageRequestState.NONE
  ) {
    val context = RuntimeEnvironment.getApplication()
    val provider = ConversationOptionsMenu.Provider(
      callback = FakeCallback(
        ConversationOptionsMenu.Snapshot(
          recipient = recipient,
          isPushAvailable = isPushAvailable,
          canShowAsBubble = Observable.just(false),
          isActiveGroup = isActiveV2Group,
          isActiveV2Group = isActiveV2Group,
          isInActiveGroup = !isActiveV2Group,
          hasActiveGroupCall = false,
          distributionType = 0,
          threadId = 1L,
          messageRequestState = messageRequestState,
          isInBubble = false
        )
      ),
      lifecycleDisposable = LifecycleDisposable(),
      afterFirstRenderMode = false
    )

    val menu: Menu = MenuBuilder(context)
    provider.onCreateMenu(menu, android.view.MenuInflater(context))
  }

  private fun individual(isSelf: Boolean = false): Recipient = mockk(relaxed = true) {
    every { this@mockk.isGroup } returns false
    every { this@mockk.isSelf } returns isSelf
  }

  private fun group(): Recipient = mockk(relaxed = true) {
    every { this@mockk.isGroup } returns true
    every { this@mockk.isSelf } returns false
  }

  private inner class FakeCallback(
    private val snapshot: ConversationOptionsMenu.Snapshot
  ) : ConversationOptionsMenu.Callback {

    override fun getSnapshot(): ConversationOptionsMenu.Snapshot = snapshot

    override fun isTextHighlighted(): Boolean = false

    override fun onOptionsMenuCreated(menu: Menu) = Unit

    override fun onCallActionsAvailable(canVoiceCall: Boolean, canVideoCall: Boolean) {
      reportedVoice = canVoiceCall
      reportedVideo = canVideoCall
    }

    override fun handleVideo() = Unit
    override fun handleDial() = Unit
    override fun handleViewMedia() = Unit
    override fun handleAddShortcut() = Unit
    override fun handleSearch() = Unit
    override fun handleAddToContacts() = Unit
    override fun handleManageGroup() = Unit
    override fun handleLeavePushGroup() = Unit
    override fun handleInviteLink() = Unit
    override fun handleMuteNotifications() = Unit
    override fun handleUnmuteNotifications() = Unit
    override fun handleConversationSettings() = Unit
    override fun handleSelectMessageExpiration() = Unit
    override fun handleCreateBubble() = Unit
    override fun handleGoHome() = Unit
    override fun showExpiring(recipient: Recipient) = Unit
    override fun clearExpiring() = Unit
    override fun handleFormatText(id: Int) = Unit
    override fun handleBlock() = Unit
    override fun handleUnblock() = Unit
    override fun handleReportSpam() = Unit
    override fun handleMessageRequestAccept() = Unit
    override fun handleDeleteConversation() = Unit
    override fun handleExportChat() = Unit
  }
}
