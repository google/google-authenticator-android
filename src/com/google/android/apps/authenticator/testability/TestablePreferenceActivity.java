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

package com.google.android.apps.authenticator.testability;

import android.content.Intent;
import android.preference.PreferenceActivity;

/**
 * Base class for {@link PreferenceActivity} instances to make them more testable.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class TestablePreferenceActivity extends PreferenceActivity {

  @Override
  public void startActivity(Intent intent) {
    StartActivityListener listener = DependencyInjector.getStartActivityListener();
    if ((listener != null) && (listener.onStartActivityInvoked(this, intent))) {
      return;
    }

    super.startActivity(intent);
  }

  @Override
  public void startActivityForResult(Intent intent, int requestCode) {
    StartActivityListener listener = DependencyInjector.getStartActivityListener();
    if ((listener != null) && (listener.onStartActivityInvoked(this, intent))) {
      return;
    }

    super.startActivityForResult(intent, requestCode);
  }
}
