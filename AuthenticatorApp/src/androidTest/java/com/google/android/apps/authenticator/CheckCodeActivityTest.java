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

import com.google.android.apps.authenticator.AccountDb.OtpType;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.MoreAsserts;
import android.test.ViewAsserts;
import android.view.View;
import android.widget.TextView;

/**
 * Unit tests for {@link CheckCodeActivity}.
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class CheckCodeActivityTest extends ActivityInstrumentationTestCase2<CheckCodeActivity> {

  public CheckCodeActivityTest() {
    super(TestUtilities.APP_PACKAGE_NAME, CheckCodeActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
    AccountDb accountDb = DependencyInjector.getAccountDb();
    accountDb.update(
        "johndoe@gmail.com", "7777777777777777", "johndoe@gmail.com", OtpType.TOTP, null);
    accountDb.update(
        "shadowmorton@aol.com", "2222222222222222", "shadowmorton@aol.com", OtpType.HOTP, null);
    accountDb.update(
        "maryweiss@yahoo.com", "7777777777777777", "maryweiss@yahoo.com", OtpType.HOTP, 0);

    setActivityInitialTouchMode(false);
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  public void testWithTimeBasedAccount() {
    // For TOTP accounts, AuthenticatorActivity never calls CheckCodeActivity, however, the
    // code exists and we check its behavior here.
    setActivityIntent(new Intent(Intent.ACTION_MAIN).putExtra("user", "johndoe@gmail.com"));
    CheckCodeActivity mActivity = getActivity();
    TextView mCodeTextView = (TextView) mActivity.findViewById(R.id.code_value);
    TextView mCheckCodeTextView = (TextView) mActivity.findViewById(R.id.check_code);
    TextView mCounterValue = (TextView) mActivity.findViewById(R.id.counter_value);

    // check existence of fields
    assertNotNull(mActivity);
    assertNotNull(mCheckCodeTextView);
    assertNotNull(mCodeTextView);
    assertNotNull(mCounterValue);

    // check visibility
    View origin = mActivity.getWindow().getDecorView();
    ViewAsserts.assertOnScreen(origin, mCheckCodeTextView);
    ViewAsserts.assertOnScreen(origin, mCodeTextView);
    assertTrue(mActivity.findViewById(R.id.code_area).isShown()); // layout area
    assertFalse(mCounterValue.isShown());  // TOTP has no counter value to show.
    assertFalse(mActivity.findViewById(R.id.counter_area).isShown());  // layout area

    // check values
    MoreAsserts.assertContainsRegex("johndoe@gmail.com", mCheckCodeTextView.getText().toString());
    assertEquals("724477", mCodeTextView.getText().toString());
  }

  public void testWithCounterBasedAccount() {
    setActivityIntent(new Intent(Intent.ACTION_MAIN).putExtra("user", "maryweiss@yahoo.com"));
    CheckCodeActivity mActivity = getActivity();
    TextView mCodeTextView = (TextView) mActivity.findViewById(R.id.code_value);
    TextView mCheckCodeTextView = (TextView) mActivity.findViewById(R.id.check_code);
    TextView mCounterValue = (TextView) mActivity.findViewById(R.id.counter_value);

    // check existence of fields
    assertNotNull(mCheckCodeTextView);
    assertNotNull(mCodeTextView);
    assertNotNull(mCounterValue);

    // check visibility
    View origin = mActivity.getWindow().getDecorView();
    ViewAsserts.assertOnScreen(origin, mCheckCodeTextView);
    ViewAsserts.assertOnScreen(origin, mCodeTextView);
    ViewAsserts.assertOnScreen(origin, mCounterValue);
    assertTrue(mActivity.findViewById(R.id.code_area).isShown()); // layout area
    assertTrue(mActivity.findViewById(R.id.counter_area).isShown());  // layout area

    // check values
    MoreAsserts.assertContainsRegex("maryweiss@yahoo.com", mCheckCodeTextView.getText().toString());
    assertEquals("724477", mCodeTextView.getText().toString());
    assertEquals("0", mCounterValue.getText().toString());
  }

  public void testWithAnotherCounterBasedAccount() {
    setActivityIntent(new Intent(Intent.ACTION_MAIN).putExtra("user", "shadowmorton@aol.com"));
    CheckCodeActivity mActivity = getActivity();
    TextView mCodeTextView = (TextView) mActivity.findViewById(R.id.code_value);
    TextView mCheckCodeTextView = (TextView) mActivity.findViewById(R.id.check_code);
    MoreAsserts.assertContainsRegex(
        "shadowmorton@aol.com", mCheckCodeTextView.getText().toString());
    assertEquals("086620", mCodeTextView.getText().toString());
  }
}
