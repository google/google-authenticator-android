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

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.otp.AccountDb.AccountIndex;
import com.google.android.apps.authenticator.otp.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.android.apps.authenticator2.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link CheckCodeActivity}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class CheckCodeActivityTest {

  private static final String ISSUER = "Test%20Issuer";

  @Rule public ActivityTestRule<CheckCodeActivity> activityTestRule =
      new ActivityTestRule<>(
          CheckCodeActivity.class, /* initialTouchMode= */ false, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    AccountDb accountDb = DependencyInjector.getAccountDb();
    accountDb.add("johndoe@gmail.com", "7777777777777777", OtpType.TOTP, null, null, null);
    accountDb.add("johndoe@gmail.com", "7777777777777777", OtpType.TOTP, null, null, ISSUER);
    accountDb.add("shadowmorton@aol.com", "2222222222222222", OtpType.HOTP, null, null, ISSUER);
    accountDb.add("maryweiss@yahoo.com", "7777777777777777", OtpType.HOTP, 0, null, null);
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
  }

  @Test
  public void testWithTimeBasedAccountWithNullIssuer() {
    doWithTimeBasedAccount(false);
  }

  @Test
  public void testWithTimeBasedAccountWithIssuer() {
    doWithTimeBasedAccount(true);
  }

  private void doWithTimeBasedAccount(boolean useAccountWithIssuer) {
    String issuer = null;
    if (useAccountWithIssuer) {
      issuer = ISSUER;
    }
    // For TOTP accounts, AuthenticatorActivity never calls CheckCodeActivity, however, the
    // code exists and we check its behavior here.
    CheckCodeActivity activity =
        activityTestRule.launchActivity(
            new Intent(Intent.ACTION_MAIN)
                .putExtra("index", new AccountIndex("johndoe@gmail.com", issuer)));

    TextView codeTextView = activity.findViewById(R.id.code_value);
    TextView checkCodeTextView = activity.findViewById(R.id.check_code);
    TextView counterValue = activity.findViewById(R.id.counter_value);

    // check existence of fields
    assertThat(activity).isNotNull();
    assertThat(checkCodeTextView).isNotNull();
    assertThat(codeTextView).isNotNull();
    assertThat(counterValue).isNotNull();

    // check visibility
    onView(equalTo(checkCodeTextView)).check(matches(isDisplayed()));
    onView(equalTo(codeTextView)).check(matches(isDisplayed()));
    assertThat(activity.findViewById(R.id.code_area).isShown()).isTrue(); // layout area
    assertThat(counterValue.isShown()).isFalse(); // TOTP has no counter value to show.
    assertThat(activity.findViewById(R.id.counter_area).isShown()).isFalse(); // layout area

    // check values
    assertThat(checkCodeTextView.getText().toString()).contains("johndoe@gmail.com");
    assertThat(codeTextView.getText().toString()).isEqualTo(Utilities.getStyledPincode("724477"));
  }

  @Test
  public void testWithCounterBasedAccount() {
    CheckCodeActivity activity =
        activityTestRule.launchActivity(
            new Intent(Intent.ACTION_MAIN)
                .putExtra("index", new AccountIndex("maryweiss@yahoo.com", null)));

    TextView codeTextView = activity.findViewById(R.id.code_value);
    TextView checkCodeTextView = activity.findViewById(R.id.check_code);
    TextView counterValue = activity.findViewById(R.id.counter_value);

    // check existence of fields
    assertThat(checkCodeTextView).isNotNull();
    assertThat(codeTextView).isNotNull();
    assertThat(counterValue).isNotNull();

    // check visibility
    onView(equalTo(checkCodeTextView)).check(matches(isDisplayed()));
    onView(equalTo(codeTextView)).check(matches(isDisplayed()));
    onView(equalTo(counterValue)).check(matches(isDisplayed()));
    assertThat(activity.findViewById(R.id.code_area).isShown()).isTrue(); // layout area
    assertThat(activity.findViewById(R.id.counter_area).isShown()).isTrue(); // layout area

    // check values
    assertThat(checkCodeTextView.getText().toString()).contains("maryweiss@yahoo.com");
    assertThat(codeTextView.getText().toString()).isEqualTo(Utilities.getStyledPincode("724477"));
    assertThat(counterValue.getText().toString()).isEqualTo("0");
  }

  @Test
  public void testWithAnotherCounterBasedAccount() {
    CheckCodeActivity activity = 
        activityTestRule.launchActivity(
            new Intent(Intent.ACTION_MAIN)
                .putExtra("index", new AccountIndex("shadowmorton@aol.com", ISSUER)));

    TextView codeTextView = activity.findViewById(R.id.code_value);
    TextView checkCodeTextView = activity.findViewById(R.id.check_code);
    assertThat(checkCodeTextView.getText().toString()).contains("shadowmorton@aol.com");
    assertThat(codeTextView.getText().toString()).isEqualTo(Utilities.getStyledPincode("086620"));
  }
}
