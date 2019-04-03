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

import android.os.Handler;
import com.google.android.apps.authenticator.time.Clock;
import com.google.android.apps.authenticator.util.Utilities;

/**
 * Task that periodically notifies its listener about the time remaining until the value of a TOTP
 * counter changes.
 */
public class TotpCountdownTask implements Runnable {
  private final TotpCounter mCounter;
  private final Clock mClock;
  private final long mRemainingTimeNotificationPeriod;
  private final Handler mHandler = new Handler();

  private long mLastSeenCounterValue = Long.MIN_VALUE;
  private boolean mShouldStop;
  private Listener mListener;

  /**
   * Listener notified of changes to the time remaining until the counter value changes.
   */
  public interface Listener {

    /**
     * Invoked when the time remaining till the TOTP counter changes its value.
     *
     * @param millisRemaining time (milliseconds) remaining.
     */
    void onTotpCountdown(long millisRemaining);

    /** Invoked when the TOTP counter changes its value. */
    void onTotpCounterValueChanged();
  }

  /**
   * Constructs a new {@code TotpRefreshTask}.
   *
   * @param counter TOTP counter this task monitors.
   * @param clock TOTP clock that drives this task.
   * @param remainingTimeNotificationPeriod approximate interval (milliseconds) at which this task
   *        notifies its listener about the time remaining until the @{code counter} changes its
   *        value.
   */
  public TotpCountdownTask(TotpCounter counter, TotpClock clock,
      long remainingTimeNotificationPeriod) {
    mCounter = counter;
    mClock = clock;
    mRemainingTimeNotificationPeriod = remainingTimeNotificationPeriod;
  }

  /**
   * Sets the listener that this task will periodically notify about the state of the TOTP counter.
   *
   * @param listener listener or {@code null} for no listener.
   */
  public void setListener(Listener listener) {
    mListener = listener;
  }

  /**
   * Starts this task and immediately notifies the listener that the counter value has changed.
   *
   * <p>The immediate notification during startup ensures that the listener does not miss any
   * updates.
   *
   * @throws IllegalStateException if the task has already been stopped.
   */
  public void startAndNotifyListener() {
    if (mShouldStop) {
      throw new IllegalStateException("Task already stopped and cannot be restarted.");
    }

    run();
  }

  /**
   * Stops this task. This task will never notify the listener after the task has been stopped.
   */
  public void stop() {
    mShouldStop = true;
  }

  @Override
  public void run() {
    if (mShouldStop) {
      return;
    }

    long now = mClock.nowMillis();
    long counterValue = getCounterValue(now);
    if (mLastSeenCounterValue != counterValue) {
      mLastSeenCounterValue = counterValue;
      fireTotpCounterValueChanged();
    }
    fireTotpCountdown(getTimeTillNextCounterValue(now));

    scheduleNextInvocation();
  }

  private void scheduleNextInvocation() {
    long now = mClock.nowMillis();
    long counterValueAge = getCounterValueAge(now);
    long timeTillNextInvocation =
        mRemainingTimeNotificationPeriod - (counterValueAge % mRemainingTimeNotificationPeriod);
    mHandler.postDelayed(this, timeTillNextInvocation);
  }

  private void fireTotpCountdown(long timeRemaining) {
    if ((mListener != null) && (!mShouldStop)) {
      mListener.onTotpCountdown(timeRemaining);
    }
  }

  private void fireTotpCounterValueChanged() {
    if ((mListener != null) && (!mShouldStop)) {
      mListener.onTotpCounterValueChanged();
    }
  }

  /**
   * Gets the value of the counter at the specified time instant.
   *
   * @param time time instant (milliseconds since epoch).
   */
  private long getCounterValue(long time) {
    return mCounter.getValueAtTime(Utilities.millisToSeconds(time));
  }

  /**
   * Gets the time remaining till the counter assumes its next value.
   *
   * @param time time instant (milliseconds since epoch) for which to perform the query.
   *
   * @return time (milliseconds) till next value.
   */
  private long getTimeTillNextCounterValue(long time) {
    long currentValue = getCounterValue(time);
    long nextValue = currentValue + 1;
    long nextValueStartTime = Utilities.secondsToMillis(mCounter.getValueStartTime(nextValue));
    return nextValueStartTime - time;
  }

  /**
   * Gets the age of the counter value at the specified time instant.
   *
   * @param time time instant (milliseconds since epoch).
   *
   * @return age (milliseconds).
   */
  private long getCounterValueAge(long time) {
    return time - Utilities.secondsToMillis(mCounter.getValueStartTime(getCounterValue(time)));
  }
}
