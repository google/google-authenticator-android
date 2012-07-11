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

import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.dataimport.ImportController;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.test.MoreAsserts;
import android.view.View;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for authenticator activity (part/shard 2).
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class AuthenticatorActivityPart2Test
    extends ActivityInstrumentationTestCase2<AuthenticatorActivity> {

  private AccountDb mAccountDb;
  @Mock private ImportController mMockDataImportController;

  public AuthenticatorActivityPart2Test() {
    super(TestUtilities.APP_PACKAGE_NAME, AuthenticatorActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
    mAccountDb = DependencyInjector.getAccountDb();
    initMocks(this);
    DependencyInjector.setDataImportController(mMockDataImportController);
  }

  @Override
  protected void tearDown() throws Exception {
    // Stop the activity to avoid it using the DependencyInjector after it's been closed.
    TestUtilities.invokeFinishActivityOnUiThread(getActivity());

    DependencyInjector.close();

    super.tearDown();
  }

  public void testAccountSetup_validTotpUriAccepted() throws Throwable {
    getActivity();

    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    callOnActivityResultOnUiThreadWithScannedUri(
        "otpauth://totp/" + accountName + "?secret=" + secret);

    List<String> accountNames = new ArrayList<String>();
    assertEquals(1, mAccountDb.getNames(accountNames));
    assertEquals(accountName, accountNames.get(0));
    assertEquals(secret, mAccountDb.getSecret(accountName));
    assertEquals(OtpType.TOTP, mAccountDb.getType(accountName));
    assertFalse(getActivity().isFinishing()); // AuthenticatorActivity should continue
  }

  public void testAccountSetup_validHotpUriWithoutCounterAccepted() throws Throwable {
    getActivity();

    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    callOnActivityResultOnUiThreadWithScannedUri(
        "otpauth://hotp/" + accountName + "?secret=" + secret);

    List<String> accountNames = new ArrayList<String>();
    assertEquals(1, mAccountDb.getNames(accountNames));
    assertEquals(accountName, accountNames.get(0));
    assertEquals(secret, mAccountDb.getSecret(accountName));
    assertEquals(OtpType.HOTP, mAccountDb.getType(accountName));
    assertEquals(new Integer(0), mAccountDb.getCounter(accountName));
    assertFalse(getActivity().isFinishing()); // AuthenticatorActivity should continue
  }

  public void testAccountSetup_validHotpUriWithCounterAccepted() throws Throwable {
    getActivity();

    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    callOnActivityResultOnUiThreadWithScannedUri(
        "otpauth://hotp/" + accountName + "?secret=" + secret + "&counter=264");

    List<String> accountNames = new ArrayList<String>();
    assertEquals(1, mAccountDb.getNames(accountNames));
    assertEquals(accountName, accountNames.get(0));
    assertEquals(secret, mAccountDb.getSecret(accountName));
    assertEquals(OtpType.HOTP, mAccountDb.getType(accountName));
    assertEquals(new Integer(264), mAccountDb.getCounter(accountName));
    assertFalse(getActivity().isFinishing()); // AuthenticatorActivity should continue
  }

  /////////////////////  Tests with Scanned URIs returned in ActivityResult ////////////////

  private void checkBadAccountSetup(String uri, int dialogId) throws Throwable {
    getActivity();

    callOnActivityResultOnUiThreadWithScannedUri(uri);

    List<String> accountNames = new ArrayList<String>();
    mAccountDb.getNames(accountNames);
    MoreAsserts.assertEmpty(accountNames);
    TestUtilities.assertDialogWasDisplayed(getActivity(), dialogId);
    assertFalse(getActivity().isFinishing()); // AuthenticatorActivity should continue
  }

  public void testAccountSetup_uriWithMissingSecretRejected() throws Throwable {
    checkBadAccountSetup("otpauth://totp/9.99.99.999?secret2=unused",
                         Utilities.INVALID_SECRET_IN_QR_CODE);
  }

  public void testAccountSetup_uriWithEmptySecretRejected() throws Throwable {
    checkBadAccountSetup("otpauth://totp/9.99.99.999?secret=", Utilities.INVALID_SECRET_IN_QR_CODE);
  }

  public void testAccountSetup_uriWithInvalidSecretRejected() throws Throwable {
    // The URI contains an invalid base32 characters: 1 and 8
    checkBadAccountSetup("otpauth://totp/9.99.99.999?secret=CQ1HUXJ2VWDI8WFF",
                         Utilities.INVALID_SECRET_IN_QR_CODE);
  }

  public void testAccountSetup_withNullUri() throws Throwable {
    checkBadAccountSetup(null, Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withNullScheme() throws Throwable {
    checkBadAccountSetup("totp/9.99.99.999?secret=CQAHUXJ2VWDI7WFF", Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withBadScheme() throws Throwable {
    checkBadAccountSetup("otpauth?//totp/9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withEmptyAuthority() throws Throwable {
    checkBadAccountSetup("otpauth:///9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withBadAuthority() throws Throwable {
    checkBadAccountSetup("otpauth://bad/9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withEmptyUserAccount() throws Throwable {
    checkBadAccountSetup("otpauth://totp/?secret=CQAHUXJ2VWDI7WFF",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withWhiteSpaceForUserAccount() throws Throwable {
    checkBadAccountSetup("otpauth://totp/    ?secret=CQAHUXJ2VWDI7WFF",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withCounterTooBig() throws Throwable {
    checkBadAccountSetup("otpauth://hotp/?secret=CQAHUXJ2VWDI7WFF&counter=34359738368",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withNonNumericCounter() throws Throwable {
    checkBadAccountSetup("otpauth://hotp/?secret=CQAHUXJ2VWDI7WFF&counter=abc",
                         Utilities.INVALID_QR_CODE);
  }

  public void testAccountSetup_withNullIntent() throws Throwable {
    getActivity();

    callOnActivityResultOnUiThread(AuthenticatorActivity.SCAN_REQUEST, Activity.RESULT_OK, null);

    List<String> accountNames = new ArrayList<String>();
    mAccountDb.getNames(accountNames);
    MoreAsserts.assertEmpty(accountNames);
    TestUtilities.assertDialogWasDisplayed(getActivity(), Utilities.INVALID_QR_CODE);
    assertFalse(getActivity().isFinishing()); // AuthenticatorActivity should continue
  }

  /**
   * Invokes the activity's {@code onActivityResult} as though it received the specified scanned
   * URI.
   */
  private void callOnActivityResultOnUiThreadWithScannedUri(String uri) throws Throwable {
    Intent intent = new Intent(Intent.ACTION_VIEW, null);
    intent.putExtra("SCAN_RESULT", uri);
    callOnActivityResultOnUiThread(AuthenticatorActivity.SCAN_REQUEST, Activity.RESULT_OK, intent);
  }

  private void callOnActivityResultOnUiThread(
      final int requestCode, final int resultCode, final Intent intent) throws Throwable {
    runTestOnUiThread(new Runnable() {
      @Override
      public void run () {
        getActivity().onActivityResult(requestCode, resultCode, intent);
      }
    });
  }

  public void testDirectAccountSetupWithConfirmationAccepted() throws Throwable {
    // start AuthenticatorActivity with a valid Uri for account setup.
    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    Intent intent = new Intent(Intent.ACTION_VIEW,
        Uri.parse("otpauth://totp/" + accountName + "?secret=" + secret));
    setActivityIntent(intent);
    getActivity();
    // check main screen does not have focus because of save confirmation dialog.
    View contentView = getActivity().findViewById(R.id.content_no_accounts);
    assertFalse(contentView.hasWindowFocus());
    // click Ok on the dialog box which has focus.
    TestUtilities.tapDialogPositiveButton(this);
    // check main screen gets focus back after dialog window disappears.
    TestUtilities.waitForWindowFocus(contentView);
    // check update to database.
    List<String> accountNames = new ArrayList<String>();
    assertEquals(1, mAccountDb.getNames(accountNames));
    assertEquals(accountName, accountNames.get(0));
    assertEquals(secret, mAccountDb.getSecret(accountName));
    assertEquals(OtpType.TOTP, mAccountDb.getType(accountName));
  }

  public void testDirectAccountSetupWithConfirmationRejected() throws Throwable {
    // start AuthenticatorActivity with a valid Uri for account setup.
    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    Intent intent = new Intent(Intent.ACTION_VIEW,
        Uri.parse("otpauth://totp/" + accountName + "?secret=" + secret));
    setActivityIntent(intent);
    getActivity();
    // check main screen does not have focus because of save confirmation dialog.
    View contentView = getActivity().findViewById(R.id.content_no_accounts);
    assertFalse(contentView.hasWindowFocus());
    // click Cancel on the save confirmation dialog box.
    TestUtilities.tapDialogNegativeButton(this);
    // check main screen gets focus back after dialog window disappears.
    TestUtilities.waitForWindowFocus(contentView);
    // check database has not been updated.
    List<String> accountNames = new ArrayList<String>();
    assertEquals(0, mAccountDb.getNames(accountNames));
    assertEquals(null, mAccountDb.getSecret(accountName));
  }
}
