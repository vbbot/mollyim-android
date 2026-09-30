package org.thoughtcrime.securesms.groups.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.contacts.paged.light.LightContactItem
import org.thoughtcrime.securesms.contacts.paged.light.LightContactListScreen
import org.thoughtcrime.securesms.contacts.paged.light.LightContactState
import org.thoughtcrime.securesms.contacts.paged.rememberDefaultContactSearchItemClickCallbacks
import org.thoughtcrime.securesms.conversation.mutiselect.forward.SearchConfigurationProvider
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightScrim
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.ui.sharablegrouplink.GroupLinkBottomSheetDialogFragment
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.fragments.findListener
import java.util.Locale

/**
 * Fragment that shows all members in a group (including self)
 */
class MemberSearchFragment : ComposeFragment(), RecipientBottomSheetDialogFragment.Callback {

  companion object {

    private val TAG = Log.tag(MemberSearchFragment::class.java)
    private const val ARG_GROUP_ID = "group_id"
    private const val ARG_CAN_ADD = "can_add"
    private const val ARG_HAS_GROUP_LINK = "has_group_link"

    const val RESULT_ADD_MEMBERS = "result_add"

    fun newInstance(groupId: GroupId.V2, canAdd: Boolean, hasGroupLink: Boolean): MemberSearchFragment {
      return MemberSearchFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_GROUP_ID, groupId)
          putBoolean(ARG_CAN_ADD, canAdd)
          putBoolean(ARG_HAS_GROUP_LINK, hasGroupLink)
        }
      }
    }
  }

  private val groupId: GroupId.V2 by lazy {
    requireArguments().requireParcelableCompat(ARG_GROUP_ID, GroupId.V2::class.java)
  }

  private val contactViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = SelectionLimits(0, 0),
      isMultiSelect = false,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = false,
      arbitraryRepository = findListener<SearchConfigurationProvider>()?.getArbitraryRepository() ?: MemberArbitraryRepository(),
      searchRepository = SearchRepository(requireContext().getString(R.string.Recipient_you)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext(), requireContext().getString(R.string.Recipient_you))
    )
  }

  private val memberSearchViewModel: MemberSearchViewModel by viewModels()

  override fun onRecipientBottomSheetDismissed() {
    contactViewModel.refreshGroupData()
  }

  override fun onMessageClicked() = Unit

  @Composable
  override fun FragmentContent() {
    val memberFilter by memberSearchViewModel.memberFilter.collectAsStateWithLifecycle()
    val canAdd = requireArguments().getBoolean(ARG_CAN_ADD)
    val hasGroupLink = requireArguments().getBoolean(ARG_HAS_GROUP_LINK)
    CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
      MemberSearchScreen(
        contactViewModel = contactViewModel,
        memberFilter = memberFilter,
        onFilterSelected = { memberSearchViewModel.setFilter(it) },
        mapStateToConfiguration = { state -> getConfiguration(state, memberFilter, canAdd, hasGroupLink) },
        contactSearchCallbacks = remember {
          SearchCallbacks(
            fragmentManager = childFragmentManager,
            groupId = groupId,
            onAddMembers = {
              setFragmentResult(RESULT_ADD_MEMBERS, Bundle.EMPTY)
              findNavController().popBackStack()
            }
          )
        }
      )
    }
  }

  class SearchCallbacks(
    private val fragmentManager: FragmentManager,
    private val groupId: GroupId.V2,
    private val onAddMembers: () -> Unit
  ) : ContactSearchCallbacks.Simple() {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      val recipientId = contactSearchKeys.filterIsInstance<ContactSearchKey.RecipientSearchKey>().firstOrNull()?.recipientId
      if (recipientId != null) {
        RecipientBottomSheetDialogFragment.show(fragmentManager, recipientId, groupId)
      }
      return emptySet()
    }

    fun onAdd() {
      onAddMembers()
    }

    fun onInvite() {
      GroupLinkBottomSheetDialogFragment.show(fragmentManager, groupId)
    }
  }

  private fun getConfiguration(contactSearchState: ContactSearchState, memberFilter: MemberSearchViewModel.MemberFilter, canAdd: Boolean, hasGroupLink: Boolean): ContactSearchConfiguration {
    return findListener<SearchConfigurationProvider>()?.getSearchConfiguration(childFragmentManager, contactSearchState) ?: ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.GroupMembers(
          includeHeader = false,
          includeLetterHeaders = true,
          groupId = groupId,
          showGroupsInCommon = false,
          showSelfAsYou = true,
          roleFilter = memberFilter.toContactSearchRole()
        )
      )

      val actionTypes = memberSearchActionTypes(canAdd, hasGroupLink)
      if (actionTypes.isNotEmpty()) {
        addSection(ContactSearchConfiguration.Section.Arbitrary(actionTypes))
      }

      withEmptyState {
        addSection(ContactSearchConfiguration.Section.Empty)
        addSection(ContactSearchConfiguration.Section.Arbitrary(memberSearchEmptyActionTypes()))
      }
    }
  }

  class DividerModel : MappingModel<DividerModel> {
    override fun areItemsTheSame(newItem: DividerModel) = true
    override fun areContentsTheSame(newItem: DividerModel) = true
  }

  class AddMembersModel : MappingModel<AddMembersModel> {
    override fun areItemsTheSame(newItem: AddMembersModel) = true
    override fun areContentsTheSame(newItem: AddMembersModel) = true
  }

  class InviteViaModel : MappingModel<InviteViaModel> {
    override fun areItemsTheSame(newItem: InviteViaModel) = true
    override fun areContentsTheSame(newItem: InviteViaModel) = true
  }

  class MemberArbitraryRepository : ArbitraryRepository {
    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?) = section.types.size

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      return section.types.toList().subList(startIndex, endIndex).map { ContactSearchData.Arbitrary(it) }
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return when (arbitrary.type) {
        TYPE_DIVIDER -> DividerModel()
        TYPE_ADD_MEMBERS -> AddMembersModel()
        TYPE_INVITE_VIA -> InviteViaModel()
        else -> throw IllegalArgumentException("Unknown type: ${arbitrary.type}")
      }
    }

    companion object {
      const val TYPE_DIVIDER = "divider"
      const val TYPE_ADD_MEMBERS = "add-members"
      const val TYPE_INVITE_VIA = "invite-via"
    }
  }
}

