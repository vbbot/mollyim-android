/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration

class MemberSearchLightStateTest {

  @Test
  fun `normal results expose only actions allowed by group capabilities`() {
    assertThat(memberSearchActionTypes(canAdd = false, hasGroupLink = false).toList())
      .isEqualTo(emptyList())
    assertThat(memberSearchActionTypes(canAdd = true, hasGroupLink = false).toList())
      .isEqualTo(listOf("divider", "add-members"))
    assertThat(memberSearchActionTypes(canAdd = false, hasGroupLink = true).toList())
      .isEqualTo(listOf("divider", "invite-via"))
    assertThat(memberSearchActionTypes(canAdd = true, hasGroupLink = true).toList())
      .isEqualTo(listOf("divider", "add-members", "invite-via"))
  }

  @Test
  fun `empty results preserve both recovery actions`() {
    assertThat(memberSearchEmptyActionTypes().toList())
      .isEqualTo(listOf("divider", "add-members", "invite-via"))
  }

  @Test
  fun `member arbitrary rows route only to their existing actions`() {
    assertThat(memberSearchRowAction("divider")).isEqualTo(MemberSearchRowAction.DIVIDER)
    assertThat(memberSearchRowAction("add-members")).isEqualTo(MemberSearchRowAction.ADD_MEMBERS)
    assertThat(memberSearchRowAction("invite-via")).isEqualTo(MemberSearchRowAction.INVITE_VIA)
    assertThat(memberSearchRowAction("unknown")).isNull()
  }

  @Test
  fun `every member filter keeps its repository role`() {
    assertThat(MemberSearchViewModel.MemberFilter.ALL.toContactSearchRole())
      .isEqualTo(ContactSearchConfiguration.MemberRole.ALL)
    assertThat(MemberSearchViewModel.MemberFilter.ADMINS.toContactSearchRole())
      .isEqualTo(ContactSearchConfiguration.MemberRole.ADMINS)
    assertThat(MemberSearchViewModel.MemberFilter.CONTACTS.toContactSearchRole())
      .isEqualTo(ContactSearchConfiguration.MemberRole.CONTACTS)
  }
}
