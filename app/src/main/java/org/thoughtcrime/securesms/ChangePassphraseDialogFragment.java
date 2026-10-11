package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.PassphraseValidator;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.passphrase.ChangePassphraseContent;
import org.thoughtcrime.securesms.passphrase.ChangePassphraseForm;
import org.thoughtcrime.securesms.passphrase.ChangePassphraseFormState;
import org.thoughtcrime.securesms.passphrase.NewPassphraseEntry;
import org.thoughtcrime.securesms.util.WindowUtil;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Sets, changes or removes the database passphrase.
 *
 * LIGHT PHONE: still a DialogFragment with the same factory, modes and listener, but shown as a
 * full-screen Light form (see {@link ChangePassphraseContent}) instead of a Material alert dialog.
 * Everything that decides what a submit does is unchanged and lives here.
 */
public class ChangePassphraseDialogFragment extends DialogFragment {

  private static final String TAG = Log.tag(ChangePassphraseDialogFragment.class);

  private static final String KEY_MODE = "mode";

  public static final int MODE_CHANGE  = 0;
  public static final int MODE_ENABLE  = 1;
  public static final int MODE_DISABLE = 2;

  private int mode = MODE_CHANGE;

  private MasterSecretChangedListener listener;

  private PassphraseValidator validator;
  private CountDownLatch      validatorReady;

  private final ChangePassphraseFormState formState = new ChangePassphraseFormState();
  private AsyncTask                       masterSecretTask;

  public static ChangePassphraseDialogFragment newInstance() {
    return new ChangePassphraseDialogFragment();
  }

  public static ChangePassphraseDialogFragment newInstance(int mode) {
    ChangePassphraseDialogFragment frag = new ChangePassphraseDialogFragment();
    Bundle args = new Bundle();
    args.putInt(KEY_MODE, mode);
    frag.setArguments(args);
    return frag;
  }

  public void setMasterSecretChangedListener(final MasterSecretChangedListener listener) {
    this.listener = listener;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Full screen, no frame, on the same style the app's other full-screen dialogs use.
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen);

    if (getArguments() != null) {
      mode = getArguments().getInt(KEY_MODE);
    }