internal enum class MemberSearchRowAction {
  DIVIDER,
  ADD_MEMBERS,
  INVITE_VIA
}

internal fun memberSearchRowAction(type: String): MemberSearchRowAction? = when (type) {
  MemberSearchFragment.MemberArbitraryRepository.TYPE_DIVIDER -> MemberSearchRowAction.DIVIDER
  MemberSearchFragment.MemberArbitraryRepository.TYPE_ADD_MEMBERS -> MemberSearchRowAction.ADD_MEMBERS
  MemberSearchFragment.MemberArbitraryRepository.TYPE_INVITE_VIA -> MemberSearchRowAction.INVITE_VIA
  else -> null
}

/** The insertion order is also the order the arbitrary repository returns to the paged list. */
internal fun memberSearchActionTypes(canAdd: Boolean, hasGroupLink: Boolean): Set<String> = buildSet {
  if (canAdd || hasGroupLink) {
    add(MemberSearchFragment.MemberArbitraryRepository.TYPE_DIVIDER)
  }
  if (canAdd) {
    add(MemberSearchFragment.MemberArbitraryRepository.TYPE_ADD_MEMBERS)
  }
  if (hasGroupLink) {
    add(MemberSearchFragment.MemberArbitraryRepository.TYPE_INVITE_VIA)
  }
}

/** Matches the existing empty configuration: no-results keeps both recovery actions reachable. */
internal fun memberSearchEmptyActionTypes(): Set<String> = setOf(
  MemberSearchFragment.MemberArbitraryRepository.TYPE_DIVIDER,
  MemberSearchFragment.MemberArbitraryRepository.TYPE_ADD_MEMBERS,
  MemberSearchFragment.MemberArbitraryRepository.TYPE_INVITE_VIA
)

internal fun MemberSearchViewModel.MemberFilter.toContactSearchRole(): ContactSearchConfiguration.MemberRole = when (this) {
  MemberSearchViewModel.MemberFilter.ALL -> ContactSearchConfiguration.MemberRole.ALL
  MemberSearchViewModel.MemberFilter.ADMINS -> ContactSearchConfiguration.MemberRole.ADMINS
  MemberSearchViewModel.MemberFilter.CONTACTS -> ContactSearchConfiguration.MemberRole.CONTACTS
}

