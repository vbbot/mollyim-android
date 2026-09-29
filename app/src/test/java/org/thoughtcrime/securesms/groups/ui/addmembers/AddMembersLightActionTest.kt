/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.addmembers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class AddMembersLightActionTest {

  @Test
  fun `done requires a new selection and no lookup`() {
    assertThat(addMembersPrimaryAction(selectionCount = 0, isLookingUpRecipient = false).enabled).isFalse()
    assertThat(addMembersPrimaryAction(selectionCount = 1, isLookingUpRecipient = true).enabled).isFalse()
    assertThat(addMembersPrimaryAction(selectionCount = 1, isLookingUpRecipient = false).enabled).isTrue()
  }

  @Test
  fun `confirm produces only the return selected IDs effect`() {
    val transition = reduceAddMembersConfirmation(
      submitted = false,
      action = AddMembersConfirmationAction.CONFIRM
    )

    assertThat(transition.submitted).isTrue()
    assertThat(transition.effect).isEqualTo(AddMembersConfirmationEffect.RETURN_SELECTED_IDS)
  }

  @Test
  fun `dismiss produces only the dismiss effect`() {
    val transition = reduceAddMembersConfirmation(
      submitted = false,
      action = AddMembersConfirmationAction.DISMISS
    )

    assertThat(transition.submitted).isTrue()
    assertThat(transition.effect).isEqualTo(AddMembersConfirmationEffect.DISMISS)
  }

  @Test
  fun `confirmation cannot emit a second effect`() {
    AddMembersConfirmationAction.entries.forEach { action ->
      val transition = reduceAddMembersConfirmation(submitted = true, action = action)

      assertThat(transition.submitted).isTrue()
      assertThat(transition.effect).isNull()
    }
  }
}
