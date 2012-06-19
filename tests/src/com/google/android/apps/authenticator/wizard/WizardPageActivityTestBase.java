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

package com.google.android.apps.authenticator.wizard;

import com.google.android.apps.authenticator.TestUtilities;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.content.ComponentName;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import java.io.Serializable;
import java.util.concurrent.TimeoutException;

/**
 * Base class for unit tests of Activity classes representing pages of a wizard.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
@SuppressWarnings("rawtypes")
public class WizardPageActivityTestBase<
    A extends WizardPageActivity, WizardState extends Serializable>
    extends ActivityInstrumentationTestCase2<A> {

  public WizardPageActivityTestBase(Class<A> activityClass) {
    super(TestUtilities.APP_PACKAGE_NAME, activityClass);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
    TestUtilities.withLaunchPreventingStartActivityListenerInDependencyResolver();
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();
    super.tearDown();
  }

  protected void setWizardStateInActivityIntent(WizardState state) {
    setActivityIntent(new Intent().putExtra(WizardPageActivity.KEY_WIZARD_STATE, state));
  }

  @SuppressWarnings("unchecked")
  protected WizardState getWizardStateFromIntent(Intent intent) {
    return (WizardState) intent.getSerializableExtra(WizardPageActivity.KEY_WIZARD_STATE);
  }

  /**
   * Asserts that pressing the {@code Back} key finishes the Activity under test.
   */
  protected void assertBackKeyFinishesActivity() throws InterruptedException, TimeoutException {
    TestUtilities.invokeActivityOnBackPressedOnUiThread(getActivity());

    assertTrue(getActivity().isFinishing());
  }

  /**
   * Asserts that pressing the {@code Back} key does not finish the Activity under test.
   */
  protected void assertBackKeyDoesNotFinishActivity()
      throws InterruptedException, TimeoutException {
    TestUtilities.invokeActivityOnBackPressedOnUiThread(getActivity());

    assertFalse(getActivity().isFinishing());
  }

  protected void assertLeftButtonPressFinishesActivity() {
    pressButton(R.id.button_left);
    assertTrue(getActivity().isFinishing());
  }

  private void pressButton(int buttonViewId) {
    View button = getActivity().findViewById(buttonViewId);
    // The button can only be pressed if it's on screen and visible
    assertNotNull(button);
    TestUtilities.assertViewVisibleOnScreen(button);
    assertTrue(button.isEnabled());
    TestUtilities.assertViewVisibleOnScreen(button);

    TestUtilities.clickView(getInstrumentation(), button);
  }

  protected void pressLeftButton() {
    pressButton(R.id.button_left);
  }

  protected void pressRightButton() {
    pressButton(R.id.button_right);
  }

  protected void pressMiddleButton() {
    pressButton(R.id.button_middle);
  }

  private Intent pressButtonAndCaptureActivityStartIntent(int buttonViewId) {
    pressButton(buttonViewId);

    return TestUtilities.verifyWithTimeoutThatStartActivityAttemptedExactlyOnce();
  }

  protected Intent pressMiddleButtonAndCaptureActivityStartIntent() {
    return pressButtonAndCaptureActivityStartIntent(R.id.button_middle);
  }

  protected Intent pressRightButtonAndCaptureActivityStartIntent() {
    return pressButtonAndCaptureActivityStartIntent(R.id.button_right);
  }

  protected void assertIntentForClassInTargetPackage(Class<?> expectedClass, Intent intent) {
    assertEquals(
        new ComponentName(
            getInstrumentation().getTargetContext(),
            expectedClass),
        intent.getComponent());
  }
}
