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

import android.content.Intent;

/**
 * Page of the "How it works" intro flow.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class IntroPageActivity extends WizardPageActivity {

  @Override
  protected WizardState getWizardStateFromIntent(Intent intent) {
    // Create a new (empty) state regardless of what's in the Intent, because this Activity
    // is the start of the intro flow
    return new WizardState();
  }
}
