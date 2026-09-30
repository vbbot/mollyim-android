/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val SEARCH_NAV_HEIGHT_UNITS = 4f
private const val SEARCH_NAV_SIDE_PADDING_UNITS = 2f
private const val SEARCH_NAV_ICON_SIZE_UNITS = 2f
private const val SEARCH_NAV_GAP_UNITS = 0.75f
private const val DATE_CELL_SIZE_UNITS = 2.5f

private fun Modifier.consumePointersBehindChrome(): Modifier {
  return pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false).consume()
      while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) break
      }
    }
  }
}

/** Immutable presentation state for the search navigation row. */
data class LightConversationSearchNavigationState(
  val position: Int = 0,
  val count: Int = 0,
  val loading: Boolean = false
) {
  init {
    require(count >= 0)
    require(position >= 0)
  }

  /** Signal's "up" action advances to the next older match in its result ordering. */
  val canMoveToPrevious: Boolean
    get() = !loading && count > 0 && position < count - 1

  /** Signal's "down" action returns to the next newer match. */
  val canMoveToNext: Boolean
    get() = !loading && count > 0 && position > 0
}

/**
 * The editable Light query row shown in place of the toolbar's AppCompat SearchView.
 *
 * [onSubmit] stays separate from [onQueryChange]: the former preserves
 * ConversationSearchViewModel's forced-submit path while typing still uses its debounce.
 */
@Composable
fun LightConversationSearchQuery(
  query: String,
  onQueryChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onBack: () -> Unit,
  incognitoKeyboard: Boolean,
  focusRequestVersion: Int,
  focusClearVersion: Int,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(focusRequestVersion) {
    if (focusRequestVersion > 0) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }
  LaunchedEffect(focusClearVersion) {
    if (focusClearVersion > 0) {
      focusManager.clearFocus(force = true)
    }
  }

  LightSearchField(
    query = query,
    onQueryChange = onQueryChange,
    hint = stringResource(R.string.SearchToolbar_search),
    focusRequester = focusRequester,
    onBack = onBack,
    onSubmit = onSubmit,
    incognitoKeyboard = incognitoKeyboard,
    modifier = modifier.fillMaxWidth()
  )
}

