package org.thoughtcrime.securesms.pin;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.light.LightConfirmAction;
import org.thoughtcrime.securesms.light.LightConfirmDialog;

import java.io.IOException;
import java.util.Arrays;

public final class RegistrationLockV2Dialog {

  private static final String TAG = Log.tag(RegistrationLockV2Dialog.class);

  private RegistrationLockV2Dialog() {}

  public static void showEnableDialog(@NonNull Context context, @NonNull Runnable onDismiss) {
    show(
        context,
        context.getString(R.string.RegistrationLockV2Dialog_turn_on_registration_lock),
        context.getString(R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again),
        context.getString(R.string.RegistrationLockV2Dialog_turn_on),
        "Successfully enabled registration lock.",
        SvrRepository::enableRegistrationLockForUserWithPin,
        R.string.preferences_app_protection__failed_to_enable_registration_lock,
        onDismiss
    );
  }

  public static void showDisableDialog(@NonNull Context context, @NonNull Runnable onDismiss) {
    show(
        context,
        context.getString(R.string.RegistrationLockV2Dialog_turn_off_registration_lock),
        context.getString(R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again),
        context.getString(R.string.RegistrationLockV2Dialog_turn_off),
        "Successfully disabled registration lock.",
        SvrRepository::disableRegistrationLockForUserWithPin,
        R.string.preferences_app_protection__failed_to_disable_registration_lock,
        onDismiss
    );
  }

  /**
   * The enable and disable confirmations, which differ only in their labels and their outcome.
   *
   * <p>Both used to be a {@code MaterialAlertDialogBuilder} whose positive button carried the whole
   * behaviour: it turned a spinner on, ran a network call, turned the spinner off, toasted on
   * failure, and dismissed. That is now the notice's own busy state, because the work belongs to
   * the confirmation -- the user pressed a row on this surface and is waiting on this surface --
   * and a separate spinner floating over a live dialog was only ever a second way of saying
   * "working". One behavioural change comes with it, and it is a deliberate one: the rows go inert
   * while the call is in flight, where the old dialog left its button live and would have run the
   * request again on a second tap.
   *
   * <p>The dismiss callback is wired to the window rather than to the task, so a user who backs out
   * mid-request still gets it, exactly as the old {@code setOnDismissListener} did.
   */
  private static void show(@NonNull Context context,
                           @NonNull String title,
                           @NonNull String message,
                           @NonNull String confirmLabel,
                           @NonNull String successLog,
                           @NonNull IoAction action,
                           int failureMessage,
                           @NonNull Runnable onDismiss)
  {
    LightConfirmDialog dialog = LightConfirmDialog.create(context, title, message);

    // The destructive one -- turning the lock off -- is last and separated, per the design
    // language's ordering rule. With two rows there is nothing to separate them with, so the order
    // is the whole of it.
    dialog.setActions(Arrays.asList(
        new LightConfirmAction(context.getString(android.R.string.cancel), dialog::dismiss),
        new LightConfirmAction(confirmLabel, () -> run(context, dialog, successLog, action, failureMessage))
    ));

    dialog.setOnDismissListener(onDismiss);
    dialog.show();
  }

  private static void run(@NonNull Context context,
                          @NonNull LightConfirmDialog dialog,
                          @NonNull String successLog,
                          @NonNull IoAction action,
                          int failureMessage)
  {
    dialog.setBusy(true);

    SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
      try {
        action.run();
        Log.i(TAG, successLog);
        return true;
      } catch (IOException e) {
        Log.w(TAG, "Failed to change the registration lock setting.", e);
        return false;
      }
    }, success -> {
      if (!success) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show();
      }

      dialog.dismiss();
    });
  }

  /** One of the two repository calls, which are {@code @WorkerThread} and throw {@link IOException}. */
  private interface IoAction {
    void run() throws IOException;
  }
}
