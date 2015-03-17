/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

package com.google.android.apps.authenticator.timesync;

import com.google.android.apps.authenticator.RunOnThisLooperThreadExecutor;
import com.google.android.apps.authenticator.TotpClock;
import com.google.android.apps.authenticator.Utilities;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller of the {@link SyncNowActivity}. As soon as started, the controller attempts to
 * obtain the network time using a {@link NetworkTimeProvider}, then computes the offset between
 * the device's system time and the network time and updates {@link TotpClock} to use that as
 * its time correction value.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
class SyncNowController {
  enum Result {
    TIME_CORRECTED,
    TIME_ALREADY_CORRECT,
    CANCELLED_BY_USER,
    ERROR_CONNECTIVITY_ISSUE,
  }

  /** Presentation layer. */
  interface Presenter {

    /** Invoked when the controller starts up. */
    void onStarted();

    /** Invoked when the controller is finished. */
    void onDone(Result result);
  }

  private enum State {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
  }

  private static final String LOG_TAG = "TimeSync";

  private final TotpClock mTotpClock;
  private final NetworkTimeProvider mNetworkTimeProvider;
  private final Executor mBackgroundExecutor;
  private final Executor mCallbackFromBackgroundExecutor;
  private final boolean mBackgroundExecutorServiceOwnedByThisController;

  private Presenter mPresenter;
  private State mState = State.NOT_STARTED;
  private Result mResult;

  // @VisibleForTesting
  SyncNowController(
      TotpClock totpClock,
      NetworkTimeProvider networkTimeProvider,
      Executor backgroundExecutor,
      boolean backgroundExecutorServiceOwnedByThisController,
      Executor callbackFromBackgroundExecutor) {
    mTotpClock = totpClock;
    mNetworkTimeProvider = networkTimeProvider;
    mBackgroundExecutor = backgroundExecutor;
    mBackgroundExecutorServiceOwnedByThisController =
        backgroundExecutorServiceOwnedByThisController;
    mCallbackFromBackgroundExecutor = callbackFromBackgroundExecutor;
  }

  SyncNowController(TotpClock totpClock, NetworkTimeProvider networkTimeProvider) {
    this(
        totpClock,
        networkTimeProvider,
        Executors.newSingleThreadExecutor(),
        true,
        new RunOnThisLooperThreadExecutor());
  }

  /**
   * Attaches the provided presentation layer to this controller. The previously attached
   * presentation layer (if any) stops receiving events from this controller.
   */
  void attach(Presenter presenter) {
    mPresenter = presenter;
    switch (mState) {
      case NOT_STARTED:
        start();
        break;
      case IN_PROGRESS:
        if (mPresenter != null) {
          mPresenter.onStarted();
        }
        break;
      case DONE:
        if (mPresenter != null) {
          mPresenter.onDone(mResult);
        }
        break;
      default:
        throw new IllegalStateException(String.valueOf(mState));
    }
  }

  /**
   * Detaches the provided presentation layer from this controller. Does nothing if the presentation
   * layer is not the same as the layer currently attached to the controller.
   */
  void detach(Presenter presenter) {
    if (presenter != mPresenter) {
      return;
    }
    switch (mState) {
      case NOT_STARTED:
      case IN_PROGRESS:
        onCancelledByUser();
        break;
      case DONE:
        break;
      default:
        throw new IllegalStateException(String.valueOf(mState));
    }
  }

  /**
   * Requests that this controller abort its operation. Does nothing if the provided presentation
   * layer is not the same as the layer attached to this controller.
   */
  void abort(Presenter presenter) {
    if (mPresenter != presenter) {
      return;
    }
    onCancelledByUser();
  }

  /**
   * Starts this controller's operation (initiates a Time Sync).
   */
  private void start() {
    mState = State.IN_PROGRESS;
    if (mPresenter != null) {
      mPresenter.onStarted();
    }
    // Avoid blocking this thread on the Time Sync operation by invoking it on a different thread
    // (provided by the Executor) and posting the results back to this thread.
    mBackgroundExecutor.execute(new Runnable() {
      @Override
      public void run() {
        runBackgroundSyncAndPostResult(mCallbackFromBackgroundExecutor);
      }
    });
  }

  private void onCancelledByUser() {
    finish(Result.CANCELLED_BY_USER);
  }

  /**
   * Invoked when the time correction value was successfully obtained from the network time
   * provider.
   *
   * @param timeCorrectionMinutes number of minutes by which this device is behind the correct time.
   */
  private void onNewTimeCorrectionObtained(int timeCorrectionMinutes) {
    if (mState != State.IN_PROGRESS) {
      // Don't apply the new time correction if this controller is not waiting for this.
      // This callback may be invoked after the Time Sync operation has been cancelled or stopped
      // prematurely.
      return;
    }

    long oldTimeCorrectionMinutes = mTotpClock.getTimeCorrectionMinutes();
    Log.i(LOG_TAG, "Obtained new time correction: "
        + timeCorrectionMinutes + " min, old time correction: "
        + oldTimeCorrectionMinutes + " min");
    if (timeCorrectionMinutes == oldTimeCorrectionMinutes) {
      finish(Result.TIME_ALREADY_CORRECT);
    } else {
      mTotpClock.setTimeCorrectionMinutes(timeCorrectionMinutes);
      finish(Result.TIME_CORRECTED);
    }
  }

  /**
   * Terminates this controller's operation with the provided result/outcome.
   */
  private void finish(Result result) {
    if (mState == State.DONE) {
      // Not permitted to change state when already DONE
      return;
    }
    if (mBackgroundExecutorServiceOwnedByThisController) {
      ((ExecutorService) mBackgroundExecutor).shutdownNow();
    }
    mState = State.DONE;
    mResult = result;
    if (mPresenter != null) {
      mPresenter.onDone(result);
    }
  }

  /**
   * Obtains the time correction value (<b>may block for a while</b>) and posts the result/error
   * using the provided {@link Handler}.
   */
  private void runBackgroundSyncAndPostResult(Executor callbackExecutor) {
    long networkTimeMillis;
    try {
      networkTimeMillis = mNetworkTimeProvider.getNetworkTime();
    } catch (IOException e) {
      Log.w(LOG_TAG, "Failed to obtain network time due to connectivity issues");
      callbackExecutor.execute(new Runnable() {
        @Override
        public void run() {
          finish(Result.ERROR_CONNECTIVITY_ISSUE);
        }
      });
      return;
    }

    long timeCorrectionMillis = networkTimeMillis - System.currentTimeMillis();
    final int timeCorrectionMinutes = (int) Math.round(
        ((double) timeCorrectionMillis) / Utilities.MINUTE_IN_MILLIS);
    callbackExecutor.execute(new Runnable() {
      @Override
      public void run() {
        onNewTimeCorrectionObtained(timeCorrectionMinutes);
      }
    });
  }
}
