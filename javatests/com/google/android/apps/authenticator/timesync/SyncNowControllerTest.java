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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.time.Clock;
import com.google.android.apps.authenticator.util.Utilities;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/** Unit tests for {@link SyncNowController}. */
@RunWith(JUnit4.class)
public class SyncNowControllerTest {

  private static final long SYSTEM_TIME_MILLIS = 12817816442L; // arbitrary value

  @Mock private NetworkTimeProvider mockNetworkTimeProvider;
  @Mock private Clock mockSystemClock;
  @Mock private TotpClock mockTotpClock;
  @Mock private SyncNowController.Presenter mockPresenter;
  private Executor backgroundExecutor;
  private Executor callbackExecutor;

  private SyncNowController controller;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    withSystemClockNowMillis(SYSTEM_TIME_MILLIS);
    withTotpClockSystemWallClock(mockSystemClock);

    // By default, configure the controller to invoke its background operations on the calling
    // thread so that tests do not depend on other threads (especially Looper threads) and are
    // easier to read due to lack of concurrency complications.
    withImmediateExecutors();
  }

  @Test
  public void testAdjustmentMade() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS + 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked()).isEqualTo(SyncNowController.Result.TIME_CORRECTED);
    assertThat(verifyTotpClockSetTimeCorrectionInvoked()).isEqualTo(3);

    reset(mockPresenter);
    controller.detach(mockPresenter);
    verifyZeroInteractions(mockPresenter);
  }

  @Test
  public void testAdjustmentNotNeeded() throws Exception {
    withTotpClockTimeCorrectionMinutes(-3);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS - 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked())
        .isEqualTo(SyncNowController.Result.TIME_ALREADY_CORRECT);
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  @Test
  public void testConnectivityError() throws Exception {
    withNetworkTimeProviderThrowing(new IOException());

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked())
        .isEqualTo(SyncNowController.Result.ERROR_CONNECTIVITY_ISSUE);
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  @Test
  public void testCancelledByUserBeforeBackgroundOperation() throws Exception {
    withTotpClockTimeCorrectionMinutes(-7);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS - 7 * Utilities.MINUTE_IN_MILLIS);
    withBackgroundExecutorThatAbortsControllerBeforeExecuting();

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked())
        .isEqualTo(SyncNowController.Result.CANCELLED_BY_USER);
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  @Test
  public void testCancelledByUserBeforeCallback() throws Exception {
    withTotpClockTimeCorrectionMinutes(-7);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS - 7 * Utilities.MINUTE_IN_MILLIS);
    withCallbackExecutorThatAbortsControllerBeforeExecuting();

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked())
        .isEqualTo(SyncNowController.Result.CANCELLED_BY_USER);
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  @Test
  public void testAttachToNewPresenter() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS + 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked()).isEqualTo(SyncNowController.Result.TIME_CORRECTED);
    assertThat(verifyTotpClockSetTimeCorrectionInvoked()).isEqualTo(3);
    reset(mockTotpClock, mockNetworkTimeProvider);

    mockPresenter = mock(SyncNowController.Presenter.class);
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked()).isEqualTo(SyncNowController.Result.TIME_CORRECTED);
    verifyZeroInteractions(mockTotpClock, mockNetworkTimeProvider);
  }

  @Test
  public void testDetachPresenterBeforeFinished() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(SYSTEM_TIME_MILLIS + 3 * Utilities.MINUTE_IN_MILLIS);
    withBackgroundExecutorThatDetachesPresenterBeforeExecuting();

    createController();
    controller.attach(mockPresenter);
    assertThat(verifyPresenterOnDoneInvoked())
        .isEqualTo(SyncNowController.Result.CANCELLED_BY_USER);
    verify(mockTotpClock, never()).setTimeCorrectionMinutes(anyInt());
  }

  private void createController() {
    controller =
        new SyncNowController(
            mockTotpClock, mockNetworkTimeProvider, backgroundExecutor, false, callbackExecutor);
  }

  private void withNetworkTimeProviderReturningMillis(long timeMillis) throws IOException {
    doReturn(timeMillis).when(mockNetworkTimeProvider).getNetworkTime();
  }

  private void withNetworkTimeProviderThrowing(IOException exception) throws IOException {
    doThrow(exception).when(mockNetworkTimeProvider).getNetworkTime();
  }

  private void withTotpClockTimeCorrectionMinutes(int timeCorrectionMinutes) {
    doReturn(timeCorrectionMinutes).when(mockTotpClock).getTimeCorrectionMinutes();
  }

  private SyncNowController.Result verifyPresenterOnDoneInvoked() {
    ArgumentCaptor<SyncNowController.Result> resultCaptor =
        ArgumentCaptor.forClass(SyncNowController.Result.class);
    verify(mockPresenter).onDone(resultCaptor.capture());
    return resultCaptor.getValue();
  }

  private void verifyTotpClockSetTimeCorrectionNotInvoked() {
    verify(mockTotpClock, never()).setTimeCorrectionMinutes(anyInt());
  }

  private int verifyTotpClockSetTimeCorrectionInvoked() {
    ArgumentCaptor<Integer> resultCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(mockTotpClock).setTimeCorrectionMinutes(resultCaptor.capture());
    return resultCaptor.getValue();
  }

  private void withImmediateExecutors() {
    backgroundExecutor = MoreExecutors.directExecutor();
    callbackExecutor = MoreExecutors.directExecutor();
  }

  private void withBackgroundExecutorThatAbortsControllerBeforeExecuting() {
    backgroundExecutor =
        new Executor() {
          @Override
          public void execute(Runnable command) {
            controller.abort(mockPresenter);
            command.run();
          }
        };
  }

  private void withCallbackExecutorThatAbortsControllerBeforeExecuting() {
    callbackExecutor =
        new Executor() {
          @Override
          public void execute(Runnable command) {
            controller.abort(mockPresenter);
            command.run();
          }
        };
  }

  private void withBackgroundExecutorThatDetachesPresenterBeforeExecuting() {
    backgroundExecutor =
        new Executor() {
          @Override
          public void execute(Runnable command) {
            controller.detach(mockPresenter);
            command.run();
          }
        };
  }
  private void withTotpClockSystemWallClock(Clock systemClock) {
    doReturn(systemClock).when(mockTotpClock).getSystemWallClock();
  }

  private void withSystemClockNowMillis(long timeMillis) {
    doReturn(timeMillis).when(mockSystemClock).nowMillis();
  }
}
