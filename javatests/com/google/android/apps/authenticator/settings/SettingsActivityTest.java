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

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

import android.app.Activity;
import android.content.Intent;
import android.preference.Preference;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator.util.annotations.FixWhenMinSdkVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SettingsActivity}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SettingsActivityTest {

  @Rule public ActivityTestRule<SettingsActivity> activityTestRule =
      new ActivityTestRule<>(
          SettingsActivity.class, /* initialTouchMode= */ true, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    TestUtilities.withLaunchPreventingStartActivityListenerInDependencyResolver();
    Intents.init();
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
    Intents.release();
  }

  @Test
  @FixWhenMinSdkVersion(11) @SuppressWarnings("deprecation")
  public void testAboutPreference() throws Exception {
    activityTestRule.launchActivity(null);
    Preference preference = activityTestRule.getActivity().findPreference("about");
    TestUtilities.tapPreference(activityTestRule.getActivity(), preference);
    Intent launchIntent = TestUtilities.verifyWithTimeoutThatStartActivityAttemptedExactlyOnce();
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(launchIntent);

    intended(hasAction(equalTo("com.google.android.apps.authenticator.settings.ABOUT")));
  }

  @Test
  @FixWhenMinSdkVersion(11)
  @SuppressWarnings("deprecation")
  public void testTimeSyncPreference() {
    activityTestRule.launchActivity(null);
    Preference preference = activityTestRule.getActivity().findPreference("time_sync");
    TestUtilities.tapPreference(activityTestRule.getActivity(), preference);
    Intent launchIntent = TestUtilities.verifyWithTimeoutThatStartActivityAttemptedExactlyOnce();
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(launchIntent);

    intended(
        hasAction(
            equalTo("com.google.android.apps.authenticator.timesync.TIME_CORRECTION_SETTINGS")));
  }
}
