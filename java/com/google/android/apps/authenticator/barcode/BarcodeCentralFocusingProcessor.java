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

import android.util.SparseArray;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Detector.Detections;
import com.google.android.gms.vision.FocusingProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * This class is used for filtering barcodes that appear outside the square.
 */
public class BarcodeCentralFocusingProcessor extends FocusingProcessor<Barcode> {

  /**
   * This value is used to enable/disable the central filter (filter to accept only barcodes that
   * completely inside the scanner square).
   * We only enable the central filter for rear-facing camera and disable it for front-facing camera
   * (used in case the device doesn't have a rear-facing camera).
   */
  private final boolean mCentralFilterEnabled;

  public BarcodeCentralFocusingProcessor(
      Detector<Barcode> detector,
      Tracker<Barcode> tracker,
      Boolean centralFilterEnabled) {
    super(detector, tracker);
    mCentralFilterEnabled = centralFilterEnabled;
  }

  /**
   * We only accept the barcode inside the scanner square. Note that the return dimensions for the
   * barcodes is based on the camera's coordinates, so we must convert it to the canvas' coordinate
   * for comparing with the scanner square.
   */
  @Override
  public int selectFocus(Detections<Barcode> detections) {
    SparseArray<Barcode> barcodes = detections.getDetectedItems();
    for (int i = 0; i < barcodes.size(); ++i) {
      int id = barcodes.keyAt(i);
      if (!mCentralFilterEnabled
          || BarcodeCentralFilter.barcodeIsInsideScannerSquare(barcodes.valueAt(i))) {
        return id;
      }
    }
    return -1;
  }
}
