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

package com.google.android.apps.authenticator.timesync;

import static com.google.common.base.Throwables.throwIfUnchecked;

import android.util.Log;
import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.android.apps.authenticator.util.concurrent.RunOnThisLooperThreadExecutor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller of the {@link SyncNowActivity}.
 *
 * <p>As soon as started, the controller attempts to obtain the network time using a {@link
 * NetworkTimeProvider}, then computes the offset between the device's system time and the network
 * time and updates {@link TotpClock} to use that as its time correction value.
 */
public class SyncNowController {
  /** Result of a sync operation */
  public enum Result {
    TIME_CORRECTED,
    TIME_ALREADY_CORRECT,
    CANCELLED_BY_USER,
    ERROR_CONNECTIVITY_ISSUE,
  }

  /** Presentation layer. */
  public interface Presenter {

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

  public SyncNowController(
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
  public void attach(Presenter presenter) {
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
  public void detach(Presenter presenter) {
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
  public void abort(Presenter presenter) {
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
    // (provided by mBackgroundExecutor) and posting the results back to this using
    // mCallbackFromBackgroundExecutor.
    ListenableFutureTask<Integer> getTimeCorrectionFuture =
        ListenableFutureTask.create(new Callable<Integer>() {
      @Override
      public Integer call() throws Exception {
        long timeCorrectionMillis =
            mNetworkTimeProvider.getNetworkTime() - mTotpClock.getSystemWallClock().nowMillis();
        final int timeCorrectionMinutes = (int) Math.round(
            ((double) timeCorrectionMillis) / Utilities.MINUTE_IN_MILLIS);
        return timeCorrectionMinutes;
      }
    });
    Futures.addCallback(
        getTimeCorrectionFuture,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(Integer timeCorrectionMinutes) {
            onNewTimeCorrectionObtained(timeCorrectionMinutes);
          }

          @Override
          public void onFailure(Throwable e) {
            if (e instanceof IOException) {
              Log.w(LOG_TAG, "Failed to obtain network time due to connectivity issues");
              finish(Result.ERROR_CONNECTIVITY_ISSUE);
            } else {
              // Blow up
              throwIfUnchecked(e);
              throw new RuntimeException(e);
            }
          }
        },
        mCallbackFromBackgroundExecutor);

    mBackgroundExecutor.execute(getTimeCorrectionFuture);
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
}
