/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator.testability;

import android.content.ComponentName;
import android.content.Intent;
import com.google.android.apps.authenticator.settings.AppCompatPreferenceActivity;

/** Base class for {@link AppCompatPreferenceActivity} instances to make them more testable. */
public class TestablePreferenceActivity extends AppCompatPreferenceActivity {

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

  @Override
  public ComponentName startService(Intent intent) {
    StartServiceListener listener = DependencyInjector.getStartServiceListener();
    if ((listener != null) && (listener.onStartServiceInvoked(this, intent))) {
      return null;
    }

    return super.startService(intent);
  }

  @Override
  protected boolean isValidFragment(String fragmentName) {
    // PreferenceActivity is vulnerable to Fragment Injection prior to KitKat
    // (http://goo.gl/jz36Ip)
    // Override this to always return false as we do not use Fragment for Preference.
    return false;
  }
}
