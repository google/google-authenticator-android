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
 * Unit tests for {@link Preconditions}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class PreconditionsTest extends TestCase {

  public void testCheckNotNullSingleArg() {
    Object reference = "test";
    assertSame(reference, Preconditions.checkNotNull(reference));

    try {
      Preconditions.checkNotNull(null);
      fail("NullPointerException should have been thrown");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  public void testCheckArgumentSingleArg() {
    Preconditions.checkArgument(true);

    try {
      Preconditions.checkArgument(false);
      fail("IllegalArgumentException should have been thrown");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }
}
