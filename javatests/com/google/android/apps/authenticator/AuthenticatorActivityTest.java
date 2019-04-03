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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.pressMenuKey;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.BundleMatchers.hasEntry;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtras;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.action.GeneralSwipeAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.barcode.BarcodeCaptureActivity;
import com.google.android.apps.authenticator.barcode.BarcodeConditionChecker;
import com.google.android.apps.authenticator.common.ApplicationContext;
import com.google.android.apps.authenticator.enroll2sv.wizard.AddAccountActivity;
import com.google.android.apps.authenticator.howitworks.HowItWorksActivity;
import com.google.android.apps.authenticator.otp.AccountDb;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.otp.CheckCodeActivity;
import com.google.android.apps.authenticator.otp.EnterKeyActivity;
import com.google.android.apps.authenticator.otp.OtpProvider;
import com.google.android.apps.authenticator.otp.OtpSource;
import com.google.android.apps.authenticator.otp.PinInfo;
import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.otp.TotpCounter;
import com.google.android.apps.authenticator.settings.SettingsActivity;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator.time.Clock;
import com.google.android.apps.authenticator.util.EmptySpaceClickableDragSortListView;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.android.apps.authenticator.util.permissions.PermissionRequestor;
import com.google.android.apps.authenticator2.R;
import com.google.common.collect.Iterables;
import com.mobeta.android.dslv.DragSortItemView;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Callable;
import javax.inject.Singleton;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit tests for {@link AuthenticatorActivity} (part/shard 1). */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AuthenticatorActivityTest {

  private static final String HOTP_ACCOUNT_NAME = "johndoeHotp@gmail.com";
  private static final String FIRST_HOTP_EXPECTED_PIN = "683298";
  private static final long NOW_MILLIS = 1000L;
  private static final int CLICK_DELAY_INTERVAL = 200;

  private AccountDb accountDb;
  private OtpSource otpSource;
  @Mock private TotpClock mockTotpClock;
  @Mock private Clock mockClock;
  @Mock private BarcodeConditionChecker mockBarcodeConditionChecker;
  @Mock private PermissionRequestor mockPermissionRequestor;

  @Rule public ActivityTestRule<AuthenticatorActivity> activityTestRule =
      new ActivityTestRule<>(
          AuthenticatorActivity.class, /* initialTouchMode= */ true, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    Intents.init();

    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    accountDb = DependencyInjector.getAccountDb();

    initMocks(this);
    otpSource = new OtpProvider(accountDb, mockTotpClock);

    // To launch the SettingsActivity, setting the mock module here.
    // (SettingsActivity is used in the testOptionsMenuSettings test)
    DaggerInjector.init(new MockModule());
    when(mockTotpClock.nowMillis()).thenReturn(NOW_MILLIS);

    // Make sure the notice for first account added is not showing.
    PreferenceManager
        .getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
        .edit()
        .putInt(AuthenticatorActivity.PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT, 1)
        .commit();

    when(mockBarcodeConditionChecker.isCameraAvailableOnDevice(any(Activity.class)))
        .thenReturn(true);
    when(mockBarcodeConditionChecker.isGooglePlayServicesAvailable(any(Activity.class)))
        .thenReturn(true);
    when(mockBarcodeConditionChecker.getIsBarcodeDetectorOperational(any(Activity.class)))
        .thenReturn(true);
    when(mockBarcodeConditionChecker.isLowStorage(any(Activity.class))).thenReturn(false);

    when(mockPermissionRequestor.shouldShowRequestPermissionRationale(
            any(Activity.class), eq(Manifest.permission.CAMERA)))
        .thenReturn(false);
    when(mockPermissionRequestor.checkSelfPermission(
            any(Context.class), eq(Manifest.permission.CAMERA)))
        .thenReturn(PackageManager.PERMISSION_GRANTED);

    setDarkModeEnabled(InstrumentationRegistry.getInstrumentation().getTargetContext(), false);
  }

  @After
  public void tearDown() throws Exception {
    // Stop the activity to avoid it using the DependencyInjector after it's been closed.
    TestUtilities.invokeFinishActivityOnUiThread(activityTestRule.getActivity());

    DependencyInjector.close();

    Intents.release();
  }

  @Test
  public void testGetTitle() {
    activityTestRule.launchActivity(null);
    View contentNoAccounts = activityTestRule.getActivity().findViewById(R.id.content_no_accounts);
    assertThat(contentNoAccounts).isNotNull();
    View contentAccountsPresent =
        activityTestRule.getActivity().findViewById(R.id.content_accounts_present);
    assertThat(contentAccountsPresent).isNotNull();
    assertThat(activityTestRule.getActivity().getTitle().toString())
        .isEqualTo(activityTestRule.getActivity().getString(R.string.app_name));
  }

  @Test
  public void testOnboardingNotCompletedForNewUser() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    setOnboardingCompletedPreference(context, false);
    AuthenticatorActivity activity = activityTestRule.launchActivity(null);
    assertThat(activity.onboardingCompleted).isFalse();
  }

  @Test
  public void testOnboardingCompletedForUpgradingUser() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    setOnboardingCompletedPreference(context, false);
    AuthenticatorActivity activity = activityTestRule.launchActivity(null);
    assertThat(activity.onboardingCompleted).isTrue();
  }

  //////////////////////// Main screen UI Tests ///////////////////////////////

  @Test
  public void testNoAccountUi() {
    activityTestRule.launchActivity(null);
    onView(withId(R.id.user_list)).check(matches(not(isDisplayed())));
    onView(withId(R.id.add_account_fab)).check(matches(not(isDisplayed())));

    onView(withId(R.id.add_account_button)).check(matches(isDisplayed()));
    onView(withId(R.id.content_no_accounts)).check(matches(isDisplayed()));
  }

  @Test
  public void testBeginSetupButtonWithFirstOnboarding() {
    setOnboardingCompletedPreference(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), false);

    activityTestRule.launchActivity(null);

    onView(withId(R.id.add_account_button)).perform(click());

    Intent expectedIntent = new Intent(activityTestRule.getActivity(), HowItWorksActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(
                allOf(
                    hasEntry(
                        equalTo(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE),
                        equalTo(true))))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testBeginSetupButtonOnboardingCompleted() {
    setOnboardingCompletedPreference(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), true);

    activityTestRule.launchActivity(null);

    onView(withId(R.id.add_account_button)).perform(click());

    Intent expectedIntent = new Intent(activityTestRule.getActivity(), AddAccountActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(equalTo(expectedIntent.getExtras()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testScreenWithOneAccountNoFirstNoticeDisplayed() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_header)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_detail)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_button_done)).check(matches(not(isDisplayed())));
  }

  @Test
  public void testScreenWithOneTotpAccountWithFirstNoticeDisplayed() {
    PreferenceManager
        .getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
        .edit()
        .putInt(AuthenticatorActivity.PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT, 0)
        .commit();

    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_header)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_detail)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_button_done)).check(matches(isDisplayed()));

    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo("johndoeTotp@gmail.com");
    assertThat(Integer.parseInt(pin) >= 0 && Integer.parseInt(pin) <= 999999).isTrue();
    assertThat(pin.length()).isEqualTo(6);
    assertThat(listEntry.findViewById(R.id.next_otp).isShown()).isFalse();
    assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isTrue();

    // Click the "Done" button to dismiss the notice message
    onView(withId(R.id.first_account_message_button_done)).perform(click());
    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_header)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_detail)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_button_done)).check(matches(not(isDisplayed())));
  }

  @Test
  public void testScreenWithOneHotpAccountWithFirstNoticeDisplayed() {
    PreferenceManager
        .getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
        .edit()
        .putInt(AuthenticatorActivity.PREF_KEY_LAST_LAUNCH_ACCOUNT_COUNT, 0)
        .commit();

    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_header)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_detail)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_button_done)).check(matches(isDisplayed()));

    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo(HOTP_ACCOUNT_NAME);
    // starts empty
    assertThat(pin).isEqualTo(activityTestRule.getActivity().getString(R.string.empty_pin));
    assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isFalse();

    // Get next OTP value by clicking the "Get next code" button of this list item.
    View buttonView = listEntry.findViewById(R.id.next_otp);
    assertThat(buttonView.isShown()).isTrue();
    TestUtilities.clickView(InstrumentationRegistry.getInstrumentation(), buttonView);
    pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(pin).isEqualTo(FIRST_HOTP_EXPECTED_PIN);
    assertThat(accountDb.getCounter(new AccountIndex(HOTP_ACCOUNT_NAME, null)))
        .isEqualTo(Integer.valueOf(1));

    // Check the styled pin code
    String styledPin = ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString();
    assertThat(Utilities.getStyledPincode(FIRST_HOTP_EXPECTED_PIN)).isEqualTo(styledPin);

    // Click the "Done" button to dismiss the notice message
    onView(withId(R.id.first_account_message_button_done)).perform(click());
    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.first_account_message_header)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_detail)).check(matches(not(isDisplayed())));
    onView(withId(R.id.first_account_message_button_done)).check(matches(not(isDisplayed())));
  }

  @Test
  public void testAddAccountFabShowingBottosheet() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.add_account_fab)).perform(click());

    assertThat(activityTestRule.getActivity().bottomSheetDialog).isNotNull();
    assertThat(activityTestRule.getActivity().bottomSheetDialog.isShowing()).isTrue();

    onView(withId(R.id.bottom_sheet_scan_barcode_layout)).check(matches(isDisplayed()));
    onView(withId(R.id.bottom_sheet_enter_key_layout)).check(matches(isDisplayed()));
  }

  @Test
  public void testAddAccountFabScanBarcodeItetap() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    AuthenticatorActivity activity = activityTestRule.launchActivity(null);

    ListView userList = activity.findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.add_account_fab)).perform(click());

    assertThat(activity.bottomSheetDialog).isNotNull();
    assertThat(activity.bottomSheetDialog.isShowing()).isTrue();
    onView(withId(R.id.bottom_sheet_scan_barcode_layout)).check(matches(isDisplayed()));
    onView(withId(R.id.bottom_sheet_scan_barcode_layout)).perform(click());

    Intent expectedIntent =
        AuthenticatorActivity.getLaunchIntentActionScanBarcode(
            activityTestRule.getActivity(), false);
    intended(
        allOf(
            hasAction(AuthenticatorActivity.ACTION_SCAN_BARCODE),
            hasComponent(equalTo(expectedIntent.getComponent()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testAddAccountFabEnterKeyItemTap() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    AuthenticatorActivity activity = activityTestRule.launchActivity(null);

    ListView userList = activity.findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);

    onView(withId(R.id.add_account_fab)).check(matches(isDisplayed()));
    onView(withId(R.id.add_account_fab)).perform(click());

    assertThat(activity.bottomSheetDialog).isNotNull();
    assertThat(activity.bottomSheetDialog.isShowing()).isTrue();

    onView(withId(R.id.bottom_sheet_enter_key_layout)).perform(click());
    Intent expectedIntent = new Intent(activity, EnterKeyActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(equalTo(expectedIntent.getExtras()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testGetOtpWithOneTotpAccount() {
    accountDb.add("johndoeTotp@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);

    assertThat(userList.getChildCount()).isEqualTo(1);
    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo("johndoeTotp@gmail.com");
    assertThat(Integer.parseInt(pin) >= 0 && Integer.parseInt(pin) <= 999999).isTrue();
    assertThat(pin.length()).isEqualTo(6);
    assertThat(listEntry.findViewById(R.id.next_otp).isShown()).isFalse();
    assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isTrue();
  }

  @Test
  public void testGetOtpWithOneHotpAccountUsingGetNextCodeButtonClick() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);
    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo(HOTP_ACCOUNT_NAME);
    // starts empty
    assertThat(pin).isEqualTo(activityTestRule.getActivity().getString(R.string.empty_pin));
    assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isFalse();

    // Get next OTP value by clicking the "Get next code" button of this list item.
    View buttonView = listEntry.findViewById(R.id.next_otp);
    assertThat(buttonView.isShown()).isTrue();
    TestUtilities.clickView(InstrumentationRegistry.getInstrumentation(), buttonView);
    pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(pin).isEqualTo(FIRST_HOTP_EXPECTED_PIN);
    assertThat(accountDb.getCounter(new AccountIndex(HOTP_ACCOUNT_NAME, null)))
        .isEqualTo(Integer.valueOf(1));

    // Check the styled pin code
    String styledPin = ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString();
    assertThat(Utilities.getStyledPincode(FIRST_HOTP_EXPECTED_PIN)).isEqualTo(styledPin);
  }

  @Test
  public void testGetOtpWithOneHotpAccountUsingListRowClick() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(1);
    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo(HOTP_ACCOUNT_NAME);
    // starts empty
    assertThat(pin).isEqualTo(activityTestRule.getActivity().getString(R.string.empty_pin));
    assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isFalse();

    // Get next OTP value by clicking the list item/row.
    assertThat(TestUtilities.clickListViewItem(userList, 0)).isTrue();
    listEntry = userList.getChildAt(0);
    onView(is((View) listEntry.findViewById(R.id.pin_value)))
        .check(
            matches(
                withText(
                    Utilities.getStyledPincode(
                        FIRST_HOTP_EXPECTED_PIN)))); // Verify the expected pin is present
    assertThat(accountDb.getCounter(new AccountIndex(HOTP_ACCOUNT_NAME, null)))
        .isEqualTo(Integer.valueOf(1));
  }

  @Test
  public void testGetOtpWithMultipleAccounts() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);
    accountDb.add(
        "johndoeTotp1@gmail.com",
        "2222222222222222",
        OtpType.TOTP,
        null,
        null,
        AccountDb.GOOGLE_ISSUER_NAME);

    activityTestRule.launchActivity(null);

    final ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(2);

    // check hotp account
    View listEntry0 = userList.getChildAt(0);
    String user = ((TextView) listEntry0.findViewById(R.id.current_user)).getText().toString();
    String pin =
        getOriginalPincode(
            ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user).isEqualTo(HOTP_ACCOUNT_NAME);
    // starts empty
    assertThat(pin).isEqualTo(activityTestRule.getActivity().getString(R.string.empty_pin));
    assertThat(listEntry0.findViewById(R.id.countdown_icon).isShown()).isFalse();
    View buttonView = listEntry0.findViewById(R.id.next_otp);
    assertThat(buttonView.isShown()).isTrue();
    // get next Otp value by clicking icon
    TestUtilities.clickView(InstrumentationRegistry.getInstrumentation(), buttonView);
    listEntry0 = userList.getChildAt(0); // get refreshed value after clicking nextOtp button.
    pin =
        getOriginalPincode(
            ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString());
    assertThat(pin).isEqualTo(FIRST_HOTP_EXPECTED_PIN);

    // check first totp account
    View listEntry1 = userList.getChildAt(1);
    user = ((TextView) listEntry1.findViewById(R.id.current_user)).getText().toString();
    pin =
        getOriginalPincode(
            ((TextView) listEntry1.findViewById(R.id.pin_value)).getText().toString());
    assertThat(user)
        .isEqualTo(
            Utilities.getCombinedTextForIssuerAndAccountName(
                AccountDb.GOOGLE_ISSUER_NAME, "johndoeTotp1@gmail.com"));
    assertThat(Integer.parseInt(pin) > 0 && Integer.parseInt(pin) <= 999999).isTrue();
    assertThat(listEntry1.findViewById(R.id.next_otp).isShown()).isFalse();
    assertThat(listEntry1.findViewById(R.id.countdown_icon).isShown()).isTrue();
  }

  @Test
  public void testGetOtpWithMultipleAccounts_collidingNames() {
    // Different kinds of colliding names
    AccountIndex[] colliding = {
      new AccountIndex("collider@gmail.com", null),
      new AccountIndex("collider@gmail.com", AccountDb.GOOGLE_ISSUER_NAME),
      new AccountIndex("Yahoo:collider@yahoo.com", null),
      new AccountIndex("Yahoo:collider@yahoo.com", "Yahoo"),
    };

    accountDb.add(
        colliding[0].getName(),
        "5555555555555555",
        OtpType.TOTP,
        null,
        null,
        colliding[0].getIssuer());
    accountDb.add(
        colliding[1].getName(),
        "6666666666666666",
        OtpType.TOTP,
        null,
        null,
        colliding[1].getIssuer());
    accountDb.add(
        colliding[2].getName(),
        "2222222222222222",
        OtpType.TOTP,
        null,
        null,
        colliding[2].getIssuer());
    accountDb.add(
        colliding[3].getName(),
        "7777777777777777",
        OtpType.TOTP,
        null,
        null,
        colliding[3].getIssuer());

    activityTestRule.launchActivity(null);

    final ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(4);

    for (int i = 0; i < accountDb.getAccounts().size(); i++) {
      View listEntry = userList.getChildAt(i);
      String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
      String pin =
          getOriginalPincode(
              ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString());
      // Validate that the i-th entry in the list matches the i-th account added from colliding[]
      assertThat(user)
          .isEqualTo(
              Utilities.getCombinedTextForIssuerAndAccountName(
                  colliding[i].getIssuer(), colliding[i].getStrippedName()));
      // Check that the OTP code shown is in the normal range
      assertThat(Integer.parseInt(pin) > 0 && Integer.parseInt(pin) <= 999999).isTrue();
      // TOTP accounts don't have a "next otp" button, but they do have a countdown timer
      assertThat(listEntry.findViewById(R.id.next_otp).isShown()).isFalse();
      assertThat(listEntry.findViewById(R.id.countdown_icon).isShown()).isTrue();
    }
  }

  @Test
  public void testTalkBackTextForAccount() throws Throwable {
    // This test checks the text returned by an account row's getContentDescription as well as the
    // text added into AccessibilityEvent by the row.
    // A real object is used for the AuthenticatorActivity#otpProvider instance,
    // but a mock (spy) instance is used only for this test method because it needs to return
    // fixed pin codes to verify.
    String totpAccount = "johndoeTotp@gmail.com";
    String hotpAccount = "johndoeHotp@google.com";
    TotpClock mockOtpClock = mock(TotpClock.class);
    otpSource = spy(otpSource);
    doReturn("192834").when(otpSource).getNextCode(new AccountIndex(totpAccount, null));
    doReturn("722389").when(otpSource).getNextCode(new AccountIndex(hotpAccount, null));
    doReturn(mockOtpClock).when(otpSource).getTotpClock();
    doReturn(new TotpCounter(30)).when(otpSource).getTotpCounter();
    accountDb.add(totpAccount, "7777777777777777", OtpType.TOTP, null, null, null);
    accountDb.add(hotpAccount, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    assertThat(userList.getChildCount()).isEqualTo(2);

    // TOTP account
    View totpListEntry = getUserRowView((DragSortItemView) userList.getChildAt(0));
    assertThat(String.valueOf(totpListEntry.getContentDescription()))
        .isEqualTo("1 9 2   8 3 4 " + totpAccount);

    AccessibilityEvent accessibilityEvent = AccessibilityEvent.obtain();
    assertThat(totpListEntry.dispatchPopulateAccessibilityEvent(accessibilityEvent)).isTrue();
    assertThat(accessibilityEvent.getContentDescription()).isNull();
    assertThat(String.valueOf(Iterables.getOnlyElement(accessibilityEvent.getText())))
        .isEqualTo("1 9 2   8 3 4 " + totpAccount);

    // HOTP account
    // No OTP is yet displayed for the HOTP account -- the text should say "get code <account name>"
    View hotpListEntry = getUserRowView((DragSortItemView) userList.getChildAt(1));
    assertThat(String.valueOf(hotpListEntry.getContentDescription()))
        .isEqualTo(
            activityTestRule.getActivity().getString(R.string.counter_pin) + " " + hotpAccount);

    accessibilityEvent = AccessibilityEvent.obtain();
    assertThat(hotpListEntry.dispatchPopulateAccessibilityEvent(accessibilityEvent)).isTrue();
    assertThat(accessibilityEvent.getContentDescription()).isNull();
    assertThat(String.valueOf(Iterables.getOnlyElement(accessibilityEvent.getText())))
        .isEqualTo(
            activityTestRule.getActivity().getString(R.string.counter_pin) + " " + hotpAccount);

    View getNextCodeButton = hotpListEntry.findViewById(R.id.next_otp);
    assertThat(getNextCodeButton.isShown()).isTrue();
    // Get an OTP
    TestUtilities.clickView(InstrumentationRegistry.getInstrumentation(), getNextCodeButton);
    // Re-obtain the list entry View reference as it may have changed due to how ListView refreshes
    // itself.
    hotpListEntry = getUserRowView((DragSortItemView) userList.getChildAt(1));
    assertThat(String.valueOf(hotpListEntry.getContentDescription()))
        .isEqualTo("7 2 2   3 8 9 " + hotpAccount);

    accessibilityEvent = AccessibilityEvent.obtain();
    assertThat(hotpListEntry.dispatchPopulateAccessibilityEvent(accessibilityEvent)).isTrue();
    assertThat(accessibilityEvent.getContentDescription()).isNull();
    assertThat(String.valueOf(Iterables.getOnlyElement(accessibilityEvent.getText())))
        .isEqualTo("7 2 2   3 8 9 " + hotpAccount);
  }

  //////////////////////////   Context Menu Tests  ////////////////////////////

  @Test
  @SuppressWarnings("unchecked")
  public void testContextMenuCheckCodeForHotpAccount() {
    accountDb.add(
        HOTP_ACCOUNT_NAME,
        "7777777777777777",
        OtpType.HOTP,
        null,
        null,
        AccountDb.GOOGLE_ISSUER_NAME);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    openContextualActionBarOrMenuAndInvokeItem(userList, 0, R.string.check_code_menu_item);

    intended(
        allOf(
            hasComponent(
                equalTo(
                    new ComponentName(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        CheckCodeActivity.class))),
            hasExtras(
                hasEntry(
                    equalTo("index"),
                    equalTo(new AccountIndex(HOTP_ACCOUNT_NAME, AccountDb.GOOGLE_ISSUER_NAME))))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testContextMenuCheckCodeNotAvailableForTotpAccount() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.TOTP, null, null, "SomeIssuer");

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    // The Android framework does not out of the box provide a good enough way to check whether
    // the context menu item is present or not.
    // To help this test, AuthenticatorActivity keeps a reference to the most recently created
    // ContextMenu. We thus check that (1) opening the context menu and invoking the the
    // "Check code" menu item fails, and (2) that the most recent ContextMenu after that
    // invocation does not offer this menu item.
    MenuItem item =
        openListViewContextualActionBarAndFindMenuItem(
            activityTestRule.getActivity(), userList, 0, R.string.check_code_menu_item);

    // The item should either be not there (null) or should not be visible.
    if (item != null) {
      assertThat(item.isVisible()).isFalse();
    }
  }

  @Test
  public void testContextMenuRemove() throws Exception {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, "Yahoo");

    activityTestRule.launchActivity(null);

    final ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    final View listEntry0 = userList.getChildAt(0);
    openContextualActionBarOrMenuAndInvokeItem(userList, 0, R.string.context_menu_remove_account);
    // Select Remove on confirmation dialog to remove account.
    TestUtilities.tapDialogPositiveButton(InstrumentationRegistry.getInstrumentation());
    // check main screen gets focus back;
    TestUtilities.waitForWindowFocus(listEntry0);
    // check that account is deleted in database.
    assertThat(accountDb.getAccounts()).isEmpty();
  }

  @Test
  public void testContextMenuRename() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, "Yahoo");

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    openContextualActionBarOrMenuAndInvokeItem(userList, 0, R.string.rename);
    // Type new name and select save on dialog.
    onView(withId(R.id.rename_edittext))
        .check(matches(withText(HOTP_ACCOUNT_NAME)))
        .perform(ViewActions.clearText())
        .perform(typeText("newname@gmail.com"));
    onView(withText(R.string.submit)).perform(click());
    // check main screen gets focus back;
    onView(is(userList)).check(matches(hasFocus()));
    // check update to database.
    assertThat(accountDb.getAccounts())
        .containsExactly(new AccountIndex("newname@gmail.com", "Yahoo"))
        .inOrder();
  }

  @Test
  public void testContextMenuRenameNotAvailableForGoogleCorpAccount() {
    accountDb.add(
        AccountDb.GOOGLE_CORP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    // The Android framework does not out of the box provide a good enough way to check whether
    // the context menu item is present or not.
    // To help this test, AuthenticatorActivity keeps a reference to the most recently created
    // ContextMenu. We thus check that (1) opening the context menu and invoking the the
    // "Rename" menu item fails, and (2) that the most recent ContextMenu after that
    // invocation does not offer this menu item.
    MenuItem item =
        openListViewContextualActionBarAndFindMenuItem(
            activityTestRule.getActivity(), userList, 0, R.string.rename);

    // The item should either be not there (null) or should not be visible.
    if (item != null) {
      assertThat(item.isVisible()).isFalse();
    }
  }

  @Test
  @FixWhenMinSdkVersion(11)
  @SuppressWarnings("deprecation")
  public void testContextMenuCopyToClipboard() {
    // use HOTP to avoid any timing issues when "current" pin is compared with clip board text.
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    // find and click next otp button.
    View buttonView = activityTestRule.getActivity().findViewById(R.id.next_otp);
    TestUtilities.clickView(InstrumentationRegistry.getInstrumentation(), buttonView);
    // get the pin being displayed
    View listEntry0 = userList.getChildAt(0);
    String pin =
        getOriginalPincode(
            ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString());
    openListViewContextualActionBar(activityTestRule.getActivity(), userList, 0);

    InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    TestUtilities.runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            // Check clipboard value.
            String clipboardContent = "";
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()
                && clipboard.getPrimaryClipDescription()
                    .hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
              clipboardContent = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            }
            assertThat(clipboardContent).isEqualTo(pin);
            return null;
          }
        });
  }

  @Test
  @TargetApi(19)
  public void testContextualActionBarClosedAfterListUpdate() {
    accountDb.add(HOTP_ACCOUNT_NAME, "7777777777777777", OtpType.HOTP, null, null, null);

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);
    openListViewContextualActionBar(activityTestRule.getActivity(), userList, 0);
    // Simulate a new account being added
    accountDb.add(
        "janedoeHotp@gmail.com",
        "8888888888888888",
        OtpType.HOTP,
        null,
        null,
        AccountDb.GOOGLE_ISSUER_NAME);
    refreshView();
    // We should no longer be in action mode
    assertThat(activityTestRule.getActivity().actionMode).isNull();
  }

  @Test
  @TargetApi(Build.VERSION_CODES.KITKAT)
  public void testContextualActionBar() {
    accountDb.add(
        "first", "7777777777777777", OtpType.HOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    accountDb.add("second", "6666666666666666", OtpType.TOTP, null, null, null);
    accountDb.add("third", "5555555555555555", OtpType.TOTP, null, null, "Yahoo");

    activityTestRule.launchActivity(null);

    ListView userList = activityTestRule.getActivity().findViewById(R.id.user_list);

    // Test that clicking on a list item starts the CAB and that only one item is checked/selected
    // at a time.
    ActionMode actionMode =
        (ActionMode) openListViewContextualActionBar(activityTestRule.getActivity(), userList, 1);
    assertThat(String.valueOf(actionMode.getTitle())).isEqualTo("second");
    assertThat(userList.getCheckedItemCount()).isEqualTo(1);

    // Check that clicking on another list item marks that item as checked and unchecks all other
    // items.
    assertThat(TestUtilities.clickListViewItem(userList, 0)).isTrue();
    assertThat(String.valueOf(actionMode.getTitle())).isEqualTo("first");
    assertThat(userList.getCheckedItemCount()).isEqualTo(1);
    // Check that clicking on a HOTP item while the CAB is being displayed does not generate a
    // code.
    assertThat(accountDb.getCounter(new AccountIndex("first", AccountDb.GOOGLE_ISSUER_NAME)))
        .isEqualTo(Integer.valueOf(0));
  }

  ///////////////////////////   Options Menu Tests  /////////////////////////////

  @Test
  public void testOptionsMenuHowItWorksOnFirstOnboarding() {
    setOnboardingCompletedPreference(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), false);

    activityTestRule.launchActivity(null);

    onView(isRoot()).perform(pressMenuKey());
    onView(
            withText(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()
                    .getString(R.string.how_it_works_menu_item)))
        .perform(click());

    Intent expectedIntent = new Intent(activityTestRule.getActivity(), HowItWorksActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(
                allOf(
                    hasEntry(
                        equalTo(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE),
                        equalTo(true))))));
  }

  @Test
  public void testOptionsMenuSwitchUiModeHidedInBeginSetupScreenOnLightMode() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    activityTestRule.launchActivity(null);

    onView(isRoot()).perform(pressMenuKey());
    try {
      onView(withText(context.getString(R.string.switch_ui_mode_dark))).perform(click());
      Assert.fail("Switch UI mode option should be hidden in begin setup screen");
    } catch (NoMatchingViewException ignored) {
      // Expected.
    }
  }

  @Test
  public void testOptionsMenuSwitchUiModeHidedInBeginSetupScreenOnDarkMode() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    setDarkModeEnabled(context, true);

    activityTestRule.launchActivity(null);

    onView(isRoot()).perform(pressMenuKey());
    try {
      onView(withText(context.getString(R.string.switch_ui_mode_light))).perform(click());
      Assert.fail("Switch UI mode option should be hidden in begin setup screen");
    } catch (NoMatchingViewException ignored) {
      // Expected.
    }
  }

  @Test
  public void testOptionsMenuSwitchUiModeOnLightMode() {
    accountDb.add(
        "first", "7777777777777777", OtpType.HOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    activityTestRule.launchActivity(null);

    assertThat(activityTestRule.getActivity().darkModeEnabled).isFalse();
    onView(isRoot()).perform(pressMenuKey());
    onView(withText(context.getString(R.string.switch_ui_mode_dark))).perform(click());
    onView(isRoot()).perform(pressMenuKey());
    onView(withText(context.getString(R.string.switch_ui_mode_light)))
        .check(matches(isDisplayed()));
    assertThat(activityTestRule.getActivity().darkModeEnabled).isTrue();
  }

  @Test
  public void testOptionsMenuSwitchUiModeOnDarkMode() {
    accountDb.add(
        "first", "7777777777777777", OtpType.HOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    setDarkModeEnabled(context, true);

    activityTestRule.launchActivity(null);

    assertThat(activityTestRule.getActivity().darkModeEnabled).isTrue();
    onView(isRoot()).perform(pressMenuKey());
    onView(withText(context.getString(R.string.switch_ui_mode_light))).perform(click());
    onView(isRoot()).perform(pressMenuKey());
    onView(withText(context.getString(R.string.switch_ui_mode_dark))).check(matches(isDisplayed()));
    assertThat(activityTestRule.getActivity().darkModeEnabled).isFalse();
  }

  @Test
  public void testOptionsMenuHowItWorksOnboardingCompleted() {
    setOnboardingCompletedPreference(
        InstrumentationRegistry.getInstrumentation().getTargetContext(), true);

    activityTestRule.launchActivity(null);

    onView(isRoot()).perform(pressMenuKey());
    onView(
            withText(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()
                    .getString(R.string.how_it_works_menu_item)))
        .perform(click());

    Intent expectedIntent = new Intent(activityTestRule.getActivity(), HowItWorksActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(
                allOf(
                    hasEntry(
                        equalTo(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE),
                        equalTo(false))))));
  }

  @Test
  public void testOptionsMenuSettings() {
    checkOptionsMenuItemWithComponent(R.string.settings_menu_item, SettingsActivity.class);
  }

  @Test
  public void testIntentActionScanBarcodeWithoutCameraFeature() {
    when(mockBarcodeConditionChecker.isCameraAvailableOnDevice(any(Activity.class)))
        .thenReturn(false);

    activityTestRule.launchActivity(new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE));

    TestUtilities.assertDialogWasDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_CAMERA_NOT_AVAILABLE);
  }

  @Test
  public void testIntentActionScanBarcodeWithoutGooglePlayServices() {
    when(mockBarcodeConditionChecker.isGooglePlayServicesAvailable(any(Activity.class)))
        .thenReturn(false);

    activityTestRule.launchActivity(new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE));

    TestUtilities.assertDialogWasDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_INSTALL_GOOGLE_PLAY_SERVICES);
  }

  @Test
  public void testIntentActionScanBarcodeWhenNotOperational() {
    when(mockBarcodeConditionChecker.getIsBarcodeDetectorOperational(any(Activity.class)))
        .thenReturn(false);

    activityTestRule.launchActivity(new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE));

    TestUtilities.assertDialogWasDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_BARCODE_SCANNER_NOT_AVAILABLE);
  }

  @Test
  public void testIntentActionScanBarcodeWhenNotOperationalBecauseLowStorage() {
    when(mockBarcodeConditionChecker.getIsBarcodeDetectorOperational(any(Activity.class)))
        .thenReturn(false);
    when(mockBarcodeConditionChecker.isLowStorage(any(Activity.class))).thenReturn(true);

    activityTestRule.launchActivity(new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE));

    TestUtilities.assertDialogWasDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_LOW_STORAGE_FOR_BARCODE_SCANNER);
  }

  @Test
  public void testIntentActionScanBarcode() {
    activityTestRule.launchActivity(new Intent(AuthenticatorActivity.ACTION_SCAN_BARCODE));
    Intent expectedIntent =
        AuthenticatorActivity.getLaunchIntentActionScanBarcode(
            activityTestRule.getActivity(), false);
    intended(
        allOf(
            hasAction(AuthenticatorActivity.ACTION_SCAN_BARCODE),
            hasComponent(equalTo(expectedIntent.getComponent()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testReorderAccounts() {
    accountDb.add(
        "first", "7777777777777777", OtpType.HOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    accountDb.add("second", "6666666666666666", OtpType.TOTP, null, null, null);
    accountDb.add("third", "5555555555555555", OtpType.TOTP, null, null, "Yahoo");

    activityTestRule.launchActivity(null);

    EmptySpaceClickableDragSortListView userList =
        activityTestRule.getActivity().findViewById(R.id.user_list);
    View firstView = userList.getChildAt(0);
    View thirdView = userList.getChildAt(2);
    View firstDragHandle = firstView.findViewById(R.id.user_row_drag_handle);
    View thirdDragHandle = thirdView.findViewById(R.id.user_row_drag_handle);
    int dragHandleWidth = firstDragHandle.getWidth();
    int dragHandleHeight = firstDragHandle.getHeight();
    int[] firstDragHandleLocation = new int[2];
    int[] thirdDragHandleLocation = new int[2];
    firstDragHandle.getLocationOnScreen(firstDragHandleLocation);
    thirdDragHandle.getLocationOnScreen(thirdDragHandleLocation);
    float fromX = firstDragHandleLocation[0] + (dragHandleWidth / 2.0f);
    float fromY = firstDragHandleLocation[1] + (dragHandleHeight / 2.0f);
    float toX = thirdDragHandleLocation[0] + (dragHandleWidth / 2.0f);
    float toY = thirdDragHandleLocation[1] + (dragHandleHeight / 2.0f);

    onView(equalTo(firstView))
        .perform(longClick())
        .perform(
            new GeneralSwipeAction(
                Swipe.SLOW,
                (view) -> {
                  return new float[] {fromX, fromY};
                },
                (view) -> {
                  return new float[] {toX, toY};
                },
                Press.FINGER));

    ListAdapter listAdapter = userList.getAdapter();
    PinInfo firstPinInfo = (PinInfo) listAdapter.getItem(0);
    PinInfo secondPinInfo = (PinInfo) listAdapter.getItem(1);
    PinInfo thirdPinInfo = (PinInfo) listAdapter.getItem(2);
    assertThat(firstPinInfo.getIndex().getName()).isEqualTo("second");
    assertThat(secondPinInfo.getIndex().getName()).isEqualTo("third");
    assertThat(thirdPinInfo.getIndex().getName()).isEqualTo("first");
  }

  @Test
  public void testClickEmptySpaceOnListUnselectItem() throws InterruptedException {
    accountDb.add(
        "first", "7777777777777777", OtpType.HOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);

    AuthenticatorActivity activity = activityTestRule.launchActivity(null);

    EmptySpaceClickableDragSortListView userList = activity.findViewById(R.id.user_list);

    assertThat(activity.actionMode).isNull();
    onView(equalTo(userList.getChildAt(0))).perform(longClick());
    assertThat(activity.actionMode).isNotNull();

    Point size = new Point();
    activity.getWindowManager().getDefaultDisplay().getSize(size);
    int height = size.y;
    int width = size.x;

    Instrumentation instr = InstrumentationRegistry.getInstrumentation();
    long downTime = SystemClock.uptimeMillis();
    long eventTime = SystemClock.uptimeMillis();

    instr.sendPointerSync(
        MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, height / 2, width / 2, 0));
    instr.sendPointerSync(
        MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, height / 2, width / 2, 0));

    // We need to delay to wait for the contextual action bar is completely removed.
    Thread.sleep(CLICK_DELAY_INTERVAL);
    assertThat(activity.actionMode).isNull();
  }

  /**
   * This function return the original pin code from the styled pin code, which may has a space in
   * the middle of the string
   *
   * @param pin The styled pin code
   * @return The original pin code with no spaces
   */
  private static String getOriginalPincode(String pin) {
    boolean was6DigitsPincode = true;
    if (pin.length() != 7) {
      was6DigitsPincode = false;
    } else {
      for (int i = 0; i < 7; ++i) {
        if ((i == 3 && pin.charAt(3) != ' ')
            || (i != 3 && !('0' <= pin.charAt(i) && pin.charAt(i) <= '9'))) {
          was6DigitsPincode = false;
        }
      }
    }

    if (!was6DigitsPincode) {
      return pin;
    }

    return pin.replace(" ", "");
  }

  @SuppressWarnings("unchecked")
  @TargetApi(11)
  @FixWhenMinSdkVersion(11) // Will be able to get rid of the contextual menu
  private void openContextualActionBarOrMenuAndInvokeItem(
      ListView listView, int position, int menuItemTextId) {
    String menuItemText = activityTestRule.getActivity().getString(menuItemTextId);

    onView(equalTo(listView.getChildAt(position))).perform(longClick());

    // Force a UI synchronization by checking a trivial statement about the Root view
    onView(isRoot()).check(ViewAssertions.matches(isDisplayed()));
    // Now either the old fashioned context menu or CAB is open...
    // Check that the CAB appears to be active
    ActionMode actionMode = activityTestRule.getActivity().actionMode;
    assertThat(actionMode).isNotNull();

    // In either case, find the requested menu item and click it
    try {
      // Could be a text menu or an image menu (with content descriptions matching the text)
      onView(
              allOf(
                  isDisplayed(),
                  anyOf(withText(menuItemText), withContentDescription(menuItemText))))
          .perform(click());
    } catch (NoMatchingViewException e) {

      // Perhaps the menu item is buried in the overflow menu. Try opening that.
      // We can't use openActionBarOverflowOrOptionsMenu because a Toolbar is used instead of the
      // default ActionBar, so when the CAB show up it will be show on a different ActionBar that
      // overlay the Toolbar and there will be two different "OverflowMenuButton" on the Toolbar and
      // the CAB, which make the Espresso get an ambiguous exception failure.
      onView(
              allOf(
                  withClassName(endsWith("OverflowMenuButton")),
                  withParent(withParent(withClassName(endsWith("ActionBarContextView"))))))
          .perform(click());

      // Overflow menu items are only shown as text, and do not have the original MenuItem's id
      onView(withText(menuItemText)).perform(click());
    }
  }

  @TargetApi(11)
  // The return value is of type android.view.ActionMode. However, since the ActionMode class is
  // only available in API Level 11+, we have to declare the return type as Object in order to
  // for this test class to load fine on Gingerbread and below.
  private static Object openListViewContextualActionBar(
      AuthenticatorActivity activity, ListView listView, int position) {
    View listEntry = listView.getChildAt(position);
    onView(is(listEntry)).perform(longClick());
    ActionMode actionMode = activity.actionMode;
    if (actionMode == null) {
      throw new RuntimeException(
          "Failed to open list entry contextual action bar: got ActionMode == null");
    }
    return actionMode;
  }

  @TargetApi(11)
  private static MenuItem openListViewContextualActionBarAndFindMenuItem(
      AuthenticatorActivity activity, ListView listView, int position, int menuItemId) {
    ActionMode actionMode =
        (ActionMode) openListViewContextualActionBar(activity, listView, position);
    return actionMode.getMenu().findItem(menuItemId);
  }

  @TargetApi(11)
  private static void openListViewContextualActionBarAndInvokeItem(
      AuthenticatorActivity activity,
      final ListView listView,
      final int position,
      final int menuItemId) {
    ActionMode actionMode =
        (ActionMode) openListViewContextualActionBar(activity, listView, position);
    MenuItem menuItem = actionMode.getMenu().findItem(menuItemId);
    try {
      onView(withContentDescription(menuItem.getTitle().toString())).perform(click());
    } catch (NoMatchingViewException e) {
      // Might be in the overflow menu
      Espresso.openActionBarOverflowOrOptionsMenu(activity);
      onView(withText(menuItem.getTitle().toString())).perform(click());
    }
  }

  /**
   * Set the preference for key KEY_ONBOARDING_COMPLETED of {@link AuthenticatorActivity}
   *
   * @param context the target context for the current activity
   * @param value when the value is false, this function just simply remove the key
   */
  private static void setOnboardingCompletedPreference(Context context, boolean value) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (value) {
      preferences.edit().putBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, true).commit();
    } else {
      preferences.edit().remove(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED).commit();
    }
  }

  /** Set the preference for key KEY_DARK_MODE_ENABLED of {@link AuthenticatorActivity}. */
  private static void setDarkModeEnabled(Context context, boolean value) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    preferences.edit().putBoolean(AuthenticatorActivity.KEY_DARK_MODE_ENABLED, value).commit();
  }

  private static View getUserRowView(DragSortItemView dragSortItemView) {
    View userRowView = null;
    for (int i = 0; i < dragSortItemView.getChildCount(); i++) {
      if (dragSortItemView.getChildAt(i) instanceof UserRowView) {
        userRowView = dragSortItemView.getChildAt(i);
      }
    }
    return userRowView;
  }

  private void refreshView() {
    TestUtilities.runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            activityTestRule.getActivity().refreshView(true);
            return null;
          }
        });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }

  @SuppressWarnings("unchecked")
  private void checkOptionsMenuItemWithComponent(int menuTextId, Class<?> cls) {
    activityTestRule.launchActivity(null);

    onView(isRoot()).perform(pressMenuKey());
    onView(
            withText(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()
                    .getString(menuTextId)))
        .perform(click());
    Matcher<Intent> intentMatcher =
        hasComponent(
            equalTo(
                new ComponentName(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), cls)));
    intended(intentMatcher);
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  /** Dagger module for unit tests */
  @Module(
      library = true,
      injects = {AuthenticatorActivity.class, SettingsActivity.class, BarcodeCaptureActivity.class})
  public class MockModule {

    @Provides
    @Singleton
    @ApplicationContext
    Context providesContext() {
      return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Provides
    OtpSource providesOtpSource() {
      return otpSource;
    }

    @Provides
    @Singleton
    BarcodeConditionChecker provideGoogleApiAvailabilityHelper() {
      return mockBarcodeConditionChecker;
    }

    @Provides
    @Singleton
    public PermissionRequestor providesPermissionRequestor() {
      return mockPermissionRequestor;
    }
  }
}
