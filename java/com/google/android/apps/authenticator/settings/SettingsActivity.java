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

package com.google.android.apps.authenticator.settings;

import android.os.Bundle;
import com.google.android.apps.authenticator.testability.TestablePreferenceActivity;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import com.google.android.apps.authenticator2.R;

/** Top-level preferences Activity. */
public class SettingsActivity extends TestablePreferenceActivity {

  @FixWhenMinSdkVersion(11) @SuppressWarnings("deprecation")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.preferences);
  }
}
