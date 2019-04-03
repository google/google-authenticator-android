/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.apps.authenticator.barcode.BarcodeCaptureActivity;
import com.google.android.apps.authenticator.barcode.BarcodeConditionChecker;
import com.google.android.apps.authenticator.enroll2sv.wizard.AddAccountActivity;
import com.google.android.apps.authenticator.howitworks.HowItWorksActivity;
import com.google.android.apps.authenticator.otp.AccountDb;
import com.google.android.apps.authenticator.otp.AccountDb.AccountDbIdUpdateFailureException;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.otp.CheckCodeActivity;
import com.google.android.apps.authenticator.otp.EnterKeyActivity;
import com.google.android.apps.authenticator.otp.OtpSource;
import com.google.android.apps.authenticator.otp.OtpSourceException;
import com.google.android.apps.authenticator.otp.PinInfo;
import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.otp.TotpCountdownTask;
import com.google.android.apps.authenticator.otp.TotpCounter;
import com.google.android.apps.authenticator.settings.SettingsActivity;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator.util.EmptySpaceClickableDragSortListView;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.android.apps.authenticator2.R;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortItemView;
import com.mobeta.android.dslv.DragSortListView.DragListener;
import com.mobeta.android.dslv.DragSortListView.DropListener;
import java.io.Serializable;
import java.util.List;
import javax.inject.Inject;

/** The main activity that displays usernames and codes */
@FixWhenMinSdkVersion(11) // Will be able to remove the context menu
public class AuthenticatorActivity extends TestableActivity {

  /** The tag for log messages */
  private static final String LOCAL_TAG = "AuthenticatorActivity";

  private static final long VIBRATE_DURATION = 200L;

  /**
   * Key under which {@link #darkModeEnabled} is stored presenting if the UI mode is dark mode from
   * the last launch of this {@code Activity} by the user.
   */
  public static final String KEY_DARK_MODE_ENABLED = "darkModeEnabled";

  /**
   * Key under which {@link #onboardingCompleted} is stored to know if user has completed the first
   * onboarding experience or not.
   */
  public static final String KEY_ONBOARDING_COMPLETED = "onboardingCompleted";

  /** Frequency (milliseconds) with which TOTP countdown indicators are updated. */
  public static final long TOTP_COUNTDOWN_REFRESH_PERIOD_MILLIS = 100L;

  /**
   * Minimum amount of time (milliseconds) that has to elapse from the moment a HOTP code is
   * generated for an account until the moment the next code can be generated for the account. This
   * is to prevent the user from generating too many HOTP codes in a short period of time.
   */
  private static final long HOTP_MIN_TIME_INTERVAL_BETWEEN_CODES = 5000;

  /**
   * The maximum amount of time (milliseconds) for which a HOTP code is displayed after it's been
   * generated.
   */
  private static final long HOTP_DISPLAY_TIMEOUT = 2 * 60 * 1000;

  @VisibleForTesting static final int DIALOG_ID_SAVE_KEY = 13;

  @VisibleForTesting static final int DIALOG_ID_INSTALL_GOOGLE_PLAY_SERVICES = 14;

  @VisibleForTesting static final int DIALOG_ID_INVALID_QR_CODE = 15;

  @VisibleForTesting static final int DIALOG_ID_INVALID_SECRET_IN_QR_CODE = 16;

  @VisibleForTesting static final int DIALOG_ID_BARCODE_SCANNER_NOT_AVAILABLE = 18;

  @VisibleForTesting static final int DIALOG_ID_LOW_STORAGE_FOR_BARCODE_SCANNER = 19;

  @VisibleForTesting static final int DIALOG_ID_CAMERA_NOT_AVAILABLE = 20;

  /**
   * Intent action to that tells this Activity to initiate the scanning of barcode to add an
   * account.
   */
  @VisibleForTesting
  public static final String ACTION_SCAN_BARCODE =
      AuthenticatorActivity.class.getName() + ".ScanBarcode";

  /** Intent URL to open the Google Play Services install page in Google Play. */
  public static final String GOOGLE_PLAY_SERVICES_INSTALL_FROM_GOOGLE_PLAY =
      "market://details?id=com.google.android.gms";

  /** Intent URL to open the Google Play Services install page on web browser. */
  public static final String GOOGLE_PLAY_SERVICES_INSTALL_FROM_WEB_BROWSER =
      "https://play.google.com/store/apps/details?id=com.google.android.gms";

  private View contentNoAccounts;
  private View contentAccountsPresent;
  protected EmptySpaceClickableDragSortListView userList;
  private PinListAdapter userAdapter;
  protected PinInfo[] users = {};
  private Toolbar toolbar;

  /** Counter used for generating TOTP verification codes. */
  private TotpCounter totpCounter;

  /** Clock used for generating TOTP verification codes. */
  private TotpClock totpClock;

  /**
   * Task that periodically notifies this activity about the amount of time remaining until the TOTP
   * codes refresh. The task also notifies this activity when TOTP codes refresh.
   */
  private TotpCountdownTask totpCountdownTask;

  /**
   * Phase of TOTP countdown indicators. The phase is in {@code [0, 1]} with {@code 1} meaning full
   * time step remaining until the code refreshes, and {@code 0} meaning the code is refreshing
   * right now.
   */
  private double totpCountdownPhase;

  protected AccountDb accountDb;
  @Inject OtpSource otpProvider;

  /**
   * Key under which the parameters of the Save Key dialog are stored in a dialog args {@link
   * Bundle}.
   */
  private static final String KEY_SAVE_KEY_DIALOG_PARAMS = "saveKeyDialogParams";

  /**
   * Whether this activity is currently displaying a confirmation prompt in response to the "save
   * key" Intent.
   */
  private boolean saveKeyIntentConfirmationInProgress;

  /**
   * Key of the preference which stores the number of {@link AccountDb} accounts present during the
   * last launch of this {@code Activity} by the user.
   */
  @VisibleForTesting
  static final String PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT = "accountCountDuringLastMainPageLaunch";

  /**
   * Key under which {@link #firstAccountAddedNoticeDisplayRequired} is stored in the instance state
   * {@link Bundle}.
   */
  private static final String KEY_FIRST_ACCOUNT_ADDED_NOTICE_DISPLAY_REQUIRED =
      "firstAccountAddedNoticeDisplayRequired";

  /**
   * Whether to display the notice with information on how to use this app after having added the
   * very first account (verification code generator).
   */
  private boolean firstAccountAddedNoticeDisplayRequired;

  /** Whether we are in the dark mode or not. */
  @VisibleForTesting
  boolean darkModeEnabled;

  /** Whether user has completed the first onboarding experience or not. */
  @VisibleForTesting
  boolean onboardingCompleted;

  /** Contains the bottom sheet instance showed when user click the red FAB */
  @VisibleForTesting BottomSheetDialog bottomSheetDialog;

  protected SharedPreferences preferences;

  private static final String OTP_SCHEME = "otpauth";
  private static final String TOTP = "totp"; // time-based
  private static final String HOTP = "hotp"; // counter-based
  private static final String ISSUER_PARAM = "issuer";
  private static final String SECRET_PARAM = "secret";
  private static final String COUNTER_PARAM = "counter";
  @VisibleForTesting static final int SCAN_REQUEST = 31337;

  @VisibleForTesting ContextMenu mostRecentContextMenu;

  @VisibleForTesting
  ActionMode actionMode;

  @Inject BarcodeConditionChecker barcodeConditionChecker;

