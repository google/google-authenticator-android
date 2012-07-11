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

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.apps.authenticator.RunImmediatelyOnCallingThreadExecutor;
import com.google.android.apps.authenticator.TotpClock;
import com.google.android.apps.authenticator.Utilities;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * Unit tests for {@link SyncNowController}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class SyncNowControllerTest extends TestCase {

  @Mock private NetworkTimeProvider mMockNetworkTimeProvider;
  @Mock private TotpClock mMockTotpClock;
  @Mock private SyncNowController.Presenter mMockPresenter;
  private Executor mBackgroundExecutor;
  private Executor mCallbackExecutor;

  private SyncNowController mController;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    // By default, configure the controller to invoke its background operations on the calling
    // thread so that tests do not depend on other threads (especially Looper threads) and are
    // easier to read due to lack of concurrency complications.
    withImmediateExecutors();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testAdjustmentMade() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() + 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.TIME_CORRECTED, verifyPresenterOnDoneInvoked());
    assertEquals(3, verifyTotpClockSetTimeCorrectionInvoked());

    reset(mMockPresenter);
    mController.detach(mMockPresenter);
    verifyZeroInteractions(mMockPresenter);
  }

  public void testAdjustmentNotNeeded() throws Exception {
    withTotpClockTimeCorrectionMinutes(-3);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() - 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.TIME_ALREADY_CORRECT, verifyPresenterOnDoneInvoked());
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  public void testConnectivityError() throws Exception {
    withNetworkTimeProviderThrowing(new IOException());

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.ERROR_CONNECTIVITY_ISSUE, verifyPresenterOnDoneInvoked());
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  public void testCancelledByUserBeforeBackgroundOperation() throws Exception {
    withTotpClockTimeCorrectionMinutes(-7);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() - 7 * Utilities.MINUTE_IN_MILLIS);
    withBackgroundExecutorThatAbortsControllerBeforeExecuting();

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.CANCELLED_BY_USER, verifyPresenterOnDoneInvoked());
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  public void testCancelledByUserBeforeCallback() throws Exception {
    withTotpClockTimeCorrectionMinutes(-7);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() - 7 * Utilities.MINUTE_IN_MILLIS);
    withCallbackExecutorThatAbortsControllerBeforeExecuting();

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.CANCELLED_BY_USER, verifyPresenterOnDoneInvoked());
    verifyTotpClockSetTimeCorrectionNotInvoked();
  }

  public void testAttachToNewPresenter() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() + 3 * Utilities.MINUTE_IN_MILLIS);

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.TIME_CORRECTED, verifyPresenterOnDoneInvoked());
    assertEquals(3, verifyTotpClockSetTimeCorrectionInvoked());
    reset(mMockTotpClock, mMockNetworkTimeProvider);

    mMockPresenter = mock(SyncNowController.Presenter.class);
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.TIME_CORRECTED, verifyPresenterOnDoneInvoked());
    verifyZeroInteractions(mMockTotpClock, mMockNetworkTimeProvider);
  }

  public void testDetachPresenterBeforeFinished() throws Exception {
    withTotpClockTimeCorrectionMinutes(7);
    withNetworkTimeProviderReturningMillis(
        System.currentTimeMillis() + 3 * Utilities.MINUTE_IN_MILLIS);
    withBackgroundExecutorThatDetachesPresenterBeforeExecuting();

    createController();
    mController.attach(mMockPresenter);
    assertEquals(SyncNowController.Result.CANCELLED_BY_USER, verifyPresenterOnDoneInvoked());
    verifyZeroInteractions(mMockTotpClock);
  }

  private void createController() {
    mController = new SyncNowController(
        mMockTotpClock,
        mMockNetworkTimeProvider,
        mBackgroundExecutor,
        false,
        mCallbackExecutor);
  }

  private void withNetworkTimeProviderReturningMillis(long timeMillis) throws IOException {
    doReturn(timeMillis).when(mMockNetworkTimeProvider).getNetworkTime();
  }

  private void withNetworkTimeProviderThrowing(IOException exception) throws IOException {
    doThrow(exception).when(mMockNetworkTimeProvider).getNetworkTime();
  }

  private void withTotpClockTimeCorrectionMinutes(int timeCorrectionMinutes) {
    doReturn(timeCorrectionMinutes).when(mMockTotpClock).getTimeCorrectionMinutes();
  }

  private SyncNowController.Result verifyPresenterOnDoneInvoked() {
    ArgumentCaptor<SyncNowController.Result> resultCaptor =
        ArgumentCaptor.forClass(SyncNowController.Result.class);
    verify(mMockPresenter).onDone(resultCaptor.capture());
    return resultCaptor.getValue();
  }

  private void verifyTotpClockSetTimeCorrectionNotInvoked() {
    verify(mMockTotpClock, never()).setTimeCorrectionMinutes(anyInt());
  }

  private int verifyTotpClockSetTimeCorrectionInvoked() {
    ArgumentCaptor<Integer> resultCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(mMockTotpClock).setTimeCorrectionMinutes(resultCaptor.capture());
    return resultCaptor.getValue();
  }

  private void withImmediateExecutors() {
    mBackgroundExecutor = new RunImmediatelyOnCallingThreadExecutor();
    mCallbackExecutor = new RunImmediatelyOnCallingThreadExecutor();
  }

  private void withBackgroundExecutorThatAbortsControllerBeforeExecuting() {
    mBackgroundExecutor = new Executor() {
      @Override
      public void execute(Runnable command) {
        mController.abort(mMockPresenter);
        command.run();
      }
    };
  }

  private void withCallbackExecutorThatAbortsControllerBeforeExecuting() {
    mCallbackExecutor = new Executor() {
      @Override
      public void execute(Runnable command) {
        mController.abort(mMockPresenter);
        command.run();
      }
    };
  }

  private void withBackgroundExecutorThatDetachesPresenterBeforeExecuting() {
    mBackgroundExecutor = new Executor() {
      @Override
      public void execute(Runnable command) {
        mController.detach(mMockPresenter);
        command.run();
      }
    };
  }
}
