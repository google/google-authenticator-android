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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.enroll2sv.wizard.AddAccountActivity;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator2.R;
import com.google.common.annotations.VisibleForTesting;

/**
 * The activity for the "How it works" which give user the first onboarding experience to explain
 * why this app is important, how to use this app, and how to get started The user can click the
 * next button or swipe to go to the next page
 */
public class HowItWorksActivity extends TestableActivity {

  public static final String KEY_FIRST_ONBOARDING_EXPERIENCE = "firstOnboardingExperience";

  /** Number of layout in the "How it works" pager */
  private static final int HOWITWORKS_PAGER_LAYOUT_COUNT = 3;

  /** List of item layout for the "How it works" pager */
  private static final int[] HOWITWORKS_PAGER_LAYOUT_ID_LIST = {
    R.layout.howitworks_layout_1, R.layout.howitworks_layout_2, R.layout.howitworks_layout_3
  };

  private ViewPager pager;
  private ImageButton buttonNext;
  private Button buttonSkip;
  private Button buttonDone;

  /** Whether we are in a very first onboarding experience or not. */
  private boolean onFirstOnboardingExperience;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // On non-tablet devices, we lock the screen to portrait mode.
    boolean isTablet = getResources().getBoolean(R.bool.isTablet);
    if (isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    } else {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
    }

    setContentView(R.layout.howitworks);

    if (savedInstanceState != null) {
      onFirstOnboardingExperience = savedInstanceState.getBoolean(KEY_FIRST_ONBOARDING_EXPERIENCE);
    } else {
      Intent intent = getIntent();
      onFirstOnboardingExperience =
          intent != null && intent.getBooleanExtra(KEY_FIRST_ONBOARDING_EXPERIENCE, false);
    }

    pager = (ViewPager) findViewById(R.id.howitworks_pager);
    pager.setAdapter(new HowItWorksPagerAdapter(getSupportFragmentManager()));
    pager.addOnPageChangeListener(onPageChangeListener);
    PagingIndicator pagingIndicator = (PagingIndicator) findViewById(R.id.paging_indicator);
    pagingIndicator.setViewPager(pager);

    buttonSkip = (Button) findViewById(R.id.howitworks_button_skip);
    buttonSkip.setOnClickListener(view -> finishHowItWorksActivitiy());
    buttonSkip.setVisibility(View.VISIBLE);

    buttonNext = (ImageButton) findViewById(R.id.howitworks_button_next);
    buttonNext.setOnClickListener(
        v -> {
          int currentItem = getViewPagerCurrentItem();
          pager.setCurrentItem(currentItem + 1, true);
        });
    buttonNext.setVisibility(View.VISIBLE);

    buttonDone = (Button) findViewById(R.id.howitworks_button_done);
    buttonDone.setOnClickListener(view -> finishHowItWorksActivitiy());
    buttonDone.setVisibility(View.GONE);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(KEY_FIRST_ONBOARDING_EXPERIENCE, onFirstOnboardingExperience);
  }

  /**
   * Finish current {@link HowItWorksActivity}. If we are on the very first onboarding experience,
   * proceed to the {@link AddAccountActivity}.
   */
  private void finishHowItWorksActivitiy() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    preferences.edit().putBoolean(AuthenticatorActivity.KEY_ONBOARDING_COMPLETED, true).commit();
    if (onFirstOnboardingExperience) {
      startActivity(new Intent(HowItWorksActivity.this, AddAccountActivity.class));
    }
    finish();
  }

  private final OnPageChangeListener onPageChangeListener =
      new SimpleOnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
          // If user is not in the last page, we show the "Skip" button and the "Next" button.
          // On the last page we hide the "Skip" and "Next" button, only show the "Done" button.
          if (position < getViewPagerTotalItem() - 1) {
            buttonSkip.setVisibility(View.VISIBLE);
            buttonNext.setVisibility(View.VISIBLE);
            buttonDone.setVisibility(View.GONE);
          } else {
            buttonSkip.setVisibility(View.GONE);
            buttonNext.setVisibility(View.GONE);
            buttonDone.setVisibility(View.VISIBLE);
          }
        }
      };

  /** This pager contains the pages for the layouts being showed on the "How it works" view. */
  public static class HowItWorksPagerAdapter extends FragmentPagerAdapter {

    public HowItWorksPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      Fragment fragment = new HowItWorksFragment();
      Bundle args = new Bundle();
      args.putInt(HowItWorksFragment.ARG_LAYOUT_ID, HOWITWORKS_PAGER_LAYOUT_ID_LIST[position]);
      fragment.setArguments(args);
      return fragment;
    }

    @Override
    public int getCount() {
      return HOWITWORKS_PAGER_LAYOUT_COUNT;
    }
  }

  /** This fragment show the layout specified with ID in the {@code ARG_LAYOUT_ID} argument. */
  public static class HowItWorksFragment extends Fragment {

    public static final String ARG_LAYOUT_ID = "layoutId";

    @Nullable
    @Override
    public View onCreateView(
        LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
      Bundle args = getArguments();
      int contentLayoutId = args.getInt(ARG_LAYOUT_ID);
      return inflater.inflate(contentLayoutId, container, false);
    }
  }

  /**
   * Get the current item of the view pager.
   *
   * @return current item of the view pager.
   */
  @VisibleForTesting
  int getViewPagerCurrentItem() {
    return pager.getCurrentItem();
  }

  /**
   * Set the current item of the view pager.
   *
   * @param position the position of the item we want to set for the view pager.
   * @return the current iteam of the view pager after setting action.
   */
  @VisibleForTesting
  int setViewPagerCurrentItem(int position) {
    pager.setCurrentItem(position);
    return pager.getCurrentItem();
  }

  /**
   * Get the number of layout item of the view pager.
   *
   * @return the number of layout item being showed on the view pager.
   */
  @VisibleForTesting
  int getViewPagerTotalItem() {
    return pager.getAdapter().getCount();
  }
}
