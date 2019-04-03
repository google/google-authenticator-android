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

package com.google.android.apps.authenticator.otp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.equalTo;

import android.app.Instrumentation;
import android.support.design.widget.TextInputLayout;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.android.apps.authenticator2.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EnterKeyActivity}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class EnterKeyActivityTest {

  private EnterKeyActivity activity;
  private Instrumentation instr;
  private EditText keyEntryField;
  private EditText accountName;
  private TextInputLayout keyEntryFieldInputLayout;
  private RadioButton typeTotp;
  private RadioButton typeHotp;
  private Button submitButton;
  private AccountDb accountDb;

  @Rule public ActivityTestRule<EnterKeyActivity> activityTestRule =
      new ActivityTestRule<>(
          EnterKeyActivity.class, /* initialTouchMode= */ false, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    TestUtilities.withLaunchPreventingStartActivityListenerInDependencyResolver();
    accountDb = DependencyInjector.getAccountDb();
    instr = InstrumentationRegistry.getInstrumentation();

    activity = activityTestRule.launchActivity(null);

    accountName = activity.findViewById(R.id.account_name);
    keyEntryField = activity.findViewById(R.id.key_value);
    typeTotp = activity.findViewById(R.id.type_choice_totp);
    typeHotp = activity.findViewById(R.id.type_choice_hotp);
    submitButton = activity.findViewById(R.id.add_account_button_enter_key);
    keyEntryFieldInputLayout = activity.findViewById(R.id.key_value_input_layout);
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
  }

  @Test
  public void testPreconditions() {
    // check that the test has input fields
    assertThat(accountName).isNotNull();
    assertThat(keyEntryField).isNotNull();
    assertThat(typeTotp).isNotNull();
    assertThat(typeHotp).isNotNull();
    assertThat(typeTotp.isChecked() || typeHotp.isChecked()).isTrue();
  }

  @Test
  public void testStartingFieldValues() {
    assertThat(accountName.getText().toString()).isEqualTo("");
    assertThat(keyEntryField.getText().toString()).isEqualTo("");

    assertThat(activity.getResources().getStringArray(R.array.type)[OtpType.TOTP.value])
        .isEqualTo(typeTotp.getText().toString());
    assertThat(activity.getResources().getStringArray(R.array.type)[OtpType.HOTP.value])
        .isEqualTo(typeHotp.getText().toString());
  }

  @Test
  public void testFieldsAreOnScreen() {
    onView(equalTo(accountName)).check(matches(isDisplayed()));
    onView(equalTo(keyEntryField)).check(matches(isDisplayed()));
    onView(equalTo(typeTotp)).check(matches(isDisplayed()));
    onView(equalTo(typeHotp)).check(matches(isDisplayed()));
    onView(equalTo(submitButton)).check(matches(isDisplayed()));
  }

  private void checkCorrectEntry(
      String accountName, String expectedIndexName, AccountDb.OtpType type) {
    // enter account name
    TestUtilities.setText(instr, this.accountName, accountName);
    // enter key
    TestUtilities.setText(instr, keyEntryField, "7777777777777777");
    if (type == OtpType.TOTP) {
      TestUtilities.clickView(instr, typeTotp);
    } else {
      TestUtilities.clickView(instr, typeHotp);
    }

    assertThat(activity.isFinishing()).isFalse();
    // save
    TestUtilities.clickView(instr, submitButton);
    // check activity's resulting update of database.
    AccountIndex index = new AccountIndex(expectedIndexName, null);
    assertThat(accountDb.indexExists(index)).isTrue();
    assertThat(accountDb.getType(index)).isEqualTo(type);
    assertThat(accountDb.getCounter(index).intValue()).isEqualTo(0);

    assertThat(activity.isFinishing()).isTrue();
  }

  @Test
  public void testCorrectEntryTOTP() {
    String accountName = "johndoe@gmail.com";
    checkCorrectEntry(accountName, accountName, AccountDb.OtpType.TOTP);
    assertThat(accountDb.getAccounts()).hasSize(1);
  }

  @Test
  public void testCorrectEntryHOTP() {
    String accountName = "johndoe@gmail.com";
    checkCorrectEntry(accountName, accountName, AccountDb.OtpType.HOTP);
    assertThat(accountDb.getAccounts()).hasSize(1);
  }

  @Test
  public void testCorrectEntryWithDuplicateNullIssuerName() {
    String accountName = "johndoe@gmail.com";
    accountDb.add(accountName, "2222222222222222", OtpType.TOTP, null, null, null);
    checkCorrectEntry(accountName, accountName + "(1)", AccountDb.OtpType.TOTP);
    assertThat(accountDb.getAccounts()).hasSize(2);
  }

  @Test
  public void testCorrectEntryWithDuplicateIssuerName() {
    String accountName = "johndoe@gmail.com";
    accountDb.add(
        accountName, "2222222222222222", OtpType.TOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    checkCorrectEntry(accountName, accountName, AccountDb.OtpType.TOTP);
    assertThat(accountDb.getAccounts()).hasSize(2);
  }

  @Test
  public void testCorrectEntryWithFullyQualifiedNameAndDuplicateNullIssuerName() {
    String accountName = "johndoe@gmail.com";
    accountDb.add(accountName, "2222222222222222", OtpType.TOTP, null, null, null);
    // Enter a fully qualified account name with an issuer in front
    accountName = AccountDb.GOOGLE_ISSUER_NAME + ":" + accountName;
    // No collision should occur
    checkCorrectEntry(accountName, accountName, AccountDb.OtpType.TOTP);
    assertThat(accountDb.getAccounts()).hasSize(2);
  }

  @Test
  public void testCorrectEntryWithFullyQualifiedNameAndDuplicateIssuerName() {
    String accountName = "johndoe@gmail.com";
    accountDb.add(
        accountName, "2222222222222222", OtpType.TOTP, null, null, AccountDb.GOOGLE_ISSUER_NAME);
    // Enter a fully qualified account name with an issuer in front
    accountName = AccountDb.GOOGLE_ISSUER_NAME + ":" + accountName;
    // The null issuer always displays the unaltered version of the name, even after a collision
    checkCorrectEntry(accountName, accountName, AccountDb.OtpType.TOTP);
    assertThat(accountDb.getAccounts()).hasSize(2);
  }

  @Test
  public void testSubmitFailsWithShortKey() {
    TestUtilities.setText(instr, accountName, "johndoe@gmail.com");
    TestUtilities.clickView(instr, typeTotp);

    // enter bad key without submitting, check status message
    TestUtilities.setText(instr, keyEntryField, "@@");
    assertThat(keyEntryFieldInputLayout.getError().toString())
        .isEqualTo(activity.getString(R.string.enter_key_illegal_char));

    // clear bad keys, see status message is cleared.
    TestUtilities.setText(instr, keyEntryField, "");
    assertThat(keyEntryFieldInputLayout.getError()).isNull();

    // enter short key, check status message is empty
    TestUtilities.setText(instr, keyEntryField, "77777");
    assertThat(keyEntryFieldInputLayout.getError()).isNull();

    // submit short key, and verify no updates to database and check status msg.
    TestUtilities.clickView(instr, submitButton);
    assertThat(activity.isFinishing()).isFalse();
    assertThat(accountDb.getAccounts()).isEmpty();
    assertThat(keyEntryFieldInputLayout.getError().toString())
        .isEqualTo(activity.getString(R.string.enter_key_too_short));
    // check key field is unchanged.
    assertThat(keyEntryField.getText().toString()).isEqualTo("77777");

    // submit empty key.
    TestUtilities.setText(instr, keyEntryField, "");
    TestUtilities.clickView(instr, submitButton);
    assertThat(activity.isFinishing()).isFalse();
    assertThat(accountDb.getAccounts()).isEmpty();
    assertThat(keyEntryFieldInputLayout.getError().toString())
        .isEqualTo(activity.getString(R.string.enter_key_too_short));
  }

  @Test
  public void testSubmitWithEmptyAccountName() {
    TestUtilities.setText(instr, keyEntryField, "7777777777777777");
    TestUtilities.clickView(instr, typeTotp);

    // enter empty name
    TestUtilities.setText(instr, accountName, "");
    TestUtilities.clickView(instr, submitButton);
    assertThat(accountDb.getAccounts()).hasSize(1);
    assertThat(accountDb.getSecret(new AccountIndex("", null))).isEqualTo("7777777777777777");
  }

  @Test
  public void testSubmitWithWeirdAccountName() {
    TestUtilities.setText(instr, keyEntryField, "7777777777777777");
    TestUtilities.clickView(instr, typeTotp);

    // enter empty name
    TestUtilities.setText(instr, accountName, ",,");
    TestUtilities.clickView(instr, submitButton);
    assertThat(accountDb.getAccounts()).hasSize(1);
    assertThat(accountDb.getSecret(new AccountIndex(",,", null))).isEqualTo("7777777777777777");
  }
}
