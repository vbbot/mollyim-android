package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.EditNotificationProfileScheduleViewModel.SaveScheduleResult
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.formatHours
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.orderOfDaysInWeek
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_TO_STARTING_LETTER: Map<DayOfWeek, Int> = mapOf(
  DayOfWeek.SUNDAY to R.string.EditNotificationProfileSchedule__sunday_first_letter,
  DayOfWeek.MONDAY to R.string.EditNotificationProfileSchedule__monday_first_letter,
  DayOfWeek.TUESDAY to R.string.EditNotificationProfileSchedule__tuesday_first_letter,
  DayOfWeek.WEDNESDAY to R.string.EditNotificationProfileSchedule__wednesday_first_letter,
  DayOfWeek.THURSDAY to R.string.EditNotificationProfileSchedule__thursday_first_letter,
  DayOfWeek.FRIDAY to R.string.EditNotificationProfileSchedule__friday_first_letter,
  DayOfWeek.SATURDAY to R.string.EditNotificationProfileSchedule__saturday_first_letter
)

/**
 * Can edit existing or use during create flow to setup a profile schedule.
 */
class EditNotificationProfileScheduleFragment : ComposeFragment() {

  private val viewModel: EditNotificationProfileScheduleViewModel by viewModels(factoryProducer = { EditNotificationProfileScheduleViewModel.Factory(profileId, createMode) })
  private val lifecycleDisposable = LifecycleDisposable()

  private val profileId: Long by lazy { EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments()).profileId }
  private val createMode: Boolean by lazy { EditNotificationProfileScheduleFragmentArgs.fromBundle(requireArguments()).createMode }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
  }

  @Composable
  override fun FragmentContent() {
    val schedule by remember(viewModel) { viewModel.schedule().asFlow() }
      .collectAsStateWithLifecycle(initialValue = null)
    var saving by remember { mutableStateOf(false) }

    ScheduleContent(
      schedule = schedule,
      createMode = createMode,
      saving = saving,
      onBack = { findNavController().navigateUp() },
      onEnabledChanged = viewModel::setEnabled,
      onDayChanged = viewModel::toggleDay,
      onStartTimeClick = { time -> showTimeSelector(true, time) },
      onEndTimeClick = { time -> showTimeSelector(false, time) },
      onSave = {
        if (!saving) {
          saving = true
          lifecycleDisposable += viewModel.save(createMode)
            .doFinally { saving = false }
            .subscribeBy(
              onSuccess = { result ->
                when (result) {
                  SaveScheduleResult.Success -> {
                    if (createMode) {
                      findNavController().safeNavigate(EditNotificationProfileScheduleFragmentDirections.actionEditNotificationProfileScheduleFragmentToNotificationProfileCreatedFragment(profileId))
                    } else {
                      findNavController().navigateUp()
                    }
                  }
                  SaveScheduleResult.NoDaysSelected -> {
                    Toast.makeText(requireContext(), R.string.EditNotificationProfileSchedule__schedule_must_have_at_least_one_day, Toast.LENGTH_LONG).show()
                  }
                }
              }
            )
        }
      }
    )
  }

  private fun showTimeSelector(isStart: Boolean, time: LocalTime) {
    val timeFormat = if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
    val timePickerFragment = MaterialTimePicker.Builder()
      .setTimeFormat(timeFormat)
      .setHour(time.hour)
      .setMinute(time.minute)
      .setTitleText(if (isStart) R.string.EditNotificationProfileSchedule__set_start_time else R.string.EditNotificationProfileSchedule__set_end_time)
      .build()

    timePickerFragment.addOnDismissListener {
      timePickerFragment.clearOnDismissListeners()
      timePickerFragment.clearOnPositiveButtonClickListeners()
    }

    timePickerFragment.addOnPositiveButtonClickListener {
      val hour = timePickerFragment.hour
      val minute = timePickerFragment.minute

      if (isStart) {
        viewModel.setStartTime(hour, minute)
      } else {
        viewModel.setEndTime(hour, minute)
      }
    }

    timePickerFragment.show(childFragmentManager, "TIME_PICKER")
  }
}

