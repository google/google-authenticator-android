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

package com.google.android.apps.authenticator.howitworks;

import com.google.android.apps.authenticator.wizard.WizardPageActivity;
import com.google.android.apps.authenticator2.R;

import android.os.Bundle;

import java.io.Serializable;

/**
 * The page of the "How it works" flow that explains that the user can ask Google not to
 * ask for verification codes every time the user signs in from a verified computer or device.
 * The user needs to click the Exit button to exit the flow.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class IntroVerifyDeviceActivity extends WizardPageActivity<Serializable> {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setPageContentView(R.layout.howitworks_verify_device);
    setTextViewHtmlFromResource(R.id.details, R.string.howitworks_page_verify_device_details);

    mRightButton.setText(R.string.button_exit_howitworks_flow);
  }

  @Override
  protected void onRightButtonPressed() {
    exitWizard();
  }
}
