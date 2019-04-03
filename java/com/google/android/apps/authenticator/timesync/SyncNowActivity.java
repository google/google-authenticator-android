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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import com.google.android.apps.authenticator.otp.TotpClock;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator2.R;
import javax.inject.Inject;

/**
 * Activity that adjusts the application's internal system time offset (for the purposes of
 * computing TOTP verification codes) by making a network request to Google and comparing Google's
 * time to the device's time.
 */
public class SyncNowActivity extends Activity implements SyncNowController.Presenter {

  // IMPLEMENTATION NOTE: This class implements a Passive View pattern. All state and business logic
  // are kept in the Controller which pushes the relevant state into this Activity. This helps with
  // testing the business logic and also with preserving state across the creation/destruction of
  // Activity instances due to screen orientation changes. Activity instances being destroyed
  // detach from the controller, and new instances being created attach to the controller. Once an
  // instance has attached, the controller will set up the Activity into the correct state and will
  // continue pushing state changes into the Activity until it detaches.

  private SyncNowController mController;

  private Dialog mProgressDialog;

  @Inject TotpClock mTotpClock;
  @Inject NetworkTimeProvider mNetworkTimeProvider;

  public SyncNowActivity() {
    super();
    DaggerInjector.inject(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    @SuppressWarnings("deprecation") // TODO: refactor to use savedInstanceState instead
    Object lastNonConfigurationInstance = getLastNonConfigurationInstance();
    if (lastNonConfigurationInstance != null) {
      mController = (SyncNowController) lastNonConfigurationInstance;
    } else {
      mController = new SyncNowController(mTotpClock, mNetworkTimeProvider);
    }

    mController.attach(this);
  }

  @Override
  protected void onStop() {
    if (isFinishing()) {
      mController.detach(this);
    }
    super.onStop();
  }

  @SuppressWarnings("deprecation") // TODO: refactor to use savedInstanceState instead
  @Override
  public Object onRetainNonConfigurationInstance() {
    return mController;
  }

  @Override
  public void onBackPressed() {
    mController.abort(this);
  }

  // --------- SyncNowController.Presenter interface implementation ------

  @Override
  public void onStarted() {
    if (isFinishing()) {
      // Ignore this callback if this Activity is already finishing or is already finished
      return;
    }

    showInProgressDialog();
  }

  @Override
  @TargetApi(11)
  public void onDone(SyncNowController.Result result) {
    if (isFinishing()) {
      // Ignore this callback if this Activity is already finishing or is already finished
      return;
    }

    dismissInProgressDialog();

    AlertDialog.Builder alertDialogBuilder = null;
    switch (result) {
      case TIME_ALREADY_CORRECT:
        alertDialogBuilder = new AlertDialog.Builder(this)
            .setTitle(R.string.timesync_sync_now_time_already_correct_dialog_title)
            .setMessage(R.string.timesync_sync_now_time_already_correct_dialog_details);
        break;
      case TIME_CORRECTED:
        alertDialogBuilder = new AlertDialog.Builder(this)
            .setTitle(R.string.timesync_sync_now_time_corrected_dialog_title)
            .setMessage(R.string.timesync_sync_now_time_corrected_dialog_details);
        break;
      case ERROR_CONNECTIVITY_ISSUE:
        alertDialogBuilder = new AlertDialog.Builder(this)
            .setTitle(R.string.timesync_sync_now_connectivity_error_dialog_title)
            .setMessage(R.string.timesync_sync_now_connectivity_error_dialog_details);
        break;
      case CANCELLED_BY_USER:
        finish();
        break;
      default:
        throw new IllegalArgumentException(String.valueOf(result));
    }

    if (alertDialogBuilder == null) { return; }

    alertDialogBuilder.setCancelable(false)
        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            finish();
          }
        })
        .create()
        .show();
  }

  // --------- SyncNowController.Presenter interface implementation END ------


  private void showInProgressDialog() {
    mProgressDialog = ProgressDialog.show(
        this,
        getString(R.string.timesync_sync_now_progress_dialog_title),
        getString(R.string.timesync_sync_now_progress_dialog_details),
        true,
        true);
    mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        mController.abort(SyncNowActivity.this);
      }
    });
  }

  /**
   * Dismisses the progress dialog. Does nothing if the dialog has not been or no longer is
   * displayed.
   *
   * @see #showInProgressDialog()
   */
  private void dismissInProgressDialog() {
    if (mProgressDialog != null) {
      mProgressDialog.dismiss();
      mProgressDialog = null;
    }
  }
}
