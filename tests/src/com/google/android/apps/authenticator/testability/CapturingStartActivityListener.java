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

package com.google.android.apps.authenticator.testability;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.concurrent.TimeoutException;

/**
 * Implementation of {@link StartActivityListener} that records launch attempts and prevents the
 * attempts from actually launching anything.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class CapturingStartActivityListener implements StartActivityListener {

  /**
   * Activity invocation attempt.
   */
  public static class Invocation {
    public Context context;
    public Intent intent;
  }

  private final Object mLock = new Object();
  private boolean mInvoked;
  private Invocation mInvocation;

  @Override
  public boolean onStartActivityInvoked(Context context, Intent intent) {
    synchronized (mLock) {
      if (mInvoked) {
        throw new IllegalStateException("Second launch attempt detected: " + intent);
      }
      mInvoked = true;
      mInvocation = new Invocation();
      mInvocation.context = context;
      mInvocation.intent = intent;
      mLock.notifyAll();
    }

    // Prevent the launch
    return true;
  }

  /**
   * Waits until the first invocation occurs and returns the invocation details.
   */
  public Invocation waitForFirstInvocation(long timeoutMillis)
      throws InterruptedException, TimeoutException {
    synchronized (mLock) {
      long deadline = SystemClock.uptimeMillis() + timeoutMillis;
      while (!mInvoked) {
        long millisTillDeadline = deadline - SystemClock.uptimeMillis();
        if (millisTillDeadline <= 0) {
          throw new TimeoutException(
              "Timed out while waiting for launch attempt for " + timeoutMillis + " ms");
        }
        mLock.wait(millisTillDeadline);
      }
      return mInvocation;
    }
  }
}
