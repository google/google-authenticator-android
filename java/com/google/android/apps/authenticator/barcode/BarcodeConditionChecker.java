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

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import com.google.android.apps.authenticator.AuthenticatorApplication;
import com.google.android.gms.common.GoogleApiAvailability;

/**
 * A class contains functions for checking conditions before running the barcode scanner.
 */
public class BarcodeConditionChecker {

  /**
   * Verifies that Google Play services is installed and enabled on this device, and that the
   * version installed on this device is no older than the one required by this client.
   */
  public boolean isGooglePlayServicesAvailable(Activity activity) {
    return GoogleApiAvailability.getInstance()
        .getApkVersion(activity.getApplicationContext()) >= 9200000;
  }

  /**
   * Returns whether the barcode detector is operational or not.
   */
  public boolean getIsBarcodeDetectorOperational(Activity activity) {
    final AuthenticatorApplication application =
        (AuthenticatorApplication) activity.getApplicationContext();
    return application.getBarcodeDetector() != null
        && application.getBarcodeDetector().isOperational();
  }

  /**
   * Check if the device has low storage.
   */
  public boolean isLowStorage(Activity activity) {
    IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
    return activity.registerReceiver(null, lowStorageFilter) != null;
  }

  /**
   * Check if the device has any front or rear camera for the barcode scanner.
   */
  public boolean isCameraAvailableOnDevice(Activity activity) {
    return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
        || activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
  }

}