/** Light result count, date jump, and previous/next actions. */
@Composable
fun LightConversationSearchNavigation(
  state: LightConversationSearchNavigationState,
  onMoveToPrevious: () -> Unit,
  onMoveToNext: () -> Unit,
  onJumpToDate: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = LightThemeTokens.colors
  val resultText = when {
    state.loading -> stringResource(R.string.ConversationSearch__searching)
    state.count > 0 -> stringResource(R.string.ConversationActivity_search_position, state.position + 1, state.count)
    else -> stringResource(R.string.ConversationActivity_no_results)
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(SEARCH_NAV_HEIGHT_UNITS.gridUnitsAsDp())
      .background(colors.background)
      .consumePointersBehindChrome()
      .padding(horizontal = SEARCH_NAV_SIDE_PADDING_UNITS.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = painterResource(R.drawable.symbol_calendar_search_24),
      contentDescription = stringResource(R.string.ConversationSearch__jump_to_date),
      colorFilter = ColorFilter.tint(colors.content),
      modifier = Modifier
        .size(SEARCH_NAV_ICON_SIZE_UNITS.gridUnitsAsDp())
        .lightClickable(onClick = onJumpToDate)
    )

    LightText(
      text = resultText,
      variant = LightTextVariant.Fine,
      align = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    SearchNavigationIcon(
      icon = LightIcons.UP,
      contentDescription = stringResource(R.string.ConversationSearch__previous_result),
      enabled = state.canMoveToPrevious,
      onClick = onMoveToPrevious
    )
    Box(modifier = Modifier.size(SEARCH_NAV_GAP_UNITS.gridUnitsAsDp()))
    SearchNavigationIcon(
      icon = LightIcons.DOWN,
      contentDescription = stringResource(R.string.ConversationSearch__next_result),
      enabled = state.canMoveToNext,
      onClick = onMoveToNext
    )
  }
}

@Composable
private fun SearchNavigationIcon(
  icon: com.thelightphone.sdk.ui.LightIconConfiguration,
  contentDescription: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  LightIcon(
    icon = icon,
    size = SEARCH_NAV_ICON_SIZE_UNITS,
    contentDescription = contentDescription,
    modifier = Modifier
      .alpha(if (enabled) 1f else 0.25f)
      .semantics { if (!enabled) disabled() }
      .then(if (enabled) Modifier.lightClickable(onClick = onClick) else Modifier)
  )
}

/**
 * Immutable month/selection state for the app-owned Light date selector.
 * Bounds are local calendar dates; conversion to and from the Material picker timestamp contract is
 * kept at the host boundary by [materialDatePickerTimestamp].
 */
data class LightConversationDatePickerState(
  val earliestDate: LocalDate,
  val latestDate: LocalDate,
  val displayedMonth: YearMonth = YearMonth.from(latestDate),
  val selectedDate: LocalDate = latestDate
) {
  init {
    require(!earliestDate.isAfter(latestDate))
    require(!selectedDate.isBefore(earliestDate) && !selectedDate.isAfter(latestDate))
  }

  private val earliestMonth: YearMonth
    get() = YearMonth.from(earliestDate)

  private val latestMonth: YearMonth
    get() = YearMonth.from(latestDate)

  val canShowPreviousMonth: Boolean
    get() = displayedMonth.isAfter(earliestMonth)

  val canShowNextMonth: Boolean
    get() = displayedMonth.isBefore(latestMonth)

  fun showPreviousMonth(): LightConversationDatePickerState {
    return if (canShowPreviousMonth) copy(displayedMonth = displayedMonth.minusMonths(1)) else this
  }

  fun showNextMonth(): LightConversationDatePickerState {
    return if (canShowNextMonth) copy(displayedMonth = displayedMonth.plusMonths(1)) else this
  }

  fun select(date: LocalDate): LightConversationDatePickerState {
    return if (isInBounds(date)) copy(selectedDate = date) else this
  }

  fun isInBounds(date: LocalDate): Boolean {
    return !date.isBefore(earliestDate) && !date.isAfter(latestDate)
  }

  /** Always six weeks, so changing month never shifts the action row on the LP3 display. */
  fun dates(firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val first = displayedMonth.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    return List(42) { index ->
      val day = index - leading + 1
      if (day in 1..displayedMonth.lengthOfMonth()) displayedMonth.atDay(day) else null
    }
  }
}

/** MaterialDatePicker's date selection value is midnight UTC for the displayed calendar date. */
internal fun materialDatePickerTimestamp(date: LocalDate): Long {
  return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** App-owned, true-black date selector used by in-conversation search. */
@Composable
fun LightConversationDatePicker(
  state: LightConversationDatePickerState,
  isDateValid: (Long) -> Boolean,
  onStateChange: (LightConversationDatePickerState) -> Unit,
  onConfirm: (Long) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  locale: Locale = Locale.getDefault()
) {
  val selectedTimestamp = materialDatePickerTimestamp(state.selectedDate)
  val selectedIsValid = state.isInBounds(state.selectedDate) && isDateValid(selectedTimestamp)
  val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
  val daysOfWeek = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }
  val dateDescriptionFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale) }
  val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .consumePointersBehindChrome()
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.ConversationSearch__close_date_picker)
      ),
      center = LightTopBarCenter.Text(stringResource(R.string.ScheduleMessageTimePickerBottomSheet__select_date_title)),
      rightButton = if (selectedIsValid) {
        LightBarButton.Text(
          text = stringResource(R.string.ConversationSearch__select_date),
          onClick = { onConfirm(selectedTimestamp) }
        )
      } else {
        null
      }
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(3f.gridUnitsAsDp())
        .padding(horizontal = SEARCH_NAV_SIDE_PADDING_UNITS.gridUnitsAsDp()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      SearchNavigationIcon(
        icon = LightIcons.BACK,
        contentDescription = stringResource(R.string.ConversationSearch__previous_month),
        enabled = state.canShowPreviousMonth,
        onClick = { onStateChange(state.showPreviousMonth()) }
      )
      LightText(
        text = state.displayedMonth.format(monthFormatter),
        variant = LightTextVariant.Heading,
        align = TextAlign.Center,
        modifier = Modifier.weight(1f)
      )
      SearchNavigationIcon(
        icon = LightIcons.ARROW_RIGHT,
        contentDescription = stringResource(R.string.ConversationSearch__next_month),
        enabled = state.canShowNextMonth,
        onClick = { onStateChange(state.showNextMonth()) }
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center
    ) {
      daysOfWeek.forEach { dayOfWeek ->
        Box(
          modifier = Modifier.size(DATE_CELL_SIZE_UNITS.gridUnitsAsDp()),
          contentAlignment = Alignment.Center
        ) {
          LightText(
            text = dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
            variant = LightTextVariant.Superfine,
            lighten = true,
            align = TextAlign.Center
          )
        }
      }
    }

    state.dates(firstDayOfWeek).chunked(7).forEach { week ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
      ) {
        week.forEach { date ->
          if (date == null) {
            Box(modifier = Modifier.size(DATE_CELL_SIZE_UNITS.gridUnitsAsDp()))
          } else {
            val timestamp = materialDatePickerTimestamp(date)
            val enabled = state.isInBounds(date) && isDateValid(timestamp)
            val selected = date == state.selectedDate
            val colors = LightThemeTokens.colors
            Box(
              modifier = Modifier
                .size(DATE_CELL_SIZE_UNITS.gridUnitsAsDp())
                .alpha(if (enabled) 1f else 0.25f)
                .background(if (selected) colors.content else colors.background)
                .semantics {
                  contentDescription = date.format(dateDescriptionFormatter)
                  this.selected = selected
                  if (!enabled) disabled()
                }
                .then(
                  if (enabled) {
                    Modifier.lightClickable { onStateChange(state.select(date)) }
                  } else {
                    Modifier
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              LightText(
                text = date.dayOfMonth.toString(),
                variant = LightTextVariant.Fine,
                color = if (selected) colors.background else colors.content,
                align = TextAlign.Center
              )
            }
          }
        }
      }
    }
  }
}

