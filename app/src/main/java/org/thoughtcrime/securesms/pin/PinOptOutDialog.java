package org.thoughtcrime.securesms.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.light.LightConfirmAction;
import org.thoughtcrime.securesms.light.LightConfirmDialog;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.Arrays;

public final class PinOptOutDialog {

  private static final String TAG = Log.tag(PinOptOutDialog.class);

  /**
   * @param rotateAep If true, this will rotate the AEP as part of the process of opting out. Only do this if the user has not enabled backups! If the user
   *    has backups enabled, you should guide them through rotating the AEP first, and then call this with [rotateAep] = false.
   */
  public static void show(@NonNull Context context, @NonNull LifecycleOwner lifecycleOwner, boolean rotateAep, @NonNull Runnable onSuccess) {
    Log.i(TAG, "show()");

    LightConfirmDialog dialog = LightConfirmDialog.create(
        context,
        context.getString(R.string.PinOptOutDialog_warning),
        context.getString(R.string.PinOptOutDialog_if_you_disable_the_pin_you_will_lose_all_data)
    );

    // Order is the design language's: the destructive action last, and clear of the ordinary one.
    // A notice that offered "disable PIN" above "cancel" would put the row that loses the user's
    // backup one row above the row that does nothing.
    dialog.setActions(Arrays.asList(
        new LightConfirmAction(context.getString(android.R.string.cancel), () -> {
          Log.i(TAG, "Cancel clicked.");
          dialog.dismiss();
        }),
        new LightConfirmAction(context.getString(R.string.PinOptOutDialog_disable_pin), () -> {
          Log.i(TAG, "Disable clicked.");

          // Dismissed before the work starts, not after. This notice has no busy state -- the
          // progress belongs to the opt-out, not to the confirmation -- and a window left standing
          // with inert rows while a spinner runs over it is worse than the spinner alone.
          dialog.dismiss();

          var progress = SimpleProgressDialog.show(context);

          SimpleTask.run(lifecycleOwner.getLifecycle(), () -> {
            SvrRepository.optOutOfPin(rotateAep);
            return null;
          }, success -> {
            Log.i(TAG, "Disable operation finished.");
            onSuccess.run();
            progress.dismiss();
          });
        })
    ));

    dialog.show();
  }
}
