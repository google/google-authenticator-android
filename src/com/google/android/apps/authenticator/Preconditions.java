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

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state. Inspired by Guava.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public final class Preconditions {

  /** Hidden to avoid instantiation. */
  private Preconditions() {}

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not
   * {@code null}.
   *
   * @return non-{@code null} reference that was validated.
   *
   * @throws NullPointerException if {@code reference} is {@code null}.
   */
  public static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @throws IllegalArgumentException if {@code expression} is {@code false}.
   */
  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling
   * instance, but not involving any parameters to the calling method.
   *
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling
   * instance, but not involving any parameters to the calling method.
   *
   * @param errorMessage the exception message to use if the check fails; will
   *        be converted to a string using {@link String#valueOf(Object)}
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(
      boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }
}
