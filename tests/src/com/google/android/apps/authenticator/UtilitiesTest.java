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
 * Unit tests for {@link Utilities}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class UtilitiesTest extends TestCase {

  public void testMillisToSeconds() {
    // Test rounding
    assertEquals(1234, Utilities.millisToSeconds(1234567));
    assertEquals(1234, Utilities.millisToSeconds(1234000));
    assertEquals(1234, Utilities.millisToSeconds(1234999));

    // Test that it works fine for longs
    assertEquals(12345678901L, Utilities.millisToSeconds(12345678901234L));
  }

  public void testSecondsToMillis() {
    assertEquals(1234000, Utilities.secondsToMillis(1234));

    // Test that it works fine for longs
    assertEquals(12345678901000L, Utilities.secondsToMillis(12345678901L));
  }
}
