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

import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator2.R;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;

/**
 * Base class for a page of a wizard.
 *
 * <p>
 * The main features are:
 * <ul>
 * <li>Layout consists of a settable contents page (see {@link #setPageContentView(int)})
 * and a button bar at the bottom of the page,</li>
 * <li>Supports three modes: Back + Next buttons, middle button only, and progress bar (with
 * indeterminate progress) with a Cancel button. Back + Next buttons is the default.</li>
 * <li>Automatically passes the status of the wizard through the pages if they are launched with
 * {@link #startPageActivity(Class)}.</li>
 * </ul>
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class WizardPageActivity<WizardState extends Serializable> extends TestableActivity {

  // @VisibleForTesting
  public static final String KEY_WIZARD_STATE = "wizardState";

  /** Type of the button bar displayed at the bottom of the page. */
  private enum ButtonBarType {
    LEFT_RIGHT_BUTTONS,
    MIDDLE_BUTTON_ONLY,
    CANCEL_BUTTON_ONLY,
  }

  private WizardState mWizardState;

  private View mLeftRightButtonBar;
  private View mMiddleButtonOnlyBar;
  private View mCancelButtonOnlyBar;

  private View mInlineProgressView;

  private ViewGroup mPageContentView;

  protected Button mLeftButton;
  protected Button mRightButton;
  protected Button mMiddleButton;
  protected View mCancelButton;

  private ButtonBarType mButtonBarType;
  private ButtonBarType mButtonBarTypeBeforeInlineProgressDisplayed;

  @SuppressWarnings("unchecked")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    WizardState wizardState;
    if (savedInstanceState == null) {
      wizardState = getWizardStateFromIntent(getIntent());
    } else {
      wizardState = (WizardState) savedInstanceState.getSerializable(KEY_WIZARD_STATE);
    }
    checkWizardStateValidity(wizardState);
    mWizardState = wizardState;

    setContentView(R.layout.wizard_page);

    mLeftRightButtonBar = findViewById(R.id.button_bar_left_right_buttons);
    mMiddleButtonOnlyBar = findViewById(R.id.button_bar_middle_button_only);
    mPageContentView = (ViewGroup) findViewById(R.id.page_content);

    mLeftButton = (Button) mLeftRightButtonBar.findViewById(R.id.button_left);
    mLeftButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onLeftButtonPressed();
      }
    });

    mRightButton = (Button) findViewById(R.id.button_right);
    mRightButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onRightButtonPressed();
      }
    });

    mMiddleButton = (Button) findViewById(R.id.button_middle);
    mMiddleButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onMiddleButtonPressed();
      }
    });

    mCancelButtonOnlyBar = findViewById(R.id.button_bar_cancel_only);
    mInlineProgressView = findViewById(R.id.inline_progress);
    mCancelButton = findViewById(R.id.button_cancel);
    setButtonBarType(ButtonBarType.LEFT_RIGHT_BUTTONS);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable(KEY_WIZARD_STATE, mWizardState);
  }

  protected WizardState getWizardState() {
    return mWizardState;
  }

  protected void setWizardState(WizardState wizardState) {
    mWizardState = wizardState;
  }

  protected View setPageContentView(int resId) {
    View view = getLayoutInflater().inflate(resId, null);
    mPageContentView.removeAllViews();
    mPageContentView.addView(view);
    return view;
  }

  protected void setButtonBarModeMiddleButtonOnly() {
    setButtonBarType(ButtonBarType.MIDDLE_BUTTON_ONLY);
  }

  /**
   * Invoked when the left button is pressed. The default implementation invokes
   * {@link #onBackPressed()}.
   */
  protected void onLeftButtonPressed() {
    onBackPressed();
  }

  /**
   * Invoked when the right button is pressed. The default implementation does nothing.
   */
  protected void onRightButtonPressed() {}

  /**
   * Invoked when the middle button is pressed. The default implementation does nothing.
   */
  protected void onMiddleButtonPressed() {}

  /**
   * Launches/displays the specified page of the wizard. The page will get a copy of the current
   * state of the wizard.
   */
  protected void startPageActivity(Class<? extends WizardPageActivity<WizardState>> activityClass) {
    Intent intent = new Intent(this, activityClass);
    intent.putExtra(KEY_WIZARD_STATE, getWizardState());
    startActivity(intent);
  }

  /**
   * Launches/displays the specified page of the wizard. The page will get a copy of the current
   * state of the wizard.
   */
  protected void startPageActivityForResult(
      Class<? extends WizardPageActivity<WizardState>> activityClass, int requestCode) {
    Intent intent = new Intent(this, activityClass);
    intent.putExtra(KEY_WIZARD_STATE, getWizardState());
    startActivityForResult(intent, requestCode);
  }

  /**
   * Extracts the state of the wizard from the provided Intent.
   *
   * @return state or {@code null} if not found.
   */
  @SuppressWarnings("unchecked")
  protected WizardState getWizardStateFromIntent(Intent intent) {
    return (intent != null)
        ? (WizardState) intent.getSerializableExtra(KEY_WIZARD_STATE)
        : null;
  }

  /**
   * Sets the contents of the {@code TextView} to the HTML contained in the string resource.
   */
  protected void setTextViewHtmlFromResource(int viewId, int resId) {
    setTextViewHtmlFromResource((TextView) findViewById(viewId), resId);
  }

  /**
   * Sets the contents of the {@code TextView} to the HTML contained in the string resource.
   */
  protected void setTextViewHtmlFromResource(TextView view, int resId) {
    view.setText(Html.fromHtml(getString(resId)));
  }

  /**
   * Sets the contents of the {@code TextView} to the provided HTML.
   */
  protected void setTextViewHtml(int viewId, String html) {
    setTextViewHtml((TextView) findViewById(viewId), html);
  }

  /**
   * Sets the contents of the {@code TextView} to the provided HTML.
   */
  protected void setTextViewHtml(TextView view, String html) {
    view.setText(Html.fromHtml(html));
  }

  /**
   * Sets the type of the button bar displayed at the bottom of the page.
   */
  private void setButtonBarType(ButtonBarType type) {
    mButtonBarType = type;
    switch (type) {
      case LEFT_RIGHT_BUTTONS:
        mLeftRightButtonBar.setVisibility(View.VISIBLE);
        mMiddleButtonOnlyBar.setVisibility(View.GONE);
        mCancelButtonOnlyBar.setVisibility(View.GONE);
        break;
      case MIDDLE_BUTTON_ONLY:
        mMiddleButtonOnlyBar.setVisibility(View.VISIBLE);
        mLeftRightButtonBar.setVisibility(View.GONE);
        mCancelButtonOnlyBar.setVisibility(View.GONE);
        break;
      case CANCEL_BUTTON_ONLY:
        mCancelButtonOnlyBar.setVisibility(View.VISIBLE);
        mLeftRightButtonBar.setVisibility(View.GONE);
        mMiddleButtonOnlyBar.setVisibility(View.GONE);
        break;
      default:
        throw new IllegalArgumentException(String.valueOf(type));
    }
  }

  /**
   * Changes this page to show an indefinite progress bar with a Cancel button.
   *
   * @param cancelListener listener invoked when the Cancel button is pressed.
   *
   * @see #dismissInlineProgress()
   */
  protected void showInlineProgress(View.OnClickListener cancelListener) {
    mCancelButton.setOnClickListener(cancelListener);
    mCancelButton.setEnabled(true);

    mInlineProgressView.setVisibility(View.VISIBLE);

    if (mButtonBarType != ButtonBarType.CANCEL_BUTTON_ONLY) {
      mButtonBarTypeBeforeInlineProgressDisplayed = mButtonBarType;
    }
    setButtonBarType(ButtonBarType.CANCEL_BUTTON_ONLY);
  }

  /**
   * Changes this page to hide the indefinite progress bar with a Cancel button. The page reverts
   * to the usual layout with the left and right buttons.
   *
   * @see #showInlineProgress(android.view.View.OnClickListener)
   */
  protected void dismissInlineProgress() {
    mCancelButton.setOnClickListener(null);
    setButtonBarType(mButtonBarTypeBeforeInlineProgressDisplayed);
  }

  protected void exitWizard() {
    Intent intent = new Intent(this, AuthenticatorActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);

    finish();
  }

  /**
   * Checks the validity of the current state of the wizard.
   *
   * @throws IllegalStateException if the state is invalid.
   */
  protected void checkWizardStateValidity(@SuppressWarnings("unused") WizardState wizardState) {}
}
