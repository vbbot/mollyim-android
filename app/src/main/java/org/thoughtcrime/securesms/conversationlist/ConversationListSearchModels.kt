/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import androidx.core.os.bundleOf
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

/**
 * The chat-filter row, which is all that is left of the conversation-list search's own model layer.
 *
 * Everything else this file held -- `registerThreads`, `registerMessages`, `registerGroupsWithMembers`,
 * `registerEmpty`, `registerChatFilters`, `composeEntries` and the five `MappingViewHolder`s behind
 * them -- rendered search results as `ConversationListItem`s: avatar, sender, snippet, delivery
 * state, unread pill. That row is gone from the app. Search results are
 * `conversationlist/light/LightSearchResultsView` now, a Compose list that projects the same
 * `ContactSearchData` the picker's rows already used, so the whole RecyclerView path had no callers
 * left. It is deleted rather than left in place because "the Material search row still exists, it is
 * merely unreachable" is how it comes back.
 *
 * What survives is the *data* side, which the search configuration still depends on:
 * [ChatFilterRepository] is handed to `ContactSearchViewModel` as its `ArbitraryRepository`, and
 * [ChatFilterOptions] is the code `ConversationListFragment` puts into the configuration when you
 * search with the unread filter on. [LightContactItem][org.thoughtcrime.securesms.contacts.paged.light.LightContactItem]
 * turns that row into a Light "CLEAR FILTER" action.
 */
object ConversationListSearchModels {

  /**
   * Supplies the arbitrary rows the chat filter adds to a search.
   *
   * Unchanged, and deliberately still an [ArbitraryRepository]: the paged data source asks for these
   * rows the same way whatever draws them, so the Light list gets them without this having to know
   * anything about Compose.
   */
  class ChatFilterRepository : ArbitraryRepository {
    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int = section.types.size

    override fun getData(
      section: ContactSearchConfiguration.Section.Arbitrary,
      query: String?,
      startIndex: Int,
      endIndex: Int,
      totalSearchSize: Int
    ): List<ContactSearchData.Arbitrary> {
      return section.types.map {
        ContactSearchData.Arbitrary(it, bundleOf("total-size" to totalSearchSize))
      }
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      val options = ChatFilterOptions.fromCode(arbitrary.type)
      val totalSearchSize = arbitrary.data?.getInt("total-size", -1) ?: -1
      return if (totalSearchSize == 1) {
        ChatFilterEmptyMappingModel(options)
      } else {
        ChatFilterMappingModel(options)
      }
    }
  }

  enum class ChatFilterOptions(val code: String) {
    WITH_TIP("with-tip"),
    WITHOUT_TIP("without-tip");

    companion object {
      fun fromCode(code: String): ChatFilterOptions {
        return entries.firstOrNull { it.code == code } ?: WITHOUT_TIP
      }
    }
  }

  /**
   * Still required by [ArbitraryRepository.getMappingModel], which every arbitrary repository has to
   * implement. Nothing renders these any more -- the Light list reads [ContactSearchData] directly --
   * but the interface is Molly's and is shared with screens this port has not reached.
   */
  open class BaseChatFilterMappingModel<T : BaseChatFilterMappingModel<T>>(val options: ChatFilterOptions) : MappingModel<T> {
    override fun areItemsTheSame(newItem: T): Boolean = true

    override fun areContentsTheSame(newItem: T): Boolean = options == newItem.options
  }

  class ChatFilterMappingModel(options: ChatFilterOptions) : BaseChatFilterMappingModel<ChatFilterMappingModel>(options)

  class ChatFilterEmptyMappingModel(options: ChatFilterOptions) : BaseChatFilterMappingModel<ChatFilterEmptyMappingModel>(options)
}
