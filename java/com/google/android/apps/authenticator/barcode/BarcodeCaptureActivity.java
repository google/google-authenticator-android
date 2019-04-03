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

package com.google.android.apps.authenticator.barcode;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import com.google.android.apps.authenticator.AuthenticatorApplication;
import com.google.android.apps.authenticator.barcode.BarcodeTracker.OnDetectionListener;
import com.google.android.apps.authenticator.barcode.preview.CameraSourcePreview;
import com.google.android.apps.authenticator.barcode.preview.GraphicOverlay;
import com.google.android.apps.authenticator.enroll2sv.wizard.AddAccountActivity;
import com.google.android.apps.authenticator.testability.DaggerInjector;
import com.google.android.apps.authenticator.testability.TestableActivity;
import com.google.android.apps.authenticator.util.permissions.PermissionRequestor;
import com.google.android.apps.authenticator2.R;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import javax.inject.Inject;

/**
 * Activity for managing barcode capture.
 *
 * The detected barcode value is returned as a result from this activity. If the app doesn't have
 * the camera permission, it will try to request the permission.
 */
public class BarcodeCaptureActivity extends TestableActivity {

  // Key value of the barcode value.
  public static final String INTENT_EXTRA_BARCODE_VALUE = "barcode_value";

  // Key value to detect if this activity is started from {@link AddAccountActivity}.
  public static final String INTENT_EXTRA_START_FROM_ADD_ACCOUNT = "start_from_add_account";

  // The tag for log messages
  private static final String TAG = "BarcodeCaptureActivity";

  // Permission request codes need to be < 256
  private static final int RC_HANDLE_CAMERA_PERM = 1;

  // The camera preview preferred framerate.
  private static final float PREFERRED_FRAMERATE = 30.0f;

  // The volume for the tone when a barcode is detected, value in range [0..100].
  private static final int TONE_VOLUME = 100;

  // The duration (in millisecond) for the tone when a barcode is detected.
  private static final int TONE_DURATION = 200;

  private CameraSource mCameraSource;
  private CameraSourcePreview mCameraSourcePreview;
  private GraphicOverlay mGraphicOverlay;
  private boolean mBarcodeDetected;

  // Detect orientation change events.
  private OrientationEventListener mOrientationEventListener;

  // Save the current orientation for detecting changes.
  private int mCurrentRotation;

  // Check if this activity is started from {@link StartAccountActivity}.
  private boolean mStartFromAddAccountActivity;

  @Inject
  PermissionRequestor mPermissionRequestor;

  public BarcodeCaptureActivity() {
    super();
    DaggerInjector.inject(this);
  }

