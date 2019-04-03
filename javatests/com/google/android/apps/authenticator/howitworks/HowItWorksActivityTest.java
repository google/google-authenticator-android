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

package com.google.android.apps.authenticator.howitworks;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtras;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.enroll2sv.wizard.AddAccountActivity;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.testing.TestUtilities;
import com.google.android.apps.authenticator2.R;
import java.util.concurrent.Callable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link HowItWorksActivity}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class HowItWorksActivityTest {

  private Instrumentation instr;

  @Rule public ActivityTestRule<HowItWorksActivity> activityTestRule =
      new ActivityTestRule<>(
          HowItWorksActivity.class, /* initialTouchMode= */ false, /* launchActivity= */ false);
  @Before
  public void setUp() throws Exception {
    Intents.init();
    instr = InstrumentationRegistry.getInstrumentation();
    DependencyInjector.resetForIntegrationTesting(instr.getTargetContext());
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
    Intents.release();
  }

  @Test
  public void testFieldsAreOnScreenOnFirstStart() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);

    onView(withId(R.id.paging_indicator)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_next)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_skip)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_done)).check(matches(not(isDisplayed())));
    onView(withId(R.id.howitworks_pager)).check(matches(isDisplayed()));
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);
  }

  @Test
  public void testFieldsAreOnScreenOnFirstStartWhenFirstOnboarding() {
    HowItWorksActivity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));

    onView(withId(R.id.paging_indicator)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_next)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_skip)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_done)).check(matches(not(isDisplayed())));
    onView(withId(R.id.howitworks_pager)).check(matches(isDisplayed()));
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);
  }

  @Test
  public void testExactlyThreeLayoutsForViewPager() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    assertThat(activity.getViewPagerTotalItem()).isEqualTo(3);
  }

  @Test
  public void testSkipButtonFinishActivity() {
    Activity activity = activityTestRule.launchActivity(null);
    onView(withId(R.id.howitworks_button_skip)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
  }

  @Test
  public void testSkipButtonFinishActivityAndOpenAddAccountActivityWhenFirstOnboarding() {
    Activity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));
    onView(withId(R.id.howitworks_button_skip)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
    Intent expectedIntent = new Intent(activity, AddAccountActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(equalTo(expectedIntent.getExtras()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testSkipButtonUpdateOnboardingCompletedPreference() {
    setOnboardingCompletedPreference(instr.getTargetContext(), false);
    assertThat(
            PreferenceManager.getDefaultSharedPreferences(instr.getTargetContext())
                .getBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, false))
        .isFalse();
    Activity activity = activityTestRule.launchActivity(null);
    onView(withId(R.id.howitworks_button_skip)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
    assertThat(
            PreferenceManager.getDefaultSharedPreferences(instr.getTargetContext())
                .getBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, false))
        .isTrue();
  }

  @Test
  public void testNextButtonOnFirstLayoutMoveToNextLayout() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    selectViewPagerItem(instr, activity, 0);
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();
  }

  @Test
  public void testNextButtonOnSecondLayoutMoveToNextLayout() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    selectViewPagerItem(instr, activity, 1);
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();
  }

  @Test
  public void testDoneButtonOnThirdLayoutFinishActivity() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    selectViewPagerItem(instr, activity, 2);
    onView(withId(R.id.howitworks_button_done)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
  }

  @Test
  public void testDoneButtonOnThirdLayoutUpdateOnboardingCompletedPreference() {
    setOnboardingCompletedPreference(instr.getTargetContext(), false);
    assertThat(
            PreferenceManager.getDefaultSharedPreferences(instr.getTargetContext())
                .getBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, false))
        .isFalse();
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    selectViewPagerItem(instr, activity, 2);
    onView(withId(R.id.howitworks_button_done)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
    assertThat(
            PreferenceManager.getDefaultSharedPreferences(instr.getTargetContext())
                .getBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, false))
        .isTrue();
  }

  @Test
  public void testNextButtonOnFirstLayoutMoveToNextLayoutWhenFirstOnboarding() {
    HowItWorksActivity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));
    selectViewPagerItem(instr, activity, 0);
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();
  }

  @Test
  public void testNextButtonOnSecondLayoutMoveToNextLayoutWhenFirstOnboarding() {
    HowItWorksActivity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));
    selectViewPagerItem(instr, activity, 1);
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();
  }

  @Test
  public void testDoneButtonOnThirdLayoutFinishActivityOpenAddAccountActivityWhenFirstOnboarding() {
    HowItWorksActivity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));
    selectViewPagerItem(instr, activity, 2);
    onView(withId(R.id.howitworks_button_done)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
    Intent expectedIntent = new Intent(activity, AddAccountActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(equalTo(expectedIntent.getExtras()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  @Test
  public void testNavigationButtonVisibility() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    selectViewPagerItem(instr, activity, 0);
    onView(withId(R.id.howitworks_button_skip)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_next)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_done)).check(matches(not(isDisplayed())));

    selectViewPagerItem(instr, activity, 1);
    onView(withId(R.id.howitworks_button_skip)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_next)).check(matches(isDisplayed()));
    onView(withId(R.id.howitworks_button_done)).check(matches(not(isDisplayed())));

    selectViewPagerItem(instr, activity, 2);
    onView(withId(R.id.howitworks_button_skip)).check(matches(not(isDisplayed())));
    onView(withId(R.id.howitworks_button_next)).check(matches(not(isDisplayed())));
    onView(withId(R.id.howitworks_button_done)).check(matches(isDisplayed()));
  }

  @Test
  public void testSwipeActionOnViewPager() {
    HowItWorksActivity activity = activityTestRule.launchActivity(null);
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);

    // Swipe a straight horizontal line at the middle of the screen height from right to left.
    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();

    // Swipe from right to left again.
    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();

    // We are on the last layout, swiping from right to left again doesn't work.
    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();

    // Now try swipe from left to right.
    onView(withId(R.id.howitworks_pager)).perform(swipeRight());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();

    // Swipe from left to right again.
    onView(withId(R.id.howitworks_pager)).perform(swipeRight());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);
    assertThat(activity.isFinishing()).isFalse();

    // We are on the first layout, swiping from left to right again doesn't work.
    onView(withId(R.id.howitworks_pager)).perform(swipeRight());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);
    assertThat(activity.isFinishing()).isFalse();

    // Now click the "next" button once an then swipe from right to left.
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();

    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();

    // We are on the last layout, click "Done" button should finish the activity.
    onView(withId(R.id.howitworks_button_done)).perform(click());
    assertThat(activity.isFinishing()).isTrue();
  }

  @Test
  public void testSwipeActionOnViewPagerWhenFirstOnboarding() {
    HowItWorksActivity activity =
        activityTestRule.launchActivity(
            new Intent().putExtra(HowItWorksActivity.KEY_FIRST_ONBOARDING_EXPERIENCE, true));
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(0);

    // Swipe a straight horizontal line at the middle of the screen height from right to left.
    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(1);
    assertThat(activity.isFinishing()).isFalse();

    // Click the "next" button.
    onView(withId(R.id.howitworks_button_next)).perform(click());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();

    // We are on the last layout, swiping from right to left again doesn't work.
    onView(withId(R.id.howitworks_pager)).perform(swipeLeft());
    assertThat(activity.getViewPagerCurrentItem()).isEqualTo(2);
    assertThat(activity.isFinishing()).isFalse();

    // We are on the last layout, click "Done" button should finish the activity.
    onView(withId(R.id.howitworks_button_done)).perform(click());
    assertThat(activity.isFinishing()).isTrue();

    // Because this is the first onboarding, AddAccountActivity should start now
    Intent expectedIntent = new Intent(activity, AddAccountActivity.class);
    intended(
        allOf(
            hasComponent(equalTo(expectedIntent.getComponent())),
            hasExtras(equalTo(expectedIntent.getExtras()))));
    assertThat(TestUtilities.isStrayIntentRemaining()).isFalse();
  }

  /**
   * Set the current item of the view pager to the requested position.
   *
   * @return the current item index after the action.
   */
  private static int selectViewPagerItem(
      Instrumentation instr, final HowItWorksActivity activity, final int position) {
    TestUtilities.runOnMainSyncWithTimeout(
        new Callable<Void>() {
          @Override
          public Void call() {
            activity.setViewPagerCurrentItem(position);
            return null;
          }
        });
    instr.waitForIdleSync();
    return activity.getViewPagerCurrentItem();
  }

  /**
   * Set the preference for key KEY_ONBOARDING_COMPLETED of {@link AuthenticatorActivity}
   *
   * @param context the target context for the current activity
   * @param value when the value is false, this function just simply remove the key
   */
  private static void setOnboardingCompletedPreference(Context context, boolean value) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (value) {
      preferences.edit().putBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, true).commit();
    } else {
      preferences.edit().remove(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED).commit();
    }
  }
}
