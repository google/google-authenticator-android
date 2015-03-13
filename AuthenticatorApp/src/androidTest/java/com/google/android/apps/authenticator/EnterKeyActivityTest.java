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

import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.test.MoreAsserts;
import android.test.ViewAsserts;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Unit tests for {@link EnterKeyActivity}.
 *
 * @author sarvar@google.com (Sarvar Patel)
 */
public class EnterKeyActivityTest extends ActivityInstrumentationTestCase2<EnterKeyActivity> {

  private EnterKeyActivity mActivity;
  private Instrumentation mInstr;
  private EditText mKeyEntryField;
  private EditText mAccountName;
  private Spinner mType;
  private Button mSubmitButton;
  private Collection<String> result = new ArrayList<String>();
  private AccountDb mAccountDb;

  public EnterKeyActivityTest() {
    super(TestUtilities.APP_PACKAGE_NAME, EnterKeyActivity.class);
  }

  @Override
  public void setUp() throws Exception {
    // TODO(sarvar): sending keys require that emulators have their keyguards
    // unlocked. We could do this with code here, this would require giving
    // permission in the apps AndroidManifest.xml. Consider if this is needed.
    super.setUp();

    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
    TestUtilities.withLaunchPreventingStartActivityListenerInDependencyResolver();
    mAccountDb = DependencyInjector.getAccountDb();

    setActivityInitialTouchMode(false);
    mInstr = getInstrumentation();
    mActivity = getActivity();
    mAccountName = (EditText) mActivity.findViewById(R.id.account_name);
    mKeyEntryField = (EditText) mActivity.findViewById(R.id.key_value);
    mType = (Spinner) mActivity.findViewById(R.id.type_choice);
    mSubmitButton = (Button) mActivity.findViewById(R.id.button_right);
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();

    super.tearDown();
  }

  public void testPreconditions() {
    // check that the test has input fields
    assertNotNull(mAccountName);
    assertNotNull(mKeyEntryField);
    assertNotNull(mType);
    assertNotNull(mType.getAdapter());
    assertEquals(2, mType.getAdapter().getCount());
  }

  public void testStartingFieldValues() {
    assertEquals("", mAccountName.getText().toString());
    assertEquals("", mKeyEntryField.getText().toString());
  }

  public void testFieldsAreOnScreen() {
    Window window = mActivity.getWindow();
    View origin = window.getDecorView();
    ViewAsserts.assertOnScreen(origin, mAccountName);
    ViewAsserts.assertOnScreen(origin, mKeyEntryField);
    ViewAsserts.assertOnScreen(origin, mType);
    ViewAsserts.assertOnScreen(origin, mSubmitButton);
  }

  private void checkCorrectEntry(AccountDb.OtpType type) {
    // enter account name
    assertEquals("johndoe@gmail.com",
        TestUtilities.setText(mInstr, mAccountName, "johndoe@gmail.com"));
    // enter key
    assertEquals("7777777777777777",
        TestUtilities.setText(mInstr, mKeyEntryField, "7777777777777777"));
    // select TOTP/HOTP type
    assertEquals(mActivity.getResources().getStringArray(R.array.type)[type.value],
        TestUtilities.selectSpinnerItem(mInstr, mType, type.value));

    assertFalse(mActivity.isFinishing());
    // save
    TestUtilities.clickView(mInstr, mSubmitButton);
    // check activity's resulting update of database.
    assertEquals(1, mAccountDb.getNames(result));
    MoreAsserts.assertContentsInOrder(result, "johndoe@gmail.com");
    assertEquals(type, mAccountDb.getType("johndoe@gmail.com"));
    assertEquals(0, mAccountDb.getCounter("johndoe@gmail.com").intValue());

    assertTrue(mActivity.isFinishing());
  }

  public void testCorrectEntryTOTP() {
    checkCorrectEntry(AccountDb.OtpType.TOTP);
  }

  public void testCorrectEntryHOTP() {
    checkCorrectEntry(AccountDb.OtpType.HOTP);
  }

  public void testSubmitFailsWithShortKey()  {
    assertEquals("johndoe@gmail.com",
        TestUtilities.setText(mInstr, mAccountName, "johndoe@gmail.com"));
    TestUtilities.selectSpinnerItem(mInstr, mType, AccountDb.OtpType.TOTP.value);
    assertEquals(
        mActivity.getResources().getStringArray(R.array.type)[AccountDb.OtpType.TOTP.value],
        TestUtilities.selectSpinnerItem(mInstr, mType, AccountDb.OtpType.TOTP.value));


    // enter bad key without submitting, check status message
    assertEquals("@@",
        TestUtilities.setText(mInstr, mKeyEntryField, "@@"));
    assertEquals(mActivity.getString(R.string.enter_key_illegal_char), mKeyEntryField.getError());

    // clear bad keys, see status message is cleared.
    assertEquals("", TestUtilities.setText(mInstr, mKeyEntryField, ""));
    assertEquals(null, mKeyEntryField.getError());

    // enter short key, check status message is empty
    assertEquals("77777",
        TestUtilities.setText(mInstr, mKeyEntryField, "77777"));
    assertEquals(null, mKeyEntryField.getError());

    // submit short key, and verify no updates to database and check status msg.
    TestUtilities.clickView(getInstrumentation(), mSubmitButton);
    assertFalse(mActivity.isFinishing());
    assertEquals(0, mAccountDb.getNames(result));
    assertEquals(mActivity.getString(R.string.enter_key_too_short), mKeyEntryField.getError());
    // check key field is unchanged.
    assertEquals("77777", mKeyEntryField.getText().toString());

    // submit empty key.
    assertEquals("",
        TestUtilities.setText(mInstr, mKeyEntryField, ""));
    TestUtilities.clickView(getInstrumentation(), mSubmitButton);
    assertFalse(mActivity.isFinishing());
    assertEquals(0, mAccountDb.getNames(result));
    assertEquals(mActivity.getString(R.string.enter_key_too_short), mKeyEntryField.getError());
  }

  // TODO(sarvar): Consider not allowing acceptance of such bad account names.
  public void testSubmitWithEmptyAccountName()  {
    assertEquals("7777777777777777",
        TestUtilities.setText(mInstr, mKeyEntryField, "7777777777777777"));
    assertEquals(
        mActivity.getResources().getStringArray(R.array.type)[AccountDb.OtpType.TOTP.value],
        TestUtilities.selectSpinnerItem(mInstr, mType, AccountDb.OtpType.TOTP.value));

    // enter empty name
    assertEquals("",
        TestUtilities.setText(mInstr, mAccountName, ""));
    TestUtilities.clickView(mInstr, mSubmitButton);
    assertEquals(1, mAccountDb.getNames(result));
    assertEquals("7777777777777777", mAccountDb.getSecret(""));
  }

  // TODO(sarvar): Consider not allowing acceptance of such bad account names.
  public void testSubmitWithWierdAccountName()  {
    assertEquals("7777777777777777",
        TestUtilities.setText(mInstr, mKeyEntryField, "7777777777777777"));
    assertEquals(
        mActivity.getResources().getStringArray(R.array.type)[AccountDb.OtpType.TOTP.value],
        TestUtilities.selectSpinnerItem(mInstr, mType, AccountDb.OtpType.TOTP.value));

    // enter empty name
    assertEquals(",,",
        TestUtilities.setText(mInstr, mAccountName, ",,"));
    TestUtilities.clickView(getInstrumentation(), mSubmitButton);
    assertEquals(1, mAccountDb.getNames(result));
    assertEquals("7777777777777777", mAccountDb.getSecret(",,"));
  }
}