  private final BarcodeTracker.OnDetectionListener mDetectionListener = new OnDetectionListener() {
    @Override
    public void onNewDetection(int id, final Barcode barcode) {
      // The barcode detection happens in a different thread than the ui thread. If a barcode is
      // detected, return it on the UI thread to prevent race condition.

      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (!mBarcodeDetected) {
            mBarcodeDetected = true;
            mCameraSourcePreview.stop();
            AudioManager audioService = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioService.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
              ToneGenerator toneGenerator
                  = new ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME);
              toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, TONE_DURATION);
            }
            onBarcodeDetected(barcode);
          }
        }
      });
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // On API 19+ devices, we make the status bar become transparent. In older devices, we simply
    // remove the status bar.
    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
      // On API 21+ we need to set the status bar color to transparent color instead of using flag
      // FLAG_TRANSLUCENT_STATUS as in API 19, 20 to make the the status bar transparent.
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
      } else {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
      }
    } else {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
          WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    mStartFromAddAccountActivity = getIntent()
        .getBooleanExtra(INTENT_EXTRA_START_FROM_ADD_ACCOUNT, false);

    setContentView(R.layout.barcode_capture_activity);

    mCameraSourcePreview = (CameraSourcePreview) findViewById(R.id.camera_source_preview);
    mGraphicOverlay = (GraphicOverlay) findViewById(R.id.graphic_overlay);
    mCurrentRotation = getWindowManager().getDefaultDisplay().getRotation();

    mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
      @Override
      public void onOrientationChanged(int i) {
        if (i == ORIENTATION_UNKNOWN) {
          return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (mCurrentRotation != rotation) {
          // Orientation changed, refresh camera.
          mCurrentRotation = rotation;
          if (mCameraSourcePreview != null) {
            mCameraSourcePreview.stop();
            mCameraSourcePreview.release();
          }
          createCameraSource();
          startCameraSource();
        }
      }
    };
    if (mOrientationEventListener.canDetectOrientation() == true) {
      mOrientationEventListener.enable();
    } else {
      mOrientationEventListener.disable();
    }

    // Check for the camera permission before accessing the camera.  If the
    // permission is not granted yet, request permission.
    if (mPermissionRequestor.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      createCameraSource();
    } else {
      requestCameraPermission();
    }
  }

  @Override
  public void onBackPressed() {
    // If this activity is started from {@link AddAccountActivity}, open it again.
    if (mStartFromAddAccountActivity) {
      startActivity(new Intent(BarcodeCaptureActivity.this, AddAccountActivity.class));
      finish();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    startCameraSource();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (mCameraSourcePreview != null) {
      mCameraSourcePreview.stop();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mCameraSourcePreview != null) {
      mCameraSourcePreview.release();
    }
    if (mOrientationEventListener != null) {
      mOrientationEventListener.disable();
    }
  }

  /**
   * Returns the detected barcode value to the caller activity.
   */
  private void onBarcodeDetected(Barcode barcode) {
    Log.d(TAG, "Detected barcode with value: " + barcode.displayValue);

    Intent returnIntent = new Intent();
    returnIntent.putExtra(INTENT_EXTRA_BARCODE_VALUE, barcode.displayValue);
    setResult(RESULT_OK, returnIntent);
    finish();
  }

  /**
   * Handles the requesting of the camera permission.  This includes showing a "Snackbar" message of
   * why the permission is needed then sending the request.
   */
  private void requestCameraPermission() {
    Log.w(TAG, "Camera permission is not granted. Requesting permission");

    final String[] permissions = new String[]{Manifest.permission.CAMERA};

    if (!mPermissionRequestor
        .shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
      mPermissionRequestor.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
      return;
    }

    View.OnClickListener listener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        mPermissionRequestor
            .requestPermissions(BarcodeCaptureActivity.this, permissions, RC_HANDLE_CAMERA_PERM);
      }
    };

    Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
        Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.ok, listener)
        .show();
  }


  private void createCameraSource() {
    int screenWidth;
    int screenHeight;
    if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB_MR2) {
      Point size = new Point();
      getWindowManager().getDefaultDisplay().getSize(size);
      screenHeight = size.y;
      screenWidth = size.x;
    } else {
      Display display = getWindowManager().getDefaultDisplay();
      screenHeight = display.getHeight();
      screenWidth = display.getWidth();
    }

    if (screenWidth > screenHeight) {
      int tmp = screenWidth;
      screenWidth = screenHeight;
      screenHeight = tmp;
    }

    // There are devices that don't have a rear camera, like a version of the Nexus 7. Here we
    // check if the device has a rear camera, and if not, try to use the front camera instead.
    int cameraFacing = CameraSource.CAMERA_FACING_BACK;
    PackageManager packageManager = getPackageManager();
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
      cameraFacing = CameraSource.CAMERA_FACING_FRONT;
    }

    // A barcode detector is created to track barcodes.  An associated focussing processor instance
    // is set with a barcode tracker to receive the barcode detection results, track the barcodes,
    // filter out the barcodes that are not inside the square rectangle and return the callback
    // when a valid barcode is detected.
    final AuthenticatorApplication application =
        (AuthenticatorApplication) getApplicationContext();
    BarcodeDetector barcodeDetector = application.getBarcodeDetector();
    BarcodeTracker barcodeTracker = new BarcodeTracker();
    barcodeTracker.setDetectionListener(mDetectionListener);
    // We disable central filter for front-facing camera as it is hard for users to place the
    // barcode completely inside the square with a front-facing camera.
    barcodeDetector.setProcessor(
        new BarcodeCentralFocusingProcessor(barcodeDetector, barcodeTracker,
            cameraFacing == CameraSource.CAMERA_FACING_BACK /* centralFilterEnabled */));

    CameraSource.Builder builder
        = new CameraSource.Builder(getApplicationContext(), barcodeDetector)
        .setFacing(cameraFacing)
        // TODO: Consider removing autofocus. For Pixel 2 phones, scanning the QR Code becomes
        // difficult.
        .setAutoFocusEnabled(true)
        .setRequestedPreviewSize(screenHeight, screenWidth)
        .setRequestedFps(PREFERRED_FRAMERATE);

    mCameraSource = builder.build();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
      String[] permissions,
      int[] grantResults) {
    if (requestCode != RC_HANDLE_CAMERA_PERM) {
      Log.e(TAG, "Got unexpected permission result: " + requestCode);
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      return;
    }

    if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Camera permission granted - initialize the camera source");
      // We have permission, so create the camera source.
      createCameraSource();
      return;
    }

    Log.e(TAG, "Permission not granted: results len = " + grantResults.length
        + " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

    // Permission is not granted.
    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int id) {
        // If this activity is started from {@link AddAccountActivity}, open it again.
        if (mStartFromAddAccountActivity) {
          startActivity(new Intent(BarcodeCaptureActivity.this, AddAccountActivity.class));
        }
        finish();
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.no_camera_permission)
        .setPositiveButton(R.string.ok, listener)
        .show();
  }

  /**
   * Starts or restarts the camera source, if it exists.
   *
   * @throws SecurityException if there is no permission to use the camera when this method was
   * called.
   */
  private void startCameraSource() throws SecurityException {
    if (mCameraSource != null) {
      mCameraSourcePreview.start(mCameraSource, mGraphicOverlay);
    }
  }
}
