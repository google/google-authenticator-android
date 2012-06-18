/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

import static com.google.testing.littlemock.LittleMock.anyInt;
import static com.google.testing.littlemock.LittleMock.anyString;
import static com.google.testing.littlemock.LittleMock.doThrow;
import static com.google.testing.littlemock.LittleMock.initMocks;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.CapturingStartActivityListener;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testability.StartActivityListener;
import com.google.android.apps.authenticator.testability.content.pm.PackageManager;
import com.google.testing.littlemock.Mock;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ViewAsserts;
import android.text.ClipboardManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for authenticator activity (part/shard 1).
 *
 * @author Sarvar Patel (sarvar@google.com)
 */
public class AuthenticatorActivityTest extends
    ActivityInstrumentationTestCase2<AuthenticatorActivity> {

  private static final int SEND_KEYS_EFFECTS_TIMEOUT_MILLIS = 1000;
  private static final int INVOKE_MENU_ITEM_TIMEOUT_MILLIS = 1000;
  private static final int ACTIVITY_FINISH_TIMEOUT_MILLIS = 1000;

  private AccountDb mAccountDb;
  @Mock private PackageManager mMockPackageManager;

  public AuthenticatorActivityTest() {
    super("com.google.android.apps.authenticator", AuthenticatorActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
    initMocks(this);

    // Mock out package manager since the UI depends on whether the "new" is installed or not
    // Pretend that no packages are installed.
    doThrow(new android.content.pm.PackageManager.NameNotFoundException())
        .when(mMockPackageManager).getPackageInfo(anyString(), anyInt());
    DependencyInjector.setPackageManager(mMockPackageManager);

    mAccountDb = DependencyInjector.getAccountDb();
  }

  @Override
  protected void tearDown() throws Exception {
    // Stop the activity to avoid it using the DependencyInjector after it's been closed.
    TestUtilities.invokeFinishActivityOnUiThread(getActivity(), ACTIVITY_FINISH_TIMEOUT_MILLIS);

    DependencyInjector.close();

    super.tearDown();
  }

  public void testGetTitle() {
    assertEquals(getActivity().getString(R.string.app_name), getActivity().getTitle());
  }

  //////////////////////// Main screen UI Tests ///////////////////////////////

  public void testNoAccountUi() throws Throwable {
    getActivity();
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    TextView enterPinTextView = (TextView) getActivity().findViewById(R.id.enter_pin);
    Button scanBarcodeButton = (Button) getActivity().findViewById(R.id.scan_barcode_button);
    Button enterKeyButton = (Button) getActivity().findViewById(R.id.enter_key_button);
    LinearLayout buttonsLayout = (LinearLayout) getActivity().findViewById(R.id.main_buttons);

    // check existence of fields
    assertNotNull(userList);
    assertNotNull(enterPinTextView);
    assertNotNull(scanBarcodeButton);
    assertNotNull(enterKeyButton);
    assertNotNull(buttonsLayout);

    // check visibility
    View origin = getActivity().getWindow().getDecorView();
    ViewAsserts.assertOnScreen(origin, enterPinTextView);
    ViewAsserts.assertOnScreen(origin, scanBarcodeButton);
    ViewAsserts.assertOnScreen(origin, enterKeyButton);
    ViewAsserts.assertOnScreen(origin, buttonsLayout);
    assertFalse(userList.isShown());
  }

  public void testGetOtpWithOneTotpAccount() throws Throwable {
    mAccountDb.update(
        "johndoeTotp@gmail.com", "7777777777777777", "johndoeTotp@gmail.com", OtpType.TOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    assertEquals(1, userList.getChildCount());
    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin = ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("johndoeTotp@gmail.com", user);
    assertTrue(Integer.parseInt(pin) >= 0 && Integer.parseInt(pin) <= 999999);
    assertEquals(6, pin.length());
    assertFalse(listEntry.findViewById(R.id.next_otp).isShown());
    assertTrue(listEntry.findViewById(R.id.countdown_icon).isShown());
  }

  public void testGetOtpWithOneHotpAccount() throws Throwable {
    mAccountDb.update(
        "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    assertEquals(1, userList.getChildCount());
    View listEntry = userList.getChildAt(0);
    String user = ((TextView) listEntry.findViewById(R.id.current_user)).getText().toString();
    String pin = ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("johndoeHotp@gmail.com", user);
    assertEquals(getActivity().getString(R.string.empty_pin), pin);  // starts empty
    assertFalse(listEntry.findViewById(R.id.countdown_icon).isShown());
    View buttonView = listEntry.findViewById(R.id.next_otp);
    assertTrue(buttonView.isShown());
    // get next Otp value by clicking icon
    TestUtilities.clickView(getInstrumentation(), buttonView);
    pin = ((TextView) listEntry.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("683298", pin);
  }

  public void testGetOtpWithMultipleAccounts() throws Throwable {
    mAccountDb.update(
        "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    mAccountDb.update(
        "johndoeTotp1@gmail.com", "2222222222222222", "johndoeTotp1@gmail.com", OtpType.TOTP, null);
    mAccountDb.update(
        "johndoeTotp2@gmail.com", "3333333333333333", "johndoeTotp2@gmail.com", OtpType.TOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    Thread.sleep(5000);
    assertEquals(3, userList.getChildCount());

    // check hotp account
    View listEntry0 = userList.getChildAt(0);
    String user = ((TextView) listEntry0.findViewById(R.id.current_user)).getText().toString();
    String pin = ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("johndoeHotp@gmail.com", user);
    assertEquals(getActivity().getString(R.string.empty_pin), pin);  // starts empty
    assertFalse(listEntry0.findViewById(R.id.countdown_icon).isShown());
    View buttonView = listEntry0.findViewById(R.id.next_otp);
    assertTrue(buttonView.isShown());
    // get next Otp value by clicking icon
    TestUtilities.clickView(getInstrumentation(), buttonView);
    listEntry0 = userList.getChildAt(0); // get refreshed value after clicking nextOtp button.
    pin = ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("683298", pin);

    // check first totp account
    View listEntry1 = userList.getChildAt(1);
    user = ((TextView) listEntry1.findViewById(R.id.current_user)).getText().toString();
    pin = ((TextView) listEntry1.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("johndoeTotp1@gmail.com", user);
    assertTrue(Integer.parseInt(pin) > 0 && Integer.parseInt(pin) <= 999999);
    assertFalse(listEntry1.findViewById(R.id.next_otp).isShown());
    assertTrue(listEntry1.findViewById(R.id.countdown_icon).isShown());

    View listEntry2 = userList.getChildAt(2);
    // check second totp account
    user = ((TextView) listEntry2.findViewById(R.id.current_user)).getText().toString();
    pin = ((TextView) listEntry2.findViewById(R.id.pin_value)).getText().toString();
    assertEquals("johndoeTotp2@gmail.com", user);
    assertTrue(Integer.parseInt(pin) > 0 && Integer.parseInt(pin) <= 999999);
    assertFalse(listEntry1.findViewById(R.id.next_otp).isShown());
    assertTrue(listEntry1.findViewById(R.id.countdown_icon).isShown());
  }

  //////////////////////////   Context Menu Tests  ////////////////////////////

  public void testContextMenuCheckCode() throws Exception {
    CapturingStartActivityListener startActivityMonitor = new CapturingStartActivityListener();
    DependencyInjector.setStartActivityListener(startActivityMonitor);

    mAccountDb.update(
      "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    View listEntry0 = userList.getChildAt(0);
    TestUtilities.openContextMenuAndInvokeItem(
        getInstrumentation(),
        getActivity(),
        listEntry0,
        AuthenticatorActivity.CHECK_KEY_VALUE_ID);

    Intent launchIntent =
        startActivityMonitor.waitForFirstInvocation(INVOKE_MENU_ITEM_TIMEOUT_MILLIS).intent;
    assertEquals(
        new ComponentName(getInstrumentation().getTargetContext(), CheckCodeActivity.class),
        launchIntent.getComponent());
    assertEquals("johndoeHotp@gmail.com", launchIntent.getStringExtra("user"));
  }

  public void testContextMenuDelete() throws Exception {
    mAccountDb.update(
      "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    View listEntry0 = userList.getChildAt(0);
    TestUtilities.openContextMenuAndInvokeItem(
        getInstrumentation(),
        getActivity(),
        listEntry0,
        AuthenticatorActivity.DELETE_ID);
    // Select OK on confirmation dialog to delete account.
    sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
    sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
    // check main  screen gets focus back;
    TestUtilities.waitForWindowFocus(listEntry0, SEND_KEYS_EFFECTS_TIMEOUT_MILLIS);
    // check that account is deleted in database.
    assertEquals(0, mAccountDb.getNames(new ArrayList<String>()));
  }

  public void testContextMenuRename() throws Exception {
    mAccountDb.update(
      "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    View listEntry0 = userList.getChildAt(0);
    TestUtilities.openContextMenuAndInvokeItem(
        getInstrumentation(),
        getActivity(),
        listEntry0,
        AuthenticatorActivity.RENAME_ID);
    // Type new name and select save on dialog.
    sendKeys("21*DPAD_RIGHT");  // move right to end;
    sendKeys("21*DEL"); // delete the entire name
    sendKeys("N E W N A M E AT G M A I L PERIOD C O M");
    sendKeys("DPAD_DOWN DPAD_LEFT DPAD_CENTER"); // select save on the dialog
    // check main  screen gets focus back;
    listEntry0 = userList.getChildAt(0);
    TestUtilities.waitForWindowFocus(listEntry0, SEND_KEYS_EFFECTS_TIMEOUT_MILLIS);
    // check update to database.
    List<String> accountNames = new ArrayList<String>();
    assertEquals(1, mAccountDb.getNames(accountNames));
    assertEquals("newname@gmail.com", accountNames.get(0));
  }

  public void testContextMenuCopyToClipboard() throws Exception {
    // use HOTP to avoid any timing issues when "current" pin is compared with clip board text.
    mAccountDb.update(
        "johndoeHotp@gmail.com", "7777777777777777", "johndoeHotp@gmail.com", OtpType.HOTP, null);
    ListView userList = (ListView) getActivity().findViewById(R.id.user_list);
    // find and click next otp button.
    View buttonView = getActivity().findViewById(R.id.next_otp);
    TestUtilities.clickView(getInstrumentation(), buttonView);
    // get the pin being displayed
    View listEntry0 = userList.getChildAt(0);
    String pin = ((TextView) listEntry0.findViewById(R.id.pin_value)).getText().toString();
    TestUtilities.openContextMenuAndInvokeItem(
        getInstrumentation(),
        getActivity(),
        listEntry0,
        AuthenticatorActivity.COPY_TO_CLIPBOARD_ID);
    // check clip board value.
    Context context = getInstrumentation().getTargetContext();
    ClipboardManager clipboard =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    assertEquals(pin, clipboard.getText());
  }

  ///////////////////////////   Options Menu Tests  /////////////////////////////

  private void checkOptionsMenuItemWithComponent(int itemId,  Class<?> cls) throws Exception {
    CapturingStartActivityListener startActivityListener = new CapturingStartActivityListener();
    DependencyInjector.setStartActivityListener(startActivityListener);

    TestUtilities.openOptionsMenuAndInvokeItem(getInstrumentation(), getActivity(), itemId);

    Intent launchIntent =
        startActivityListener.waitForFirstInvocation(INVOKE_MENU_ITEM_TIMEOUT_MILLIS).intent;
    assertEquals(new ComponentName(getInstrumentation().getTargetContext(), cls),
        launchIntent.getComponent());
  }

  public void testOptionsMenuManuallyAddAccount() throws Exception {
    checkOptionsMenuItemWithComponent(R.id.enter_key_item, EnterKeyActivity.class);
  }

  public void testOptionsMenuAbout() throws Exception {
    checkOptionsMenuItemWithComponent(R.id.settings_about, SettingsAboutActivity.class);
  }



  public void testOptionsMenuScanABarcode_withScannerInstalled() throws Exception {
    CapturingStartActivityListener startActivityListener = new CapturingStartActivityListener();
    DependencyInjector.setStartActivityListener(startActivityListener);

    TestUtilities.openOptionsMenuAndInvokeItem(
        getInstrumentation(), getActivity(), R.id.scan_barcode);

    Intent launchIntent =
        startActivityListener.waitForFirstInvocation(INVOKE_MENU_ITEM_TIMEOUT_MILLIS).intent;
    assertEquals("com.google.zxing.client.android.SCAN", launchIntent.getAction());
    assertEquals("QR_CODE_MODE", launchIntent.getStringExtra("SCAN_MODE"));
    assertEquals(false, launchIntent.getExtras().get("SAVE_HISTORY"));
  }

  public void testOptionsMenuScanABarcode_withScannerNotInstalled() throws Exception {
    // When no barcode scanner is installed no matching activities are found as emulated below.
    DependencyInjector.setStartActivityListener(new StartActivityListener() {
      @Override
      public boolean onStartActivityInvoked(Context sourceContext, Intent intent) {
        throw new ActivityNotFoundException();
      }
    });

    TestUtilities.openOptionsMenuAndInvokeItem(
        getInstrumentation(), getActivity(), R.id.scan_barcode);

    // Assert that the Download Scanner dialog is displayed -- dimissDialog throws an exception
    // if the dialog has never been displayed by this Activity.
    getActivity().dismissDialog(Utilities.DOWNLOAD_DIALOG);
  }
}
