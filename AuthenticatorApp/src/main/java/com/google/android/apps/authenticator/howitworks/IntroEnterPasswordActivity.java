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
 * The start page of the "How it works" flow that explains that in addition to entering the password
 * during sign in a verification code may be required. The user simply needs to click the Next
 * button to go to the next page.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class IntroEnterPasswordActivity extends WizardPageActivity<Serializable> {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setPageContentView(R.layout.howitworks_enter_password);
    setTextViewHtmlFromResource(R.id.details, R.string.howitworks_page_enter_password_details);
  }

  @Override
  protected void onRightButtonPressed() {
    startPageActivity(IntroEnterCodeActivity.class);
  }
}