/** View-land host for the Light query field. */
class LightConversationSearchQueryView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var query: String by mutableStateOf("")
  var incognitoKeyboard: Boolean by mutableStateOf(false)
  var onQueryChange: ((String) -> Unit)? by mutableStateOf<((String) -> Unit)?>(null)
  var onSubmit: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onBack: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  private var focusRequestVersion: Int by mutableIntStateOf(0)
  private var focusClearVersion: Int by mutableIntStateOf(0)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun requestSearchFocus() {
    focusRequestVersion++
  }

  fun clearSearchFocus() {
    focusClearVersion++
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConversationSearchQuery(
        query = query,
        onQueryChange = {
          query = it
          onQueryChange?.invoke(it)
        },
        onSubmit = { onSubmit?.invoke() },
        onBack = { onBack?.invoke() },
        incognitoKeyboard = incognitoKeyboard,
        focusRequestVersion = focusRequestVersion,
        focusClearVersion = focusClearVersion
      )
    }
  }
}

/** View-land host for the Light search navigation row. */
class LightConversationSearchNavigationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var state: LightConversationSearchNavigationState by mutableStateOf(LightConversationSearchNavigationState())
  var onMoveToPrevious: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onMoveToNext: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onJumpToDate: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun showLoading() {
    state = state.copy(loading = true)
  }

  fun setData(position: Int, count: Int) {
    state = LightConversationSearchNavigationState(position = position.coerceAtLeast(0), count = count.coerceAtLeast(0))
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConversationSearchNavigation(
        state = state,
        onMoveToPrevious = { onMoveToPrevious?.invoke() },
        onMoveToNext = { onMoveToNext?.invoke() },
        onJumpToDate = { onJumpToDate?.invoke() }
      )
    }
  }
}

/** Full-screen View-land host for the Light date selector. */
class LightConversationDatePickerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var pickerState: LightConversationDatePickerState? by mutableStateOf(null)
    private set
  var isDateValid: ((Long) -> Boolean)? by mutableStateOf<((Long) -> Boolean)?>(null)
  var onDateSelected: ((Long) -> Unit)? by mutableStateOf<((Long) -> Unit)?>(null)
  var onBack: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  val isOpen: Boolean
    get() = pickerState != null

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun show(earliestTimestamp: Long, latestDate: LocalDate, zoneId: ZoneId) {
    val earliestDate = Instant.ofEpochMilli(earliestTimestamp).atZone(zoneId).toLocalDate().coerceAtMost(latestDate)
    pickerState = LightConversationDatePickerState(
      earliestDate = earliestDate,
      latestDate = latestDate
    )
  }

  fun close() {
    pickerState = null
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      pickerState?.let { state ->
        LightConversationDatePicker(
          state = state,
          isDateValid = { isDateValid?.invoke(it) ?: true },
          onStateChange = { pickerState = it },
          onConfirm = { onDateSelected?.invoke(it) },
          onBack = { onBack?.invoke() }
        )
      }
    }
  }
}
