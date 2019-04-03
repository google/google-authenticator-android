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

package com.google.android.apps.authenticator.enroll2sv.wizard;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressMenuKey;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtras;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.Window;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.howitworks.HowItWorksActivity;
import com.google.android.apps.authenticator.otp.EnterKeyActivity;
import com.google.android.apps.authenticator.settings.SettingsActivity;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator2.R;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AddAccountActivity}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AddAccountActivityTest {

  @Rule public ActivityTestRule<AddAccountActivity> activityTestRule =
      new ActivityTestRule<>(
          AddAccountActivity.class, /* initialTouchMode= */ false, /* launchActivity= */ false);

  @Before
  public void setUp() throws Exception {
    Intents.init();
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
    Intents.release();
  }

  @Test
  public void testFieldsAreOnScreen() {
    activityTestRule.launchActivity(null);
    onView(withId(R.id.enroll2sv_choose_account_page_scan_barcode_layout))
        .check(matches(isDisplayed()));
    onView(withId(R.id.enroll2sv_choose_account_page_enter_key_layout))
        .check(matches(isDisplayed()));
  }

  @Test
  public void testScanBarcodeItemTap() {
    activityTestRule.launchActivity(null);
    onView(withId(R.id.enroll2sv_choose_account_page_scan_barcode_layout)).perform(click());
    Intent expectedIntent = AuthenticatorActivity
        .getLaunchIntentActionScanBarcode(activityTestRule.getActivity(), true);
    intended(allOf(
        hasAction(AuthenticatorActivity.ACTION_SCAN_BARCODE),
        hasComponent(equalTo(expectedIntent.getComponent()))
    ));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testEnterKeyItemTap() {
    activityTestRule.launchActivity(null);
    onView(withId(R.id.enroll2sv_choose_account_page_enter_key_layout)).perform(click());
    Intent expectedIntent = new Intent(activityTestRule.getActivity(), EnterKeyActivity.class);
    intended(allOf(
        hasComponent(equalTo(expectedIntent.getComponent())),
        hasExtras(equalTo(expectedIntent.getExtras()))
    ));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testOptionsMenuHowItWorks() {
    checkOptionsMenuItemWithComponent(R.string.how_it_works_menu_item, HowItWorksActivity.class);
  }

  @Test
  public void testOptionsMenuSettings() {
    checkOptionsMenuItemWithComponent(R.string.settings_menu_item, SettingsActivity.class);
  }

  private void checkOptionsMenuItemWithComponent(int menuTextId, Class<?> cls) {
    Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    activityTestRule.launchActivity(null);
    onView(isRoot()).perform(pressMenuKey());
    onView(withText(targetContext.getString(menuTextId))).perform(click());
    Matcher<Intent> intentMatcher = hasComponent(equalTo(new ComponentName(targetContext, cls)));
    intended(intentMatcher);
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }
}
