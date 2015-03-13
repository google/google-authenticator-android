/*
 * Copyright 2009 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.dataimport.ImportController;
import com.google.android.apps.authenticator.howitworks.IntroEnterPasswordActivity;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator2.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * The main activity that displays usernames and codes
 *
 * @author sweis@google.com (Steve Weis)
 * @author adhintz@google.com (Drew Hintz)
 * @author cemp@google.com (Cem Paya)
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AuthenticatorActivity extends TestableActivity {

  /** The tag for log messages */
  private static final String LOCAL_TAG = "AuthenticatorActivity";
  private static final long VIBRATE_DURATION = 200L;

  /** Frequency (milliseconds) with which TOTP countdown indicators are updated. */
  private static final long TOTP_COUNTDOWN_REFRESH_PERIOD = 100;

  /**
   * Minimum amount of time (milliseconds) that has to elapse from the moment a HOTP code is
   * generated for an account until the moment the next code can be generated for the account.
   * This is to prevent the user from generating too many HOTP codes in a short period of time.
   */
  private static final long HOTP_MIN_TIME_INTERVAL_BETWEEN_CODES = 5000;

  /**
   * The maximum amount of time (milliseconds) for which a HOTP code is displayed after it's been
   * generated.
   */
  private static final long HOTP_DISPLAY_TIMEOUT = 2 * 60 * 1000;

  // @VisibleForTesting
  static final int DIALOG_ID_UNINSTALL_OLD_APP = 12;

  // @VisibleForTesting
  static final int DIALOG_ID_SAVE_KEY = 13;

  /**
   * Intent action to that tells this Activity to initiate the scanning of barcode to add an
   * account.
   */
  // @VisibleForTesting
  static final String ACTION_SCAN_BARCODE =
      AuthenticatorActivity.class.getName() + ".ScanBarcode";

  private View mContentNoAccounts;
  private View mContentAccountsPresent;
  private TextView mEnterPinPrompt;
  private ListView mUserList;
  private PinListAdapter mUserAdapter;
  private PinInfo[] mUsers = {};

  /** Counter used for generating TOTP verification codes. */
  private TotpCounter mTotpCounter;

  /** Clock used for generating TOTP verification codes. */
  private TotpClock mTotpClock;

  /**
   * Task that periodically notifies this activity about the amount of time remaining until
   * the TOTP codes refresh. The task also notifies this activity when TOTP codes refresh.
   */
  private TotpCountdownTask mTotpCountdownTask;

  /**
   * Phase of TOTP countdown indicators. The phase is in {@code [0, 1]} with {@code 1} meaning
   * full time step remaining until the code refreshes, and {@code 0} meaning the code is refreshing
   * right now.
   */
  private double mTotpCountdownPhase;
  private AccountDb mAccountDb;
  private OtpSource mOtpProvider;

  /**
   * Key under which the {@link #mOldAppUninstallIntent} is stored in the instance state
   * {@link Bundle}.
   */
  private static final String KEY_OLD_APP_UNINSTALL_INTENT = "oldAppUninstallIntent";

  /**
   * {@link Intent} for uninstalling the "old" app or {@code null} if not known/available.
   *
   * <p>
   * Note: this field is persisted in the instance state {@link Bundle}. We need to resolve to this
   * error-prone mechanism because showDialog on Eclair doesn't take parameters. Once Froyo is
   * the minimum targetted SDK, this contrived code can be removed.
   */
  private Intent mOldAppUninstallIntent;

  /** Whether the importing of data from the "old" app has been started and has not yet finished. */
  private boolean mDataImportInProgress;

  /**
   * Key under which the {@link #mSaveKeyDialogParams} is stored in the instance state
   * {@link Bundle}.
   */
  private static final String KEY_SAVE_KEY_DIALOG_PARAMS = "saveKeyDialogParams";

  /**
   * Parameters to the save key dialog (DIALOG_ID_SAVE_KEY).
   *
   * <p>
   * Note: this field is persisted in the instance state {@link Bundle}. We need to resolve to this
   * error-prone mechanism because showDialog on Eclair doesn't take parameters. Once Froyo is
   * the minimum targetted SDK, this contrived code can be removed.
   */
  private SaveKeyDialogParams mSaveKeyDialogParams;

  /**
   * Whether this activity is currently displaying a confirmation prompt in response to the
   * "save key" Intent.
   */
  private boolean mSaveKeyIntentConfirmationInProgress;

  private static final String OTP_SCHEME = "otpauth";
  private static final String TOTP = "totp"; // time-based
  private static final String HOTP = "hotp"; // counter-based
  private static final String SECRET_PARAM = "secret";
  private static final String COUNTER_PARAM = "counter";
  // @VisibleForTesting
  public static final int CHECK_KEY_VALUE_ID = 0;
  // @VisibleForTesting
  public static final int RENAME_ID = 1;
  // @VisibleForTesting
  public static final int REMOVE_ID = 2;
  // @VisibleForTesting
  static final int COPY_TO_CLIPBOARD_ID = 3;
  // @VisibleForTesting
  static final int SCAN_REQUEST = 31337;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mAccountDb = DependencyInjector.getAccountDb();
    mOtpProvider = DependencyInjector.getOtpProvider();

    // Use a different (longer) title from the one that's declared in the manifest (and the one that
    // the Android launcher displays).
    setTitle(R.string.app_name);

    mTotpCounter = mOtpProvider.getTotpCounter();
    mTotpClock = mOtpProvider.getTotpClock();

    setContentView(R.layout.main);

    // restore state on screen rotation
    Object savedState = getLastNonConfigurationInstance();
    if (savedState != null) {
      mUsers = (PinInfo[]) savedState;
      // Re-enable the Get Code buttons on all HOTP accounts, otherwise they'll stay disabled.
      for (PinInfo account : mUsers) {
        if (account.isHotp) {
          account.hotpCodeGenerationAllowed = true;
        }
      }
    }

    if (savedInstanceState != null) {
      mOldAppUninstallIntent = savedInstanceState.getParcelable(KEY_OLD_APP_UNINSTALL_INTENT);
      mSaveKeyDialogParams =
          (SaveKeyDialogParams) savedInstanceState.getSerializable(KEY_SAVE_KEY_DIALOG_PARAMS);
    }

    mUserList = (ListView) findViewById(R.id.user_list);
    mContentNoAccounts = findViewById(R.id.content_no_accounts);
    mContentAccountsPresent = findViewById(R.id.content_accounts_present);
    mContentNoAccounts.setVisibility((mUsers.length > 0) ? View.GONE : View.VISIBLE);
    mContentAccountsPresent.setVisibility((mUsers.length > 0) ? View.VISIBLE : View.GONE);
    TextView noAccountsPromptDetails = (TextView) findViewById(R.id.details);
    noAccountsPromptDetails.setText(
        Html.fromHtml(getString(R.string.welcome_page_details)));

    findViewById(R.id.how_it_works_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        displayHowItWorksInstructions();
      }
    });
    findViewById(R.id.add_account_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        addAccount();
      }
    });
    mEnterPinPrompt = (TextView) findViewById(R.id.enter_pin_prompt);

    mUserAdapter = new PinListAdapter(this, R.layout.user_row, mUsers);

    mUserList.setVisibility(View.GONE);
    mUserList.setAdapter(mUserAdapter);
    mUserList.setOnItemClickListener(new OnItemClickListener(){
        @Override
        public void onItemClick(AdapterView<?> unusedParent, View row,
                                int unusedPosition, long unusedId) {
            NextOtpButtonListener clickListener = (NextOtpButtonListener) row.getTag();
            View nextOtp = row.findViewById(R.id.next_otp);
            if ((clickListener != null) && nextOtp.isEnabled()){
                clickListener.onClick(row);
            }
            mUserList.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
    });

    if (savedInstanceState == null) {
      // This is the first time this Activity is starting (i.e., not restoring previous state which
      // was saved, for example, due to orientation change)
      DependencyInjector.getOptionalFeatures().onAuthenticatorActivityCreated(this);
      importDataFromOldAppIfNecessary();
      handleIntent(getIntent());
    }
  }

  /**
   * Reacts to the {@link Intent} that started this activity or arrived to this activity without
   * restarting it (i.e., arrived via {@link #onNewIntent(Intent)}). Does nothing if the provided
   * intent is {@code null}.
   */
  private void handleIntent(Intent intent) {
    if (intent == null) {
      return;
    }

    String action = intent.getAction();
    if (action == null) {
      return;
    }

    if (ACTION_SCAN_BARCODE.equals(action)) {
      scanBarcode();
    } else if (intent.getData() != null) {
      interpretScanResult(intent.getData(), true);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putParcelable(KEY_OLD_APP_UNINSTALL_INTENT, mOldAppUninstallIntent);
    outState.putSerializable(KEY_SAVE_KEY_DIALOG_PARAMS, mSaveKeyDialogParams);
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    return mUsers;  // save state of users and currently displayed PINs
  }

  // Because this activity is marked as singleTop, new launch intents will be
  // delivered via this API instead of onResume().
  // Override here to catch otpauth:// URL being opened from QR code reader.
  @Override
  protected void onNewIntent(Intent intent) {
    Log.i(getString(R.string.app_name), LOCAL_TAG + ": onNewIntent");
    handleIntent(intent);
  }

  @Override
  protected void onStart() {
    super.onStart();

    updateCodesAndStartTotpCountdownTask();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.i(getString(R.string.app_name), LOCAL_TAG + ": onResume");

    importDataFromOldAppIfNecessary();
  }

  @Override
  protected void onStop() {
    stopTotpCountdownTask();

    super.onStop();
  }

  private void updateCodesAndStartTotpCountdownTask() {
    stopTotpCountdownTask();

    mTotpCountdownTask =
        new TotpCountdownTask(mTotpCounter, mTotpClock, TOTP_COUNTDOWN_REFRESH_PERIOD);
    mTotpCountdownTask.setListener(new TotpCountdownTask.Listener() {
      @Override
      public void onTotpCountdown(long millisRemaining) {
        if (isFinishing()) {
          // No need to reach to this even because the Activity is finishing anyway
          return;
        }
        setTotpCountdownPhaseFromTimeTillNextValue(millisRemaining);
      }

      @Override
      public void onTotpCounterValueChanged() {
        if (isFinishing()) {
          // No need to reach to this even because the Activity is finishing anyway
          return;
        }
        refreshVerificationCodes();
      }
    });

    mTotpCountdownTask.startAndNotifyListener();
  }

  private void stopTotpCountdownTask() {
    if (mTotpCountdownTask != null) {
      mTotpCountdownTask.stop();
      mTotpCountdownTask = null;
    }
  }

  /** Display list of user emails and updated pin codes. */
  protected void refreshUserList() {
    refreshUserList(false);
  }

  private void setTotpCountdownPhase(double phase) {
    mTotpCountdownPhase = phase;
    updateCountdownIndicators();
  }

  private void setTotpCountdownPhaseFromTimeTillNextValue(long millisRemaining) {
    setTotpCountdownPhase(
        ((double) millisRemaining) / Utilities.secondsToMillis(mTotpCounter.getTimeStep()));
  }

  private void refreshVerificationCodes() {
    refreshUserList();
    setTotpCountdownPhase(1.0);
  }

  private void updateCountdownIndicators() {
    for (int i = 0, len = mUserList.getChildCount(); i < len; i++) {
      View listEntry = mUserList.getChildAt(i);
      CountdownIndicator indicator =
          (CountdownIndicator) listEntry.findViewById(R.id.countdown_icon);
      if (indicator != null) {
        indicator.setPhase(mTotpCountdownPhase);
      }
    }
  }

  /**
   * Display list of user emails and updated pin codes.
   *
   * @param isAccountModified if true, force full refresh
   */
  // @VisibleForTesting
  public void refreshUserList(boolean isAccountModified) {
    ArrayList<String> usernames = new ArrayList<String>();
    mAccountDb.getNames(usernames);

    int userCount = usernames.size();

    if (userCount > 0) {
      boolean newListRequired = isAccountModified || mUsers.length != userCount;
      if (newListRequired) {
        mUsers = new PinInfo[userCount];
      }

      for (int i = 0; i < userCount; ++i) {
        String user = usernames.get(i);
        try {
          computeAndDisplayPin(user, i, false);
        } catch (OtpSourceException ignored) {}
      }

      if (newListRequired) {
        // Make the list display the data from the newly created array of accounts
        // This forces the list to scroll to top.
        mUserAdapter = new PinListAdapter(this, R.layout.user_row, mUsers);
        mUserList.setAdapter(mUserAdapter);
      }

      mUserAdapter.notifyDataSetChanged();

      if (mUserList.getVisibility() != View.VISIBLE) {
        mUserList.setVisibility(View.VISIBLE);
        registerForContextMenu(mUserList);
      }
    } else {
      mUsers = new PinInfo[0]; // clear any existing user PIN state
      mUserList.setVisibility(View.GONE);
    }

    // Display the list of accounts if there are accounts, otherwise display a
    // different layout explaining the user how this app works and providing the user with an easy
    // way to add an account.
    mContentNoAccounts.setVisibility((mUsers.length > 0) ? View.GONE : View.VISIBLE);
    mContentAccountsPresent.setVisibility((mUsers.length > 0) ? View.VISIBLE : View.GONE);
  }

  /**
   * Computes the PIN and saves it in mUsers. This currently runs in the UI
   * thread so it should not take more than a second or so. If necessary, we can
   * move the computation to a background thread.
   *
   * @param user the user email to display with the PIN
   * @param position the index for the screen of this user and PIN
   * @param computeHotp true if we should increment counter and display new hotp
   */
  public void computeAndDisplayPin(String user, int position,
      boolean computeHotp) throws OtpSourceException {

    PinInfo currentPin;
    if (mUsers[position] != null) {
      currentPin = mUsers[position]; // existing PinInfo, so we'll update it
    } else {
      currentPin = new PinInfo();
      currentPin.pin = getString(R.string.empty_pin);
      currentPin.hotpCodeGenerationAllowed = true;
    }

    OtpType type = mAccountDb.getType(user);
    currentPin.isHotp = (type == OtpType.HOTP);

    currentPin.user = user;

    if (!currentPin.isHotp || computeHotp) {
      // Always safe to recompute, because this code path is only
      // reached if the account is:
      // - Time-based, in which case getNextCode() does not change state.
      // - Counter-based (HOTP) and computeHotp is true.
      currentPin.pin = mOtpProvider.getNextCode(user);
      currentPin.hotpCodeGenerationAllowed = true;
    }

    mUsers[position] = currentPin;
  }

  /**
   * Parses a secret value from a URI. The format will be:
   *
   * otpauth://totp/user@example.com?secret=FFF...
   * otpauth://hotp/user@example.com?secret=FFF...&counter=123
   *
   * @param uri The URI containing the secret key
   * @param confirmBeforeSave a boolean to indicate if the user should be
   *                          prompted for confirmation before updating the otp
   *                          account information.
   */
  private void parseSecret(Uri uri, boolean confirmBeforeSave) {
    final String scheme = uri.getScheme().toLowerCase();
    final String path = uri.getPath();
    final String authority = uri.getAuthority();
    final String user;
    final String secret;
    final OtpType type;
    final Integer counter;

    if (!OTP_SCHEME.equals(scheme)) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid or missing scheme in uri");
      showDialog(Utilities.INVALID_QR_CODE);
      return;
    }

    if (TOTP.equals(authority)) {
      type = OtpType.TOTP;
      counter = AccountDb.DEFAULT_HOTP_COUNTER; // only interesting for HOTP
    } else if (HOTP.equals(authority)) {
      type = OtpType.HOTP;
      String counterParameter = uri.getQueryParameter(COUNTER_PARAM);
      if (counterParameter != null) {
        try {
          counter = Integer.parseInt(counterParameter);
        } catch (NumberFormatException e) {
          Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid counter in uri");
          showDialog(Utilities.INVALID_QR_CODE);
          return;
        }
      } else {
        counter = AccountDb.DEFAULT_HOTP_COUNTER;
      }
    } else {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid or missing authority in uri");
      showDialog(Utilities.INVALID_QR_CODE);
      return;
    }

    user = validateAndGetUserInPath(path);
    if (user == null) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Missing user id in uri");
      showDialog(Utilities.INVALID_QR_CODE);
      return;
    }

    secret = uri.getQueryParameter(SECRET_PARAM);

    if (secret == null || secret.length() == 0) {
      Log.e(getString(R.string.app_name), LOCAL_TAG +
          ": Secret key not found in URI");
      showDialog(Utilities.INVALID_SECRET_IN_QR_CODE);
      return;
    }

    if (AccountDb.getSigningOracle(secret) == null) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid secret key");
      showDialog(Utilities.INVALID_SECRET_IN_QR_CODE);
      return;
    }

    if (secret.equals(mAccountDb.getSecret(user)) &&
        counter == mAccountDb.getCounter(user) &&
        type == mAccountDb.getType(user)) {
      return;  // nothing to update.
    }

    if (confirmBeforeSave) {
      mSaveKeyDialogParams = new SaveKeyDialogParams(user, secret, type, counter);
      showDialog(DIALOG_ID_SAVE_KEY);
    } else {
      saveSecretAndRefreshUserList(user, secret, null, type, counter);
    }
  }

  private static String validateAndGetUserInPath(String path) {
    if (path == null || !path.startsWith("/")) {
      return null;
    }
    // path is "/user", so remove leading "/", and trailing white spaces
    String user = path.substring(1).trim();
    if (user.length() == 0) {
      return null; // only white spaces.
    }
    return user;
  }

  /**
   * Saves the secret key to local storage on the phone and updates the displayed account list.
   *
   * @param user the user email address. When editing, the new user email.
   * @param secret the secret key
   * @param originalUser If editing, the original user email, otherwise null.
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   */
  private void saveSecretAndRefreshUserList(String user, String secret,
      String originalUser, OtpType type, Integer counter) {
    if (saveSecret(this, user, secret, originalUser, type, counter)) {
      refreshUserList(true);
    }
  }

  /**
   * Saves the secret key to local storage on the phone.
   *
   * @param user the user email address. When editing, the new user email.
   * @param secret the secret key
   * @param originalUser If editing, the original user email, otherwise null.
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   *
   * @return {@code true} if the secret was saved, {@code false} otherwise.
   */
  static boolean saveSecret(Context context, String user, String secret,
                         String originalUser, OtpType type, Integer counter) {
    if (originalUser == null) {  // new user account
      originalUser = user;
    }
    if (secret != null) {
      AccountDb accountDb = DependencyInjector.getAccountDb();
      accountDb.update(user, secret, originalUser, type, counter);
      DependencyInjector.getOptionalFeatures().onAuthenticatorActivityAccountSaved(context, user);
      // TODO: Consider having a display message that activities can call and it
      //       will present a toast with a uniform duration, and perhaps update
      //       status messages (presuming we have a way to remove them after they
      //       are stale).
      Toast.makeText(context, R.string.secret_saved, Toast.LENGTH_LONG).show();
      ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
        .vibrate(VIBRATE_DURATION);
      return true;
    } else {
      Log.e(LOCAL_TAG, "Trying to save an empty secret key");
      Toast.makeText(context, R.string.error_empty_secret, Toast.LENGTH_LONG).show();
      return false;
    }
  }

  /** Converts user list ordinal id to user email */
  private String idToEmail(long id) {
    return mUsers[(int) id].user;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    String user = idToEmail(info.id);
    OtpType type = mAccountDb.getType(user);
    menu.setHeaderTitle(user);
    menu.add(0, COPY_TO_CLIPBOARD_ID, 0, R.string.copy_to_clipboard);
    // Option to display the check-code is only available for HOTP accounts.
    if (type == OtpType.HOTP) {
      menu.add(0, CHECK_KEY_VALUE_ID, 0, R.string.check_code_menu_item);
    }
    menu.add(0, RENAME_ID, 0, R.string.rename);
    menu.add(0, REMOVE_ID, 0, R.string.context_menu_remove_account);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    Intent intent;
    final String user = idToEmail(info.id); // final so listener can see value
    switch (item.getItemId()) {
      case COPY_TO_CLIPBOARD_ID:
        ClipboardManager clipboard =
          (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(mUsers[(int) info.id].pin);
        return true;
      case CHECK_KEY_VALUE_ID:
        intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, CheckCodeActivity.class);
        intent.putExtra("user", user);
        startActivity(intent);
        return true;
      case RENAME_ID:
        final Context context = this; // final so listener can see value
        final View frame = getLayoutInflater().inflate(R.layout.rename,
            (ViewGroup) findViewById(R.id.rename_root));
        final EditText nameEdit = (EditText) frame.findViewById(R.id.rename_edittext);
        nameEdit.setText(user);
        new AlertDialog.Builder(this)
        .setTitle(String.format(getString(R.string.rename_message), user))
        .setView(frame)
        .setPositiveButton(R.string.submit,
            this.getRenameClickListener(context, user, nameEdit))
        .setNegativeButton(R.string.cancel, null)
        .show();
        return true;
      case REMOVE_ID:
        // Use a WebView to display the prompt because it contains non-trivial markup, such as list
        View promptContentView =
            getLayoutInflater().inflate(R.layout.remove_account_prompt, null, false);
        WebView webView = (WebView) promptContentView.findViewById(R.id.web_view);
        webView.setBackgroundColor(Color.TRANSPARENT);
        // Make the WebView use the same font size as for the mEnterPinPrompt field
        double pixelsPerDip =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) / 10d;
        webView.getSettings().setDefaultFontSize(
            (int) (mEnterPinPrompt.getTextSize() / pixelsPerDip));
        Utilities.setWebViewHtml(
            webView,
            "<html><body style=\"background-color: transparent;\" text=\"white\">"
                + getString(
                    mAccountDb.isGoogleAccount(user)
                        ? R.string.remove_google_account_dialog_message
                        : R.string.remove_account_dialog_message)
                + "</body></html>");

        new AlertDialog.Builder(this)
          .setTitle(getString(R.string.remove_account_dialog_title, user))
          .setView(promptContentView)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setPositiveButton(R.string.remove_account_dialog_button_remove,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                  mAccountDb.delete(user);
                  refreshUserList(true);
                }
              }
          )
          .setNegativeButton(R.string.cancel, null)
          .show();
        return true;
      default:
        return super.onContextItemSelected(item);
    }
  }

  private DialogInterface.OnClickListener getRenameClickListener(final Context context,
      final String user, final EditText nameEdit) {
    return new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int whichButton) {
        String newName = nameEdit.getText().toString();
        if (newName != user) {
          if (mAccountDb.nameExists(newName)) {
            Toast.makeText(context, R.string.error_exists, Toast.LENGTH_LONG).show();
          } else {
            saveSecretAndRefreshUserList(newName,
                mAccountDb.getSecret(user), user, mAccountDb.getType(user),
                mAccountDb.getCounter(user));
          }
        }
      }
    };
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    switch (item.getItemId()) {
      case R.id.add_account:
        addAccount();
        return true;
      case R.id.how_it_works:
        displayHowItWorksInstructions();
        return true;
      case R.id.settings:
        showSettings();
        return true;
    }

    return super.onMenuItemSelected(featureId, item);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    Log.i(getString(R.string.app_name), LOCAL_TAG + ": onActivityResult");
    if (requestCode == SCAN_REQUEST && resultCode == Activity.RESULT_OK) {
      // Grab the scan results and convert it into a URI
      String scanResult = (intent != null) ? intent.getStringExtra("SCAN_RESULT") : null;
      Uri uri = (scanResult != null) ? Uri.parse(scanResult) : null;
      interpretScanResult(uri, false);
    }
  }

  private void displayHowItWorksInstructions() {
    startActivity(new Intent(this, IntroEnterPasswordActivity.class));
  }

  private void addAccount() {
    DependencyInjector.getOptionalFeatures().onAuthenticatorActivityAddAccount(this);
  }

  private void scanBarcode() {
    Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
    intentScan.putExtra("SCAN_MODE", "QR_CODE_MODE");
    intentScan.putExtra("SAVE_HISTORY", false);
    try {
      startActivityForResult(intentScan, SCAN_REQUEST);
    } catch (ActivityNotFoundException error) {
      showDialog(Utilities.DOWNLOAD_DIALOG);
    }
  }

  public static Intent getLaunchIntentActionScanBarcode(Context context) {
    return new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE)
        .setComponent(new ComponentName(context, AuthenticatorActivity.class));
  }

  private void showSettings() {
    Intent intent = new Intent();
    intent.setClass(this, SettingsActivity.class);
    startActivity(intent);
  }

  /**
   * Interprets the QR code that was scanned by the user.  Decides whether to
   * launch the key provisioning sequence or the OTP seed setting sequence.
   *
   * @param scanResult a URI holding the contents of the QR scan result
   * @param confirmBeforeSave a boolean to indicate if the user should be
   *                          prompted for confirmation before updating the otp
   *                          account information.
   */
  private void interpretScanResult(Uri scanResult, boolean confirmBeforeSave) {
    if (DependencyInjector.getOptionalFeatures().interpretScanResult(this, scanResult)) {
      // Scan result consumed by an optional component
      return;
    }
    // The scan result is expected to be a URL that adds an account.

    // If confirmBeforeSave is true, the user has to confirm/reject the action.
    // We need to ensure that new results are accepted only if the previous ones have been
    // confirmed/rejected by the user. This is to prevent the attacker from sending multiple results
    // in sequence to confuse/DoS the user.
    if (confirmBeforeSave) {
      if (mSaveKeyIntentConfirmationInProgress) {
        Log.w(LOCAL_TAG, "Ignoring save key Intent: previous Intent not yet confirmed by user");
        return;
      }
      // No matter what happens below, we'll show a prompt which, once dismissed, will reset the
      // flag below.
      mSaveKeyIntentConfirmationInProgress = true;
    }

    // Sanity check
    if (scanResult == null) {
      showDialog(Utilities.INVALID_QR_CODE);
      return;
    }

    // See if the URL is an account setup URL containing a shared secret
    if (OTP_SCHEME.equals(scanResult.getScheme()) && scanResult.getAuthority() != null) {
      parseSecret(scanResult, confirmBeforeSave);
    } else {
      showDialog(Utilities.INVALID_QR_CODE);
    }
  }

  /**
   * This method is deprecated in SDK level 8, but we have to use it because the
   * new method, which replaces this one, does not exist before SDK level 8
   */
  @Override
  protected Dialog onCreateDialog(final int id) {
    Dialog dialog = null;
    switch(id) {
      /**
       * Prompt to download ZXing from Market. If Market app is not installed,
       * such as on a development phone, open the HTTPS URI for the ZXing apk.
       */
      case Utilities.DOWNLOAD_DIALOG:
        AlertDialog.Builder dlBuilder = new AlertDialog.Builder(this);
        dlBuilder.setTitle(R.string.install_dialog_title);
        dlBuilder.setMessage(R.string.install_dialog_message);
        dlBuilder.setIcon(android.R.drawable.ic_dialog_alert);
        dlBuilder.setPositiveButton(R.string.install_button,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int whichButton) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                                           Uri.parse(Utilities.ZXING_MARKET));
                try {
                  startActivity(intent);
                }
                catch (ActivityNotFoundException e) { // if no Market app
                  intent = new Intent(Intent.ACTION_VIEW,
                                      Uri.parse(Utilities.ZXING_DIRECT));
                  startActivity(intent);
                }
              }
            }
        );
        dlBuilder.setNegativeButton(R.string.cancel, null);
        dialog = dlBuilder.create();
        break;

      case DIALOG_ID_SAVE_KEY:
        final SaveKeyDialogParams saveKeyDialogParams = mSaveKeyDialogParams;
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.save_key_message)
            .setMessage(saveKeyDialogParams.user)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                  saveSecretAndRefreshUserList(
                      saveKeyDialogParams.user,
                      saveKeyDialogParams.secret,
                      null,
                      saveKeyDialogParams.type,
                      saveKeyDialogParams.counter);
                }
              })
            .setNegativeButton(R.string.cancel, null)
            .create();
        // Ensure that whenever this dialog is to be displayed via showDialog, it displays the
        // correct (latest) user/account name. If this dialog is not explicitly removed after it's
        // been dismissed, then next time showDialog is invoked, onCreateDialog will not be invoked
        // and the dialog will display the previous user/account name instead of the current one.
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            removeDialog(id);
            onSaveKeyIntentConfirmationPromptDismissed();
          }
        });
        break;

      case Utilities.INVALID_QR_CODE:
        dialog = createOkAlertDialog(R.string.error_title, R.string.error_qr,
            android.R.drawable.ic_dialog_alert);
        markDialogAsResultOfSaveKeyIntent(dialog);
        break;

      case Utilities.INVALID_SECRET_IN_QR_CODE:
        dialog = createOkAlertDialog(
            R.string.error_title, R.string.error_uri, android.R.drawable.ic_dialog_alert);
        markDialogAsResultOfSaveKeyIntent(dialog);
        break;

      case DIALOG_ID_UNINSTALL_OLD_APP:
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dataimport_import_succeeded_uninstall_dialog_title)
            .setMessage(
                DependencyInjector.getOptionalFeatures().appendDataImportLearnMoreLink(
                    this,
                    getString(R.string.dataimport_import_succeeded_uninstall_dialog_prompt)))
            .setCancelable(true)
            .setPositiveButton(
                R.string.button_uninstall_old_app,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int whichButton) {
                    startActivity(mOldAppUninstallIntent);
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .create();
        break;

      default:
        dialog =
            DependencyInjector.getOptionalFeatures().onAuthenticatorActivityCreateDialog(this, id);
        if (dialog == null) {
          dialog = super.onCreateDialog(id);
        }
        break;
    }
    return dialog;
  }

  private void markDialogAsResultOfSaveKeyIntent(Dialog dialog) {
    dialog.setOnDismissListener(new OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialog) {
        onSaveKeyIntentConfirmationPromptDismissed();
      }
    });
  }

  /**
   * Invoked when a user-visible confirmation prompt for the Intent to add a new account has been
   * dimissed.
   */
  private void onSaveKeyIntentConfirmationPromptDismissed() {
    mSaveKeyIntentConfirmationInProgress = false;
  }

  /**
   * Create dialog with supplied ids; icon is not set if iconId is 0.
   */
  private Dialog createOkAlertDialog(int titleId, int messageId, int iconId) {
    return new AlertDialog.Builder(this)
        .setTitle(titleId)
        .setMessage(messageId)
        .setIcon(iconId)
        .setPositiveButton(R.string.ok, null)
        .create();
  }

  /**
   * A tuple of user, OTP value, and type, that represents a particular user.
   *
   * @author adhintz@google.com (Drew Hintz)
   */
  private static class PinInfo {
    private String pin; // calculated OTP, or a placeholder if not calculated
    private String user;
    private boolean isHotp = false; // used to see if button needs to be displayed

    /** HOTP only: Whether code generation is allowed for this account. */
    private boolean hotpCodeGenerationAllowed;
  }


  /** Scale to use for the text displaying the PIN numbers. */
  private static final float PIN_TEXT_SCALEX_NORMAL = 1.0f;
  /** Underscores are shown slightly smaller. */
  private static final float PIN_TEXT_SCALEX_UNDERSCORE = 0.87f;

  /**
   * Listener for the Button that generates the next OTP value.
   *
   * @author adhintz@google.com (Drew Hintz)
   */
  private class NextOtpButtonListener implements OnClickListener {
    private final Handler mHandler = new Handler();
    private final PinInfo mAccount;

    private NextOtpButtonListener(PinInfo account) {
      mAccount = account;
    }

    @Override
    public void onClick(View v) {
      int position = findAccountPositionInList();
      if (position == -1) {
        throw new RuntimeException("Account not in list: " + mAccount);
      }

      try {
        computeAndDisplayPin(mAccount.user, position, true);
      } catch (OtpSourceException e) {
        DependencyInjector.getOptionalFeatures().onAuthenticatorActivityGetNextOtpFailed(
            AuthenticatorActivity.this, mAccount.user, e);
        return;
      }

      final String pin = mAccount.pin;

      // Temporarily disable code generation for this account
      mAccount.hotpCodeGenerationAllowed = false;
      mUserAdapter.notifyDataSetChanged();
      // The delayed operation below will be invoked once code generation is yet again allowed for
      // this account. The delay is in wall clock time (monotonically increasing) and is thus not
      // susceptible to system time jumps.
      mHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              mAccount.hotpCodeGenerationAllowed = true;
              mUserAdapter.notifyDataSetChanged();
            }
          },
          HOTP_MIN_TIME_INTERVAL_BETWEEN_CODES);
      // The delayed operation below will hide this OTP to prevent the user from seeing this OTP
      // long after it's been generated (and thus hopefully used).
      mHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              if (!pin.equals(mAccount.pin)) {
                return;
              }
              mAccount.pin = getString(R.string.empty_pin);
              mUserAdapter.notifyDataSetChanged();
            }
          },
          HOTP_DISPLAY_TIMEOUT);
    }

    /**
     * Gets the position in the account list of the account this listener is associated with.
     *
     * @return {@code 0}-based position or {@code -1} if the account is not in the list.
     */
    private int findAccountPositionInList() {
      for (int i = 0, len = mUsers.length; i < len; i++) {
        if (mUsers[i] == mAccount) {
          return i;
        }
      }

      return -1;
    }
  }

  /**
   * Displays the list of users and the current OTP values.
   *
   * @author adhintz@google.com (Drew Hintz)
   */
  private class PinListAdapter extends ArrayAdapter<PinInfo>  {

    public PinListAdapter(Context context, int userRowId, PinInfo[] items) {
      super(context, userRowId, items);
    }

    /**
     * Displays the user and OTP for the specified position. For HOTP, displays
     * the button for generating the next OTP value; for TOTP, displays the countdown indicator.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent){
     LayoutInflater inflater = getLayoutInflater();
     PinInfo currentPin = getItem(position);

     View row;
     if (convertView != null) {
       // Reuse an existing view
       row = convertView;
     } else {
       // Create a new view
       row = inflater.inflate(R.layout.user_row, null);
     }
     TextView pinView = (TextView) row.findViewById(R.id.pin_value);
     TextView userView = (TextView) row.findViewById(R.id.current_user);
     View buttonView = row.findViewById(R.id.next_otp);
     CountdownIndicator countdownIndicator =
         (CountdownIndicator) row.findViewById(R.id.countdown_icon);

     if (currentPin.isHotp) {
       buttonView.setVisibility(View.VISIBLE);
       buttonView.setEnabled(currentPin.hotpCodeGenerationAllowed);
       ((ViewGroup) row).setDescendantFocusability(
           ViewGroup.FOCUS_BLOCK_DESCENDANTS); // makes long press work
       NextOtpButtonListener clickListener = new NextOtpButtonListener(currentPin);
       buttonView.setOnClickListener(clickListener);
       row.setTag(clickListener);

       countdownIndicator.setVisibility(View.GONE);
     } else { // TOTP, so no button needed
       buttonView.setVisibility(View.GONE);
       buttonView.setOnClickListener(null);
       row.setTag(null);

       countdownIndicator.setVisibility(View.VISIBLE);
       countdownIndicator.setPhase(mTotpCountdownPhase);
     }

     if (getString(R.string.empty_pin).equals(currentPin.pin)) {
       pinView.setTextScaleX(PIN_TEXT_SCALEX_UNDERSCORE); // smaller gap between underscores
     } else {
       pinView.setTextScaleX(PIN_TEXT_SCALEX_NORMAL);
     }
     pinView.setText(currentPin.pin);
     userView.setText(currentPin.user);

     return row;
    }
  }

  private void importDataFromOldAppIfNecessary() {
    if (mDataImportInProgress) {
      return;
    }
    mDataImportInProgress = true;
    DependencyInjector.getDataImportController().start(this, new ImportController.Listener() {
      @Override
      public void onOldAppUninstallSuggested(Intent uninstallIntent) {
        if (isFinishing()) {
          return;
        }

        mOldAppUninstallIntent = uninstallIntent;
        showDialog(DIALOG_ID_UNINSTALL_OLD_APP);
      }

      @Override
      public void onDataImported() {
        if (isFinishing()) {
          return;
        }

        refreshUserList(true);

        DependencyInjector.getOptionalFeatures().onDataImportedFromOldApp(
            AuthenticatorActivity.this);
      }

      @Override
      public void onFinished() {
        if (isFinishing()) {
          return;
        }

        mDataImportInProgress = false;
      }
    });
  }

  /**
   * Parameters to the {@link AuthenticatorActivity#DIALOG_ID_SAVE_KEY} dialog.
   */
  private static class SaveKeyDialogParams implements Serializable {
    private final String user;
    private final String secret;
    private final OtpType type;
    private final Integer counter;

    private SaveKeyDialogParams(String user, String secret, OtpType type, Integer counter) {
      this.user = user;
      this.secret = secret;
      this.type = type;
      this.counter = counter;
    }
  }
}
