/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class PermissionsSettingsLightStateTest {

  @Test
  fun `ordinary permissions map all members to index one`() {
    val state = PermissionsSettingsState(
      nonAdminCanAddMembers = true,
      nonAdminCanEditGroupInfo = true,
      nonAdminCanSetMemberLabel = true
    )

    assertThat(state.selectedIndex(GroupPermission.ADD_MEMBERS)).isEqualTo(ALL_MEMBERS_INDEX)
    assertThat(state.selectedIndex(GroupPermission.EDIT_GROUP_INFO)).isEqualTo(ALL_MEMBERS_INDEX)
    assertThat(state.selectedIndex(GroupPermission.ADD_MEMBER_LABELS)).isEqualTo(ALL_MEMBERS_INDEX)
  }

  @Test
  fun `ordinary permissions map admin only to index zero`() {
    val state = PermissionsSettingsState()

    assertThat(state.selectedIndex(GroupPermission.ADD_MEMBERS)).isEqualTo(ONLY_ADMINS_INDEX)
    assertThat(state.selectedIndex(GroupPermission.EDIT_GROUP_INFO)).isEqualTo(ONLY_ADMINS_INDEX)
    assertThat(state.selectedIndex(GroupPermission.ADD_MEMBER_LABELS)).isEqualTo(ONLY_ADMINS_INDEX)
  }

  @Test
  fun `announcement state inverts send permission`() {
    assertThat(
      PermissionsSettingsState(announcementGroup = true).selectedIndex(GroupPermission.SEND_MESSAGES)
    ).isEqualTo(ONLY_ADMINS_INDEX)
    assertThat(
      PermissionsSettingsState(announcementGroup = false).selectedIndex(GroupPermission.SEND_MESSAGES)
    ).isEqualTo(ALL_MEMBERS_INDEX)
  }
}
