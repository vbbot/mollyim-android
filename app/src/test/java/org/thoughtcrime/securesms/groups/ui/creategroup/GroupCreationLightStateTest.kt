/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.groups.ui.creategroup.details.LightAddGroupDetailsState

class GroupCreationLightStateTest {

  @Test
  fun `empty selection exposes skip and selected members expose next`() {
    assertThat(createGroupPrimaryAction(selectionCount = 0, isLookingUpRecipient = false).kind)
      .isEqualTo(CreateGroupPrimaryAction.Kind.SKIP)
    assertThat(createGroupPrimaryAction(selectionCount = 1, isLookingUpRecipient = false).kind)
      .isEqualTo(CreateGroupPrimaryAction.Kind.NEXT)
  }

  @Test
  fun `primary action is disabled for recipient lookup`() {
    assertThat(createGroupPrimaryAction(selectionCount = 0, isLookingUpRecipient = true).enabled).isFalse()
    assertThat(createGroupPrimaryAction(selectionCount = 3, isLookingUpRecipient = true).enabled).isFalse()
    assertThat(createGroupPrimaryAction(selectionCount = 3, isLookingUpRecipient = false).enabled).isTrue()
  }

  @Test
  fun `details state disables submit while creation is busy`() {
    assertThat(detailsState(canSubmit = true, isBusy = false).submitEnabled).isTrue()
    assertThat(detailsState(canSubmit = true, isBusy = true).submitEnabled).isFalse()
    assertThat(detailsState(canSubmit = false, isBusy = false).submitEnabled).isFalse()
  }

  @Test
  fun `MMS details hide timer and empty member state keeps add later guidance`() {
    val state = detailsState(isMms = true)

    assertThat(state.showTimer).isFalse()
    assertThat(state.showAddLater).isTrue()
  }

  @Test
  fun `Signal group details expose timer`() {
    assertThat(detailsState(isMms = false).showTimer).isTrue()
  }

  private fun detailsState(
    isMms: Boolean = false,
    canSubmit: Boolean = false,
    isBusy: Boolean = false
  ): LightAddGroupDetailsState {
    return LightAddGroupDetailsState(
      name = "",
      avatarBytes = null,
      timerValue = "Off",
      members = emptyList(),
      duplicateGroups = emptyList(),
      isMms = isMms,
      canSubmit = canSubmit,
      isBusy = isBusy,
      errorMessage = null
    )
  }
}
