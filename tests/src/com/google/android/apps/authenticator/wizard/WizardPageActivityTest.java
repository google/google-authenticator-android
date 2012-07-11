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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.TestUtilities;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator2.R;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.test.ActivityUnitTestCase;
import android.view.View;
import android.widget.TextView;

import java.io.Serializable;

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

  public void testCleanStartSucceeds_withNullWizardState() {
    WizardPageActivity<WizardState> activity = startActivityWithWizardState(null);
    assertNull(activity.getWizardState());
  }

  public void testCleanStartSucceeds_withWizardState() {
    WizardState wizardState = new WizardState();
    wizardState.mText = "123";
    WizardPageActivity<WizardState> activity = startActivityWithWizardState(wizardState);

    // Check that the wizard state has been loaded by the Activity from the Intent
    assertEquals(wizardState, activity.getWizardState());
  }

  public void testStartWithInstanceStateLoadsWizardStateFromBundle() {
    WizardState wizardStateInIntent = new WizardState();
    WizardState wizardStateInBundle = new WizardState();
    wizardStateInBundle.mText = "1234";
    Bundle savedInstanceState = new Bundle();
    savedInstanceState.putSerializable(WizardPageActivity.KEY_WIZARD_STATE, wizardStateInBundle);
    WizardPageActivity<WizardState> activity =
        startActivity(getStartIntent(wizardStateInIntent), savedInstanceState, null);

    // Check that the wizard state has been loaded by the Activity from the Bundle
    assertEquals(wizardStateInBundle, activity.getWizardState());
  }

  public void testOnSaveSaveInstanceStateSavesWizardStateInBundle() {
    WizardState wizardState = new WizardState();
    wizardState.mText = "test";
    WizardPageActivity<WizardState> activity = startActivityWithWizardState(wizardState);
    Bundle savedInstanceState = new Bundle();
    activity.onSaveInstanceState(savedInstanceState);

    assertEquals(
        wizardState,
        savedInstanceState.getSerializable(WizardPageActivity.KEY_WIZARD_STATE));
  }

  public void testSetWizardState() {
    WizardPageActivity<WizardState> activity = startActivity();
    WizardState wizardState = new WizardState();
    wizardState.mText = "test";
    assertFalse(wizardState.equals(activity.getWizardState()));
    activity.setWizardState(wizardState);
    assertEquals(wizardState, activity.getWizardState());
  }

  public void testUiStateAfterCreate() {
    WizardPageActivity<WizardState> activity = startActivity();
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
    WizardPageActivity<WizardState> activity = startActivity();
    activity.setButtonBarModeMiddleButtonOnly();
    TestUtilities.assertViewVisibleOnScreen(activity.mMiddleButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mCancelButton);
  }

  public void testUiStateInInlineProgressMode() {
    WizardPageActivity<WizardState> activity = startActivity();
    activity.showInlineProgress(null);
    TestUtilities.assertViewVisibleOnScreen(activity.mCancelButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mMiddleButton);
    assertEquals(
        activity.getString(R.string.cancel), ((TextView) activity.mCancelButton).getText());
  }

  public void testInlineProgressCallbackInvokedWhenCancelButtonPressed() {
    WizardPageActivity<WizardState> activity = startActivity();
    View.OnClickListener listener = mock(View.OnClickListener.class);
    activity.showInlineProgress(listener);
    activity.mCancelButton.performClick();

    verify(listener).onClick(activity.mCancelButton);
  }

  public void testButtonBarModeRestoredAfterDismissingInlineProgress() {
    WizardPageActivity<WizardState> activity = startActivity();
    activity.setButtonBarModeMiddleButtonOnly();
    activity.showInlineProgress(null);
    activity.dismissInlineProgress();

    TestUtilities.assertViewVisibleOnScreen(activity.mMiddleButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mLeftButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mRightButton);
    TestUtilities.assertViewOrAnyParentVisibilityGone(activity.mCancelButton);
  }

  public void testStartPageActivity() {
    WizardPageActivity<WizardState> activity = startActivity();
    activity.getWizardState().mText = "token";
    activity.startPageActivity(TestableWizardPageActivity.class);

    Intent intent = getStartedActivityIntent();
    assertNotNull(intent);
    assertEquals(
        new ComponentName(activity, TestableWizardPageActivity.class), intent.getComponent());
    assertEquals(
        activity.getWizardState(),
        intent.getSerializableExtra(WizardPageActivity.KEY_WIZARD_STATE));
  }

  public void testStartPageActivityForResult() {
    WizardPageActivity<WizardState> activity = startActivity();
    activity.getWizardState().mText = "token";
    activity.startPageActivityForResult(TestableWizardPageActivity.class, 13);

    assertEquals(13, getStartedActivityRequest());
    Intent intent = getStartedActivityIntent();
    assertNotNull(intent);
    assertEquals(
        new ComponentName(activity, TestableWizardPageActivity.class), intent.getComponent());
    assertEquals(
        activity.getWizardState(),
        intent.getSerializableExtra(WizardPageActivity.KEY_WIZARD_STATE));
  }

  public void testExitWizardLaunchesAuthenticatorActivity() {
    WizardPageActivity<WizardState> activity = startActivity();
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
  public static class TestableWizardPageActivity extends WizardPageActivity<WizardState> {

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

  private static class WizardState implements Serializable {
    private String mText;

    @Override
    public int hashCode() {
      return (mText != null) ? mText.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      WizardState other = (WizardState) obj;
      if (mText == null) {
        if (other.mText != null) {
          return false;
        }
      } else if (!mText.equals(other.mText)) {
        return false;
      }
      return true;
    }
  }
}