  public AuthenticatorActivity() {
    super();
    DaggerInjector.inject(this);
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    accountDb = DependencyInjector.getAccountDb();
    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    // Apply theme based on user's preference.
    darkModeEnabled = preferences.getBoolean(KEY_DARK_MODE_ENABLED, false);
    setTheme(
        darkModeEnabled
            ? R.style.AuthenticatorTheme_NoActionBar_Dark
            : R.style.AuthenticatorTheme_NoActionBar);

    // Use a different (longer) title from the one that's declared in the manifest (and the one that
    // the Android launcher displays).
    setTitle(R.string.app_name);

    totpCounter = otpProvider.getTotpCounter();
    totpClock = otpProvider.getTotpClock();

    setContentView(R.layout.main);

    toolbar = (Toolbar) findViewById(R.id.authenticator_toolbar);
    setSupportActionBar(toolbar);

    // If the number of accounts is bigger than 0, we assume the user has completed the onboarding
    // experience (i.e. for upgrading users who have already added accounts into the app).
    if (getAccountCount() > 0) {
      preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).commit();
    }

    // restore state on screen rotation
    @SuppressWarnings("deprecation") // TODO: refactor to use savedInstanceState instead
    Object savedState = getLastCustomNonConfigurationInstance();
    if (savedState != null) {
      users = (PinInfo[]) savedState;
      // Re-enable the Get Code buttons on all HOTP accounts, otherwise they'll stay disabled.
      for (PinInfo account : users) {
        if (account.isHotp()) {
          account.setIsHotpCodeGenerationAllowed(true);
        }
      }
    }

    if (savedInstanceState != null) {
      firstAccountAddedNoticeDisplayRequired =
          savedInstanceState.getBoolean(KEY_FIRST_ACCOUNT_ADDED_NOTICE_DISPLAY_REQUIRED, false);
    }

    userList = (EmptySpaceClickableDragSortListView) findViewById(R.id.user_list);

    // Long-tapping on a list item starts the Contextual Action Bar for
    // that item.
    registerContextualActionBarForUserList();

