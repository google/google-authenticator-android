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

import android.graphics.Rect;
import com.google.android.apps.authenticator.barcode.preview.GraphicOverlay;
import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * This class provides some userful functions for drawing the center square as well as filtering
 * barcodes that are not in the square center.
 */
public class BarcodeCentralFilter {

  // Square has 65% size to the width
  private static final float SQUARE_SIZE_PERCENT = 0.65f;

  // Padding for 3% of the square's size
  private static final float EXTRA_PADDING_SQUARE_PERCENT = 0.03f;

  // This is the frame of the square for filtering barcodes.
  private static Rect mSavedSquareFrame;

  // We save the width and height of the canvas layout for calculating in the barcode filter.
  private static int mCanvasWidth;
  private static int mCanvasHeight;

  // We save the width and height of the camera preview size for calculating in the barcode filter.
  private static Size mCameraSize;

  /**
   * This function return the frame for the square which is drawn on the {@link GraphicOverlay} of
   * {@link BarcodeCaptureActivity}, which is used for drawing the dark border outside the square.
   *
   * @param width the with of the canvas
   * @param height the height of the canvas
   * @return the frame for the square
   */
  public static Rect getSquareFrame(int width, int height) {
    int squareSize = (int) (Math.min(width, height) * SQUARE_SIZE_PERCENT);
    return new Rect(
        (width - squareSize) / 2,
        (height - squareSize) / 2,
        (width + squareSize) / 2,
        (height + squareSize) / 2);
  }

  /**
   * This function return the extra frame for the square which is drawn on the {@link
   * GraphicOverlay} as we actually have some padding between the scanner square bitmap. This frame
   * is used for drawing the actual scanner square bitmap.
   *
   * @param width the with of the canvas
   * @param height the height of the canvas
   * @return the frame for the square
   */
  public static Rect getExtraSquareFrame(int width, int height) {
    int squareSize = (int) (Math.min(width, height) * SQUARE_SIZE_PERCENT);
    int extraPadding = (int) (squareSize * EXTRA_PADDING_SQUARE_PERCENT);
    mSavedSquareFrame = new Rect(
        (width - squareSize) / 2 - extraPadding,
        (height - squareSize) / 2 - extraPadding,
        (width + squareSize) / 2 + extraPadding,
        (height + squareSize) / 2 + extraPadding);

    mCanvasWidth = width;
    mCanvasHeight = height;

    return mSavedSquareFrame;
  }

  /**
   * Checking if the barcode is inside the scanner square or not. As the found barcode's dimensions
   * is in camera's coordinates, we must convert it to canvas' coordinates.
   */
  public static boolean barcodeIsInsideScannerSquare(Barcode barcode) {
    // When the savedSquareFrame or mCameraSize is not initialized yet, which means the layout is
    // not drawn, return false.
    if (mSavedSquareFrame == null || mCameraSize == null) {
      return false;
    }

    Rect barcodeFrameCamera = barcode.getBoundingBox();
    int left = barcodeFrameCamera.left;
    int top = barcodeFrameCamera.top;
    int right = barcodeFrameCamera.right;
    int bottom = barcodeFrameCamera.bottom;

    int cameraWidth = mCameraSize.getWidth();
    int cameraHeight = mCameraSize.getHeight();

    // Convert to canvas' coordinates
    Rect barcodeFrameCanvas = new Rect(
        left * mCanvasWidth / cameraWidth,
        top * mCanvasHeight / cameraHeight,
        right * mCanvasWidth / cameraWidth,
        bottom * mCanvasHeight / cameraHeight);

    return mSavedSquareFrame.contains(barcodeFrameCanvas);
  }

  /**
   * Store the camera actual size for calculating in the barcode filter.
   *
   * @param size preview size of the camera
   */
  public static void storeCameraSize(Size size) {
    mCameraSize = size;
  }
}
