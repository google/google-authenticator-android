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
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TotpCounter}. */
@RunWith(JUnit4.class)
public class TotpCounterTest {

  @Test
  public void testConstruct_withInvalidDuration() {
    try {
      new TotpCounter(0);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      new TotpCounter(-3);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testConstruct_withNegativeStartTime() {
    try {
      new TotpCounter(1, -3);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testConstruct_withDurationAndStartTime() {
    TotpCounter counter = new TotpCounter(3, 7);
    assertThat(counter.getTimeStep()).isEqualTo(3);
    assertThat(counter.getStartTime()).isEqualTo(7);
  }

  @Test
  public void testConstruct_withDefaultStartTime() {
    TotpCounter counter = new TotpCounter(11);
    assertThat(counter.getTimeStep()).isEqualTo(11);
    assertThat(counter.getStartTime()).isEqualTo(0);
  }

  @Test
  public void testGetValueAtTime_withNegativeTime() {
    TotpCounter counter = new TotpCounter(3);
    try {
      counter.getValueAtTime(-7);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  @Test
  public void testGetValueAtTime_withTimeBeforeStartTime() {
    TotpCounter counter = new TotpCounter(3, 11);
    assertThat(counter.getValueAtTime(10)).isEqualTo(-1);
  }

  @Test
  public void testGetValueAtTime() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertThat(counter.getValueAtTime(0)).isEqualTo(-18);
    assertThat(counter.getValueAtTime(115)).isEqualTo(-2);
    assertThat(counter.getValueAtTime(116)).isEqualTo(-1);
    assertThat(counter.getValueAtTime(117)).isEqualTo(-1);
    assertThat(counter.getValueAtTime(122)).isEqualTo(-1);
    assertThat(counter.getValueAtTime(123)).isEqualTo(0);
    assertThat(counter.getValueAtTime(124)).isEqualTo(0);
    assertThat(counter.getValueAtTime(129)).isEqualTo(0);
    assertThat(counter.getValueAtTime(130)).isEqualTo(1);
    assertThat(counter.getValueAtTime(131)).isEqualTo(1);
    assertThat(counter.getValueAtTime(823)).isEqualTo(100);
    assertThat(counter.getValueAtTime(70000000123L)).isEqualTo(10000000000L);
  }

  @Test
  public void testGetValueStartTime() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertThat(counter.getValueStartTime(-100)).isEqualTo(-577);
    assertThat(counter.getValueStartTime(-1)).isEqualTo(116);
    assertThat(counter.getValueStartTime(0)).isEqualTo(123);
    assertThat(counter.getValueStartTime(1)).isEqualTo(130);
    assertThat(counter.getValueStartTime(2)).isEqualTo(137);
    assertThat(counter.getValueStartTime(100)).isEqualTo(823);
    assertThat(counter.getValueStartTime(10000000000L)).isEqualTo(70000000123L);
  }

  @Test
  public void testValueIncreasesByOneEveryTimeStep() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertValueIncreasesByOneEveryTimeStep(counter, 11, 500);
    assertValueIncreasesByOneEveryTimeStep(counter, Long.MAX_VALUE - 1234567, 500);
  }

  @Test
  public void testValueStartTimeInRangeOverTime() {
    TotpCounter counter = new TotpCounter(11, 123);
    assertValueStartTimeInRangeOverTime(counter, 0, 500);
    assertValueStartTimeInRangeOverTime(counter, Long.MAX_VALUE - 1234567, 500);
  }

  private static void assertValueIncreasesByOneEveryTimeStep(
      TotpCounter counter, long startTime, long duration) {

    long previousValue = counter.getValueAtTime(startTime);
    long previousValueStartTime = counter.getValueStartTime(previousValue);

    // Adjust the start time so that it starts when the counter first assumes the current value
    long startTimeAdjustment = startTime - previousValueStartTime;
    startTime -= startTimeAdjustment;
    duration += startTimeAdjustment;

    for (long time = startTime, endTime = startTime + duration; time <= endTime; time++) {
      long value = counter.getValueAtTime(time);
      if (value != previousValue) {
        if (value == previousValue + 1) {
          long timeSincePreviousValueStart = time - previousValueStartTime;
          if (timeSincePreviousValueStart != counter.getTimeStep()) {
            fail("Value incremented by 1 at the wrong time: " + time);
          }
          previousValue = value;
          previousValueStartTime = time;
        } else {
          fail("Value changed by an unexpected amount " + (value - previousValue)
              + " at time " + time);
        }
      } else if ((time - previousValueStartTime) == counter.getTimeStep()) {
        fail("Counter value did not change at time " + time);
      }
    }
  }

  /**
   * Asserts that during the specified time interval the start time of each value from that
   * interval is not in the future and also is no older than {@code timeStep - 1}.
   */
  private static void assertValueStartTimeInRangeOverTime(
      TotpCounter counter, long startTime, long duration) {
    for (long time = startTime, endTime = startTime + duration; time <= endTime; time++) {
      long value = counter.getValueAtTime(time);
      long valueStartTime = counter.getValueStartTime(value);
      if ((valueStartTime > time) || (valueStartTime <= time - counter.getTimeStep())) {
        fail("Start time of value " + value + " out of range: time: " + time
            + ", valueStartTime: " + startTime
            + ", timeStep: " + counter.getTimeStep());
      }
    }
  }
}
