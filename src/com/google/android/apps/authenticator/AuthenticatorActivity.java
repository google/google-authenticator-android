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


import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.TestableActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The main activity that displays usernames and codes
 *
 * @author sweis@google.com (Steve Weis)
 * @author adhintz@google.com (Drew Hintz)
 * @author cemp@google.com (Cem Paya)
 * @author klyubin@google.com (Alex Klyubin)
 */
public class AuthenticatorActivity extends TestableActivity implements OnClickListener {

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

  /**
   * Time instance (milliseconds since epoch) from from which onwards the deprecation notice should
   * be displayed.
   */
  private static final long DEPRECATION_NOTICE_START_TIME;

  static {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    c.set(Calendar.YEAR, 2012);
    c.set(Calendar.MONTH, Calendar.MARCH);
    c.set(Calendar.DAY_OF_MONTH, 29);
    c.set(Calendar.HOUR_OF_DAY, 7);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    DEPRECATION_NOTICE_START_TIME = c.getTimeInMillis();
  }

  private TextView mEnterPinTextView;
  private ListView mUserList;
  private PinListAdapter mUserAdapter;
  private PinInfo[] mUsers = {};
  private Button mScanBarcodeButton;
  private Button mEnterKeyButton;
  private LinearLayout mButtonsLayout;
  private View mDeprecationNoticeView;
  private TextView mDeprecationNoticeTextView;

  /** Counter used for generating TOTP verification codes. */
  private TotpCounter mTotpCounter;

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
   * Whether this {@link Activity} only lets the user uninstall the app and see the About screen.
   */
  private boolean mUninstallOnlyMode;

  private static final String OTP_SCHEME = "otpauth";
  private static final String TOTP = "totp"; // time-based
  private static final String HOTP = "hotp"; // counter-based
  private static final String SECRET_PARAM = "secret";
  private static final String COUNTER_PARAM = "counter";
  // @VisibleForTesting
  static final int CHECK_KEY_VALUE_ID = 0;
  // @VisibleForTesting
  static final int RENAME_ID = 1;
  // @VisibleForTesting
  static final int DELETE_ID = 2;
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

    mUserList = (ListView) findViewById(R.id.user_list);
    mEnterPinTextView = (TextView) findViewById(R.id.enter_pin);
    mEnterPinTextView.setVisibility(View.GONE);
    mScanBarcodeButton = (Button) findViewById(R.id.scan_barcode_button);
    mScanBarcodeButton.setOnClickListener(this);
    mEnterKeyButton = (Button) findViewById(R.id.enter_key_button);
    mEnterKeyButton.setOnClickListener(this);
    mButtonsLayout = (LinearLayout) findViewById(R.id.main_buttons);

    mUserAdapter = new PinListAdapter(this, R.layout.user_row, mUsers);
    mDeprecationNoticeView = findViewById(R.id.deprecation_notice);
    mDeprecationNoticeTextView =
        (TextView) mDeprecationNoticeView.findViewById(R.id.deprecation_notice_text);

    mButtonsLayout.setVisibility(View.GONE);
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


    refreshUserList(true);
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
    if (intent != null && intent.getData() != null) {
      interpretScanResult(intent.getData(), true);
    }
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
    Intent intent = getIntent();
    Uri uri = intent.getData();

    // If this activity was started by the user clicking on a link, then
    // we should fetch the secret key from the given URL.
    if (uri != null) {
      interpretScanResult(uri, true);
      setIntent(new Intent());
    }