@Composable
fun MemberSearchScreen(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  onFilterSelected: (MemberSearchViewModel.MemberFilter) -> Unit,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  var showFilterPicker by remember { mutableStateOf(false) }

  BackHandler(enabled = showFilterPicker) {
    showFilterPicker = false
  }

  MollyLightTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        MemberSearchTopBar(
          title = stringResource(R.string.MemberSearchFragment__search_members),
          onBack = { onBackPressedDispatcher?.onBackPressed() },
          onFilter = {
            focusManager.clearFocus()
            keyboardController?.hide()
            showFilterPicker = true
          }
        )

        MemberSearchContent(
          contactViewModel = contactViewModel,
          memberFilter = memberFilter,
          mapStateToConfiguration = mapStateToConfiguration,
          contactSearchCallbacks = contactSearchCallbacks,
          modifier = Modifier.weight(1f)
        )
      }

      if (showFilterPicker) {
        MemberSearchFilterPicker(
          memberFilter = memberFilter,
          onFilterSelected = { selected ->
            onFilterSelected(selected)
            showFilterPicker = false
          },
          onDismiss = { showFilterPicker = false }
        )
      }
    }
  }
}

@Composable
private fun MemberSearchTopBar(
  title: String,
  onBack: () -> Unit,
  onFilter: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars)
      .background(LightThemeTokens.colors.background)
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
      ),
      center = LightTopBarCenter.Text(title),
      rightButton = LightBarButton.Text(
        text = stringResource(R.string.MemberSearchFragment__filter).uppercase(Locale.getDefault()),
        onClick = onFilter,
        contentDescription = stringResource(R.string.MemberSearchFragment__filter)
      )
    )
  }
}

