package org.thoughtcrime.securesms.devicetransfer;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.TransferStatus;
import org.signal.devicetransfer.WifiDirect;
import org.signal.core.ui.logging.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.signal.core.ui.permissions.Permissions;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for driving the UI of all the legwork to startup Wi-Fi Direct and
 * establish the connection between the two devices. It's capable of being used by both
 * the new and old device, but delegates some of the UI (mostly strings and navigation) to
 * a subclass for old or new device.
 * <p>
 * Handles showing setup progress, verification codes, connecting states, error states, and troubleshooting.
 * <p>
 * It's state driven by the view model so it's easy to transition from step to step in the
 * process.
 */
public abstract class DeviceTransferSetupFragment extends LoggingFragment {

  private static final String TAG = Log.tag(DeviceTransferSetupFragment.class);

  private static final long PREPARE_TAKING_TOO_LONG_TIME = TimeUnit.SECONDS.toMillis(30);
  private static final long WAITING_TAKING_TOO_LONG_TIME = TimeUnit.SECONDS.toMillis(90);

  private final OnBackPressed                onBackPressed = new OnBackPressed();
  private       DeviceTransferSetupViewModel viewModel;
  private       Runnable                     takingTooLong;

  public DeviceTransferSetupFragment() {
    super(R.layout.device_transfer_setup_fragment);
  }