    refreshUserList();
  }

  @Override
  protected void onStop() {
    stopTotpCountdownTask();

    super.onStop();
  }

  private void updateCodesAndStartTotpCountdownTask() {
    stopTotpCountdownTask();

    mTotpCountdownTask = new TotpCountdownTask(mTotpCounter, TOTP_COUNTDOWN_REFRESH_PERIOD);
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
  protected void refreshUserList(boolean isAccountModified) {
    ArrayList<String> usernames = new ArrayList<String>();
    mAccountDb.getNames(usernames);

    int userCount = usernames.size();

    boolean newListRequired = isAccountModified || mUsers.length != userCount;
    if (userCount > 0) {
      if (newListRequired) {
        mUsers = new PinInfo[userCount];
      }

      for (int i = 0; i < userCount; ++i) {
        String user = usernames.get(i);
        computeAndDisplayPin(user, i, false);
      }

      if (newListRequired) {
        // Make the list display the data from the newly created array of accounts
        // This forces the list to scroll to top.
        mUserAdapter = new PinListAdapter(this, R.layout.user_row, mUsers);
        mUserList.setAdapter(mUserAdapter);
      }

      mUserAdapter.notifyDataSetChanged();

      if (mUserList.getVisibility() != View.VISIBLE) {
        mEnterPinTextView.setText(R.string.enter_pin);
        mEnterPinTextView.setVisibility(View.VISIBLE);
        mButtonsLayout.setVisibility(View.GONE);
        mUserList.setVisibility(View.VISIBLE);
        registerForContextMenu(mUserList);
      }
    } else {
      // If the user started up this app but there is no secret key yet,
      // then tell the user to visit a web page to get the secret key.
      mUsers = new PinInfo[0]; // clear any existing user PIN state
      tellUserToGetSecretKey();
    }

    boolean accountsExist = userCount > 0;
    if (isDeprecationNoticeEnabled()) {
      showDeprecationNotice(accountsExist);
    } else {
      hideDeprecationNotice(accountsExist);
    }
  }

  /**
   * Tells the user to visit a web page to get a secret key.
   */
  private void tellUserToGetSecretKey() {
    String notInitialized = getString(R.string.not_initialized);
    CharSequence styledNotInitalized = Html.fromHtml(notInitialized);
    mEnterPinTextView.setText(styledNotInitalized);
    mEnterPinTextView.setMovementMethod(LinkMovementMethod.getInstance());
    mEnterPinTextView.setVisibility(View.VISIBLE);
    mButtonsLayout.setVisibility(View.VISIBLE);
    mUserList.setVisibility(View.GONE);
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
      boolean computeHotp) {

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
      counter = (counterParameter != null) ?
          Integer.parseInt(counterParameter) : AccountDb.DEFAULT_HOTP_COUNTER;
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
      new AlertDialog.Builder(this)
      .setTitle(R.string.save_key_message)
      .setMessage(user)
      .setIcon(android.R.drawable.ic_dialog_alert)
      .setPositiveButton(R.string.ok,
          new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int whichButton) {
            saveSecretAndRefreshUserList(user, secret, null, type, counter);
          }
        })
      .setNegativeButton(R.string.cancel, null)
      .show();
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
    menu.add(0, DELETE_ID, 0, R.string.delete);
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
      case DELETE_ID:
        new AlertDialog.Builder(this)
          .setTitle(R.string.delete_message)
          .setMessage(user)
          .setIcon(android.R.drawable.ic_dialog_alert)
          .setPositiveButton(R.string.ok,
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
    // Inflate the menu XML resource.
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    boolean result = super.onPrepareOptionsMenu(menu);
    menu.findItem(R.id.enter_key_item).setVisible(!mUninstallOnlyMode);
    menu.findItem(R.id.scan_barcode).setVisible(!mUninstallOnlyMode);
    return result;
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
      case R.id.enter_key_item:
        manuallyEnterKey();
        return true;
      case R.id.scan_barcode:
        scanBarcode();
        return true;
      case R.id.settings_about:
        showSettingsAbout();
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

  @Override
  public void onClick(View view) {
    if (view == mScanBarcodeButton) {
      this.scanBarcode();
    } else if (view == mEnterKeyButton) {
      this.manuallyEnterKey();
    }
  }

  private void manuallyEnterKey() {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setClass(this, EnterKeyActivity.class);
    startActivity(intent);
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

  private void showSettingsAbout() {
    Intent intent = new Intent();
    intent.setClass(this, SettingsAboutActivity.class);
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
    if (mUninstallOnlyMode) {
      return;
    }

    // Sanity check
    if (scanResult == null) {
      showDialog(Utilities.INVALID_QR_CODE);
      return;
    }

    String scheme = scanResult.getScheme();
    String authority = scanResult.getAuthority();

    if (scheme != null && authority != null && scheme.equals(OTP_SCHEME)) {
      // The user scanned a QR code encoding some URL with custom
      // otpauth:// scheme
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
  protected Dialog onCreateDialog(int id) {
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

      case Utilities.INVALID_QR_CODE:
        dialog = createOkAlertDialog(R.string.error_title, R.string.error_qr,
            android.R.drawable.ic_dialog_alert);
        break;

      case Utilities.INVALID_SECRET_IN_QR_CODE:
        dialog = createOkAlertDialog(
            R.string.error_title, R.string.error_uri, android.R.drawable.ic_dialog_alert);
        break;
    }
    return dialog;
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

      computeAndDisplayPin(mAccount.user, position, true);
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

   void onDeprecationNoticeLearnMoreLinkClicked() {
    startActivity(new Intent(Intent.ACTION_VIEW).setClass(this, DeprecationInfoActivity.class));
  }

  private void onDeprecationNoticeLaunchNewAppLinkClicked() {
    Intent intent = new Intent(Intent.ACTION_MAIN)
       .addCategory(Intent.CATEGORY_LAUNCHER)
       .setComponent(new ComponentName(
           DeprecationInfoActivity.NEW_APP_PACKAGE_NAME,
           "com.google.android.apps.authenticator.AuthenticatorActivity"));
    startActivity(intent);
    finish();
  }

  private void onDeprecationNoticeUninstallSelfLinkClicked() {
    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
  }

  private static boolean isNewAppInstalled() {
    try {
      DependencyInjector.getPackageManager().getPackageInfo(
          DeprecationInfoActivity.NEW_APP_PACKAGE_NAME, 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  private void showDeprecationNotice(boolean accountsExist) {
    Spanned deprecationNoticeText;
    boolean uninstallOnlyMode = false;
    if (isNewAppInstalled()) {
      // "New" app installed on this device
      if (accountsExist) {
        // Some accounts are configured in this app -- it's not yet safe to uninstall this app.
        // Instead, suggest that the user launch the "new" app. The "new" app will then import
        // and delete all data from this app.
        deprecationNoticeText = Utilities.replaceEmptyUrlSpansWithClickableSpans(
            Html.fromHtml(getString(R.string.deprecation_notice_in_main_screen_launch_new_app)),
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                onDeprecationNoticeLaunchNewAppLinkClicked();
              }
            });
      } else {
        // No accounts in this app -- safe to uninstall and switch the user to the "new" app
        deprecationNoticeText = Utilities.replaceEmptyUrlSpansWithClickableSpans(
            Html.fromHtml(getString(R.string.deprecation_notice_in_main_screen_uninstall_self)),
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                onDeprecationNoticeUninstallSelfLinkClicked();
              }
            });
        uninstallOnlyMode = true;
      }
    } else {
      // "New" app not installed on this device
      deprecationNoticeText = Utilities.replaceEmptyUrlSpansWithClickableSpans(
          Html.fromHtml(getString(R.string.deprecation_notice_in_main_screen_install_new_app)),
          new OnClickListener() {
            @Override
            public void onClick(View v) {
              onDeprecationNoticeLearnMoreLinkClicked();
            }
          });
    }

    mDeprecationNoticeTextView.setText(deprecationNoticeText);
    // Enable the link(s) in this text to be selected when focus is moved
    mDeprecationNoticeTextView.setMovementMethod(LinkMovementMethod.getInstance());
    mDeprecationNoticeView.setVisibility(View.VISIBLE);

    mUninstallOnlyMode = uninstallOnlyMode;
    if (uninstallOnlyMode) {
      mEnterPinTextView.setVisibility(View.GONE);
      mButtonsLayout.setVisibility(View.GONE);
      mUserList.setVisibility(View.GONE);
    } else {
      if (accountsExist) {
        mEnterPinTextView.setVisibility(View.VISIBLE);
        mUserList.setVisibility(View.VISIBLE);
      } else {
        tellUserToGetSecretKey();
      }
    }
  }

  private void hideDeprecationNotice(boolean accountsExist) {
    mDeprecationNoticeView.setVisibility(View.GONE);

    if (accountsExist) {
      mEnterPinTextView.setVisibility(View.VISIBLE);
      mUserList.setVisibility(View.VISIBLE);
    } else {
      tellUserToGetSecretKey();
    }
  }

  /**
   * Whether this app should nag the user that this app is deprecated and that the user should
   * install/launch the new app.
   */
  private boolean isDeprecationNoticeEnabled() {
    return System.currentTimeMillis() >= DEPRECATION_NOTICE_START_TIME;
  }
}
