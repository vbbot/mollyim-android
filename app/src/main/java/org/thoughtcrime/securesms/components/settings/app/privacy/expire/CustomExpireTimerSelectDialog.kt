package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.livedata.observeAsState
import androidx.navigation.fragment.NavHostFragment
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightThemeTokens
import org.signal.core.ui.compose.ComposeDialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.LightSingleChoiceDialog
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.concurrent.TimeUnit

/**
 * Dialog for selecting a custom expire timer value.
 */
class CustomExpireTimerSelectDialog : ComposeDialogFragment() {

  private val viewModel: ExpireTimerSettingsViewModel by lazy(LazyThreadSafetyMode.NONE) {
    ViewModelProvider(NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.app_settings_expire_timer))
      .get(ExpireTimerSettingsViewModel::class.java)
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.apply {
      setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
  }

  @Composable
  override fun DialogContent() {
    val state by viewModel.state.observeAsState(ExpireTimerSettingsState())

    CustomExpireTimerScreen(
      initialTimer = state.currentTimer,
      onSet = {
        viewModel.select(it)
        dismiss()
      },
      onCancel = ::dismiss
    )
  }

  companion object {
    private const val DIALOG_TAG = "CustomTimerSelectDialog"

    fun show(fragmentManager: FragmentManager) {
      CustomExpireTimerSelectDialog().show(fragmentManager, DIALOG_TAG)
    }
  }
}

@Composable
private fun CustomExpireTimerScreen(
  initialTimer: Int,
  onSet: (Int) -> Unit,
  onCancel: () -> Unit
) {
  val initialSelection = TimerSelection.fromSeconds(initialTimer)
  var amount by rememberSaveable(initialTimer) { mutableStateOf(initialSelection.amount) }
  var unitIndex by rememberSaveable(initialTimer) { mutableStateOf(initialSelection.unit.ordinal) }
  var showAmountChoices by rememberSaveable { mutableStateOf(false) }
  var showUnitChoices by rememberSaveable { mutableStateOf(false) }
  val unit = TimerUnit.entries[unitIndex]
  val context = LocalContext.current
  val unitLabels = context.resources.getStringArray(R.array.CustomExpireTimerSelectorView__unit_labels)
  val amountChoices = (unit.minValue..unit.maxValue).map(Int::toString)
  val cancelLabel = stringResource(android.R.string.cancel)
  val setLabel = stringResource(R.string.ExpireTimerSettingsFragment__set)

  MollyLightTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      LightSettingsScreen(
        title = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
        onBack = onCancel,
        modifier = Modifier.weight(1f)
      ) {
        LightSettingsValueRow(
          title = unitLabels[unitIndex],
          value = amount.toString(),
          valueContentDescription = "$amount ${unitLabels[unitIndex]}",
          onClick = { showAmountChoices = true }
        )
        LightSettingsValueRow(
          title = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
          value = unitLabels[unitIndex],
          valueContentDescription = unitLabels[unitIndex],
          onClick = { showUnitChoices = true }
        )
      }

      LightBottomBar(
        items = listOf(
          LightBarButton.Text(cancelLabel.uppercase(), onClick = onCancel),
          LightBarButton.Text(
            setLabel.uppercase(),
            onClick = { onSet(amount * unit.valueMultiplier.toInt()) }
          )
        )
      )
    }

    if (showAmountChoices) {
      LightSingleChoiceDialog(
        title = unitLabels[unitIndex],
        items = amountChoices,
        selected = amount - unit.minValue,
        confirmAction = false,
        onSelected = { amount = it + unit.minValue },
        onDismiss = { showAmountChoices = false }
      )
    }

    if (showUnitChoices) {
      LightSingleChoiceDialog(
        title = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
        items = unitLabels.toList(),
        selected = unitIndex,
        confirmAction = false,
        onSelected = { selectedIndex ->
          unitIndex = selectedIndex
          val selectedUnit = TimerUnit.entries[selectedIndex]
          amount = amount.coerceIn(selectedUnit.minValue, selectedUnit.maxValue)
        },
        onDismiss = { showUnitChoices = false }
      )
    }
  }
}

private data class TimerSelection(
  val amount: Int,
  val unit: TimerUnit
) {
  companion object {
    fun fromSeconds(timer: Int): TimerSelection {
      if (timer <= 0) {
        return TimerSelection(amount = 1, unit = TimerUnit.MINUTES)
      }

      val unit = TimerUnit.entries.firstOrNull {
        timer / it.valueMultiplier in it.minValue.toLong()..it.maxValue.toLong()
      } ?: return TimerSelection(amount = 1, unit = TimerUnit.MINUTES)

      return TimerSelection(
        amount = (timer / unit.valueMultiplier).toInt().coerceIn(unit.minValue, unit.maxValue),
        unit = unit
      )
    }
  }
}

private enum class TimerUnit(val minValue: Int, val maxValue: Int, val valueMultiplier: Long) {
  SECONDS(1, 59, TimeUnit.SECONDS.toSeconds(1)),
  MINUTES(1, 59, TimeUnit.MINUTES.toSeconds(1)),
  HOURS(1, 23, TimeUnit.HOURS.toSeconds(1)),
  DAYS(1, 6, TimeUnit.DAYS.toSeconds(1)),
  WEEKS(1, 4, TimeUnit.DAYS.toSeconds(7))
}