/** Full-screen Light selector so all three filters remain reachable after the LP3 system IME closes. */
@Composable
internal fun MemberSearchFilterPicker(
  memberFilter: MemberSearchViewModel.MemberFilter,
  onFilterSelected: (MemberSearchViewModel.MemberFilter) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val filters = MemberSearchViewModel.MemberFilter.entries
  val labels = stringArrayResource(R.array.filter_search_entries)

  Box(modifier = modifier.fillMaxSize()) {
    // The picker is opaque and covers the list, but a background consumes nothing: without the
    // scrim a tap between the top bar and the last filter row would land on the member row, the
    // search field or the Filter button underneath. The scrim sits under the rows so it only ever
    // sees what no row claimed.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
        .lightScrim()
    )

    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = onDismiss,
            contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
          ),
          center = LightTopBarCenter.Text(stringResource(R.string.MemberSearchFragment__filter))
        )
      }

      filters.forEachIndexed { index, filter ->
        val label = labels[index].uppercase(Locale.getDefault())
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MEMBER_ROW_HEIGHT_UNITS.gridUnitsAsDp())
            .semantics(mergeDescendants = true) {
              selected = filter == memberFilter
            }
            .lightClickable(role = Role.RadioButton) { onFilterSelected(filter) }
            .padding(horizontal = MEMBER_ROW_SIDE_UNITS.gridUnitsAsDp()),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier.width(MEMBER_MARKER_SLOT_UNITS.gridUnitsAsDp()),
            contentAlignment = Alignment.CenterStart
          ) {
            LightIcon(
              icon = if (filter == memberFilter) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
              size = MEMBER_MARKER_SLOT_UNITS,
              contentDescription = null
            )
          }
          Box(modifier = Modifier.width(MEMBER_COLUMN_GAP_UNITS.gridUnitsAsDp()))
          LightText(
            text = label,
            variant = LightTextVariant.Button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

@Composable
private fun MemberSearchContent(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val focusRequester = remember { FocusRequester() }
  val lazyListState = rememberLazyListState()
  val context = LocalContext.current
  val anchorView = LocalView.current
  val query by contactViewModel.query.collectAsStateWithLifecycle()
  val data by contactViewModel.data.collectAsStateWithLifecycle()
  val selection by contactViewModel.selectionState.collectAsStateWithLifecycle()
  val controller by contactViewModel.controller.collectAsStateWithLifecycle()
  val configurationState by contactViewModel.configurationState.collectAsStateWithLifecycle()
  val clickCallbacks = rememberDefaultContactSearchItemClickCallbacks(contactViewModel, contactSearchCallbacks)
  val lightState = remember(context, data, selection) {
    memberSearchLightState(context, data, selection)
  }
  val rowsByKey = remember(lightState) { lightState.rows.associateBy(LightContactItem::key) }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  LaunchedEffect(configurationState, memberFilter) {
    contactViewModel.setConfiguration(currentMapStateToConfiguration(configurationState))
  }

  LaunchedEffect(lazyListState) {
    contactViewModel.scrollRequests.collect {
      lazyListState.requestScrollToItem(0)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    val hint = when (memberFilter) {
      MemberSearchViewModel.MemberFilter.ALL -> stringResource(R.string.MemberSearchFragment__search_members)
      MemberSearchViewModel.MemberFilter.ADMINS -> stringResource(R.string.MemberSearchFragment__search_admins)
      MemberSearchViewModel.MemberFilter.CONTACTS -> stringResource(R.string.MemberSearchFragment__search_contacts)
    }
    LightSearchField(
      hint = hint,
      query = query ?: "",
      onQueryChange = { contactViewModel.setQuery(it) },
      focusRequester = focusRequester,
      modifier = Modifier.fillMaxWidth()
    )

    LightContactListScreen(
      state = lightState,
      listState = lazyListState,
      onClick = { key ->
        // `LightContactListScreen` hands back the row's stable key rather than the row, because a
        // click may land on an instance composed before the latest paging update. Resolve it here.
        val item = rowsByKey[key] ?: return@LightContactListScreen
        when (val row = item.data) {
          is ContactSearchData.KnownRecipient -> clickCallbacks.onKnownRecipientClicked(anchorView, row, item.selected)
          is ContactSearchData.UnknownRecipient -> clickCallbacks.onUnknownRecipientClicked(anchorView, row, item.selected)
          is ContactSearchData.ChatTypeRow -> clickCallbacks.onChatTypeClicked(anchorView, row, item.selected)
          is ContactSearchData.Story -> clickCallbacks.onStoryClicked(anchorView, row, item.selected)
          is ContactSearchData.Expand -> clickCallbacks.onExpandClicked(row)
          is ContactSearchData.Arbitrary -> when (memberSearchRowAction(row.type)) {
            MemberSearchRowAction.ADD_MEMBERS -> contactSearchCallbacks.onAdd()
            MemberSearchRowAction.INVITE_VIA -> contactSearchCallbacks.onInvite()
            MemberSearchRowAction.DIVIDER,
            null -> Unit
          }
          else -> Unit
        }
      },
      onDataNeededAtSourceIndex = { index -> controller?.onDataNeededAroundIndex(index) },
      modifier = Modifier.weight(1f)
    )
  }
}

/** Converts only this screen's three arbitrary models; shared contact-picker mapping stays untouched. */
internal fun memberSearchLightState(
  context: Context,
  data: List<ContactSearchData?>,
  selection: Set<ContactSearchKey>
): LightContactState {
  val base = LightContactItem.map(
    context = context,
    data = data,
    selection = selection,
    displayCheckBox = false
  )

  val rows = base.rows.mapNotNull { item ->
    when (val source = data.getOrNull(item.sourceIndex)) {
      is ContactSearchData.Arbitrary -> when (memberSearchRowAction(source.type)) {
        MemberSearchRowAction.DIVIDER -> null
        MemberSearchRowAction.ADD_MEMBERS -> memberActionItem(
          sourceIndex = item.sourceIndex,
          source = source,
          label = context.getString(R.string.AddMembersActivity__add_members)
        )
        MemberSearchRowAction.INVITE_VIA -> memberActionItem(
          sourceIndex = item.sourceIndex,
          source = source,
          label = context.getString(R.string.MemberSearchFragment__invite_via)
        )
        null -> item
      }
      is ContactSearchData.KnownRecipient -> if (source.showAdminLabel) {
        item.withDetail(context.getString(R.string.GroupRecipientListItem_admin).uppercase(Locale.getDefault()))
      } else {
        item
      }
      else -> item
    }
  }

  return LightContactState(
    rows = rows,
    selectionMode = false,
    hasSnippets = rows.any { it.snippet.isNotEmpty() }
  )
}

private fun memberActionItem(
  sourceIndex: Int,
  source: ContactSearchData.Arbitrary,
  label: String
): LightContactItem {
  return LightContactItem(
    key = "member-action:${source.type}",
    sourceIndex = sourceIndex,
    kind = LightContactItem.Kind.ACTION,
    name = label.uppercase(Locale.getDefault()),
    detail = "",
    selected = false,
    enabled = true,
    action = null,
    data = source
  )
}

private fun LightContactItem.withDetail(newDetail: String): LightContactItem {
  return LightContactItem(
    key = key,
    sourceIndex = sourceIndex,
    kind = kind,
    name = name,
    detail = newDetail,
    snippet = snippet,
    selected = selected,
    enabled = enabled,
    action = action,
    data = data
  )
}

private const val MEMBER_ROW_HEIGHT_UNITS = 4.5f
private const val MEMBER_MARKER_SLOT_UNITS = 1f
private const val MEMBER_COLUMN_GAP_UNITS = 0.25f
private const val MEMBER_ROW_SIDE_UNITS = 0.5f
