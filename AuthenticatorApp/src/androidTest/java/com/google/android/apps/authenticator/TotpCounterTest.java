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

package com.google.android.apps.authenticator;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TotpCounter}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class TotpCounterTest extends TestCase {

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

  public void testConstruct_withNegativeStartTime() {
    try {
      new TotpCounter(1, -3);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testConstruct_withDurationAndStartTime() {
    TotpCounter counter = new TotpCounter(3, 7);
    assertEquals(3, counter.getTimeStep());
    assertEquals(7, counter.getStartTime());
  }

  public void testConstruct_withDefaultStartTime() {
    TotpCounter counter = new TotpCounter(11);
    assertEquals(11, counter.getTimeStep());
    assertEquals(0, counter.getStartTime());
  }

  public void testGetValueAtTime_withNegativeTime() {
    TotpCounter counter = new TotpCounter(3);
    try {
      counter.getValueAtTime(-7);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testGetValueAtTime_withTimeBeforeStartTime() {
    TotpCounter counter = new TotpCounter(3, 11);
    assertEquals(-1, counter.getValueAtTime(10));
  }

  public void testGetValueAtTime() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertEquals(-18, counter.getValueAtTime(0));
    assertEquals(-2, counter.getValueAtTime(115));
    assertEquals(-1, counter.getValueAtTime(116));
    assertEquals(-1, counter.getValueAtTime(117));
    assertEquals(-1, counter.getValueAtTime(122));
    assertEquals(0, counter.getValueAtTime(123));
    assertEquals(0, counter.getValueAtTime(124));
    assertEquals(0, counter.getValueAtTime(129));
    assertEquals(1, counter.getValueAtTime(130));
    assertEquals(1, counter.getValueAtTime(131));
    assertEquals(100, counter.getValueAtTime(823));
    assertEquals(10000000000L, counter.getValueAtTime(70000000123L));
  }

  public void testGetValueStartTime() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertEquals(-577, counter.getValueStartTime(-100));
    assertEquals(116, counter.getValueStartTime(-1));
    assertEquals(123, counter.getValueStartTime(0));
    assertEquals(130, counter.getValueStartTime(1));
    assertEquals(137, counter.getValueStartTime(2));
    assertEquals(823, counter.getValueStartTime(100));
    assertEquals(70000000123L, counter.getValueStartTime(10000000000L));
  }

  public void testValueIncreasesByOneEveryTimeStep() {
    TotpCounter counter = new TotpCounter(7, 123);
    assertValueIncreasesByOneEveryTimeStep(counter, 11, 500);
    assertValueIncreasesByOneEveryTimeStep(counter, Long.MAX_VALUE - 1234567, 500);
  }

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
