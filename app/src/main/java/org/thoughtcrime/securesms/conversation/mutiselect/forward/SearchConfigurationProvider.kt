package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentManager
import kotlinx.collections.immutable.persistentHashMapOf
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.light.LightContactCoverageFailure
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider

/**
 * Allows a parent of MultiselectForwardFragment to provide a custom search page configuration.
 */
interface SearchConfigurationProvider {
  /**
   * @param fragmentManager    The child fragment manager of the MultiselectForwardFragment, to launch actions in to.
   * @param contactSearchState The search state, to build the configuration from.
   *
   * @return A configuration or null. Returning null will result in MultiselectForwardFragment using it's default configuration.
   */
  fun getSearchConfiguration(fragmentManager: FragmentManager, contactSearchState: ContactSearchState): ContactSearchConfiguration? = null

  /**
   * @return An ArbitraryRepository or null. Returning null will result in not being able to use the Arbitrary section, keys, or data.
   */
  fun getArbitraryRepository(): ArbitraryRepository? = null

  /**
   * @return a mapping of additional entries. Recommended to put your arbitrary stuff here.
   */
  @Composable
  fun getAdditionalEntries(): MappingEntryProvider<Any> = persistentHashMapOf()

  /**
   * The arbitrary row types this host knows how to draw itself, as opposed to the ones
   * [ContactSelectionListModels.ArbitraryRow] already covers.
   *
   * A type that is neither in this set nor a known arbitrary row does not disappear: the Light list
   * draws it as a visible coverage failure naming the type. That is deliberate. The alternative --
   * dropping it -- would silently shorten the list on whichever screen forgot to register it, and
   * the drop is invisible precisely where it matters, because a story preview strip that fails to
   * appear looks the same as one you never had.
   */
  fun getLightArbitraryRowTypes(): Set<String> = emptySet()

  /**
   * Light presentation for a type declared by [getLightArbitraryRowTypes].
   *
   * The default says what the row is rather than drawing nothing. An empty implementation would be
   * the natural thing to leave in an interface method, and it produces a blank row indistinguishable
   * from a paging hole -- so the failure is loud here instead.
   */
  @Composable
  fun LightArbitraryRow(data: ContactSearchData.Arbitrary, modifier: Modifier) {
    Log.w(TAG, "No Light row declared for arbitrary type '${data.type}'")
    LightContactCoverageFailure(data.type, modifier)
  }

  companion object {
    private val TAG = Log.tag(SearchConfigurationProvider::class.java)
  }
}
