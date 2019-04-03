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

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * Tracker used to receive notifications for a detected barcode over time. It can notify others
 * about detected barcodes using the {@link OnDetectionListener}.
 */
class BarcodeTracker extends Tracker<Barcode> {

  /**
   * Listener interface used to supply data from a detected barcode.
   */
  public interface OnDetectionListener {

    /**
     * Called when a barcode is detected.
     *
     * The ID represents the specific detected barcode. If two different calls to this method
     * contain the same ID, it's because it has been determined that the new barcode was the same
     * barcode that was detected before.
     *
     * @param id ID representing the specified barcode.
     * @param barcode the barcode detected.
     */
    void onNewDetection(int id, Barcode barcode);
  }

  private OnDetectionListener onDetectionListener;

  /**
   * Sets a detection listener.
   */
  public void setDetectionListener(OnDetectionListener onDetectionListener) {
    this.onDetectionListener = onDetectionListener;
  }

  @Override
  public void onNewItem(int id, Barcode barcode) {
    if (onDetectionListener != null) {
      onDetectionListener.onNewDetection(id, barcode);
    }
  }
}
