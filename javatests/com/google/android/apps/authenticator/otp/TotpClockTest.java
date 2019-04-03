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

package com.google.android.apps.authenticator.otp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.apps.authenticator.testability.DependencyInjector;
import com.google.android.apps.authenticator.time.Clock;
import com.google.android.apps.authenticator.util.Utilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TotpClock}. */
@RunWith(JUnit4.class)
public class TotpClockTest {

  @Mock private Clock mMockSystemWallClock;
  private TotpClock mClock;

  @Before
  public void setUp() throws Exception {
    DependencyInjector.resetForIntegrationTesting(
        InstrumentationRegistry.getInstrumentation().getTargetContext());
    MockitoAnnotations.initMocks(this);
    mClock = new TotpClock(DependencyInjector.getContext(), mMockSystemWallClock);
  }

  @After
  public void tearDown() throws Exception {
    DependencyInjector.close();
  }

  @Test
  public void testCurrentTimeMillisUsesCurrentTimeAndTimeCorrection() {
    long systemTimeMillis = 77161712121L;
    withSystemWallClockNowMillis(systemTimeMillis);

    assertThat(mClock.getTimeCorrectionMinutes()).isEqualTo(0);
    assertThat(mClock.nowMillis()).isEqualTo(systemTimeMillis);

    mClock.setTimeCorrectionMinutes(137);
    assertThat(mClock.nowMillis()).isEqualTo(systemTimeMillis + 137 * Utilities.MINUTE_IN_MILLIS);
  }

  @Test
  public void testTimeCorrectionBackedByPreferences() {
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(DependencyInjector.getContext());
    assertThat(preferences.edit().putInt(TotpClock.PREFERENCE_KEY_OFFSET_MINUTES, 7).commit())
        .isTrue();
    assertThat(mClock.getTimeCorrectionMinutes()).isEqualTo(7);
    mClock.setTimeCorrectionMinutes(42);
    assertThat(preferences.getInt(TotpClock.PREFERENCE_KEY_OFFSET_MINUTES, 0)).isEqualTo(42);
    assertThat(mClock.getTimeCorrectionMinutes()).isEqualTo(42);
  }

  @Test
  public void testTimeCorrectionCaching() {
    // Check that the preference is only read first time the the time correction value is requested
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(DependencyInjector.getContext());
    assertThat(preferences.edit().putInt(TotpClock.PREFERENCE_KEY_OFFSET_MINUTES, 7).commit())
        .isTrue();
    assertThat(mClock.getTimeCorrectionMinutes()).isEqualTo(7);
    assertThat(preferences.edit().putInt(TotpClock.PREFERENCE_KEY_OFFSET_MINUTES, 42).commit())
        .isTrue();
    assertThat(mClock.getTimeCorrectionMinutes()).isEqualTo(7);
  }

  @Test
  public void testGetSystemWallClock() {
    assertThat(mClock.getSystemWallClock()).isSameAs(mMockSystemWallClock);
  }

  private void withSystemWallClockNowMillis(long timeMillis) {
    doReturn(timeMillis).when(mMockSystemWallClock).nowMillis();
  }
}