    if (mode == MODE_ENABLE || mode == MODE_CHANGE) {
      initializePassphraseValidator();
    }
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return ChangePassphraseContent.create(requireContext(),
                                          ChangePassphraseForm.forMode(mode),
                                          formState,
                                          this::onBackClicked,
                                          this::onOkClicked);
  }

  @Override
  public void onStart() {
    super.onStart();

    Window window = requireDialog().getWindow();
    if (window != null) {
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
      // The form sits above the LP3 system keyboard rather than under it.
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    // A dialog is its own window and does not inherit the activity's FLAG_SECURE.
    WindowUtil.initializeScreenshotSecurity(requireContext(), requireDialog().getWindow());
  }

  @Override
  public void onDetach() {
    super.onDetach();
    listener = null;
  }

  @Override
  public void onCancel(@NonNull DialogInterface dialog) {
    super.onCancel(dialog);

    if (masterSecretTask != null) {
      masterSecretTask.cancel(false);
    }
  }

  /** The top bar's back: the same cancel as system back, which the dialog already routes to {@link #onCancel}. */
  private void onBackClicked() {
    Dialog dialog = getDialog();
    if (dialog != null && isCancelable() && !formState.getInProgress()) {
      dialog.cancel();
    }
  }

  private void onOkClicked() {
    if (formState.getInProgress()) return;

    if (mode == MODE_ENABLE) {
      handleEnable();
    } else if (mode == MODE_CHANGE) {
      handleChange();
    } else if (mode == MODE_DISABLE) {
      handleDisable();
    }
  }

  private void initializePassphraseValidator() {
    validatorReady = new CountDownLatch(1);

    SignalExecutors.BOUNDED.execute(() -> {
      validator = new PassphraseValidator(Locale.getDefault());
      validatorReady.countDown();
    });
  }

  private boolean isPassphraseValidatorReady() {
    try {
      validatorReady.await();
    } catch (InterruptedException ie) {
      Log.w(TAG, ie);
      return false;
    }
    return true;
  }

  private void showProgress(boolean show) {
    formState.setInProgress(show);
  }

  private void handleEnable() {
    changePassphrase(MasterSecretUtil.getUnencryptedPassphrase());
  }

  private void handleChange() {
    final char[] oldPassphrase = ChangePassphraseFormState.copyOf(formState.getCurrentPassphrase());

    if (oldPassphrase.length > 0) {
      changePassphrase(oldPassphrase);
    } else {
      formState.setCurrentPassphraseError(getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation));
    }
  }

  private void handleDisable() {
    final char[] oldPassphrase = ChangePassphraseFormState.copyOf(formState.getCurrentPassphrase());

    if (oldPassphrase.length > 0) {
      changeMasterSecret(MasterSecretUtil.getUnencryptedPassphrase(), oldPassphrase);
    } else {
      formState.setCurrentPassphraseError(getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation));
    }
  }

  // LIGHT PHONE: the checks, their order and their messages are upstream's. Only where a message
  // appears has changed: under the field it is about, rather than in a toast or a second dialog.
  private void changePassphrase(final char[] oldPassphrase) {
    final char[] newPassphrase    = ChangePassphraseFormState.copyOf(formState.getNewPassphrase());
    final char[] repeatPassphrase = ChangePassphraseFormState.copyOf(formState.getRepeatPassphrase());

    NewPassphraseEntry entry = NewPassphraseEntry.check(newPassphrase, repeatPassphrase);
    Arrays.fill(repeatPassphrase, (char) 0);

    if (entry == NewPassphraseEntry.EMPTY) {
      formState.setNewPassphraseError(getString(R.string.PassphraseChangeActivity_enter_new_passphrase_exclamation));
      return;
    } else if (entry == NewPassphraseEntry.MISMATCH) {
      formState.setRepeatPassphraseError(getString(R.string.PassphraseChangeActivity_passphrases_dont_match_exclamation));
      return;
    }

    if (!isPassphraseValidatorReady()) return;

    PassphraseValidator.Strength strength = validator.estimate(newPassphrase);
    if (!strength.isValid()) {
      String body = getString(R.string.ChangePassphraseDialogFragment_estimated_time_to_crack_suggestion,
              strength.getError(), strength.getTimeToCrack(), strength.getSuggestion());
      formState.setNewPassphraseError(getString(R.string.ChangePassphraseDialogFragment_weak_passphrase) + "\n\n" + body);
      return;
    }

    changeMasterSecret(newPassphrase, oldPassphrase);
  }

  private void changeMasterSecret(char[] newPassphrase, char[] oldPassphrase) {
    ChangeMasterSecretTask task = new ChangeMasterSecretTask(newPassphrase, oldPassphrase);
    masterSecretTask = task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public interface MasterSecretChangedListener {
    void onMasterSecretChanged(MasterSecret masterSecret);
  }

  @SuppressLint("StaticFieldLeak")
  private class ChangeMasterSecretTask extends AsyncTask<Void, Void, MasterSecret> {

    private final Context context;
    private final char[]  newPassphrase;
    private final char[]  oldPassphrase;

    ChangeMasterSecretTask(char[] newPassphrase, char[] oldPassphrase) {
      this.context       = requireContext().getApplicationContext();
      this.newPassphrase = newPassphrase;
      this.oldPassphrase = oldPassphrase;
    }

    @Override
    protected void onPreExecute() {
      formState.clearErrors();
      showProgress(true);
    }

    @Override
    protected MasterSecret doInBackground(Void... voids) {
      setCancelable(false);

      if (isCancelled()) {
        Log.d(TAG, "ChangeMasterSecretTask was canceled");
        return null;
      }

      MasterSecret masterSecret = null;

      if (!MasterSecretUtil.isPassphraseInitialized(context)) {
        masterSecret = MasterSecretUtil.generateMasterSecret(context, newPassphrase);
      } else {
        try {
          masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(context, oldPassphrase, newPassphrase);
        } catch (InvalidPassphraseException | UnrecoverableKeyException e) {
          Log.d(TAG, e);
        }
      }

      Arrays.fill(oldPassphrase, (char) 0);
      Arrays.fill(newPassphrase, (char) 0);

      return masterSecret;
    }

    @Override
    protected void onPostExecute(MasterSecret masterSecret) {
      if (masterSecret != null) {
        if (listener != null) {
          listener.onMasterSecretChanged(masterSecret);
        }
        dismissAllowingStateLoss();
      } else {
        showProgress(false);
        setCancelable(true);
        // As before, only a mode with a current passphrase field has somewhere to show this.
        if (mode != MODE_ENABLE && isAdded()) {
          formState.setCurrentPassphraseError(getString(R.string.PassphrasePromptActivity_invalid_passphrase_exclamation));
        }
      }
    }
  }
}