    // Short-tapping on a list item generates the next code for HOTP accounts and does nothing for
    // TOTP accounts. Note that, while the Contextual Action Bar is displayed, short-tapping on a
    // list item will not generate codes and instead mark the tapped item as checked.
    userList.setOnItemClickListener(
        new OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View row, int position, long itemId) {
            // Each item in DragSortListView is wrapped with DragSortItemView.
            // Iterating the children to find the enclosed UserRowView
            DragSortItemView dragSortItemView = (DragSortItemView) row;
            View userRowView = null;
            for (int i = 0; i < dragSortItemView.getChildCount(); i++) {
              if (dragSortItemView.getChildAt(i) instanceof UserRowView) {
                userRowView = dragSortItemView.getChildAt(i);
              }
            }
            if (userRowView == null) {
              return;
            }
            NextOtpButtonListener clickListener = (NextOtpButtonListener) userRowView.getTag();
            View nextOtp = userRowView.findViewById(R.id.next_otp);
            if ((clickListener != null) && nextOtp.isEnabled()) {
              clickListener.onClick(userRowView);
            }
            userList.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
          }
        });
    userList.setOnEmptySpaceClickListener(() -> unselectItemOnList());

    contentNoAccounts = findViewById(R.id.content_no_accounts);
    contentAccountsPresent = findViewById(R.id.content_accounts_present);

    refreshLayoutByUserNumber();
    refreshOrientationState();

    findViewById(R.id.add_account_button)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                if (onboardingCompleted) {
                  addAccount();
                } else {
                  displayHowItWorksInstructions();
                }
              }
            });

    FloatingActionButton addAccountFab = (FloatingActionButton) findViewById(R.id.add_account_fab);
    addAccountFab.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showModalBottomSheet();
          }
        });

    // Make sure the Fab is always on top of the pin code list
    addAccountFab.bringToFront();
    contentAccountsPresent.invalidate();

    findViewById(R.id.first_account_message_button_done)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View view) {
                dismissFirstAccountAddedNoticeDisplay();
              }
            });

    userAdapter = new PinListAdapter(this, R.layout.user_row, users);

    userList.setAdapter(userAdapter);
    userList.setDropListener(
        new DropListener() {
          @Override
          public void drop(int from, int to) {
            userAdapter.notifyDataSetChanged();
          }
        });
    userList.setDragListener(
        new DragListener() {
          @Override
          public void drag(int from, int to) {
            if (from == to) {
              return;
            }
            List<AccountIndex> accounts = accountDb.getAccounts();
            AccountIndex firstIndex = accounts.get(from);
            AccountIndex secondIndex = accounts.get(to);
            try {
              // drag callback is fired in a worker thread, swapping the Ids doesn't affect the UI
              // thread.
              accountDb.swapId(firstIndex, secondIndex);
            } catch (AccountDbIdUpdateFailureException e) {
              Toast.makeText(
                      getApplicationContext(), R.string.accounts_reorder_failed, Toast.LENGTH_SHORT)
                  .show();
            }
            PinInfo.swapIndex(users, from, to);
          }
        });
    DragItemController dragItemController = new DragItemController(userList, this);
    dragItemController.setStartDraggingListener(
        new DragItemController.StartDraggingListener() {
          @Override
          public void startDragging() {
            unselectItemOnList();
          }
        });
    userList.setFloatViewManager(dragItemController);
    userList.setOnTouchListener(dragItemController);

    if (savedInstanceState == null) {
      // This is the first time this Activity is starting (i.e., not restoring previous state which
      // was saved, for example, due to orientation change)
      handleIntent(getIntent());
    }
  }

  @TargetApi(11)
  private void registerContextualActionBarForUserList() {
    // TODO: Consider switching to a single choice list when its action mode state starts to
    // automatically survive configuration (e.g., orientation) changes.
    //
    // Since action mode does not currently survive orientation changes in single choice mode, we
    // use multiple choice mode instead while still enforcing that only one item is checked at any
    // time.
    userList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
    final AbsListView.MultiChoiceModeListener multiChoiceModeListener =
        new AbsListView.MultiChoiceModeListener() {
          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
          }

          @Override
          public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
          }

          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            actionMode = mode;
            getMenuInflater().inflate(R.menu.account_list_context, menu);

            // This method is invoked both when the user starts the CAB and when the CAB is
            // recreated
            // after an orientation change. In the former case, no list items are checked. In the
            // latter, one of the items is checked.
            // Unfortunately, the Android framework does not preserve the state of the menu after
            // orientation changes. Thus, we need to update the menu.
            if (userList.getCheckedItemCount() > 0) {
              // Assume only one item can be checked, and blow up otherwise
              int position = getMultiSelectListSingleCheckedItemPosition(userList);
              updateCabForAccount(mode, menu, users[position]);
            }

            return true;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // This function can be triggered twice as user can double click the item while it is on
            // the
            // dismissing animation, we ignore the second click by checking if the item is
            // unselected.

            if (userList.getCheckedItemCount() == 0) {
              return false;
            }
            int position = getMultiSelectListSingleCheckedItemPosition(userList);
            if (onContextItemSelected(item, position)) {
              mode.finish();
              return true;
            }
            return false;
          }

          @Override
          public void onItemCheckedStateChanged(
              ActionMode mode, int position, long id, boolean checked) {
            if (checked) {
              mode.setTitle(users[position].getIndex().getStrippedName());

              // Ensure that only one item is checked, by unchecking all other checked items.
              SparseBooleanArray checkedItemPositions = userList.getCheckedItemPositions();
              for (int i = 0, len = userList.getCount(); i < len; i++) {
                if (i == position) {
                  continue;
                }
                boolean itemChecked = checkedItemPositions.get(i);
                if (itemChecked) {
                  userList.setItemChecked(i, false);
                }
              }

              updateCabForAccount(mode, mode.getMenu(), users[position]);
              userAdapter.notifyDataSetChanged();
            }
          }

          private void updateCabForAccount(ActionMode mode, Menu menu, PinInfo account) {
            mode.setTitle(account.getIndex().getStrippedName());
            updateMenuForAccount(menu, account);
            copyCodeToClipboard(account);
          }

          private void updateMenuForAccount(Menu menu, PinInfo account) {
            MenuItem copyMenuItem = menu.findItem(R.id.copy);
            if (copyMenuItem != null) {
              copyMenuItem.setVisible(false);
            }

            MenuItem renameMenuItem = menu.findItem(R.id.rename);
            if (renameMenuItem != null) {
              renameMenuItem.setVisible(isRenameAccountAvailableSupported(account));
            }

            MenuItem checkKeyValueMenuItem = menu.findItem(R.id.check_code);
            if (checkKeyValueMenuItem != null) {
              checkKeyValueMenuItem.setVisible(isCheckAccountKeyValueSupported(account));
            }
          }

          private void copyCodeToClipboard(PinInfo account) {
            copyStringToClipboard(userList.getContext(), account.getPin());
            Toast.makeText(
                    userList.getContext(), R.string.copied_to_clipboard_toast, Toast.LENGTH_SHORT)
                .show();
          }
        };
    userList.setMultiChoiceModeListener(multiChoiceModeListener);
  }

  /**
   * Gets the position of the one and only checked item in the provided list in multiple selection
   * mode.
   *
   * @return {@code 0}-based position.
   * @throws IllegalStateException if the list is not in multiple selection mode, or if the number
   *     of checked items is not {@code 1}.
   */
  @TargetApi(11)
  private static int getMultiSelectListSingleCheckedItemPosition(AbsListView list) {
    Preconditions.checkState(list.getCheckedItemCount() == 1);
    SparseBooleanArray checkedItemPositions = list.getCheckedItemPositions();
    Preconditions.checkState(checkedItemPositions != null);
    for (int i = 0, len = list.getCount(); i < len; i++) {
      boolean itemChecked = checkedItemPositions.get(i);
      if (itemChecked) {
        return i;
      }
    }

    throw new IllegalStateException("No items checked");
  }

  private static void copyStringToClipboard(Context context, String code) {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(context.getString(R.string.clipboard_label), code);
    clipboard.setPrimaryClip(clip);
  }

  /**
   * Reacts to the {@link Intent} that started this activity or arrived to this activity without
   * restarting it (i.e., arrived via {@link #onNewIntent(Intent)}). Does nothing if the provided
   * intent is {@code null}.
   */
  protected void handleIntent(Intent intent) {
    if (intent == null) {
      return;
    }

    String action = intent.getAction();
    if (ACTION_SCAN_BARCODE.equals(action)) {
      boolean startFromAddAccountActivity =
          intent.getBooleanExtra(BarcodeCaptureActivity.INTENT_EXTRA_START_FROM_ADD_ACCOUNT, false);
      scanBarcode(startFromAddAccountActivity);
    } else if (Intent.ACTION_VIEW.equals(action)) {
      interpretScanResult(intent.getData(), true);
    } else if ((action == null) || (Intent.ACTION_MAIN.equals(action))) {
      updateFirstAccountAddedNoticeDisplay();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putBoolean(
        KEY_FIRST_ACCOUNT_ADDED_NOTICE_DISPLAY_REQUIRED, firstAccountAddedNoticeDisplayRequired);
  }

  @SuppressWarnings("deprecation") // TODO: refactor to use savedInstanceState instead
  @Override
  public Object onRetainCustomNonConfigurationInstance() {
    return users; // save state of users and currently displayed PINs
  }

  // Because this activity is marked as singleTop, new launch intents will be
  // delivered via this API instead of onResume().
  // Override here to catch otpauth:// URL being opened from QR code reader.
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
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
    darkModeEnabled = preferences.getBoolean(KEY_DARK_MODE_ENABLED, false);
    onboardingCompleted = preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    refreshToolbarAndStatusBarStyle();
  }

  @Override
  protected void onStop() {
    stopTotpCountdownTask();

    super.onStop();
  }

  private void updateCodesAndStartTotpCountdownTask() {
    stopTotpCountdownTask();

    totpCountdownTask =
        new TotpCountdownTask(totpCounter, totpClock, TOTP_COUNTDOWN_REFRESH_PERIOD_MILLIS);
    totpCountdownTask.setListener(
        new TotpCountdownTask.Listener() {
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

    totpCountdownTask.startAndNotifyListener();
  }

  private void stopTotpCountdownTask() {
    if (totpCountdownTask != null) {
      totpCountdownTask.stop();
      totpCountdownTask = null;
    }
  }

  /**
   * On non-tablet devices, we lock the screen to portrait mode in case we are showing the begin
   * screen (i.e. no account) or showing the notice for first account added. On tablet devices, we
   * accept both portrait and landscape mode.
   */
  private void refreshOrientationState() {
    boolean isTablet = getResources().getBoolean(R.bool.isTablet);
    if (!isTablet && (getAccountCount() == 0 || firstAccountAddedNoticeDisplayRequired)) {
      // Lock to portrait mode.
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
    } else {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    }
  }

  /**
   * Refresh the style of the toolbar and status bar based on the layout being showed to user. When
   * there is no account, the toolbar must be non-shadowed, with blue color. Otherwise, the toolbar
   * must be shadowed (Not supported for pre-Lollipop) and the color will be depended on the current
   * UI mode (light/dark). The status bar's color (Not supported for pre-Lollipop also change color
   * based on the UI mode.
   */
  private void refreshToolbarAndStatusBarStyle() {
    // Shadow of the toolbar is not supported for Pre-Lollipop. We need to show a fake shadow on
    // pre-Lollipop devices.
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      toolbar.setElevation(
          getResources()
              .getDimension(
                  users.length > 0
                      ? R.dimen.toolbar_elevation_shadow
                      : R.dimen.toolbar_elevation_no_shadow));
      findViewById(R.id.toolbar_shadow).setVisibility(View.GONE);
      // Change status bar's color to dark if the dark mode is enabled and there is
      // at least one account.
      if (users.length > 0 && darkModeEnabled) {
        getWindow().setStatusBarColor(getResources().getColor(R.color.statusBarColorDark));
      } else {
        getWindow().setStatusBarColor(getResources().getColor(R.color.statusBarColor));
      }
    } else {
      findViewById(R.id.toolbar_shadow).setVisibility(View.VISIBLE);
    }
    if (users.length == 0) {
      toolbar.setBackgroundResource(R.color.google_blue500);
    } else {
      if (darkModeEnabled) {
        toolbar.setBackgroundResource(R.color.actionBarColorDark);
      } else {
        toolbar.setBackgroundResource(R.color.actionBarColor);
      }
    }
  }

  /**
   * Display the list of accounts if there are accounts, otherwise display a different layout
   * explaining the user how this app works and providing the user with an easy way to add an
   * account.
   */
  private void refreshLayoutByUserNumber() {
    int numUsers = users.length;
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      getSupportActionBar().setDisplayShowTitleEnabled(numUsers > 0);
    }
    contentNoAccounts.setVisibility(numUsers == 0 ? View.VISIBLE : View.GONE);
    contentAccountsPresent.setVisibility(numUsers > 0 ? View.VISIBLE : View.GONE);
    refreshToolbarAndStatusBarStyle();
  }

  /** Display list of user account names and updated pin codes. */
  private void refreshView() {
    refreshView(false);
  }

  private void setTotpCountdownPhase(double phase) {
    totpCountdownPhase = phase;
    updateCountdownIndicators();
  }

  private void setTotpCountdownPhaseFromTimeTillNextValue(long millisRemaining) {
    setTotpCountdownPhase(
        ((double) millisRemaining) / Utilities.secondsToMillis(totpCounter.getTimeStep()));
  }

  private void refreshVerificationCodes() {
    // When this function is called by the timer, the dragging action will be dismissed, we need
    // to re-select the current selected item in the pin code list if available
    refreshView();
    setTotpCountdownPhase(1.0);
  }

  private void updateCountdownIndicators() {
    for (int i = 0, len = userList.getChildCount(); i < len; i++) {
      View listEntry = userList.getChildAt(i);
      CountdownIndicator indicator = listEntry.findViewById(R.id.countdown_icon);
      if (indicator != null) {
        indicator.setPhase(totpCountdownPhase);
      }
    }
  }

  /**
   * Display list of user account names and updated pin codes.
   *
   * @param isAccountModified if true, force full refresh
   */
  @VisibleForTesting
  public void refreshView(boolean isAccountModified) {
    List<AccountIndex> accounts = accountDb.getAccounts();
    int userCount = accounts.size();

    if (userCount > 0) {
      boolean newListRequired = isAccountModified || users.length != userCount;
      if (newListRequired) {
        users = new PinInfo[userCount];
      }

      for (int i = 0; i < userCount; ++i) {
        AccountIndex user = accounts.get(i);
        try {
          computeAndDisplayPin(user, i, false);
        } catch (OtpSourceException ignored) {
          // Ignore
        }
      }

      if (newListRequired) {
        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
        // Make the list display the data from the newly created array of accounts
        // This forces the list to scroll to top.
        userAdapter = new PinListAdapter(this, R.layout.user_row, users);
        userList.setAdapter(userAdapter);
      }

      userAdapter.notifyDataSetChanged();
    } else {
      users = new PinInfo[0]; // clear any existing user PIN state
    }

    refreshLayoutByUserNumber();
    refreshFirstAccountAddedNoticeDisplay();
    refreshOrientationState();
  }

  /** Unselect the item on the pin code list if available. */
  @TargetApi(11)
  private void unselectItemOnList() {
    if (actionMode != null) {
      actionMode.finish();
      actionMode = null;
      try {
        int selected = getMultiSelectListSingleCheckedItemPosition(userList);
        userList.setItemChecked(selected, false);
      } catch (IllegalStateException e) {
        // No item is selected.
        Log.e(getString(R.string.app_name), LOCAL_TAG, e);
      }
    }
  }

  /**
   * This function shows the "You're All Set" flow to the user when the very first account is added.
   * If it is not the first account, this function shows the default pin code list. As two kind of
   * views use the same pin code list, we need to change visibility of some components as well as
   * modifying the pin code list parameters.
   */
  private void refreshFirstAccountAddedNoticeDisplay() {
    int[] viewIdArrayForFirstAccountAddedNotice = {
      R.id.first_account_message_header,
      R.id.first_account_message_detail,
      R.id.first_account_message_button_done
    };

    int userListPaddingTop;
    int userListPaddingBottom;
    LayoutParams layoutParams = userList.getLayoutParams();

    if (firstAccountAddedNoticeDisplayRequired) {
      // Update view visibility
      for (int viewId : viewIdArrayForFirstAccountAddedNotice) {
        findViewById(viewId).setVisibility(View.VISIBLE);
      }
      findViewById(R.id.add_account_fab).setVisibility(View.GONE);

      // Figure out layout parameters
      userListPaddingTop = getResources().getDimensionPixelSize(R.dimen.pincode_list_no_paddingTop);
      userListPaddingBottom =
          getResources().getDimensionPixelSize(R.dimen.pincode_list_no_paddingBottom);
      layoutParams.height = LayoutParams.WRAP_CONTENT;
    } else {
      // Update view visibility
      for (int viewId : viewIdArrayForFirstAccountAddedNotice) {
        findViewById(viewId).setVisibility(View.GONE);
      }
      findViewById(R.id.add_account_fab).setVisibility(View.VISIBLE);

      // Figure out layout parameters
      userListPaddingTop = getResources().getDimensionPixelSize(R.dimen.pincode_list_paddingTop);
      userListPaddingBottom =
          getResources().getDimensionPixelSize(R.dimen.pincode_list_paddingBottom);
      layoutParams.height = LayoutParams.MATCH_PARENT;
    }

    userList.setPadding(0, userListPaddingTop, 0, userListPaddingBottom);
    userList.setLayoutParams(layoutParams);
  }

  /**
   * Computes the PIN and saves it in users. This currently runs in the UI thread so it should not
   * take more than a second or so. If necessary, we can move the computation to a background
   * thread.
   *
   * @param user the user account to display with the PIN
   * @param position the index for the screen of this user and PIN
   * @param computeHotp true if we should increment counter and display new hotp
   */
  public void computeAndDisplayPin(AccountIndex user, int position, boolean computeHotp)
      throws OtpSourceException {

    PinInfo currentPin;
    if (users[position] != null) {
      currentPin = users[position]; // existing PinInfo, so we'll update it
    } else {
      OtpType type = accountDb.getType(user);
      currentPin = new PinInfo(user, type == OtpType.HOTP);
      currentPin.setPin(getString(R.string.empty_pin));
      currentPin.setIsHotpCodeGenerationAllowed(true);
    }

    if (!currentPin.isHotp() || computeHotp) {
      // Always safe to recompute, because this code path is only
      // reached if the account is:
      // - Time-based, in which case getNextCode() does not change state.
      // - Counter-based (HOTP) and computeHotp is true.
      currentPin.setPin(otpProvider.getNextCode(user));
      currentPin.setIsHotpCodeGenerationAllowed(true);
    }

    users[position] = currentPin;
  }

  /**
   * Parses a secret value from a URI. The format will be:
   *
   * <p>otpauth://totp/user@example.com?secret=FFF...
   *
   * <p>otpauth://hotp/user@example.com?secret=FFF...&counter=123
   *
   * @param uri The URI containing the secret key
   * @param confirmBeforeSave a boolean to indicate if the user should be prompted for confirmation
   *     before updating the otp account information.
   */
  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  private void parseSecret(Uri uri, boolean confirmBeforeSave) {
    final String scheme = uri.getScheme().toLowerCase();
    final String path = uri.getPath();
    final String authority = uri.getAuthority();
    final String name;
    final String issuer;
    final String secret;
    final OtpType type;
    final Integer counter;

    if (!OTP_SCHEME.equals(scheme)) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid or missing scheme in uri");
      showDialog(DIALOG_ID_INVALID_QR_CODE);
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
          showDialog(DIALOG_ID_INVALID_QR_CODE);
          return;
        }
      } else {
        counter = AccountDb.DEFAULT_HOTP_COUNTER;
      }
    } else {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid or missing authority in uri");
      showDialog(DIALOG_ID_INVALID_QR_CODE);
      return;
    }

    name = validateAndGetNameInPath(path);
    if (Strings.isNullOrEmpty(name)) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Missing user name in uri");
      showDialog(DIALOG_ID_INVALID_QR_CODE);
      return;
    }

    issuer = uri.getQueryParameter(ISSUER_PARAM);
    AccountIndex index = new AccountIndex(name, issuer);
    secret = uri.getQueryParameter(SECRET_PARAM);

    if (secret == null || secret.length() == 0) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Secret key not found in URI");
      showDialog(DIALOG_ID_INVALID_SECRET_IN_QR_CODE);
      return;
    }

    if (AccountDb.getSigningOracle(secret) == null) {
      Log.e(getString(R.string.app_name), LOCAL_TAG + ": Invalid secret key");
      showDialog(DIALOG_ID_INVALID_SECRET_IN_QR_CODE);
      return;
    }

    if (secret.equals(accountDb.getSecret(index))
        && counter.equals(accountDb.getCounter(index))
        && type == accountDb.getType(index)) {
      return; // nothing to update.
    }

    if (confirmBeforeSave) {
      Bundle dialogArgs = new Bundle();
      dialogArgs.putSerializable(
          KEY_SAVE_KEY_DIALOG_PARAMS, new SaveKeyDialogParams(index, secret, type, counter));
      showDialog(DIALOG_ID_SAVE_KEY, dialogArgs);
    } else {
      saveSecretAndRefreshUserList(index, secret, type, counter);
    }
  }

  private static String validateAndGetNameInPath(String path) {
    if (path == null || !path.startsWith("/")) {
      return null;
    }
    // path is "/name", so remove leading "/", and trailing white spaces
    String name = path.substring(1).trim();
    if (name.length() == 0) {
      return null; // only white spaces.
    }
    return name;
  }

  /**
   * Saves the secret key to local storage on the phone and updates the displayed account list.
   *
   * @param index the intended {@link AccountIndex} to update/add
   * @param secret the secret key
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   */
  private void saveSecretAndRefreshUserList(
      AccountIndex index, String secret, OtpType type, Integer counter) {
    if (saveSecret(this, index, secret, type, counter)) {
      updateFirstAccountAddedNoticeDisplay();
      refreshView(true);
    }
  }

  /**
   * Saves the secret key to local storage on the phone.
   *
   * @param index the intended {@link AccountIndex} to update/add
   * @param secret the secret key
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   * @return {@code true} if the secret was saved, {@code false} otherwise.
   */
  public static boolean saveSecret(
      Context context, AccountIndex index, String secret, OtpType type, Integer counter) {
    if (secret != null) {
      AccountDb accountDb = DependencyInjector.getAccountDb();
      accountDb.add(index.getName(), secret, type, counter, null, index.getIssuer());
      // TODO: Consider having a display message that activities can call and it will present a
      // toast with a uniform duration, and perhaps update status messages (presuming we have a way
      // to remove them after they are stale).
      Toast.makeText(context, R.string.secret_saved, Toast.LENGTH_LONG).show();
      ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VIBRATE_DURATION);
      return true;
    } else {
      Log.e(LOCAL_TAG, "Trying to save an empty secret key");
      Toast.makeText(context, R.string.error_empty_secret, Toast.LENGTH_LONG).show();
      return false;
    }
  }

  /** Gets the number of accounts in the {@link AccountDb}. */
  private int getAccountCount() {
    return accountDb.getAccounts().size();
  }

  /**
   * Sets the number of accounts in {@link AccountDb} that were there during the most recent launch
   * of this "page". "Launch" is defined from the pespective of user's actions (starting the app,
   * performing an operation that changes this page, etc.).
   */
  private void setLastLaunchAccountCount(int accountCount) {
    preferences.edit().putInt(PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT, accountCount).commit();
  }

  /**
   * Gets the number of accounts in {@link AccountDb} that were there during the most recent launch
   * of this "page". "Launch" is defined from the pespective of user's actions (starting the app,
   * performing an operation that changes this page, etc.).
   */
  private int getLastLaunchAccountCount() {
    return preferences.getInt(PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT, -1);
  }

  /** Hides the notice that appears after the first account was added. */
  private void dismissFirstAccountAddedNoticeDisplay() {
    setLastLaunchAccountCount(getAccountCount());
    firstAccountAddedNoticeDisplayRequired = false;
    refreshFirstAccountAddedNoticeDisplay();
    refreshOrientationState();
  }

  /**
   * Displays or hides, as needed, the notice that appears after the first account was added. When
   * displayed, the notice replaces the default "enter PIN" prompt above the list of accounts.
   */
  private void updateFirstAccountAddedNoticeDisplay() {
    // IMPLEMENTATION NOTE: The notice is displayed iff there are accounts in AccountDb at the
    // moment and there were no accounts last time this Activity was launched by the user.

    int previousAccountCount = getLastLaunchAccountCount();
    if (previousAccountCount < 0) {
      // Never launched before with this feature implemented -- pretend that the number of accounts
      // hasn't changed since last launch.
      previousAccountCount = getAccountCount();
    }
    int currentAccountCount = getAccountCount();
    setLastLaunchAccountCount(currentAccountCount);
    firstAccountAddedNoticeDisplayRequired =
        (previousAccountCount == 0) && (currentAccountCount > 0);
    refreshFirstAccountAddedNoticeDisplay();
    refreshOrientationState();
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    PinInfo selectedUser = users[(int) info.id];
    getMenuInflater().inflate(R.menu.account_list_context, menu);
    menu.setHeaderTitle(selectedUser.getIndex().getStrippedName());

    if (!isRenameAccountAvailableSupported(selectedUser)) {
      menu.removeItem(R.id.rename);
    }

    if (!isCheckAccountKeyValueSupported(selectedUser)) {
      menu.removeItem(R.id.check_code);
    }

    mostRecentContextMenu = menu;
  }

  /** Checks whether the specified account may be renamed. */
  private static boolean isRenameAccountAvailableSupported(PinInfo account) {
    // "Rename" action should be visible for all accounts except the Google corp account.
    return !AccountDb.GOOGLE_CORP_ACCOUNT_NAME.equals(account.getIndex().getName());
  }

  /** Checks whether displaying a secret key check code is supported for the specified account. */
  private static boolean isCheckAccountKeyValueSupported(PinInfo account) {
    // "Check code" action should only be visible for HOTP accounts.
    return account.isHotp();
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    return onContextItemSelected(item, info.id);
  }

  /**
   * Invoked when a context menu/action bar item is clicked/tapped.
   *
   * @return {@code true} if the event was consumed, {@code false} otherwise.
   */
  @SuppressWarnings("deprecation")
  @FixWhenMinSdkVersion(11) // Switch to android.content.ClipboardManager.setPrimaryClip
  @TargetApi(11)
  private boolean onContextItemSelected(MenuItem item, long itemId) {
    Intent intent;
    final AccountIndex index = users[(int) itemId].getIndex(); // final so listener can see value

    // Can't use a switch() statement here because inline constants are turned off.
    if (item.getItemId() == R.id.copy) {
      copyStringToClipboard(this, users[(int) itemId].getPin());
      Toast.makeText(this, R.string.copied_to_clipboard_toast, Toast.LENGTH_SHORT).show();
      return true;
    } else if (item.getItemId() == R.id.check_code) {
      intent = new Intent(Intent.ACTION_VIEW);
      intent.setClass(this, CheckCodeActivity.class);
      intent.putExtra("index", index);
      startActivity(intent);
      return true;
    } else if (item.getItemId() == R.id.rename) {
      final Context context = this; // final so listener can see value
      final View frame =
          getLayoutInflater().inflate(R.layout.rename, (ViewGroup) findViewById(R.id.rename_root));
      final EditText nameEdit = frame.findViewById(R.id.rename_edittext);
      nameEdit.setText(index.getStrippedName()); // User can only edit the stripped name
      new AlertDialog.Builder(this)
          .setTitle(R.string.rename)
          .setView(frame)
          .setPositiveButton(R.string.submit, this.getRenameClickListener(context, index, nameEdit))
          .setNegativeButton(R.string.cancel, null)
          .show();
      return true;
    } else if (item.getItemId() == R.id.delete) {
      AlertDialog.Builder alertDialogBuilder =
          new AlertDialog.Builder(this)
              .setTitle(getString(R.string.remove_account_dialog_title, index))
              .setMessage(
                  Utilities.getStyledTextFromHtml(
                      getString(
                          accountDb.isGoogleAccount(index)
                              ? R.string.remove_google_account_dialog_message
                              : R.string.remove_account_dialog_message)))
              .setPositiveButton(
                  R.string.remove_account_dialog_button_remove,
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                      accountDb.delete(index);
                      dismissFirstAccountAddedNoticeDisplay();
                      refreshView(true);
                    }
                  })
              .setNegativeButton(R.string.cancel, null)
              .setIcon(R.drawable.quantum_ic_report_problem_grey600_24);
      alertDialogBuilder.show();
      return true;
    }
    return super.onContextItemSelected(item);
  }

  private DialogInterface.OnClickListener getRenameClickListener(
      final Context context, final AccountIndex user, final EditText nameEdit) {
    return new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int whichButton) {
        String newName = nameEdit.getText().toString().trim();
        AccountIndex newIndex = new AccountIndex(newName, user.getIssuer());
        if (!newIndex.getStrippedName().equals(user.getStrippedName())) {
          if (accountDb.findSimilarExistingIndex(newIndex) != null) {
            Toast.makeText(context, R.string.error_exists, Toast.LENGTH_LONG).show();
          } else {
            accountDb.rename(user, newName);
            dismissFirstAccountAddedNoticeDisplay();
            refreshView(true);
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
  public boolean onPrepareOptionsMenu(Menu menu) {
    // Only show dark mode option when there is at least 1 account.
    if (users.length >= 1) {
      menu.findItem(R.id.switch_ui_mode).setVisible(true);
      menu.findItem(R.id.switch_ui_mode)
          .setTitle(darkModeEnabled ? R.string.switch_ui_mode_light : R.string.switch_ui_mode_dark);
    } else {
      menu.findItem(R.id.switch_ui_mode).setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.how_it_works) {
      displayHowItWorksInstructions();
      return true;
    } else if (item.getItemId() == R.id.switch_ui_mode) {
      switchUiMode();
      return true;
    } else if (item.getItemId() == R.id.settings) {
      showSettings();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void displayHowItWorksInstructions() {
    Intent intent = new Intent(this, HowItWorksActivity.class);
    intent.putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, !onboardingCompleted);
    startActivity(intent);
  }

  private void switchUiMode() {
    darkModeEnabled = !darkModeEnabled;
    preferences.edit().putBoolean(KEY_DARK_MODE_ENABLED, darkModeEnabled).commit();
    // Restart the activity to apply new theme. Here we try to remove the fade animation to help
    // users feel the app just do some light refreshing.
    finish();
    overridePendingTransition(0, 0);
    startActivity(new Intent(this, getClass()));
    overridePendingTransition(0, 0);
  }

  private void showSettings() {
    startActivity(new Intent(this, SettingsActivity.class));
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    Log.i(getString(R.string.app_name), LOCAL_TAG + ": onActivityResult");
    if (requestCode == SCAN_REQUEST && resultCode == Activity.RESULT_OK) {
      // Grab the scan results and convert it into a URI
      String scanResult =
          (intent != null)
              ? intent.getStringExtra(BarcodeCaptureActivity.INTENT_EXTRA_BARCODE_VALUE)
              : null;
      Uri uri = (scanResult != null) ? Uri.parse(scanResult) : null;
      interpretScanResult(uri, false);
    }
  }

  private void showModalBottomSheet() {
    bottomSheetDialog = new BottomSheetDialog(this);
    bottomSheetDialog.setContentView(R.layout.main_bottom_sheet);
    bottomSheetDialog
        .findViewById(R.id.bottom_sheet_scan_barcode_layout)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View view) {
                startActivity(getLaunchIntentActionScanBarcode(AuthenticatorActivity.this, false));
                if (bottomSheetDialog != null) {
                  bottomSheetDialog.dismiss();
                }
              }
            });
    bottomSheetDialog
        .findViewById(R.id.bottom_sheet_enter_key_layout)
        .setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View view) {
                startActivity(new Intent(AuthenticatorActivity.this, EnterKeyActivity.class));
                if (bottomSheetDialog != null) {
                  bottomSheetDialog.dismiss();
                }
              }
            });
    bottomSheetDialog.show();
  }

  private void addAccount() {
    startActivity(new Intent(this, AddAccountActivity.class));
  }

  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  private void scanBarcode(boolean startFromAddAccountActivity) {
    // Check if the device has no camera. This check is for case when user click the Scan Barcode
    // option in the {@link AddAccountActivity}.
    if (!barcodeConditionChecker.isCameraAvailableOnDevice(this)) {
      showDialog(DIALOG_ID_CAMERA_NOT_AVAILABLE);
      return;
    }

    // Check that the device has google play services installed and
    // no earlier than the required version.
    if (!barcodeConditionChecker.isGooglePlayServicesAvailable(this)) {
      showDialog(DIALOG_ID_INSTALL_GOOGLE_PLAY_SERVICES);
      return;
    }

    // Note: The first time that an app using the barcode or face API is installed on a
    // device, GMS will download a native libraries to the device in order to do detection.
    // Usually this completes before the app is run for the first time.
    if (!barcodeConditionChecker.getIsBarcodeDetectorOperational(this)) {
      // Check for low storage.  If there is low storage, the native library will not be
      // downloaded, so detection will not become operational.
      if (barcodeConditionChecker.isLowStorage(this)) {
        showDialog(DIALOG_ID_LOW_STORAGE_FOR_BARCODE_SCANNER);
      } else {
        showDialog(DIALOG_ID_BARCODE_SCANNER_NOT_AVAILABLE);
      }
      return;
    }

    Intent intentScan = new Intent(this, BarcodeCaptureActivity.class);
    intentScan.putExtra(
        BarcodeCaptureActivity.INTENT_EXTRA_START_FROM_ADD_ACCOUNT, startFromAddAccountActivity);
    startActivityForResult(intentScan, SCAN_REQUEST);
  }

  public static Intent getLaunchIntentActionScanBarcode(
      Context context, boolean startFromAddAccountActivity) {
    return new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE)
        .putExtra(
            BarcodeCaptureActivity.INTENT_EXTRA_START_FROM_ADD_ACCOUNT, startFromAddAccountActivity)
        .setComponent(new ComponentName(context, AuthenticatorActivity.class));
  }

  /**
   * Interprets the QR code that was scanned by the user. Decides whether to launch the key
   * provisioning sequence or the OTP seed setting sequence.
   *
   * @param scanResult a URI holding the contents of the QR scan result
   * @param confirmBeforeSave a boolean to indicate if the user should be prompted for confirmation
   *     before updating the otp account information.
   */
  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  private void interpretScanResult(Uri scanResult, boolean confirmBeforeSave) {
    // The scan result is expected to be a URL that adds an account.

    // If confirmBeforeSave is true, the user has to confirm/reject the action.
    // We need to ensure that new results are accepted only if the previous ones have been
    // confirmed/rejected by the user. This is to prevent the attacker from sending multiple results
    // in sequence to confuse/DoS the user.
    if (confirmBeforeSave) {
      if (saveKeyIntentConfirmationInProgress) {
        Log.w(LOCAL_TAG, "Ignoring save key Intent: previous Intent not yet confirmed by user");
        return;
      }
      // No matter what happens below, we'll show a prompt which, once dismissed, will reset the
      // flag below.
      saveKeyIntentConfirmationInProgress = true;
    }

    // Sanity check
    if (scanResult == null) {
      showDialog(DIALOG_ID_INVALID_QR_CODE);
      return;
    }

    // See if the URL is an account setup URL containing a shared secret
    if (OTP_SCHEME.equals(scanResult.getScheme()) && scanResult.getAuthority() != null) {
      parseSecret(scanResult, confirmBeforeSave);
    } else {
      showDialog(DIALOG_ID_INVALID_QR_CODE);
    }
  }

  @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
  @Override
  protected Dialog onCreateDialog(final int id, Bundle args) {
    Dialog dialog = null;
    switch (id) {
        /** Prompt to download Google Play Services from Google Play. */
      case DIALOG_ID_INSTALL_GOOGLE_PLAY_SERVICES:
        AlertDialog.Builder dlBuilder = new AlertDialog.Builder(this);
        dlBuilder.setMessage(Html.fromHtml(getString(R.string.update_google_play_services)));
        dlBuilder.setPositiveButton(
            R.string.update_button,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int whichButton) {
                Intent intent =
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(GOOGLE_PLAY_SERVICES_INSTALL_FROM_GOOGLE_PLAY));
                try {
                  startActivity(intent);
                } catch (ActivityNotFoundException e) { // if no Google Play app
                  startActivity(
                      new Intent(
                          Intent.ACTION_VIEW,
                          Uri.parse(GOOGLE_PLAY_SERVICES_INSTALL_FROM_WEB_BROWSER)));
                }
              }
            });
        dlBuilder.setNegativeButton(R.string.not_update_button, null);
        dialog = dlBuilder.create();
        break;

      case DIALOG_ID_SAVE_KEY:
        final SaveKeyDialogParams saveKeyDialogParams =
            (SaveKeyDialogParams) args.getSerializable(KEY_SAVE_KEY_DIALOG_PARAMS);
        dialog =
            new AlertDialog.Builder(this)
                .setTitle(R.string.save_key_message)
                // TODO: Add support for a nicely labeled issuer in this dialog
                .setMessage(
                    saveKeyDialogParams.index.toString()) // Use the fully qualified name here
                .setIcon(R.drawable.quantum_ic_report_problem_grey600_24)
                .setPositiveButton(
                    R.string.ok,
                    new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int whichButton) {
                        saveSecretAndRefreshUserList(
                            saveKeyDialogParams.index,
                            saveKeyDialogParams.secret,
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
        dialog.setOnDismissListener(
            new DialogInterface.OnDismissListener() {
              @Override
              public void onDismiss(DialogInterface dialog) {
                removeDialog(id);
                onSaveKeyIntentConfirmationPromptDismissed();
              }
            });
        break;

      case DIALOG_ID_INVALID_QR_CODE:
        dialog =
            createOkAlertDialog(
                R.string.error_title,
                R.string.error_qr,
                R.drawable.quantum_ic_report_problem_grey600_24);
        markDialogAsResultOfSaveKeyIntent(dialog);
        break;

      case DIALOG_ID_INVALID_SECRET_IN_QR_CODE:
        dialog =
            createOkAlertDialog(
                R.string.error_title,
                R.string.error_uri,
                R.drawable.quantum_ic_report_problem_grey600_24);
        markDialogAsResultOfSaveKeyIntent(dialog);
        break;

      case DIALOG_ID_BARCODE_SCANNER_NOT_AVAILABLE:
        dialog = createOkAlertDialog(0, R.string.barcode_scanner_not_available, 0);
        break;

      case DIALOG_ID_LOW_STORAGE_FOR_BARCODE_SCANNER:
        dialog =
            createOkAlertDialog(
                R.string.low_storage_error_title, R.string.low_storage_error_detail, 0);
        break;

      case DIALOG_ID_CAMERA_NOT_AVAILABLE:
        dialog =
            new AlertDialog.Builder(this)
                .setTitle(R.string.camera_not_found_on_device_title)
                .setMessage(R.string.camera_not_found_on_device_detail)
                .setPositiveButton(R.string.close, null)
                .create();
        break;

        default:
          // do nothing
    }
    return dialog;
  }

  private void markDialogAsResultOfSaveKeyIntent(Dialog dialog) {
    dialog.setOnDismissListener(
        new OnDismissListener() {
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
    saveKeyIntentConfirmationInProgress = false;
  }

  /**
   * Create dialog with supplied ids.
   *
   * @param titleId title is not set if titleId is 0.
   * @param messageId messageId of the message.
   * @param iconId icon is not set if iconId is 0.
   */
  private Dialog createOkAlertDialog(int titleId, int messageId, int iconId) {
    AlertDialog.Builder builder =
        new AlertDialog.Builder(this).setMessage(messageId).setPositiveButton(R.string.ok, null);
    if (titleId != 0) {
      builder.setTitle(titleId);
    }
    if (iconId != 0) {
      builder.setIcon(iconId);
    }
    return builder.create();
  }

  /** Scale to use for the text displaying the PIN numbers. */
  private static final float PIN_TEXT_SCALEX_NORMAL = 1.0f;
  /** Underscores are shown slightly smaller. */
  private static final float PIN_TEXT_SCALEX_UNDERSCORE = 0.87f;

  /** Listener for the Button that generates the next OTP value. */
  private class NextOtpButtonListener implements OnClickListener {
    private final Handler handler = new Handler();
    private final PinInfo account;

    private NextOtpButtonListener(PinInfo account) {
      this.account = account;
    }

    @SuppressWarnings("deprecation") // TODO: refactor to use DialogFrament
    @Override
    public void onClick(View v) {
      int position = findAccountPositionInList();
      if (position == -1) {
        throw new RuntimeException("Account not in list: " + account);
      }

      try {
        computeAndDisplayPin(account.getIndex(), position, true);
      } catch (OtpSourceException e) {
        throw new RuntimeException("Failed to generate OTP for account", e);
      }

      final String pin = account.getPin();

      // Temporarily disable code generation for this account
      account.setIsHotpCodeGenerationAllowed(false);
      userAdapter.notifyDataSetChanged();
      // The delayed operation below will be invoked once code generation is yet again allowed for
      // this account. The delay is in wall clock time (monotonically increasing) and is thus not
      // susceptible to system time jumps.
      handler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              account.setIsHotpCodeGenerationAllowed(true);
              userAdapter.notifyDataSetChanged();
            }
          },
          HOTP_MIN_TIME_INTERVAL_BETWEEN_CODES);
      // The delayed operation below will hide this OTP to prevent the user from seeing this OTP
      // long after it's been generated (and thus hopefully used).
      handler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              if (!pin.equals(account.getPin())) {
                return;
              }
              account.setPin(getString(R.string.empty_pin));
              userAdapter.notifyDataSetChanged();
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
      for (int i = 0, len = users.length; i < len; i++) {
        if (users[i].equals(account)) {
          return i;
        }
      }

      return -1;
    }
  }

  /** Displays the list of users and the current OTP values. */
  private class PinListAdapter extends ArrayAdapter<PinInfo> {

    public PinListAdapter(Context context, int userRowId, PinInfo[] items) {
      super(context, userRowId, items);
    }

    /**
     * Displays the user and OTP for the specified position. For HOTP, displays the button for
     * generating the next OTP value; for TOTP, displays the countdown indicator.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = getLayoutInflater();
      PinInfo currentPin = getItem(position);

      View row;
      if (convertView != null) {
        // Reuse an existing view
        row = convertView;
      } else {
        // Create a new view
        row = inflater.inflate(R.layout.user_row, null);

        // This is a workaround to address the issue on DragSortListView that complex
        // LinearLayout doesn't take the full width.
        // https://github.com/bauerca/drag-sort-listview/issues/73
        row.setLayoutParams(
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      }

      TextView pinView = row.findViewById(R.id.pin_value);
      TextView userView = row.findViewById(R.id.current_user);
      View buttonView = row.findViewById(R.id.next_otp);
      CountdownIndicator countdownIndicator = row.findViewById(R.id.countdown_icon);

      // We only show drag handle on selected item when the number of items is larger than 1.
      boolean showDragHandle = false;
      try {
        if (getMultiSelectListSingleCheckedItemPosition(userList) == position && getCount() >= 2) {
          showDragHandle = true;
        }
      } catch (IllegalStateException ignored) {
        // No pin code is selected.
      }
      row.findViewById(R.id.user_row_drag_handle_image)
          .setVisibility(showDragHandle ? View.VISIBLE : View.GONE);

      if (currentPin.isHotp()) {
        buttonView.setVisibility(View.VISIBLE);
        buttonView.setEnabled(currentPin.isHotpCodeGenerationAllowed());
        ((ViewGroup) row)
            .setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); // makes long press work
        NextOtpButtonListener clickListener = new NextOtpButtonListener(currentPin);
        buttonView.setOnClickListener(clickListener);
        row.setTag(clickListener);

        countdownIndicator.setVisibility(View.GONE);
      } else { // TOTP, so no button needed
        buttonView.setVisibility(View.GONE);
        buttonView.setOnClickListener(null);
        row.setTag(null);

        countdownIndicator.setVisibility(View.VISIBLE);
        countdownIndicator.setPhase(totpCountdownPhase);
      }

      if (getString(R.string.empty_pin).equals(currentPin.getPin())) {
        pinView.setTextScaleX(PIN_TEXT_SCALEX_UNDERSCORE); // smaller gap between underscores
      } else {
        pinView.setTextScaleX(PIN_TEXT_SCALEX_NORMAL);
      }
      pinView.setText(Utilities.getStyledPincode(currentPin.getPin()));
      userView.setText(
          Utilities.getCombinedTextForIssuerAndAccountName(
              currentPin.getIndex().getIssuer(), currentPin.getIndex().getStrippedName()));

      return row;
    }
  }

  /**
   * Custom {@link DragSortController} object for the pin code list to provide the shadow for
   * dragged item as well as a listener when user start dragging to unselect the item in list.
   */
  private static class DragItemController extends DragSortController {

    /** Handle event when user start dragging. */
    public interface StartDraggingListener {

      /** Event when user start dragging the item. */
      void startDragging();
    }

    private final Activity activity;
    private final EmptySpaceClickableDragSortListView dragSortListView;
    private StartDraggingListener startDraggingListener;
    private Bitmap floatBitmap;
    private View floatView;

    public DragItemController(
        EmptySpaceClickableDragSortListView dragSortListView, Activity activity) {
      super(dragSortListView, R.id.user_row_drag_handle, DragSortController.ON_DOWN, 0);
      this.dragSortListView = dragSortListView;
      this.activity = activity;
      setRemoveEnabled(false);
    }

    public void setStartDraggingListener(StartDraggingListener startDraggingListener) {
      this.startDraggingListener = startDraggingListener;
    }

    @Override
    public int startDragPosition(MotionEvent ev) {
      ListAdapter adapter = dragSortListView.getAdapter();
      // We lock the dragging when the number of account is 0 or 1.
      if (adapter == null || adapter.getCount() <= 1) {
        return DragSortController.MISS;
      }

      // We only allow dragging on selected item.
      int position = super.startDragPosition(ev);
      boolean allowDragging = false;

      try {
        if (getMultiSelectListSingleCheckedItemPosition(dragSortListView) == position) {
          allowDragging = true;
        }
      } catch (IllegalStateException ignored) {
        // No pin code is selected.
      }

      if (!allowDragging) {
        position = DragSortController.MISS;
      }

      if (position != DragSortController.MISS && startDraggingListener != null) {
        startDraggingListener.startDragging();
      }
      return position;
    }

    @Override
    public View onCreateFloatView(int position) {
      View view =
          dragSortListView.getChildAt(
              position
                  + dragSortListView.getHeaderViewsCount()
                  - dragSortListView.getFirstVisiblePosition());
      if (view == null) {
        return null;
      }
      View userRowLayout = view.findViewById(R.id.user_row_layout);
      if (userRowLayout != null) {
        userRowLayout.setPressed(false);
        userRowLayout.setSelected(false);
        userRowLayout.setActivated(false);
      }

      // Take a picture of the selected item and add it to the holder with shadow we have prepared.
      view.setDrawingCacheEnabled(true);
      floatBitmap = Bitmap.createBitmap(view.getDrawingCache());
      view.setDrawingCacheEnabled(false);

      if (floatView == null) {
        LayoutInflater inflater = activity.getLayoutInflater();
        floatView = inflater.inflate(R.layout.user_row_dragged, null);
      }
      ImageView imageView = floatView.findViewById(R.id.user_row_dragged_image);
      imageView.setImageBitmap(floatBitmap);
      imageView.setLayoutParams(new RelativeLayout.LayoutParams(view.getWidth(), view.getHeight()));

      return floatView;
    }

    /** Removes the Bitmap created in onCreateFloatView() and tells the system to recycle it. */
    @Override
    public void onDestroyFloatView(View floatView) {
      ImageView imageView = floatView.findViewById(R.id.user_row_dragged_image);
      if (imageView != null) {
        imageView.setImageDrawable(null);
      }
      floatBitmap.recycle();
      floatBitmap = null;
    }
  }

  /** Parameters to the {@link AuthenticatorActivity#DIALOG_ID_SAVE_KEY} dialog. */
  private static class SaveKeyDialogParams implements Serializable {
    private final AccountIndex index;
    private final String secret;
    private final OtpType type;
    private final Integer counter;

    private SaveKeyDialogParams(AccountIndex index, String secret, OtpType type, Integer counter) {
      Preconditions.checkNotNull(index);
      Preconditions.checkNotNull(secret);
      Preconditions.checkNotNull(type);
      Preconditions.checkNotNull(counter);
      this.index = index;
      this.secret = secret;
      this.type = type;
      this.counter = counter;
    }
  }
}
