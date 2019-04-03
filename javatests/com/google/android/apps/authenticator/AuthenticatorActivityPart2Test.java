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
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.barcode.BarcodeCaptureActivity;
import com.google.android.apps.authenticator.barcode.BarcodeConditionChecker;
import com.google.android.apps.authenticator.otp.AccountDb;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.otp.OtpProvider;
import com.google.android.apps.authenticator.otp.OtpSource;
import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator2.R;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for authenticator activity (part/shard 2). */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class AuthenticatorActivityPart2Test {

  private AccountDb mAccountDb;
  @Mock private TotpClock mockTotpClock;

  @Rule public ActivityTestRule<AuthenticatorActivity> activityTestRule =
      new ActivityTestRule<>(
          AuthenticatorActivity.class, /* initialTouchMode= */ true, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    mAccountDb = DependencyInjector.getAccountDb();

    initMocks(this);
    DaggerInjector.init(new MockModule());
  }

  @After
  public void tearDown() throws Exception {
    // Stop the activity to avoid it using the DependencyInjector after it's been closed.
    TestUtilities.invokeFinishActivityOnUiThread(activityTestRule.getActivity());

    DependencyInjector.close();
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithoutIssuer() throws Exception {
    doAccountSetupViaReceivedIntentFor("test@gmail.com", null);
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithoutIssuer_prefixedName() throws Exception {
    doAccountSetupViaReceivedIntentFor("Some Prefix:test@gmail.com", null);
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithIssuer() throws Exception {
    doAccountSetupViaReceivedIntentFor("test@gmail.com", "Some Issuer");
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithGoogleIssuer() throws Exception {
    doAccountSetupViaReceivedIntentFor("test@gmail.com", AccountDb.GOOGLE_ISSUER_NAME);
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithPrefixedIssuer() throws Exception {
    doAccountSetupViaReceivedIntentFor("Some issuer:  test@gmail.com", "Some Issuer");
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithMismatchedIssuer() throws Exception {
    doAccountSetupViaReceivedIntentFor("Mismatched:test@gmail.com", "Some Issuer");
  }

  private void doAccountSetupViaReceivedIntentFor(String accountName, String issuer) {
    String issuerParam = (issuer == null) ? "" : "&issuer=" + uriEncode(issuer);
    activityTestRule.launchActivity(
        new Intent(Intent.ACTION_VIEW)
            .setData(
                Uri.parse("otpauth://totp/" + accountName + "?secret=AAAABBBB" + issuerParam)));
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    // The Activity is supposed to display a dialog prompting the user whether to add the account.
    assertThat(mAccountDb.getAccounts()).isEmpty();
    onView(withText(R.string.ok)).perform(click());

    AccountIndex index = new AccountIndex(accountName, issuer);
    assertThat(mAccountDb.getAccounts()).containsExactly(index).inOrder();
    assertThat(mAccountDb.getSecret(index)).isEqualTo("AAAABBBB");
    assertThat(mAccountDb.getType(index)).isEqualTo(OtpType.TOTP);
    // AuthenticatorActivity should continue
    assertThat(activityTestRule.getActivity().isFinishing()).isFalse();
  }

  @Test
  public void testAccountSetupViaReceivedIntentWithWrongAction() {
    String accountName = "test@gmail.com";
    activityTestRule.launchActivity(
        new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AuthenticatorActivity.class)
            .setData(Uri.parse("otpauth://totp/" + accountName + "?secret=AAAABBBB")));
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();

    // Check that the account was not added
    TestUtilities.assertDialogWasNotDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_SAVE_KEY);
    assertThat(mAccountDb.getAccounts()).isEmpty();
  }

  @Test
  public void testAccountSetup_validTotpUriWithoutIssuerAccepted() throws Throwable {
    activityTestRule.launchActivity(null);
    doAccountSetupWithValidUri("9.99.99.999", null, OtpType.TOTP, null);
    doAccountSetupWithValidUri("SomePrefix:test@gmail.com", null, OtpType.TOTP, null);
  }

  @Test
  public void testAccountSetup_validTotpUriWithIssuerAccepted() throws Throwable {
    activityTestRule.launchActivity(null);
    doAccountSetupWithValidUri("9.99.99.999", "Some Issuer", OtpType.TOTP, null);
    doAccountSetupWithValidUri("9.99.99.999", AccountDb.GOOGLE_ISSUER_NAME, OtpType.TOTP, null);
    doAccountSetupWithValidUri("Some Issuer:test@gmail.com", "Some Issuer", OtpType.TOTP, null);
    doAccountSetupWithValidUri("Issuer:  whitespace@test.com", "Issuer", OtpType.TOTP, null);
    doAccountSetupWithValidUri("Mismatched:test@gmail.com", "Issuer", OtpType.TOTP, null);
  }

  @Test
  public void testAccountSetup_validHotpUriWithoutCounterAccepted() throws Throwable {
    activityTestRule.launchActivity(null);
    String accountName = "9.99.99.999";
    doAccountSetupWithValidUri(accountName, null, OtpType.HOTP, null);
    doAccountSetupWithValidUri(accountName, "Some Issuer", OtpType.HOTP, null);
    doAccountSetupWithValidUri(accountName, AccountDb.GOOGLE_ISSUER_NAME, OtpType.HOTP, null);
  }

  @Test
  public void testAccountSetup_validHotpUriWithCounterAccepted() throws Throwable {
    activityTestRule.launchActivity(null);
    String accountName = "9.99.99.999";
    doAccountSetupWithValidUri(accountName, null, OtpType.HOTP, 264);
    doAccountSetupWithValidUri(accountName, "Some Issuer", OtpType.HOTP, 264);
    doAccountSetupWithValidUri(accountName, AccountDb.GOOGLE_ISSUER_NAME, OtpType.HOTP, 264);
  }

  public void doAccountSetupWithValidUri(
      String accountName, String issuer, OtpType type, Integer counter) {
    String secret = "CQAHUXJ2VWDI7WFF";
    String uriType = type.name().toLowerCase();
    String counterParam = (counter == null) ? "" : "&counter=" + counter;
    String issuerParam = (issuer == null) ? "" : "&issuer=" + uriEncode(issuer);
    String path = uriType + "/" + accountName;
    callOnActivityResultOnUiThreadWithScannedUri(
        "otpauth://" + path + "?secret=" + secret + counterParam + issuerParam);

    AccountIndex index = new AccountIndex(accountName, issuer);
    assertThat(mAccountDb.getAccounts()).containsExactly(index).inOrder();
    assertThat(mAccountDb.getSecret(index)).isEqualTo(secret);
    assertThat(mAccountDb.getType(index)).isEqualTo(type);
    if (counter != null) {
      assertThat(mAccountDb.getCounter(index)).isEqualTo(counter);
    }
    assertThat(activityTestRule.getActivity().isFinishing()).isFalse(); // AuthenticatorActivity should continue
    mAccountDb.deleteAllData(); // Reset for next run
  }

  /////////////////////  Tests with Scanned URIs returned in ActivityResult ////////////////

  private void checkBadAccountSetup(String uri, int dialogId) {
    activityTestRule.launchActivity(null);

    callOnActivityResultOnUiThreadWithScannedUri(uri);

    assertThat(mAccountDb.getAccounts()).isEmpty();
    TestUtilities.assertDialogWasDisplayed(activityTestRule.getActivity(), dialogId);
    // AuthenticatorActivity should continue
    assertThat(activityTestRule.getActivity().isFinishing()).isFalse();
  }

  @Test
  public void testAccountSetup_uriWithMissingSecretRejected() throws Throwable {
    checkBadAccountSetup(
        "otpauth://totp/9.99.99.999?secret2=unused",
        AuthenticatorActivity.DIALOG_ID_INVALID_SECRET_IN_QR_CODE);
  }

  @Test
  public void testAccountSetup_uriWithEmptySecretRejected() throws Throwable {
    checkBadAccountSetup(
        "otpauth://totp/9.99.99.999?secret=",
        AuthenticatorActivity.DIALOG_ID_INVALID_SECRET_IN_QR_CODE);
  }

  @Test
  public void testAccountSetup_uriWithInvalidSecretRejected() throws Throwable {
    // The URI contains an invalid base32 characters: 1 and 8
    checkBadAccountSetup(
        "otpauth://totp/9.99.99.999?secret=CQ1HUXJ2VWDI8WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_SECRET_IN_QR_CODE);
  }

  @Test
  public void testAccountSetup_withNullUri() throws Throwable {
    checkBadAccountSetup(null, AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withNullScheme() throws Throwable {
    checkBadAccountSetup(
        "totp/9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withBadScheme() throws Throwable {
    checkBadAccountSetup(
        "otpauth?//totp/9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withEmptyAuthority() throws Throwable {
    checkBadAccountSetup(
        "otpauth:///9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withBadAuthority() throws Throwable {
    checkBadAccountSetup(
        "otpauth://bad/9.99.99.999?secret=CQAHUXJ2VWDI7WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withEmptyUserAccount() throws Throwable {
    checkBadAccountSetup(
        "otpauth://totp/?secret=CQAHUXJ2VWDI7WFF", AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withWhiteSpaceForUserAccount() throws Throwable {
    checkBadAccountSetup(
        "otpauth://totp/    ?secret=CQAHUXJ2VWDI7WFF",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withCounterTooBig() throws Throwable {
    checkBadAccountSetup(
        "otpauth://hotp/?secret=CQAHUXJ2VWDI7WFF&counter=34359738368",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withNonNumericCounter() throws Throwable {
    checkBadAccountSetup(
        "otpauth://hotp/?secret=CQAHUXJ2VWDI7WFF&counter=abc",
        AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
  }

  @Test
  public void testAccountSetup_withNullIntent() {
    activityTestRule.launchActivity(null);

    TestUtilities.invokeOnActivityResultOnUiThread(
        activityTestRule.getActivity(),
        AuthenticatorActivity.SCAN_REQUEST, Activity.RESULT_OK, null);

    assertThat(mAccountDb.getAccounts()).isEmpty();
    TestUtilities.assertDialogWasDisplayed(
        activityTestRule.getActivity(), AuthenticatorActivity.DIALOG_ID_INVALID_QR_CODE);
    // AuthenticatorActivity should continue
    assertThat(activityTestRule.getActivity().isFinishing()).isFalse();
  }

  /**
   * Invokes the activity's {@code onActivityResult} as though it received the specified scanned
   * URI.
   */
  private void callOnActivityResultOnUiThreadWithScannedUri(String uri) {
    Intent intent = new Intent(Intent.ACTION_VIEW, null);
    intent.putExtra(BarcodeCaptureActivity.INTENT_EXTRA_BARCODE_VALUE, uri);
    TestUtilities.invokeOnActivityResultOnUiThread(
        activityTestRule.getActivity(),
        AuthenticatorActivity.SCAN_REQUEST, Activity.RESULT_OK, intent);
  }

  @Test
  public void testDirectAccountSetupWithConfirmationAccepted() throws Throwable {
    // start AuthenticatorActivity with a valid Uri for account setup.
    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    AccountIndex index = new AccountIndex(accountName, null); // Use a null issuer for now
    activityTestRule.launchActivity(
        new Intent(
            Intent.ACTION_VIEW, Uri.parse("otpauth://totp/" + accountName + "?secret=" + secret)));
    // check main screen does not have focus because of save confirmation dialog.
    View contentView = activityTestRule.getActivity().findViewById(R.id.content_no_accounts);
    assertThat(contentView.hasWindowFocus()).isFalse();
    // click Ok on the dialog box which has focus.
    TestUtilities.tapDialogPositiveButton(InstrumentationRegistry.getInstrumentation());
    // check main screen gets focus back after dialog window disappears.
    TestUtilities.waitForWindowFocus(contentView);
    // check update to database.
    assertThat(mAccountDb.getAccounts()).containsExactly(index).inOrder();
    assertThat(mAccountDb.getSecret(index)).isEqualTo(secret);
    assertThat(mAccountDb.getType(index)).isEqualTo(OtpType.TOTP);
  }

  @Test
  public void testDirectAccountSetupWithConfirmationRejected() throws Throwable {
    // start AuthenticatorActivity with a valid Uri for account setup.
    String secret = "CQAHUXJ2VWDI7WFF";
    String accountName = "9.99.99.999";
    AccountIndex index = new AccountIndex(accountName, null); // Use a null issuer for now
    activityTestRule.launchActivity(
        new Intent(
            Intent.ACTION_VIEW, Uri.parse("otpauth://totp/" + accountName + "?secret=" + secret)));
    // check main screen does not have focus because of save confirmation dialog.
    View contentView = activityTestRule.getActivity().findViewById(R.id.content_no_accounts);
    assertThat(contentView.hasWindowFocus()).isFalse();
    // click Cancel on the save confirmation dialog box.
    TestUtilities.tapDialogNegativeButton(InstrumentationRegistry.getInstrumentation());
    // check main screen gets focus back after dialog window disappears.
    TestUtilities.waitForWindowFocus(contentView);
    // check database has not been updated.
    assertThat(mAccountDb.getAccounts()).isEmpty();
    assertThat(mAccountDb.getSecret(index)).isNull();
  }

  private static String uriEncode(String raw) {
    return Uri.encode(raw);
  }

  /** Dagger module for unit tests */
  @Module(
      library = true,
      injects = {
        AuthenticatorActivity.class,
      })
  public class MockModule {
    @Provides
    OtpSource providesOtpSource() {
      return new OtpProvider(mAccountDb, mockTotpClock);
    }

    @Provides
    @Singleton
    public BarcodeConditionChecker provideGoogleApiAvailabilityHelper() {
      return new BarcodeConditionChecker();
    }
  }
}
