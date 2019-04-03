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
import com.google.android.apps.authenticator2.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SettingsAboutActivity}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SettingsAboutActivityTest {

  @Rule public ActivityTestRule<SettingsAboutActivity> activityTestRule =
      new ActivityTestRule<>(
          SettingsAboutActivity.class, /* initialTouchMode= */ true, /* launchActivity= */ false);

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
  public void testVersionTakenFromPackageVersion() throws Exception {
    SettingsAboutActivity activity = activityTestRule.launchActivity(null);
    Preference preference = activity.findPreference("version");
    String expectedVersion =
        InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext()
            .getPackageManager()
            .getPackageInfo(activity.getPackageName(), 0)
            .versionName;
    assertThat(preference.getSummary().toString()).isEqualTo(expectedVersion);
  }

  @Test
  public void testTermsOfServicePreferenceOpensUrl() throws Exception {
    Intent intent = tapOnPreferenceAndCatchFiredIntent("terms");
    assertDefaultViewActionIntent(
        InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext()
            .getString(R.string.terms_page_url),
        intent);
  }

  @Test
  public void testPrivacyPolicyPreferenceOpensUrl() throws Exception {
    Intent intent = tapOnPreferenceAndCatchFiredIntent("privacy");
    assertDefaultViewActionIntent(
        InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext()
            .getString(R.string.privacy_page_url),
        intent);
  }

  @Test
  public void testOpenSourceNoticesPreference() throws Exception {
    Intent intent = tapOnPreferenceAndCatchFiredIntent("notices");
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(intent);

    intended(
        hasAction(
            equalTo("com.google.android.apps.authenticator.settings.OPEN_SOURCE_NOTICES")));
  }

  private static void assertDefaultViewActionIntent(String expectedData, Intent intent) {
    assertThat(intent.getAction()).isEqualTo("android.intent.action.VIEW");
    assertThat(intent.getDataString()).isEqualTo(expectedData);
  }

  @FixWhenMinSdkVersion(11) @SuppressWarnings("deprecation")
  private Intent tapOnPreferenceAndCatchFiredIntent(String preferenceKey) {
    SettingsAboutActivity activity = activityTestRule.launchActivity(null);
    TestUtilities.tapPreference(activity, activity.findPreference(preferenceKey));
    return TestUtilities.verifyWithTimeoutThatStartActivityAttemptedExactlyOnce();
  }
}