  /**
   * Whether this side draws the Light setup screens ({@link DeviceTransferSetupLightView}) rather than
   * the original layout. The new device does; the old device -- the sending side, under Settings --
   * keeps the layout until that flow is converted as a whole.
   */
  protected boolean usesLightUi() {
    return false;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    if (usesLightUi()) {
      DeviceTransferSetupLightView lightView = new DeviceTransferSetupLightView(requireContext());
      lightView.setOnBackRequested(() -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
      return lightView;
    }
    return super.onCreateView(inflater, container, savedInstanceState);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    DeviceTransferSetupUi ui = view instanceof DeviceTransferSetupLightView ? (DeviceTransferSetupLightView) view
                                                                             : new LayoutSetupUi(view);

    viewModel = new ViewModelProvider(this).get(DeviceTransferSetupViewModel.class);

    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      SetupStep step = state.getCurrentSetupStep();

      ui.showStep(step);

      Log.i(TAG, "Handling step: " + step.name());
      switch (step) {
        case INITIAL:
          ui.setStatus("");
          // fall through so the INITIAL state checks permissions
        case PERMISSIONS_CHECK:
          requestRequiredPermission();
          break;
        case PERMISSIONS_DENIED:
          ui.showError(getString(getErrorTextForStep(step)), getString(R.string.DeviceTransferSetup__grant_location_permission), () -> viewModel.checkPermissions());
          break;
        case LOCATION_CHECK:
          verifyLocationEnabled();
          break;
        case LOCATION_DISABLED:
          ui.showError(getString(getErrorTextForStep(step)), getString(R.string.DeviceTransferSetup__turn_on_location_services), this::openLocationServices);
          break;
        case WIFI_CHECK:
          verifyWifiEnabled();
          break;
        case WIFI_DISABLED:
          ui.showError(getString(getErrorTextForStep(step)), getString(R.string.DeviceTransferSetup__turn_on_wifi), this::openWifiSettings);
          break;
        case WIFI_DIRECT_CHECK:
          verifyWifiDirectAvailable();
          break;
        case WIFI_DIRECT_UNAVAILABLE:
          ui.showError(getString(getErrorTextForStep(step)), getString(getErrorResolveButtonTextForStep(step)), this::navigateWhenWifiDirectUnavailable);
          break;
        case START:
          ui.setStatus(getString(getStatusTextForStep(SetupStep.SETTING_UP, false)));
          startTransfer();
          break;
        case SETTING_UP:
          ui.setStatus(getString(getStatusTextForStep(step, false)));
          startTakingTooLong(() -> ui.setStatus(getString(getStatusTextForStep(step, true))), PREPARE_TAKING_TOO_LONG_TIME);
          break;
        case WAITING:
          ui.setStatus(getString(getStatusTextForStep(step, false)));
          cancelTakingTooLong();
          startTakingTooLong(() -> {
            DeviceToDeviceTransferService.stop(requireContext());
            viewModel.onWaitingTookTooLong();
          }, WAITING_TAKING_TOO_LONG_TIME);
          break;
        case VERIFY:
          cancelTakingTooLong();
          ui.showVerify(String.format(Locale.US, "%07d", state.getAuthenticationCode()),
                        () -> {
                          DeviceToDeviceTransferService.setAuthenticationCodeVerified(requireContext(), true);
                          viewModel.onVerified();
                        },
                        () -> ui.confirmNumbersDoNotMatch(() -> {
                          EventBus.getDefault().unregister(this);
                          DeviceToDeviceTransferService.setAuthenticationCodeVerified(requireContext(), false);
                          DeviceToDeviceTransferService.stop(requireContext());
                          EventBus.getDefault().removeStickyEvent(TransferStatus.class);
                          navigateAwayFromTransfer();
                        }));
          break;
        case WAITING_FOR_OTHER_TO_VERIFY:
          break;
        case CONNECTED:
          Log.d(TAG, "Connected! isNotShutdown: " + viewModel.isNotShutdown());
          if (viewModel.isNotShutdown()) {
            navigateToTransferConnected();
          }
          break;
        case TROUBLESHOOTING:
          ui.showTroubleshooting(getString(getStatusTextForStep(step, false)),
                                 this::openApplicationSystemSettings,
                                 this::openLocationServices,
                                 this::openWifiSettings,
                                 this::openWifiDirectSettings,
                                 this::gotoSupport,
                                 () -> viewModel.checkPermissions());
          break;
        case ERROR:
          ui.showError(getString(getErrorTextForStep(step)), getString(R.string.DeviceTransferSetup__retry), () -> viewModel.checkPermissions());
          DeviceToDeviceTransferService.stop(requireContext());
          cancelTakingTooLong();
          ui.showErrorConnecting(getString(getStatusTextForStep(step, false)),
                                 () -> viewModel.checkPermissions(),
                                 () -> {
                                   EventBus.getDefault().unregister(this);
                                   EventBus.getDefault().removeStickyEvent(TransferStatus.class);
                                   navigateAwayFromTransfer();
                                 },
                                 () -> {
                                   EventBus.getDefault().unregister(this);
                                   EventBus.getDefault().removeStickyEvent(TransferStatus.class);
                                   navigateAwayFromTransfer();
                                   startActivity(new Intent(requireContext(), SubmitDebugLogActivity.class));
                                 });
          break;
      }
    });
  }

  protected abstract @StringRes int getStatusTextForStep(@NonNull SetupStep step, boolean takingTooLongInStep);

  protected abstract @StringRes int getErrorTextForStep(@NonNull SetupStep step);

  protected abstract @StringRes int getErrorResolveButtonTextForStep(@NonNull SetupStep step);

  protected abstract void navigateWhenWifiDirectUnavailable();

  protected abstract void startTransfer();

  protected abstract void navigateToTransferConnected();

  protected abstract void navigateAwayFromTransfer();

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressed);

    TransferStatus event = EventBus.getDefault().getStickyEvent(TransferStatus.class);
    if (event == null) {
      viewModel.checkPermissions();
    } else {
      Log.i(TAG, "Sticky event already exists for transfer, assuming service is running and we are reattaching");
    }

    EventBus.getDefault().register(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.onResume();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onDestroyView() {
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    cancelTakingTooLong();
    EventBus.getDefault().unregister(this);
    super.onDestroyView();
  }

  private void requestRequiredPermission() {
    Permissions.with(this)
               .request(WifiDirect.requiredPermission())
               .ifNecessary()
               .withRationaleDialog(getString(getErrorTextForStep(SetupStep.PERMISSIONS_DENIED)), false, R.drawable.symbol_location_white_24)
               .withPermanentDenialDialog(getString(getErrorTextForStep(SetupStep.PERMISSIONS_DENIED)))
               .onAllGranted(() -> viewModel.onPermissionsGranted())
               .onAnyDenied(() -> viewModel.onLocationPermissionDenied())
               .execute();
  }

  private void openApplicationSystemSettings() {
    startActivity(Permissions.getApplicationSettingsIntent(requireContext()));
  }

  private void verifyLocationEnabled() {
    LocationManager locationManager = ContextCompat.getSystemService(requireContext(), LocationManager.class);
    if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      viewModel.onLocationEnabled();
    } else {
      viewModel.onLocationDisabled();
    }
  }

  private void openLocationServices() {
    try {
      startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "No location settings", e);
      Toast.makeText(requireContext(), R.string.DeviceTransferSetup__unable_to_open_wifi_settings, Toast.LENGTH_LONG).show();
    }
  }

  private void verifyWifiEnabled() {
    WifiManager wifiManager = ContextCompat.getSystemService(requireContext(), WifiManager.class);
    if (wifiManager != null && wifiManager.isWifiEnabled()) {
      viewModel.onWifiEnabled();
    } else {
      viewModel.onWifiDisabled(wifiManager == null);
    }
  }

  private void openWifiSettings() {
    try {
      startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "No wifi settings", e);
      Toast.makeText(requireContext(), R.string.DeviceTransferSetup__unable_to_open_wifi_settings, Toast.LENGTH_LONG).show();
    }
  }

  private void openWifiDirectSettings() {
    try {
      Intent wifiDirect = new Intent(Intent.ACTION_MAIN);
      wifiDirect.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setClassName("com.android.settings", "com.android.settings.Settings$WifiP2pSettingsActivity");

      startActivity(wifiDirect);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, "Unable to open wifi direct settings", e);
      openWifiSettings();
    }
  }

  private void verifyWifiDirectAvailable() {
    WifiDirect.AvailableStatus availability = WifiDirect.getAvailability(requireContext());
    if (availability != WifiDirect.AvailableStatus.AVAILABLE) {
      viewModel.onWifiDirectUnavailable(availability);
    } else {
      viewModel.onWifiDirectAvailable();
    }
  }

  private void gotoSupport() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.transfer_support_url));
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventMainThread(@NonNull TransferStatus event) {
    viewModel.onTransferEvent(event);
  }

  private void startTakingTooLong(@NonNull Runnable runnable, long tooLong) {
    if (takingTooLong == null) {
      takingTooLong = () -> {
        takingTooLong = null;
        runnable.run();
      };
      ThreadUtil.runOnMainDelayed(takingTooLong, tooLong);
    }
  }

  private void cancelTakingTooLong() {
    if (takingTooLong != null) {
      ThreadUtil.cancelRunnableOnMain(takingTooLong);
      takingTooLong = null;
    }
  }

  /**
   * The original layout, unchanged, behind {@link DeviceTransferSetupUi}. The old device still draws
   * this; every view and dialog here is exactly what the fragment used to set up inline.
   */
  private final class LayoutSetupUi implements DeviceTransferSetupUi {

    private final Group          progressGroup;
    private final Group          errorGroup;
    private final View           verifyGroup;
    private final View           waitingGroup;
    private final View           troubleshooting;
    private final TextView       status;
    private final TextView       error;
    private final MaterialButton errorResolve;
    private final TextView       sasNumber;
    private final MaterialButton verifyNo;
    private final MaterialButton verifyYes;

    LayoutSetupUi(@NonNull View view) {
      progressGroup   = view.findViewById(R.id.device_transfer_setup_fragment_progress_group);
      errorGroup      = view.findViewById(R.id.device_transfer_setup_fragment_error_group);
      verifyGroup     = view.findViewById(R.id.device_transfer_setup_fragment_verify);
      waitingGroup    = view.findViewById(R.id.device_transfer_setup_fragment_waiting);
      troubleshooting = view.findViewById(R.id.device_transfer_setup_fragment_troubleshooting);
      status          = view.findViewById(R.id.device_transfer_setup_fragment_status);
      error           = view.findViewById(R.id.device_transfer_setup_fragment_error);
      errorResolve    = view.findViewById(R.id.device_transfer_setup_fragment_error_resolve);
      sasNumber       = view.findViewById(R.id.device_transfer_setup_fragment_sas_verify_code);
      verifyNo        = view.findViewById(R.id.device_transfer_setup_fragment_sas_verify_no);
      verifyYes       = view.findViewById(R.id.device_transfer_setup_fragment_sas_verify_yes);
    }

    @Override
    public void showStep(@NonNull SetupStep step) {
      progressGroup.setVisibility(step.isProgress() ? View.VISIBLE : View.GONE);
      errorGroup.setVisibility(step.isError() ? View.VISIBLE : View.GONE);
      verifyGroup.setVisibility(step == SetupStep.VERIFY ? View.VISIBLE : View.GONE);
      waitingGroup.setVisibility(step == SetupStep.WAITING_FOR_OTHER_TO_VERIFY ? View.VISIBLE : View.GONE);
      troubleshooting.setVisibility(step == SetupStep.TROUBLESHOOTING ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setStatus(@NonNull CharSequence text) {
      status.setText(text);
    }

    @Override
    public void showError(@NonNull CharSequence message, @NonNull CharSequence actionLabel, @NonNull Runnable onAction) {
      error.setText(message);
      errorResolve.setText(actionLabel);
      errorResolve.setOnClickListener(v -> onAction.run());
    }

    @Override
    public void showVerify(@NonNull String code, @NonNull Runnable onContinue, @NonNull Runnable onNumbersDoNotMatch) {
      sasNumber.setText(code);
      verifyNo.setOnClickListener(v -> onNumbersDoNotMatch.run());
      verifyYes.setOnClickListener(v -> onContinue.run());
    }

    @Override
    public void showTroubleshooting(@NonNull CharSequence titleText,
                                    @NonNull Runnable onLocationPermission,
                                    @NonNull Runnable onLocationServices,
                                    @NonNull Runnable onWifi,
                                    @NonNull Runnable onWifiDirect,
                                    @NonNull Runnable onSupport,
                                    @NonNull Runnable onTryAgain)
    {
      TextView title = troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_title);
      title.setText(titleText);

      int gapWidth = ViewUtil.dpToPx(12);
      TextView step1 = troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_step1);
      step1.setText(SpanUtil.bullet(getString(R.string.DeviceTransferSetup__make_sure_the_following_permissions_are_enabled), gapWidth));
      TextView step2 = troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_step2);
      step2.setMovementMethod(LinkMovementMethod.getInstance());
      step2.setText(SpanUtil.clickSubstring(requireContext(),
                                            SpanUtil.bullet(getString(R.string.DeviceTransferSetup__on_the_wifi_direct_screen_remove_all_remembered_groups_and_unlink_any_invited_or_connected_devices), gapWidth),
                                            getString(R.string.DeviceTransferSetup__wifi_direct_screen),
                                            v -> onWifiDirect.run()));
      TextView step3 = troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_step3);
      step3.setText(SpanUtil.bullet(getString(R.string.DeviceTransferSetup__try_turning_wifi_off_and_on_on_both_devices), gapWidth));
      TextView step4 = troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_step4);
      step4.setText(SpanUtil.bullet(getString(R.string.DeviceTransferSetup__make_sure_both_devices_are_in_transfer_mode), gapWidth));

      troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_location_permission)
                     .setOnClickListener(v -> onLocationPermission.run());
      troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_location_services)
                     .setOnClickListener(v -> onLocationServices.run());
      troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_wifi)
                     .setOnClickListener(v -> onWifi.run());
      troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_go_to_support)
                     .setOnClickListener(v -> onSupport.run());
      troubleshooting.findViewById(R.id.device_transfer_setup_fragment_troubleshooting_try_again)
                     .setOnClickListener(v -> onTryAgain.run());
    }

    @Override
    public void confirmNumbersDoNotMatch(@NonNull Runnable onStopTransfer) {
      new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.DeviceTransferSetup__the_numbers_do_not_match)
                                                      .setMessage(R.string.DeviceTransferSetup__if_the_numbers_on_your_devices_do_not_match_its_possible_you_connected_to_the_wrong_device)
                                                      .setPositiveButton(R.string.DeviceTransferSetup__stop_transfer, (d, w) -> onStopTransfer.run())
                                                      .setNegativeButton(android.R.string.cancel, null)
                                                      .show();
    }

    @Override
    public void showErrorConnecting(@NonNull CharSequence message, @NonNull Runnable onRetry, @NonNull Runnable onCancel, @NonNull Runnable onSubmitDebugLogs) {
      new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.DeviceTransferSetup__error_connecting)
                                                      .setMessage(message)
                                                      .setPositiveButton(R.string.DeviceTransferSetup__retry, (d, w) -> onRetry.run())
                                                      .setNegativeButton(android.R.string.cancel, (d, w) -> onCancel.run())
                                                      .setNeutralButton(R.string.DeviceTransferSetup__submit_debug_logs, (d, w) -> onSubmitDebugLogs.run())
                                                      .setCancelable(false)
                                                      .show();
    }
  }

  private class OnBackPressed extends OnBackPressedCallback {

    public OnBackPressed() {
      super(true);
    }

    @Override
    public void handleOnBackPressed() {
      DeviceToDeviceTransferService.stop(requireContext());
      EventBus.getDefault().removeStickyEvent(TransferStatus.class);
      NavHostFragment.findNavController(DeviceTransferSetupFragment.this).popBackStack();
    }
  }
}
