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

package com.google.android.apps.authenticator.enroll2sv.wizard;

import static com.google.testing.littlemock.LittleMock.mock;
import static com.google.testing.littlemock.LittleMock.verify;

import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.TestUtilities;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.content.ComponentName;
import android.content.Intent;
import android.test.ActivityUnitTestCase;
import android.view.View;
import android.widget.TextView;

/**
 * Unit tests for {@link WizardPageActivity}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class WizardPageActivityTest
    extends ActivityUnitTestCase<WizardPageActivityTest.TestableWizardPageActivity> {

  public WizardPageActivityTest() {
    super(TestableWizardPageActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DependencyInjector.resetForIntegrationTesting(getInstrumentation().getTargetContext());
  }

  @Override
  protected void tearDown() throws Exception {
    DependencyInjector.close();
    super.tearDown();
  }

  public void testCleanStartFails_withNullWizardState() {
    try {
      startActivityWithWizardState(null);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testUiStateAfterCreate() {
    WizardPageActivity activity = startActivity();
    TestUtilities.assertViewVisibleOnScreen(activity.mLeftButton);
    TestUtilities.assertViewVisibleOnScreen(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mMiddleButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mCancelButton);
    assertEquals(
        activity.getString(R.string.button_back), ((TextView) activity.mLeftButton).getText());
    assertEquals(activity.getString(
        R.string.button_next), ((TextView) activity.mRightButton).getText());
  }

  public void testUiStateInButtonBarMiddleButtonOnlyMode() {
    WizardPageActivity activity = startActivity();
    activity.setButtonBarModeMiddleButtonOnly();
    TestUtilities.assertViewVisibleOnScreen(activity.mMiddleButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mCancelButton);
  }

  public void testUiStateInInlineProgressMode() {
    WizardPageActivity activity = startActivity();
    activity.showInlineProgress(null);
    TestUtilities.assertViewVisibleOnScreen(activity.mCancelButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mMiddleButton);
    assertEquals(
        activity.getString(R.string.cancel), ((TextView) activity.mCancelButton).getText());
  }

  public void testInlineProgressCallbackInvokedWhenCancelButtonPressed() {
    WizardPageActivity activity = startActivity();
    View.OnClickListener listener = mock(View.OnClickListener.class);
    activity.showInlineProgress(listener);
    activity.mCancelButton.performClick();

    verify(listener).onClick(activity.mCancelButton);
  }

  public void testButtonBarModeRestoredAfterDismissingInlineProgress() {
    WizardPageActivity activity = startActivity();
    activity.setButtonBarModeMiddleButtonOnly();
    activity.showInlineProgress(null);
    activity.dismissInlineProgress();

    TestUtilities.assertViewVisibleOnScreen(activity.mMiddleButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mCancelButton);
  }

  public void testStartPageActivity() {
    WizardPageActivity activity = startActivity();
    activity.startPageActivity(WizardPageActivity.class);

    Intent intent = getStartedActivityIntent();
    assertNotNull(intent);
    assertEquals(new ComponentName(activity, WizardPageActivity.class), intent.getComponent());
    assertEquals(
        activity.getWizardState(),
        intent.getSerializableExtra(WizardPageActivity.KEY_WIZARD_STATE));
  }

  public void testStartPageActivityForResult() {
    WizardPageActivity activity = startActivity();
    activity.startPageActivityForResult(WizardPageActivity.class, 13);

    assertEquals(13, getStartedActivityRequest());
    Intent intent = getStartedActivityIntent();
    assertNotNull(intent);
    assertEquals(new ComponentName(activity, WizardPageActivity.class), intent.getComponent());
    assertEquals(
        activity.getWizardState(),
        intent.getSerializableExtra(WizardPageActivity.KEY_WIZARD_STATE));
  }

  public void testExitWizardLaunchesAuthenticatorActivity() {
    WizardPageActivity activity = startActivity();
    activity.exitWizard();

    Intent intent = getStartedActivityIntent();
    assertNotNull(intent);
    assertEquals(new ComponentName(activity, AuthenticatorActivity.class), intent.getComponent());
    assertTrue(
        (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) == Intent.FLAG_ACTIVITY_CLEAR_TOP);
  }

  public void testLeftButtonInvokesOnLeftButtonPressed() {
    TestableWizardPageActivity activity = startActivity();
    activity.mLeftButton.performClick();

    assertTrue(activity.mOnLeftButtonPressedInvoked);
  }

  public void testRightButtonInvokesOnRightButtonPressed() {
    TestableWizardPageActivity activity = startActivity();
    activity.mRightButton.performClick();

    assertTrue(activity.mOnRightButtonPressedInvoked);
  }

  public void testMiddleButtonInvokesOnMiddleButtonPressed() {
    TestableWizardPageActivity activity = startActivity();
    activity.setButtonBarModeMiddleButtonOnly();
    activity.mMiddleButton.performClick();

    assertTrue(activity.mOnMiddleButtonPressedInvoked);
  }

  public void testOnLeftButtonPressedInvokesOnBackPressed() {
    TestableWizardPageActivity activity = startActivity();
    activity.onLeftButtonPressed();

    assertTrue(activity.mOnBackPressedInvoked);
  }

  private static Intent getStartIntent(WizardState wizardState) {
    Intent intent = new Intent();
    if (wizardState != null) {
      intent.putExtra(WizardPageActivity.KEY_WIZARD_STATE, wizardState);
    }
    return intent;
  }

  private TestableWizardPageActivity startActivity() {
    return startActivityWithWizardState(new WizardState());
  }

  private TestableWizardPageActivity startActivityWithWizardState(WizardState wizardState) {
    return startActivity(getStartIntent(wizardState), null, null);
  }

  /**
   * Subclass of {@link WizardPageActivity} to test whether certain methods of the class are
   * invoked.
   */
  public static class TestableWizardPageActivity extends WizardPageActivity {

    private boolean mOnLeftButtonPressedInvoked;
    private boolean mOnRightButtonPressedInvoked;
    private boolean mOnMiddleButtonPressedInvoked;
    private boolean mOnBackPressedInvoked;

    @Override
    protected void onLeftButtonPressed() {
      mOnLeftButtonPressedInvoked = true;
      super.onLeftButtonPressed();
    }

    @Override
    protected void onRightButtonPressed() {
      mOnRightButtonPressedInvoked = true;
      super.onRightButtonPressed();
    }

    @Override
    protected void onMiddleButtonPressed() {
      mOnMiddleButtonPressedInvoked = true;
      super.onMiddleButtonPressed();
    }

    @Override
    public void onBackPressed() {
      mOnBackPressedInvoked = true;
      super.onBackPressed();
    }
  }
}