@Composable
private fun ScheduleContent(
  schedule: NotificationProfileSchedule?,
  createMode: Boolean,
  saving: Boolean,
  onBack: () -> Unit,
  onEnabledChanged: (Boolean) -> Unit,
  onDayChanged: (DayOfWeek) -> Unit,
  onStartTimeClick: (LocalTime) -> Unit,
  onEndTimeClick: (LocalTime) -> Unit,
  onSave: () -> Unit
) {
  org.thoughtcrime.securesms.light.MollyLightTheme {
    org.thoughtcrime.securesms.light.LightSettingsScreen(
      title = if (createMode) "" else androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__schedule),
      onBack = onBack
    ) {
      if (createMode) {
        org.thoughtcrime.securesms.light.LightSettingsSection(
          androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__add_a_schedule)
        )
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__set_up_a_schedule_to_enable_this_notification_profile_automatically),
          onClick = null
        )
      }

      if (schedule == null) {
        org.thoughtcrime.securesms.light.LightSettingsProgressRow(
          title = androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__schedule)
        )
      } else {
        org.thoughtcrime.securesms.light.LightSettingsToggleRow(
          title = androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__schedule),
          checked = schedule.enabled,
          enabled = !saving,
          onCheckedChange = onEnabledChanged
        )

        org.thoughtcrime.securesms.light.LightSettingsSection(
          androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__start)
        )
        val startTime = schedule.startTime().formatTime(androidx.compose.ui.platform.LocalContext.current)
        org.thoughtcrime.securesms.light.LightSettingsValueRow(
          title = androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__start),
          value = startTime,
          valueContentDescription = startTime,
          enabled = schedule.enabled && !saving,
          onClick = { onStartTimeClick(schedule.startTime()) }
        )

        val endTime = schedule.endTime().formatTime(androidx.compose.ui.platform.LocalContext.current)
        org.thoughtcrime.securesms.light.LightSettingsValueRow(
          title = androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__end),
          value = endTime,
          valueContentDescription = endTime,
          enabled = schedule.enabled && !saving,
          onClick = { onEndTimeClick(schedule.endTime()) }
        )

        org.thoughtcrime.securesms.light.LightSettingsSection(
          androidx.compose.ui.res.stringResource(R.string.EditNotificationProfileSchedule__schedule)
        )
        Locale.getDefault().orderOfDaysInWeek().forEach { day ->
          org.thoughtcrime.securesms.light.LightSettingsToggleRow(
            title = androidx.compose.ui.res.stringResource(DAY_TO_STARTING_LETTER.getValue(day)),
            checked = schedule.daysEnabled.contains(day),
            enabled = schedule.enabled && !saving,
            onCheckedChange = { onDayChanged(day) }
          )
        }

        org.thoughtcrime.securesms.light.LightSettingsDivider()
        if (saving) {
          org.thoughtcrime.securesms.light.LightSettingsProgressRow(
            title = saveLabel(createMode, schedule.enabled)
          )
        } else {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = saveLabel(createMode, schedule.enabled),
            onClick = onSave
          )
        }
      }
    }
  }
}

@Composable
private fun saveLabel(createMode: Boolean, scheduleEnabled: Boolean): String {
  val label = when {
    !createMode -> R.string.EditNotificationProfileSchedule__save
    scheduleEnabled -> R.string.EditNotificationProfileSchedule__next
    else -> R.string.EditNotificationProfileSchedule__skip
  }
  return androidx.compose.ui.res.stringResource(label)
}

private fun LocalTime.formatTime(context: Context): SpannableString {
  val amPm = DateTimeFormatter.ofPattern("a")
    .format(this)

  val formattedTime: String = this.formatHours(context)

  return SpannableString(formattedTime).apply {
    val amPmIndex = formattedTime.indexOf(string = amPm, ignoreCase = true)
    if (amPmIndex != -1) {
      setSpan(AbsoluteSizeSpan(ViewUtil.spToPx(20f)), amPmIndex, amPmIndex + amPm.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
  }
}
